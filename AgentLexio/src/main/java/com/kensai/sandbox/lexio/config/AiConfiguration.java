package com.kensai.sandbox.lexio.config;

import com.kensai.sandbox.lexio.ai.assistant.LexioAssistant;
import com.kensai.sandbox.lexio.ai.tools.VocabularyExerciseTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfiguration {

    @Bean
    public LexioAssistant lexioAssistant(
            ChatModel chatModel,
            VocabularyExerciseTools vocabularyExerciseTools) {

        return AiServices.builder(LexioAssistant.class)
                .chatModel(chatModel)
                .tools(vocabularyExerciseTools)
                .chatMemory(MessageWindowChatMemory.builder()
                        .maxMessages(50)
                        .build())
                .build();
    }
}
