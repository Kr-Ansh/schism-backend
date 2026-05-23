package com.irons.schismbackend.service;

import com.irons.schismbackend.core.engine.CodeGenerator;
import com.irons.schismbackend.core.model.GameSession;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameSessionService {

    // Safe thread-isolated dictionary keeping track of active game spaces
    private final Map<String, GameSession> activeSessions = new ConcurrentHashMap<>();

    // Provisions a fresh, randomized session state between two unique player profiles.
    public GameSession createGame(String playerAId, String playerBId) {
        GameSession newSession = new GameSession(
                playerAId,
                playerBId,
                CodeGenerator.generateMask(),
                CodeGenerator.generateMask()
        );

        activeSessions.put(newSession.getSessionId(), newSession);
        return newSession;
    }

    // Looks up an ongoing session by its string identifier.
    public Optional<GameSession> getSession(String sessionId) {
        return Optional.ofNullable(activeSessions.get(sessionId));
    }

    // Purges a session from the system state mapping once a match settles or drops out.
    public void terminateSession(String sessionId) {
        activeSessions.remove(sessionId);
    }
}
