package com.furkan.apidebugagent.llm;

import java.util.Collection;

public class UnknownLlmProviderException extends LlmException {

    public UnknownLlmProviderException(String provider, Collection<String> knownProviders) {
        super("Unknown llm.provider: " + provider + "; known providers: " + knownProviders);
    }

}
