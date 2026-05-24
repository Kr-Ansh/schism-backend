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

        log.info("Processing room action allocation request for player : {}", request.getPlayerId());
        RoomActionResponse response = matchmakingService.handleMatchmaking(request);


        // IF IT'S A SOLO ROBOT MATCH: Only talk back directly to the player who requested it!
        if (request.isVsRobot()) {
            String soloTopic = "/topic/room/status/" + request.getPlayerId();
            log.info("Isolating solo robot match to target channel: {}", soloTopic);
            messagingTemplate.convertAndSend(soloTopic, response);
            return;
        }
        // IF IT'S A 1v1 MATCH THAT JUST STARTED: Alert BOTH matched players simultaneously
        // Instead of using @SendToUser (which breaks on the cloud without Principal management),
        // we explicitly broadcast matchmaking updates directly to the room's shared topic channel.
        String roomCode = response.getRoomCode() != null ? response.getRoomCode() : request.getRoomCode();
        String roomTopic = "/topic/room/" + roomCode;

        if ("MATCH_START".equals(response.getStatus())) {
            GameSession session = matchmakingService.getSessionTrackingId(response.getSessionId());

            RoomActionResponse hostAlert = RoomActionResponse.builder()
                    .sessionId(session.getSessionId())
                    .roomCode(response.getRoomCode())
                    .status("MATCH_START")
                    .activeTurnPlayerId(session.getCurrentTurnPlayerId())
                    .message("An adversarial connection has compromised your lobby. Game initialized.")
                    .build();

            // Broadcast MATCH_START directly down the shared room channel so BOTH players transition simultaneously!
            log.info("Broadcasting MATCH_START payload to shared channel: {}", roomTopic);
            messagingTemplate.convertAndSend(roomTopic, hostAlert);
        } else {
            // Otherwise, send the basic "WAITING_FOR_PLAYER" state back down to the host who just opened the lobby
            log.info("Broadcasting WAITING_FOR_PLAYER payload to channel: {}", roomTopic);
            messagingTemplate.convertAndSend("/topic/room/status", response);
        }
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