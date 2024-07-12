package org.apache.camel.jbang.ai.data;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.jbang.ai.types.AlpacaRecord;
import org.apache.camel.jbang.ai.util.CatalogUtil;
import org.apache.camel.jbang.ai.util.handlers.BufferedStreamingResponseHandler;
import org.apache.camel.jbang.ai.util.steps.Steps;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.util.StopWatch;

public abstract class CatalogProcessor {
    private static final PromptTemplate QUESTION_GENERATOR_PROMPT_TEMPLATE = PromptTemplate.from(
            "Please write a question about the Apache Camel component {{component}} option named {{optionName}} that can be answered by the following information: {{information}}");

    private static final PromptTemplate ANSWER_GENERATOR_PROMPT_TEMPLATE = PromptTemplate.from(
            "Please write a paragraph explaining the following information: \"{{information}}\" as if replying to the following question: {{question}}. Only generate the response and nothing else.");

    protected final OpenAiStreamingChatModel chatModel;
    protected final CamelCatalog catalog;

    protected static class ChatContext {
        public final String name;
        public final BaseOptionModel optionModel;
        public final AlpacaRecord alpacaRecord;
        public final String rawData;

        public ChatContext(String name, BaseOptionModel optionModel, AlpacaRecord alpacaRecord, String rawData) {
            this.name = name;
            this.optionModel = optionModel;
            this.alpacaRecord = alpacaRecord;
            this.rawData = rawData;
        }
    }

    protected CatalogProcessor(OpenAiStreamingChatModel chatModel, CamelCatalog catalog) {
        this.chatModel = chatModel;
        this.catalog = catalog;
    }

    public abstract void process(int startFrom)
            throws InterruptedException;

    protected void processRecords(int startFrom, List<String> componentNames, int totalComponents)
            throws InterruptedException {
        for (int i = startFrom; i < componentNames.size(); i++) {
            processRecord(componentNames, i, totalComponents);
        }
    }

    protected abstract void processRecord(List<String> componentNames, int i, int totalComponents) throws InterruptedException;

    protected void processOption(List<AlpacaRecord> alpacaRecords, String componentName,
            List<? extends BaseOptionModel> optionModels, String type) throws InterruptedException {
        int componentOptionCount = 0;
        final int componentOptionTotal = optionModels.size();
        for (BaseOptionModel optionModel : optionModels) {
            StopWatch watch = new StopWatch();
            System.out.printf("[%s] Processing %s option %d of %d: %s -> %s", CatalogUtil.currentTime(), type, componentOptionCount, componentOptionTotal,
                    componentName, optionModel.getName());
            createRecord(componentName, optionModel, alpacaRecords);
            componentOptionCount++;
            final long taken = watch.taken();
            System.out.printf(" [took %d s]%n", Duration.ofMillis(taken).toSeconds());

        }
    }

    protected void createRecord(String componentName, BaseOptionModel optionModel, List<AlpacaRecord> alpacaRecords) {

        Steps.InitialStep.start()
                .chat(c -> startChat(c, componentName, optionModel))
                .andThen(this::generateQuestion)
                .andThen(c -> generateResponse(c, alpacaRecords));
    }

    public void startChat(Steps.ChatMeta chatMeta, String name, BaseOptionModel optionModel) {
        final String rawData = CatalogUtil.toEmbeddableText(name, optionModel);

        chatMeta.setContext(new ChatContext(name, optionModel, new AlpacaRecord(), rawData));
    }

    private void generateQuestion(Steps.ChatMeta chatMeta) {
        ChatContext chatContext = chatMeta.context(ChatContext.class);

        final UserMessage userMessage = generateQuestionPrompt(chatContext.rawData, chatContext.name, chatContext.optionModel);


        final String questionResponse;
        try {
            questionResponse = generateQuestion(chatModel, chatContext.name, userMessage, chatContext.alpacaRecord);
            if (questionResponse == null) {
                return;
            }

            chatMeta.setConversationUnit(new Steps.ConversationUnit(userMessage, questionResponse));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    protected static String generateQuestion(
            OpenAiStreamingChatModel chatModel, String componentName, UserMessage questionMessage, AlpacaRecord alpacaRecord)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        final BufferedStreamingResponseHandler responseHandler =
                new BufferedStreamingResponseHandler(latch);

        chatModel.generate(questionMessage, responseHandler);

        if (!latch.await(2, TimeUnit.MINUTES)) {
            System.err.println("No response was generated for " + componentName + ". Skipping");
            return null;
        }

        return responseHandler.getResponse();
    }

    private void generateResponse(Steps.ChatMeta chatMeta, List<AlpacaRecord> alpacaRecords) {
        ChatContext chatContext = chatMeta.context(ChatContext.class);
        Steps.ConversationUnit conversationUnit = chatMeta.conversationUnit();

        final String generatedResponse;
        try {
            generatedResponse = generateResponse(chatModel, chatContext.name, chatContext.rawData,
                    conversationUnit.response());

            if (generatedResponse == null) {
                return;
            }

            chatContext.alpacaRecord.setInstruction(conversationUnit.response());
            chatContext.alpacaRecord.setInput("");
            chatContext.alpacaRecord.setOutput(generatedResponse);
            alpacaRecords.add(chatContext.alpacaRecord);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected static String generateResponse(
            OpenAiStreamingChatModel chatModel, String componentName, String information, String questionResponse)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        final UserMessage responseMessage = generateResponse(information, questionResponse);
        final BufferedStreamingResponseHandler responseHandler =
                new BufferedStreamingResponseHandler(latch);

        chatModel.generate(responseMessage, responseHandler);

        if (!latch.await(2, TimeUnit.MINUTES)) {
            System.err.println("No response was generated for component " + componentName + ". Skipping");
            return null;
        }

        return responseHandler.getResponse();
    }

    protected UserMessage generateQuestionPrompt(String rawData,
            String name, BaseOptionModel optionModel) {


        Map<String, Object> variables = new HashMap<>();
        variables.put("component", name);
        variables.put("optionName", optionModel.getName());
        variables.put("information", rawData);

        final Prompt prompt = QUESTION_GENERATOR_PROMPT_TEMPLATE.apply(variables);
        return prompt.toUserMessage();
    }

    protected static UserMessage generateResponse(String information, String question) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("information", information);
        variables.put("question", question);

        final Prompt prompt = ANSWER_GENERATOR_PROMPT_TEMPLATE.apply(variables);

        return prompt.toUserMessage();
    }
}
