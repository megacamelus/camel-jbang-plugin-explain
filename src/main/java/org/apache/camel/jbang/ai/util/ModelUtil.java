package org.apache.camel.jbang.ai.util;

import java.time.Duration;

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

public final class ModelUtil {
    public static OpenAiStreamingChatModel buildModel(String url, String apiKey, String modelName) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(url)
                .apiKey(apiKey)
                .timeout(Duration.ofMinutes(2))
                .maxTokens(Integer.MAX_VALUE)
                .modelName(modelName).build();
    }
}
