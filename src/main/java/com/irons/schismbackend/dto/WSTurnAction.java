package com.irons.schismbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WSTurnAction {

    private String sessionId; // Target match reference UUID
    private String activePlayerId; // Device player execution fingerprint
    private List<Integer> injectedGuess; // The 4-digit sequence being deployed (1-6)
    private boolean isVsRobot; // Structural routing flag targeting Gemini mode
}
