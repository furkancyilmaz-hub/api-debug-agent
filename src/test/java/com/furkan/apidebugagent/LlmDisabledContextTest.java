package com.furkan.apidebugagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "llm.enabled=false")
class LlmDisabledContextTest {

    @Test
    void shouldStartContextWhenLlmDisabled() {
    }

}
