package org.apache.camel.jbang.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;

import static org.apache.camel.jbang.ai.util.ModelUtil.buildModel;

public class DataServiceClient {
    private static final PromptTemplate PROMPT_TEMPLATE = PromptTemplate.from(
            "Please write a question about the Apache Camel component {{component}} option named {{optionName}} that can be answered by the following information: {{information}}");

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

        final List<String> componentNames = catalog.findComponentNames();

        for (String componentName : componentNames) {
            System.out.println("Processing: " + componentName);

            final ComponentModel componentModel = catalog.componentModel(componentName);
            final List<ComponentModel.ComponentOptionModel> componentOptions = componentModel.getComponentOptions();

            for (ComponentModel.ComponentOptionModel optionModel : componentOptions) {
                final UserMessage messages = generateMessages(componentName, optionModel);

                CountDownLatch latch = new CountDownLatch(1);

                chatModel.generate(messages, new AiMessageStreamingResponseHandler(latch));

                latch.await(45, TimeUnit.SECONDS);
            }

            final List<ComponentModel.EndpointOptionModel> endpointParameterOptions1 =
                    componentModel.getEndpointParameterOptions();
            for (ComponentModel.EndpointOptionModel endpointParameterModel : endpointParameterOptions1) {
                final UserMessage messages = generateMessages(componentName, endpointParameterModel);

                CountDownLatch latch = new CountDownLatch(1);

                chatModel.generate(messages, new AiMessageStreamingResponseHandler(latch));

                latch.await(45, TimeUnit.SECONDS);
            }
        }
    }

    private static UserMessage generateMessages(
            String componentName, BaseOptionModel optionModel) {
        final String data = toEmbeddableText(componentName, optionModel);

        Map<String, Object> variables = new HashMap<>();
        variables.put("component", componentName);
        variables.put("optionName", optionModel.getName());
        variables.put("information", data);

        final Prompt prompt = PROMPT_TEMPLATE.apply(variables);

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

        public AiMessageStreamingResponseHandler(CountDownLatch latch) {
            this.latch = latch;
        }

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
    }
}
