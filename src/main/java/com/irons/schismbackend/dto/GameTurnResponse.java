package com.irons.schismbackend.dto;

public record GameTurnResponse(
        int secure, // Correct number, correct position
        int corrupted, // Correct number, incorrect position
        int leakedPoints, // Raw data points exposed back to the enemy
        boolean isWinner, // True if match criteria reached (4 Secure)
        String systemMessage // General status context or terminal notifications
) {
}
