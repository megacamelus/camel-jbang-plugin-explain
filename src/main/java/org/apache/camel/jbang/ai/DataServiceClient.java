package org.apache.camel.jbang.ai;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.jbang.ai.types.AlpacaRecord;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;

import static org.apache.camel.jbang.ai.util.ModelUtil.buildModel;

public class DataServiceClient {
    private static final PromptTemplate QUESTION_GENERATOR_PROMPT_TEMPLATE = PromptTemplate.from(
            "Please write a question about the Apache Camel component {{component}} option named {{optionName}} that can be answered by the following information: {{information}}");

    private static final PromptTemplate ANSWER_GENERATOR_PROMPT_TEMPLATE = PromptTemplate.from(
            "Please write a paragraph explaining the following information: \"{{information}}\" as if replying to the following question: {{question}}. Only generate the response and nothing else.");

    private final String url;
    private final String apiKey;
    private final String modelName;


    public DataServiceClient(String url, String apiKey, String modelName) {
        this.url = url;
        this.apiKey = apiKey;
        this.modelName = modelName;
    }

    public int run() throws InterruptedException {
        OpenAiStreamingChatModel chatModel = buildModel(url, apiKey, modelName);

        createSyntheticQuestions(chatModel);

        return 0;
    }

    private void createSyntheticQuestions(OpenAiStreamingChatModel chatModel) throws InterruptedException {
        CamelCatalog catalog = new DefaultCamelCatalog(true);

        final List<AlpacaRecord> alpacaRecords = new ArrayList<>(1024);
        processCatalog(chatModel, catalog, alpacaRecords);

        saveRecords(alpacaRecords);
    }

    private static void processCatalog(OpenAiStreamingChatModel chatModel, CamelCatalog catalog, List<AlpacaRecord> alpacaRecords)
            throws InterruptedException {

        final List<String> componentNames = catalog.findComponentNames();
        int i = 0;
        final int totalComponents = componentNames.size();
        for (String componentName : componentNames) {
            System.out.printf("Processing component %d of %d: %s%n", i, totalComponents, componentName);

            final ComponentModel componentModel = catalog.componentModel(componentName);

            final List<ComponentModel.ComponentOptionModel> componentOptions = componentModel.getComponentOptions();
            processComponentOption(chatModel, alpacaRecords, componentName, componentOptions, "component");

            final List<ComponentModel.EndpointOptionModel> endpointParameterOptions =
                    componentModel.getEndpointParameterOptions();
            processComponentOption(chatModel, alpacaRecords, componentName, endpointParameterOptions, "endpoint");
            i++;
        }
    }

    private static void processComponentOption(
            OpenAiStreamingChatModel chatModel, List<AlpacaRecord> alpacaRecords, String componentName,
            List<? extends BaseOptionModel> optionModels, String type) throws InterruptedException {
        int componentOptionCount = 0;
        final int componentOptionTotal = optionModels.size();
        for (BaseOptionModel optionModel : optionModels) {
            System.out.printf("Processing %s option %d of %d: %s -> %s%n", type, componentOptionCount, componentOptionTotal,
                    componentName, optionModel.getName());
            createRecord(chatModel, componentName, optionModel, alpacaRecords);
            componentOptionCount++;
        }
    }

    private static void saveRecords(List<AlpacaRecord> alpacaRecords) {
        if (!alpacaRecords.isEmpty()) {
            ObjectMapper mapper = new ObjectMapper();

            try {
                mapper.writeValue(new File("camel.json"), alpacaRecords);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void createRecord(
            OpenAiStreamingChatModel chatModel, String componentName, BaseOptionModel optionModel,
            List<AlpacaRecord> alpacaRecords) throws InterruptedException {

        AlpacaRecord alpacaRecord = new AlpacaRecord();

        final QuestionMeta questionMeta = generateQuestion(componentName, optionModel);
        final String questionResponse = generateQuestion(chatModel, componentName, questionMeta.userMessage, alpacaRecord);
        if (questionResponse == null) {
            return;
        }
        System.out.println("Generated question: " + questionResponse);

        final String responseResponse = generateResponse(chatModel, componentName, questionMeta.information, questionResponse);
        if (responseResponse == null) {
            return;
        }
        System.out.println("Generated response: " + responseResponse);

        alpacaRecord.setInstruction(questionResponse);
        alpacaRecord.setInput("");
        alpacaRecord.setOutput(responseResponse);


        alpacaRecords.add(alpacaRecord);
    }

    private static String generateQuestion(
            OpenAiStreamingChatModel chatModel, String componentName, UserMessage questionMessage, AlpacaRecord alpacaRecord)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        final AiMessageStreamingResponseHandler responseHandler =
                new AiMessageStreamingResponseHandler(latch);

        chatModel.generate(questionMessage, responseHandler);

        if (!latch.await(45, TimeUnit.SECONDS)) {
            System.err.println("No response was generated for component " + componentName + ". Skipping");
            return null;
        }

        return responseHandler.getResponse();
    }

    private static String generateResponse(
            OpenAiStreamingChatModel chatModel, String componentName, String information, String questionResponse)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        final UserMessage responseMessage = generateResponse(information, questionResponse);
        final AiMessageStreamingResponseHandler responseHandler =
                new AiMessageStreamingResponseHandler(latch);

        chatModel.generate(responseMessage, responseHandler);

        if (!latch.await(45, TimeUnit.SECONDS)) {
            System.err.println("No response was generated for component " + componentName + ". Skipping");
            return null;
        }

        return responseHandler.getResponse();
    }

    private record QuestionMeta(UserMessage userMessage, String information) {}

    private static QuestionMeta generateQuestion(
            String componentName, BaseOptionModel optionModel) {
        final String data = toEmbeddableText(componentName, optionModel);

        Map<String, Object> variables = new HashMap<>();
        variables.put("component", componentName);
        variables.put("optionName", optionModel.getName());
        variables.put("information", data);

        final Prompt prompt = QUESTION_GENERATOR_PROMPT_TEMPLATE.apply(variables);

        return new QuestionMeta(prompt.toUserMessage(), data);
    }

    private static UserMessage generateResponse(String information, String question) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("information", information);
        variables.put("question", question);

        final Prompt prompt = ANSWER_GENERATOR_PROMPT_TEMPLATE.apply(variables);

        return prompt.toUserMessage();
    }

    private static String toEmbeddableText(String componentName, BaseOptionModel optionModel) {
        SimpleRequestBuilder request = new SimpleRequestBuilder();

        request.append("component", componentName)
                .append("option", optionModel.getName())
                .append("description", optionModel.getDescription())
                .append("defaultValue", optionModel.getDefaultValue())
                .append("type", optionModel.getType())
                .append("required", optionModel.isRequired())
                .append("groups", optionModel.getGroup());

        return request.build();
    }

    private static class AiMessageStreamingResponseHandler implements StreamingResponseHandler<AiMessage> {
        private final CountDownLatch latch;
        private StringBuffer responseBuffer = new StringBuffer();

        public AiMessageStreamingResponseHandler(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void onNext(String s) {
            System.out.print(s);
            responseBuffer.append(s);
        }

        @Override
        public void onError(Throwable throwable) {
            latch.countDown();
        }

        @Override
        public void onComplete(Response<AiMessage> response) {
            try {
                System.out.printf("%n");
                StreamingResponseHandler.super.onComplete(response);
            } finally {
                latch.countDown();
            }
        }

        public String getResponse() {
            return responseBuffer.toString();
        }
    }
}
