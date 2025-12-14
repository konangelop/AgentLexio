package com.kensai.sandbox.lexio.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kensai.sandbox.lexio.ai.assistant.VocabularyGenerator;
import com.kensai.sandbox.lexio.web.dto.TopicAssessment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyGenerationService {

    private final VocabularyGenerator vocabularyGenerator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record GeneratedQuestion(
        String sentenceWithBlank,
        String completeSentence,
        String targetWord,
        String englishWord,
        String englishTranslation
    ) {}

    public TopicAssessment assessTopic(String topic) {
        log.info("Assessing topic difficulty: {}", topic);
        try {
            String response = vocabularyGenerator.assessTopicLevel(topic);
            log.debug("Topic assessment response: {}", response);

            // Extract JSON from response (handle markdown code blocks)
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);

            String level = node.has("level") ? node.get("level").asText() : "A1";
            String reasoning = node.has("reasoning") ? node.get("reasoning").asText() : "";
            String simplerTopic = node.has("simplerTopic") && !node.get("simplerTopic").isNull()
                ? node.get("simplerTopic").asText() : null;

            return new TopicAssessment(topic, level, reasoning, simplerTopic);
        } catch (Exception e) {
            log.error("Error assessing topic: {}", topic, e);
            // Default to A2 on error to be safe
            return new TopicAssessment(topic, "A2", "Could not assess topic", null);
        }
    }

    public List<GeneratedQuestion> generateQuestions(String topic, String level, int count) {
        log.info("Generating {} questions for topic '{}' at level {}", count, topic, level);
        try {
            String response = vocabularyGenerator.generateVocabularySentences(topic, level, count);
            log.debug("Generated questions response: {}", response);

            // Extract JSON from response
            String json = extractJson(response);
            List<GeneratedQuestion> questions = objectMapper.readValue(json, new TypeReference<>() {});

            log.info("Successfully generated {} questions", questions.size());
            return questions;
        } catch (Exception e) {
            log.error("Error generating questions for topic: {}", topic, e);
            return generateFallbackQuestions(count);
        }
    }

    private String extractJson(String response) {
        // Remove markdown code blocks if present
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    private List<GeneratedQuestion> generateFallbackQuestions(int count) {
        List<GeneratedQuestion> fallback = new ArrayList<>();
        fallback.add(new GeneratedQuestion(
            "Guten ___, wie geht es Ihnen?",
            "Guten Tag, wie geht es Ihnen?",
            "Tag", "day",
            "Good ___, how are you?"
        ));
        fallback.add(new GeneratedQuestion(
            "Ich ___ Deutsch.",
            "Ich lerne Deutsch.",
            "lerne", "learn",
            "I ___ German."
        ));
        fallback.add(new GeneratedQuestion(
            "Das ___ ist sehr schön heute.",
            "Das Wetter ist sehr schön heute.",
            "Wetter", "weather",
            "The ___ is very nice today."
        ));
        return fallback.subList(0, Math.min(count, fallback.size()));
    }
}
