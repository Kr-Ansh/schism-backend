package com.irons.schismbackend.dto;

import java.util.List;

public record GameGuessRequest(
        List<Integer> guessDigits, // The 4 numbers the player is guessing
        List<Integer> opponentSecret, // The target hidden profile being interrogated
        List<Integer> playerOwnSecret // The player's own profile to calculate leak footprints
) {
}
