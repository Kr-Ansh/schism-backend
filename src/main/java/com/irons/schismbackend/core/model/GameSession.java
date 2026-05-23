package com.irons.schismbackend.core.model;

import lombok.Getter;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class GameSession {

    @Getter
    private final String sessionId;
    @Getter
    private final String playerAId;
    @Getter
    private final String playerBId;

    @Getter
    private final GameCode playerASecret;
    @Getter
    private final GameCode playerBSecret;

    private final StringBuilder matchHistoryLog = new StringBuilder("=== INITIAL ENCRYPTION ENGAGED ===\n");

    // Thread-safe state track variables
    private final AtomicReference<String> currentTurnPlayerId;
    private final AtomicBoolean isGameOver;
    private final AtomicReference<String> winnerPlayerId;

    public GameSession(String playerAId, String playerBId, GameCode playerASecret, GameCode playerBSecret) {

        this.sessionId = UUID.randomUUID().toString();
        this.playerAId = playerAId;
        this.playerBId = playerBId;
        this.playerASecret = playerASecret;
        this.playerBSecret = playerBSecret;

        // Player A defaults to the first moves
        this.currentTurnPlayerId = new AtomicReference<>(playerAId);
        this.isGameOver = new AtomicBoolean(false);
        this.winnerPlayerId = new AtomicReference<>(null);
    }

    // Thread-safe method to alternate turns
    public void switchTurn() {
        currentTurnPlayerId.updateAndGet( currentId ->
                currentId.equals(playerAId) ? playerBId : playerAId
        );
    }

    public void endGame(String winnerId) {
        isGameOver.set(true);
        winnerPlayerId.set(winnerId);
    }

    public synchronized void appendHistory(String logEntry) {
        this.matchHistoryLog.append(logEntry).append("\n");
    }

    public synchronized String getMatchHistoryLog() { return this.matchHistoryLog.toString(); }
    public String getCurrentTurnPlayerId() { return currentTurnPlayerId.get(); }
    public boolean isGameOver() { return isGameOver.get(); }
    public String getWinnerPlayerId() { return winnerPlayerId.get(); }
}
