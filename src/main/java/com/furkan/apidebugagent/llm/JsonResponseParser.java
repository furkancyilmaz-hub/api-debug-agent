package com.furkan.apidebugagent.llm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonResponseParser {

    private static final Pattern CODE_FENCE = Pattern.compile("^```(?:json)?\\s*\\n?(.*?)\\n?```$", Pattern.DOTALL);
    private static final int SNIPPET_LENGTH = 200;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonNode parse(String rawText) {
        String candidate = stripCodeFence(rawText.trim());
        try {
            return objectMapper.readTree(candidate);
        }
        catch (JsonProcessingException e) {
            throw new LlmJsonParseException("Model response is not valid JSON: " + snippet(candidate), e);
        }
    }

    private String stripCodeFence(String text) {
        Matcher matcher = CODE_FENCE.matcher(text);
        return matcher.matches() ? matcher.group(1).trim() : text;
    }

    private String snippet(String text) {
        return text.length() <= SNIPPET_LENGTH ? text : text.substring(0, SNIPPET_LENGTH) + "...";
    }

}