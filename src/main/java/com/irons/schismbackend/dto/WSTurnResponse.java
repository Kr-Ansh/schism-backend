package com.irons.schismbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WSTurnResponse {

    private String targetSessionId;   // Context safety verification id
    private String activeTurnPlayerId; // Explicitly flashes whose turn it is now

    // Interrogation Clues (Null if it was the opponent's turn)
    private Integer secureCount;
    private Integer corruptedCount;

    // Paradox Footprints
    private int elementsLeaked;       // Quantified raw leak footprint metrics

    private boolean matchTerminated;  // Win condition binary flag
    private String winnerPlayerId;    // Winner identification string
    private String securityLogMessage; // Immersive poetic psychological alert strings or Gemini taunts

    private List<Integer> playerASecretSolution;
    private List<Integer> playerBSecretSolution;
}
