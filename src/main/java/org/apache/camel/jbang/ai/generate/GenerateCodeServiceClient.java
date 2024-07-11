package org.apache.camel.jbang.ai.generate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;

import static org.apache.camel.jbang.ai.util.ModelUtil.buildModel;
import static org.apache.camel.jbang.ai.util.RagUtil.findRelevant;
import static org.apache.camel.jbang.ai.util.RagUtil.toPrompt;

public class GenerateCodeServiceClient {
    private static final PromptTemplate PROMPT_TEMPLATE = PromptTemplate.from(
            "Please generate this code to the best of your ability:\n"
                    + "\n"
                    + "Description:\n"
                    + "{{question}}\n"
                    + "\n"
                    + "Consider the following additional information:\n"
                    + "{{information}}");

    private final String url;
    private final String apiKey;
    private final String modelName;
    private final String systemPrompt;
    private final String what;
    private final String host;
    private final int port;
    private final String collectionName;


    public GenerateCodeServiceClient(
            String url, String apiKey, String modelName, String systemPrompt, String what, String host,
            int port, String collectionName) {
        this.url = url;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.systemPrompt = systemPrompt;
        this.what = what;
        this.host = host;
        this.port = port;
        this.collectionName = collectionName;
    }

    public int run() throws InterruptedException {
        OpenAiStreamingChatModel chatModel = buildModel(url, apiKey, modelName);

        final List<ChatMessage> messages = createChatMessages();

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

    private List<ChatMessage> createChatMessages() {
        final List<EmbeddingMatch<TextSegment>> relevantEmbeddings = findRelevant(host, port, collectionName, what);

        final Prompt prompt = toPrompt(PROMPT_TEMPLATE, relevantEmbeddings, what);

        List<ChatMessage> messages;
        if (systemPrompt != null) {
            SystemMessage systemMessage = SystemMessage.systemMessage(systemPrompt);
            messages = List.of(systemMessage, prompt.toUserMessage());
        } else {
            messages = List.of(prompt.toUserMessage());
        }
        return messages;
    }



}
