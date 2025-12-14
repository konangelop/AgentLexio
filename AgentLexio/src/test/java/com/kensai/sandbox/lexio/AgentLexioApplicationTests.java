package com.kensai.sandbox.lexio;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "langchain4j.anthropic.chat-model.api-key=test-key"
})
class AgentLexioApplicationTests {

    @Test
    void contextLoads() {
    }
}
