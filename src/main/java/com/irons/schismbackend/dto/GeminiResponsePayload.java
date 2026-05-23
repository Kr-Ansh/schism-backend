package com.irons.schismbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeminiResponsePayload {

    private List<Integer> aiNextGuess; // Must be 4 unique digits between 1 and 6
    private String analyticalTaunt; // Cold, unsettling, psychological text
}
