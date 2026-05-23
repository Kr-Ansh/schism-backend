package com.irons.schismbackend.core.model;

import java.util.List;
import java.util.stream.Collectors;

public record GameCode(List<Integer> digits) {

    public GameCode {

        if(digits == null || digits.size() != 4) {
            throw new IllegalArgumentException("A Schism game code must be exactly 4 digits.");
        }
        if(digits.stream().anyMatch(d -> d < 1 || d > 6)) {
            throw new IllegalArgumentException("Digits must be strictly between 1 and 6.");
        }
        if(digits.stream().distinct().count() != 4) {
            throw new IllegalArgumentException("Digits within a profile code cannot repeat.");
        }
    }

    @Override
    public String toString() {
        return digits.stream().map(String::valueOf).collect(Collectors.joining("-"));
    }
}
