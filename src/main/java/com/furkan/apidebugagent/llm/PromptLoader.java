package com.furkan.apidebugagent.llm;

import java.util.Map;
import java.util.Objects;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class PromptLoader {

    private static final String LOCATION_PREFIX = "classpath:prompts/";
    private static final String EXTENSION = ".st";

    private final ResourceLoader resourceLoader;

    public PromptLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String render(String promptName, Map<String, Object> vars) {
        Resource resource = resourceLoader.getResource(LOCATION_PREFIX + promptName + EXTENSION);
        if (!resource.exists()) {
            throw new PromptNotFoundException(promptName);
        }
        PromptTemplate promptTemplate = new PromptTemplate(resource);
        return promptTemplate.render(Objects.requireNonNullElse(vars, Map.of()));
    }

}