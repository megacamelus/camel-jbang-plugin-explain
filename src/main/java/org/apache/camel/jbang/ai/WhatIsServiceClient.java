package org.apache.camel.jbang.ai;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;

import static java.util.stream.Collectors.joining;

public class WhatIsServiceClient {

    private final String url;
    private final String apiKey;
    private final String modelName;
    private final String userPrompt;
    private final String systemPrompt;
    private final String what;
    private final String host;
    private final int port;
    private final String collectionName;
    private static final PromptTemplate PROMPT_TEMPLATE = PromptTemplate.from(
            "Answer the following question to the best of your ability:\n"
                    + "\n"
                    + "Question:\n"
                    + "{{question}}\n"
                    + "\n"
                    + "Base your answer on the following information:\n"
                    + "{{information}}");

    public WhatIsServiceClient(
            String url, String apiKey, String modelName, String userPrompt, String systemPrompt, String what, String host,
            int port, String collectionName) {
        this.url = url;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.userPrompt = userPrompt;
        this.systemPrompt = systemPrompt;
        this.what = what;
        this.host = host;
        this.port = port;
        this.collectionName = collectionName;
    }

    public int run() throws InterruptedException {
        OpenAiStreamingChatModel chatModel = buildModel(modelName);

        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        Embedding questionEmbedding = embeddingModel.embed(what).content();

        EmbeddingStore<TextSegment> embeddingStore =
                QdrantEmbeddingStore.builder()
                        .host(host)
                        .port(port)
                        .collectionName(collectionName)
                        .build();

        int maxResults = 4;
        double minScore = 0.7;
        List<EmbeddingMatch<TextSegment>> relevantEmbeddings
                = embeddingStore.findRelevant(questionEmbedding, maxResults, minScore);

        String information = relevantEmbeddings.stream()
                .map(match -> match.embedded().text())
                .collect(joining("\n\n"));

        Map<String, Object> variables = new HashMap<>();
        variables.put("question", what);
        variables.put("information", information);

        Prompt prompt = PROMPT_TEMPLATE.apply(variables);

        List<ChatMessage> messages;
        if (systemPrompt != null) {
            SystemMessage systemMessage = SystemMessage.systemMessage(systemPrompt);
            messages = List.of(systemMessage, prompt.toUserMessage());
        } else {
            messages = List.of(prompt.toUserMessage());
        }

        CountDownLatch latch = new CountDownLatch(1);

        chatModel.generate(messages, new StreamingResponseHandler<>() {
            @Override
            public void onNext(String s) {
                System.out.print(s);
            }

            @Override
            public void onError(Throwable throwable) {
                latch.countDown();
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                try {
                    StreamingResponseHandler.super.onComplete(response);
                } finally {
                    latch.countDown();
                }
            }
        });


        latch.await(2, TimeUnit.MINUTES);
        return 0;
    }

    public OpenAiStreamingChatModel buildModel(String modelName) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(url)
                .apiKey(apiKey)
                .timeout(Duration.ofMinutes(2))
                .maxTokens(Integer.MAX_VALUE)
                .modelName(modelName).build();
    }
}
