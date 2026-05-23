package com.irons.schismbackend.service;

import com.irons.schismbackend.core.engine.CodeGenerator;
import com.irons.schismbackend.core.model.GameSession;
import com.irons.schismbackend.dto.RoomActionRequest;
import com.irons.schismbackend.dto.RoomActionResponse;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MatchmakingService {

    // Map custom 5-character Room Codes to live GameSession objects
    private final Map<String, GameSession> activeRooms = new ConcurrentHashMap<>();

    // Tracks rooms waiting for a second player to join
    private final Map<String, String> waitingRooms = new ConcurrentHashMap<>(); // RoomCode -> HostPlayerId

    private final Random random = new Random();

    // Entry-point for match provisioning. Routes directly between Solo AI and Room Codes.
    public RoomActionResponse handleMatchmaking(RoomActionRequest request) {

        // Mode 1: Solo vs AI
        if(request.isVsRobot()) {
            GameSession aiSession = new GameSession(
                    request.getPlayerId(),
                    "ROBOT_ALPHA",
                    CodeGenerator.generateMask(),
                    CodeGenerator.generateMask()
            );
            // Save directly into active rooms map under a dummy code or trace it via sessionId later
            activeRooms.put(aiSession.getSessionId(), aiSession);

            return RoomActionResponse.builder()
                    .sessionId(aiSession.getSessionId())
                    .status("AI_READY")
                    .message("Adversary initialized. System online.")
                    .build();
        }

        // Mode 2: Join Existing Room
        if(request.getRoomCode() != null && !request.getRoomCode().trim().isEmpty()) {

            String sanitizedCode = request.getRoomCode().toUpperCase().trim();

            if(!waitingRooms.containsKey(sanitizedCode)) {
                return RoomActionResponse.builder()
                        .status("ERROR")
                        .message("Room code invalid or expired")
                        .build();
            }

            String hostPlayerId = waitingRooms.remove(sanitizedCode);

            GameSession interactiveSession = new GameSession(
                    hostPlayerId,
                    request.getPlayerId(),
                    CodeGenerator.generateMask(),
                    CodeGenerator.generateMask()
            );

            activeRooms.put(sanitizedCode, interactiveSession);
            activeRooms.put(interactiveSession.getSessionId(), interactiveSession); // map both for fast lookup

            return RoomActionResponse.builder()
                    .sessionId(interactiveSession.getSessionId())
                    .roomCode(sanitizedCode)
                    .status("MATCH_START")
                    .message("Secure tunner established. Player B joined")
                    .build();
        }

        // Mode 3: Host/Generate New Room Code
        String generatedCode = generateAlphaNumericCode();
        waitingRooms.put(generatedCode, request.getPlayerId());

        return RoomActionResponse.builder()
                .roomCode(generatedCode)
                .status("WAITING_FOR_PLAYER")
                .message("Room created. Share your access key.")
                .build();
    }

    public GameSession getSessionTrackingId(String locatorId) {
        return activeRooms.get(locatorId);
    }

    private String generateAlphaNumericCode() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        for(int i=0; i<5; i++) {
            code.append(characters.charAt(random.nextInt(characters.length())));
        }
        return code.toString();
    }
}
