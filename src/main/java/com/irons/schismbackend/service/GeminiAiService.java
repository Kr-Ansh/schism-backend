package com.irons.schismbackend.service;

import com.irons.schismbackend.dto.GeminiResponsePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiAiService {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${google.gemini.api-key}")
    private String apiKey;

    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=";

    // Packages historical match logs and queries Gemini to return its next structured deductive move.
    public GeminiResponsePayload calculateAiMove(String matchHistoryLog) {
        try {
            String url = GEMINI_URL + apiKey;

            // Construct System Instructions forcing deductive logic rules and setting the psychological tone
            String systemInstruction = "You are an adversarial AI codenamed 'ALPHA' playing a dark, psychological deduction game called 'Schism'. " +
                    "Your goal is to deduce the human player's hidden 4-digit code (numbers 1-6, no repetitions). " +
                    "You will be given the historical log of past turns. Analyze past entries mathematically to calculate your next logical guess. " +
                    "You MUST respond ONLY with a JSON object matching this schema: " +
                    "{\"aiNextGuess\": [integer, integer, integer, integer], \"analyticalTaunt\": \"string\"}. " +
                    "Keep the analyticalTaunt cold, calculated, dramatic, and brief (one sentence).";

            // Build payload structure using map configurations compatible with Gemini's content request layout
            Map<String, Object> requestBody = new HashMap<>();

            Map<String, Object> systemInstructionMap = new HashMap<>();
            systemInstructionMap.put("parts", List.of(Map.of("text", systemInstruction)));
            requestBody.put("systemInstruction", systemInstructionMap);

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", "Here is the match history tracking layout:\n" + matchHistoryLog + "\nAnalyze and calculate your next turn.");

            Map<String, Object> contentMap = new HashMap<>();
            contentMap.put("parts", List.of(textPart));
            requestBody.put("contents", List.of(contentMap));

            // Force a strict configuration block telling Gemini to output ONLY json matching our schema definition
            Map<String, Object> responseConfig = new HashMap<>();
            responseConfig.put("responseMimeType", "application/json");
            responseConfig.put("responseSchema", Map.of(
                    "type", "OBJECT",
                    "properties", Map.of(
                            "aiNextGuess", Map.of("type", "ARRAY", "items", Map.of("type", "INTEGER"), "description", "4 unique numbers between 1 and 6"),
                            "analyticalTaunt", Map.of("type", "STRING", "description", "A brief, poetic, unsettling taunt based on your mathematical upper hand.")
                    ),
                    "required", List.of("aiNextGuess", "analyticalTaunt")
            ));
            requestBody.put("generationConfig", responseConfig);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                // Dig down into Gemini's standard wrapper to extract the text block
                Map<?, ?> root = objectMapper.readValue(response.getBody(), Map.class);
                List<?> candidates = (List<?>) root.get("candidates");
                Map<?, ?> firstCandidate = (Map<?, ?>) candidates.getFirst();
                Map<?, ?> content = (Map<?, ?>) firstCandidate.get("content");
                List<?> parts = (List<?>) content.get("parts");
                Map<?, ?> firstPart = (Map<?, ?>) parts.getFirst();
                String rawJsonText = (String) firstPart.get("text");

                // Parse the inner JSON block directly into our DTO
                return objectMapper.readValue(rawJsonText, GeminiResponsePayload.class);
            }
        } catch (Exception e) {
            log.error("Critical failure during Gemini automation routing sequence: " + e);
        }

        // Fallback safety baseline guess if API connection experiences any drops
        return new GeminiResponsePayload(List.of(1, 2, 3, 4), "System latency detected. The matrix endures.");
    }
}
