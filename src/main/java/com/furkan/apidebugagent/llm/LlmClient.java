package com.furkan.apidebugagent.llm;

import java.util.Map;

public interface LlmClient {

    String provider();

    LlmResult ask(String promptName, Map<String, Object> vars);

}
