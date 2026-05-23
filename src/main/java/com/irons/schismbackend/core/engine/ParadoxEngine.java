package com.irons.schismbackend.core.engine;

import com.irons.schismbackend.core.model.EvaluationResult;
import com.irons.schismbackend.core.model.GameCode;

import java.util.List;
import java.util.stream.IntStream;

public class ParadoxEngine {

    public static EvaluationResult evaluateTurn(GameCode guesserQuery, GameCode defenderSecret, GameCode guesserSecret) {

        List<Integer> queryDigits = guesserQuery.digits();
        List<Integer> defenderDigits = defenderSecret.digits();
        List<Integer> guesserDigits = guesserSecret.digits();

        // 1. Calculate Secure Matches (Correct digit and correct position)
        int secureCount = (int) IntStream.range(0, 4)
                .filter(i -> queryDigits.get(i).equals(defenderDigits.get(i)))
                .count();

        // 2. Calculate Corrupted Matches (Correct digit but in the wrong position)
        int corruptedCount = (int) IntStream.range(0, 4)
                .filter(i -> !queryDigits.get(i).equals(defenderDigits.get(i))) // Exclude exact position matches
                .filter(i -> defenderDigits.contains(queryDigits.get(i))) // Must exist somewhere in target
                .count();

        // 3. Calculate The Paradox Leak (How many elements of their own profile did they type?)
        int leakCount = (int) queryDigits.stream()
                .filter(guesserDigits::contains) // Checks intersection with their own layout
                .count();

        return new EvaluationResult(secureCount, corruptedCount, leakCount);
    }
}
