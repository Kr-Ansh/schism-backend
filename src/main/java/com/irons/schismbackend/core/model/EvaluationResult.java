package com.irons.schismbackend.core.model;

public record EvaluationResult(
        int secureCount, // Correct number, correct position
        int corruptedCount, // Correct number, incorrect position
        int leakCount // Number of digits leaked back to opponent
) {
    public boolean isPerfectMatch() {
        return secureCount == 4;
    }
}
