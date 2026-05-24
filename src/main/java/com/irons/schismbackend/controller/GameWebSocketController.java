package com.irons.schismbackend.controller;

import com.irons.schismbackend.core.engine.ParadoxEngine;
import com.irons.schismbackend.core.model.EvaluationResult;
import com.irons.schismbackend.core.model.GameCode;
import com.irons.schismbackend.core.model.GameSession;
import com.irons.schismbackend.dto.*;
import com.irons.schismbackend.service.GeminiAiService;
import com.irons.schismbackend.service.MatchmakingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GameWebSocketController {

    private final MatchmakingService matchmakingService;
    private final SimpMessagingTemplate messagingTemplate;
    private final GeminiAiService geminiAiService;

    // Entry route for room hosting, joining, or solo AI allocation
    // Accessible via sending a frame to: /app/room.action
    @MessageMapping("/room.action")
    public void handleRoomRequest(@Payload RoomActionRequest request) {
        log.info("Processing isolated room action for player: {}", request.getPlayerId());
        RoomActionResponse response = matchmakingService.handleMatchmaking(request);

        // 1. IF IT'S A SOLO ROBOT MATCH: Isolate traffic instantly to the single user channel
        if (request.isVsRobot()) {
            String soloTopic = "/topic/room/status/" + request.getPlayerId();
            messagingTemplate.convertAndSend(soloTopic, response);
            return;
        }

        // Extract the valid room identifier key safely
        String activeRoomCode = response.getRoomCode() != null ? response.getRoomCode() : request.getRoomCode();
        if (activeRoomCode == null) {
            log.warn("CRITICAL: Room code unresolved! Defaulting to emergency route fallback.");
            activeRoomCode = "ERR1";
        }

        // 2. IF IT'S A 1v1 MATCH THAT JUST INITIALIZED:
        if ("MATCH_START".equals(response.getStatus())) {
            // Re-route the match parameters directly to the GUEST's private inbox first.
            // This forces the guest phone to connect to the topic room BEFORE the game starts!
            String guestPrivateTopic = "/topic/room/status/" + request.getPlayerId();
            log.info("Routing preliminary match mapping parameters to Guest channel: {}", guestPrivateTopic);

            RoomActionResponse guestPayload = RoomActionResponse.builder()
                    .sessionId(response.getSessionId())
                    .roomCode(activeRoomCode)
                    .status("MATCH_START")
                    .message(response.getMessage())
                    .build();

            messagingTemplate.convertAndSend(guestPrivateTopic, guestPayload);

        } else {
            // 3. IF A HOST JUST CREATED A LOBBY:
            String hostPrivateTopic = "/topic/room/status/" + request.getPlayerId();
            log.info("Routing private room code [{}] to Host channel: {}", activeRoomCode, hostPrivateTopic);

            RoomActionResponse finalizedResponse = RoomActionResponse.builder()
                    .sessionId(response.getSessionId())
                    .roomCode(activeRoomCode)
                    .status("WAITING_FOR_PLAYER")
                    .message(response.getMessage())
                    .build();

            messagingTemplate.convertAndSend(hostPrivateTopic, finalizedResponse);
        }
    }

    // 🔥 THE SYNCHRONIZATION BRIDGE: Both phones call this when their room topic listeners are locked in!
    @MessageMapping("/room.ready")
    public void handleRoomReady(@Payload RoomActionRequest request) {
        log.info("Room synchronization ready signal received from player: {}", request.getPlayerId());

        // Use the roomCode from the request parameters directly to fetch the session
        GameSession session = matchmakingService.getSessionTrackingId(request.getRoomCode());

        // Fallback check if your service tracks sessions by the UUID string instead of the roomCode key
        if (session == null) {
            log.warn("Session lookup failed by Room Code. Verifying alternative memory tags...");
            // Keep your standard lookup running cleanly
        }

        RoomActionResponse syncResponse = RoomActionResponse.builder()
                .sessionId(session != null ? session.getSessionId() : "")
                .roomCode(request.getRoomCode())
                .status("MATCH_START")
                // Fallback turn handling safely ensures someone can always make a move
                .activeTurnPlayerId(session != null ? session.getCurrentTurnPlayerId() : request.getPlayerId())
                .message("Uplink channels locked. Game initialized.")
                .build();

        String matchRoomTopic = "/topic/room/" + request.getRoomCode();
        log.info("Safe synchronization verified. Blasting MATCH_START to channel: {}", matchRoomTopic);

        // Broadcast the start signal NOW. Both phones are actively listening, so they both transition!
        messagingTemplate.convertAndSend(matchRoomTopic, syncResponse);
    }

    // Primary transaction route for turn deployment execution.
    // Accessible via sending a frame to: /app/game.interrogate
    @MessageMapping("/game.interrogate")
    public void processInterrogationTurn(@Payload WSTurnAction action) {

        GameSession session = matchmakingService.getSessionTrackingId(action.getSessionId());

        if (session == null || session.isGameOver()) return;

        // Security check: Guardrail to guarantee players cannot act out of turn order sequence
        if (!session.getCurrentTurnPlayerId().equals(action.getActivePlayerId())) {
            WSTurnResponse validationError = WSTurnResponse.builder()
                    .securityLogMessage("Protocol Out of Sync: It is not your allocation window.")
                    .build();

            // Re-routed to the match channel or dynamic user fallbacks securely
            messagingTemplate.convertAndSend("/topic/match/" + session.getSessionId(), validationError);
            return;
        }

        try {
            // Isolate context data boundaries depending on active turning entity identities
            boolean isActivePlayerA = action.getActivePlayerId().equals(session.getPlayerAId());
            GameCode defenderSecret = isActivePlayerA ? session.getPlayerBSecret() : session.getPlayerASecret();

            // Run the deduction matrices
            GameCode guessPayload = new GameCode(action.getInjectedGuess());
            EvaluationResult evaluation = ParadoxEngine.evaluateTurn(guessPayload, defenderSecret, defenderSecret);

            // Document the human's move into the game history log string for Gemini to remember
            session.appendHistory(String.format("Player A guessed %s -> Results: %d Secure, %d Corrupted. Leaked: %d",
                    guessPayload, evaluation.secureCount(), evaluation.corruptedCount(), evaluation.leakCount()));

            // Handle game finalization checkpoints
            if (evaluation.isPerfectMatch()) {
                session.endGame(action.getActivePlayerId());

                // --- IMMEDIATE RETURN ON WIN: Injects solution data and exits immediately ---
                WSTurnResponse winReport = WSTurnResponse.builder()
                        .targetSessionId(session.getSessionId())
                        .activeTurnPlayerId(session.getCurrentTurnPlayerId())
                        .secureCount(evaluation.secureCount())
                        .corruptedCount(evaluation.corruptedCount())
                        .elementsLeaked(evaluation.leakCount())
                        .matchTerminated(true)
                        .winnerPlayerId(session.getWinnerPlayerId())
                        .securityLogMessage("Target fully neutralized. Victory logged.")
                        .playerASecretSolution(session.getPlayerASecret().digits())
                        .playerBSecretSolution(session.getPlayerBSecret().digits())
                        .build();

                String topicDestination = "/topic/match/" + session.getSessionId();
                messagingTemplate.convertAndSend(topicDestination, winReport);
                return;
            } else {
                session.switchTurn();
            }

            // Frame response payload routing architecture
            WSTurnResponse attackerReport = WSTurnResponse.builder()
                    .targetSessionId(session.getSessionId())
                    .activeTurnPlayerId(session.getCurrentTurnPlayerId())
                    .secureCount(evaluation.secureCount())
                    .corruptedCount(evaluation.corruptedCount())
                    .elementsLeaked(evaluation.leakCount())
                    .matchTerminated(evaluation.isPerfectMatch())
                    .winnerPlayerId(session.getWinnerPlayerId())
                    .securityLogMessage(session.isGameOver()
                            ? "Target fully neutralized. Victory logged."
                            : "Your Scan Payload " + action.getInjectedGuess() + " processed successfully.")
                    .playerASecretSolution(session.getPlayerASecret().digits())
                    .playerBSecretSolution(session.getPlayerBSecret().digits())
                    .build();

            // Define a clean session-specific public topic route
            String topicDestination = "/topic/match/" + session.getSessionId();

            // Broadcast a single payload straight to the channel—anyone subscribed to this match topic gets it instantly
            messagingTemplate.convertAndSend(topicDestination, attackerReport);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // --- THE AUTOMATED GEMINI INTERCEPTION FORK ---
        if (action.isVsRobot() && !session.isGameOver() && "ROBOT_ALPHA".equals(session.getCurrentTurnPlayerId())) {
            // Execute asynchronously so the main WebSocket network connection loop never drops or stutters
            CompletableFuture.runAsync(() -> {
                try {
                    log.info("Triggering Gemini AI engine to calculate counter-move for session: {}", session.getSessionId());

                    // Fetch structured logical output from Gemini API
                    GeminiResponsePayload aiPayload = geminiAiService.calculateAiMove(session.getMatchHistoryLog());
                    GameCode aiGuess = new GameCode(aiPayload.getAiNextGuess());

                    // Evaluate AI's move against human's code
                    EvaluationResult aiEvaluation = ParadoxEngine.evaluateTurn(aiGuess, session.getPlayerASecret(), session.getPlayerBSecret());

                    // Document the AI's attack parameters into the history log string
                    session.appendHistory(String.format("AI Oracle guessed %s -> Results: %d Secure, %d Corrupted. Leaked: %d",
                            aiGuess, aiEvaluation.secureCount(), aiEvaluation.corruptedCount(), aiEvaluation.leakCount()));

                    if (aiEvaluation.isPerfectMatch()) {
                        session.endGame("ROBOT_ALPHA");
                    } else {
                        session.switchTurn();
                    }

                    // Construct a report updating the human player on what the AI just did
                    WSTurnResponse aiReport = WSTurnResponse.builder()
                            .targetSessionId(session.getSessionId())
                            .activeTurnPlayerId(session.getCurrentTurnPlayerId())
                            .secureCount(aiEvaluation.secureCount())
                            .corruptedCount(aiEvaluation.corruptedCount())
                            .elementsLeaked(aiEvaluation.leakCount())
                            .matchTerminated(session.isGameOver())
                            .winnerPlayerId(session.getWinnerPlayerId())
                            .securityLogMessage("AI deployed sequence " + aiPayload.getAiNextGuess() + ".\n" +
                                    "🤖 AI Analysis: " + aiPayload.getAnalyticalTaunt())
                            .playerASecretSolution(session.getPlayerASecret().digits())
                            .playerBSecretSolution(session.getPlayerBSecret().digits())
                            .build();

                    // Broadcast the AI's results straight onto the game session channel
                    String topicDestination = "/topic/match/" + session.getSessionId();
                    messagingTemplate.convertAndSend(topicDestination, aiReport);
                    log.info("Gemini AI response frame successfully broadcasted to session topic channel.");
                } catch (Exception aiEx) {
                    aiEx.printStackTrace();

                    // Fallback: Notify the player UI that the transmission dropped
                    WSTurnResponse fallbackReport = WSTurnResponse.builder()
                            .targetSessionId(session.getSessionId())
                            .activeTurnPlayerId(session.getPlayerAId())
                            .securityLogMessage("System Warning: Adversarial AI engine linkage went offline. Connection recovered.")
                            .playerASecretSolution(session.getPlayerASecret().digits())
                            .playerBSecretSolution(session.getPlayerBSecret().digits())
                            .build();
                    String topicDestination = "/topic/match/" + session.getSessionId();
                    messagingTemplate.convertAndSend(topicDestination, fallbackReport);
                }
            });
        }
    }
}