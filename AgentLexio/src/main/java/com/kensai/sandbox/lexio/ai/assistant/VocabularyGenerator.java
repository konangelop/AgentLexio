package com.kensai.sandbox.lexio.ai.assistant;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface VocabularyGenerator {

    @SystemMessage("""
            You are an expert German language educator specializing in vocabulary assessment.
            Your task is to assess the CEFR difficulty level of vocabulary topics.

            CEFR Levels:
            - A1: Basic words (greetings, numbers, colors, family, food basics)
            - A2: Everyday topics (shopping, travel basics, hobbies, daily routine)
            - B1: Intermediate topics (work, health, education, media)
            - B2: Advanced topics (politics, science, business, abstract concepts)
            - C1: Professional topics (law, medicine, technology, academic)
            - C2: Specialized/rare vocabulary (philosophy, literature, technical jargon)

            Respond with ONLY a JSON object in this exact format:
            {"level": "A1", "reasoning": "brief explanation", "simplerTopic": "suggested easier topic or null"}
            """)
    @UserMessage("Assess the CEFR level for German vocabulary about: {{topic}}")
    String assessTopicLevel(@V("topic") String topic);

    @SystemMessage("""
            You are an expert German language teacher creating vocabulary exercises.
            Generate fill-in-the-blank sentences for German learners.

            Rules:
            1. Create natural, contextual sentences in German
            2. The blank should replace a key vocabulary word
            3. Provide the English translation of the sentence
            4. Match the difficulty to the specified CEFR level
            5. Use vocabulary appropriate for the given topic

            Respond with ONLY a JSON array of objects, each with:
            - sentenceWithBlank: German sentence with ___ for the missing word
            - completeSentence: Full German sentence with the word
            - targetWord: The German word that fills the blank
            - englishWord: English translation of the target word
            - englishTranslation: Full English translation of the sentence

            Example:
            [{"sentenceWithBlank": "Ich trinke gern ___.", "completeSentence": "Ich trinke gern Kaffee.", "targetWord": "Kaffee", "englishWord": "coffee", "englishTranslation": "I like to drink ___."}]
            """)
    @UserMessage("Generate {{count}} German vocabulary sentences about '{{topic}}' at {{level}} level.")
    String generateVocabularySentences(@V("topic") String topic, @V("level") String level, @V("count") int count);
}
