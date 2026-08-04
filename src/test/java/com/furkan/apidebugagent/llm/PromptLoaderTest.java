package com.furkan.apidebugagent.llm;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptLoaderTest {

    private final PromptLoader promptLoader = new PromptLoader(new DefaultResourceLoader());

    @Test
    void shouldRenderTemplateWithSubstitutedVariable() {
        String rendered = promptLoader.render("greeting", Map.of("name", "World"));

        assertThat(rendered).isEqualTo("Hello, World!" + System.lineSeparator());
    }

    @Test
    void shouldThrowPromptNotFoundExceptionWhenPromptDoesNotExist() {
        assertThatThrownBy(() -> promptLoader.render("does-not-exist", Map.of()))
            .isInstanceOf(PromptNotFoundException.class)
            .hasMessageContaining("does-not-exist");
    }

}
