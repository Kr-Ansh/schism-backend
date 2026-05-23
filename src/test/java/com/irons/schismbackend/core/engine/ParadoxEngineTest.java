package com.irons.schismbackend.core.engine;

import com.irons.schismbackend.core.model.EvaluationResult;
import com.irons.schismbackend.core.model.GameCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParadoxEngineTest {

    @Test
    void testEvaluateTurn_ScenarioNormal() {

        GameCode playerASecret = new GameCode(List.of(4, 1, 5, 3));
        GameCode playerBSecret = new GameCode(List.of(2, 5, 3, 6));

        GameCode playerAGuess = new GameCode(List.of(2, 1, 6, 4));

        EvaluationResult result = ParadoxEngine.evaluateTurn(playerAGuess, playerBSecret, playerASecret);

        assertEquals(1, result.secureCount(), "Should have 1 exact match (the number 2)");
        assertEquals(1, result.corruptedCount(), "Should have 1 misplaced match (the number 6)");
        assertEquals(2, result.leakCount(), "Should leak 2 points because 1 and 4 belong to Player A");
        assertFalse(result.isPerfectMatch(), "Should not be a perfect winning match");
    }

    @Test
    void testEvaluateTurn_ScenarioPerfect() {

        GameCode playerASecret = new GameCode(List.of(4, 1, 5, 3));
        GameCode playerBSecret = new GameCode(List.of(2, 5, 3, 6));

        GameCode playerAGuess = new GameCode(List.of(2, 5, 3, 6));

        EvaluationResult result = ParadoxEngine.evaluateTurn(playerAGuess, playerBSecret, playerASecret);

        assertEquals(4, result.secureCount());
        assertEquals(0, result.corruptedCount());
        assertTrue(result.isPerfectMatch());
    }
}