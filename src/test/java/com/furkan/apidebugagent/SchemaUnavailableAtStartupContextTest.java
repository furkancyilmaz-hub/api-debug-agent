package com.furkan.apidebugagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "target.base-url=http://localhost:1")
class SchemaUnavailableAtStartupContextTest {

    @Test
    void shouldStartContextWhenDemoApiUnreachable() {
    }

}
