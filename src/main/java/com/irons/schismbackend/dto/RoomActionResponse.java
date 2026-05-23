package com.irons.schismbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomActionResponse {

    private String sessionId;  // The actual backend GameSession UUID
    private String roomCode;   // The human-readable 5-character string (e.g., "K7X9R")
    private String status;     // "WAITING_FOR_PLAYER", "MATCH_START", or "AI_READY"
    private String message;    // Informational alert string
}
