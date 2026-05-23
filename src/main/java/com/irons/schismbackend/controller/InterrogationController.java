package com.irons.schismbackend.controller;

import com.irons.schismbackend.core.engine.ParadoxEngine;
import com.irons.schismbackend.core.model.EvaluationResult;
import com.irons.schismbackend.core.model.GameCode;
import com.irons.schismbackend.dto.GameGuessRequest;
import com.irons.schismbackend.dto.GameTurnResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/game")
public class InterrogationController {

    @PostMapping("/interrogate")
    public ResponseEntity<GameTurnResponse> processTurn(@RequestBody GameGuessRequest request) {
        try {
            GameCode guess = new GameCode(request.guessDigits());
            GameCode opponentSecret = new GameCode(request.opponentSecret());
            GameCode playerSecret = new GameCode(request.playerOwnSecret());

            EvaluationResult evaluation = ParadoxEngine.evaluateTurn(guess, opponentSecret, playerSecret);

            GameTurnResponse response = new GameTurnResponse(
                    evaluation.secureCount(),
                    evaluation.corruptedCount(),
                    evaluation.leakCount(),
                    evaluation.isPerfectMatch(),
                    evaluation.isPerfectMatch() ? "Target fully compromised. Connection closed." : "Data Processed."
            );

            return ResponseEntity.ok(response);
        } catch(IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new GameTurnResponse(
                    0, 0, 0, false, "Protocol violation: " + e.getMessage()
            ));
        }
    }
}
