package org.apache.camel.jbang.ai.generate;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import org.apache.camel.CamelException;
import org.apache.camel.jbang.ai.util.MarkdownParser;
import org.apache.camel.jbang.ai.util.VelocityTemplateParser;
import org.apache.camel.jbang.ai.util.steps.Steps;

import static org.apache.camel.jbang.ai.util.ModelUtil.buildConservativeModel;

public class GenerateTestServiceClient {
    private static final PromptTemplate EXTRACT_ENDPOINT_PROMPT_TEMPLATE = PromptTemplate.from("what are the endpoints in this route?" +
            "\n" +
            "{{route}}\n" +
            "Please tell me only the endpoints and nothing else. If there are more than one, separate them with comma.");

    private static final PromptTemplate GENERATE_SENDER_PROMPT_TEMPLATE = PromptTemplate.from("Please write a method" +
            " named sendMessages that uses the ProducerTemplate to send one message to this Camel endpoint" +
            "{{endpoint}}\n" +
            "Please generate only the method and nothing else. Do not provide explanations.");

    private static final PromptTemplate CREATE_ROUTE_PROMPT_TEMPLATE = PromptTemplate.from("Please create a method named createRouteBuilder that " +
            "creates this camel route:" +
            "{{route}}");

    private static final PromptTemplate GENERATE_CLASS_PROMPT_TEMPLATE = PromptTemplate.from(
            "Please write a test class for the following Camel route ensuring that extends the given class:\n"
                    + "{{class}}\n"
                    + "Route:\n"
                    + "{{route}}\n"
                    + "\n"
                    + "Consider the following information to build the class\n"
                    + "Sender method: {{sender}}"
                    + "Route builder method: {{routeBuilder}}");

    public static final String CONTEXT_ROUTE_UNDER_TEST = "routeUnderTest";
    public static final String CONTEXT_ENDPOINTS = "endpoints";
    public static final String CONTEXT_SENDER = "senderMethod";
    public static final String CONTEXT_ROUTE_BUILDER = "routeBuilder";
    public static final String CONTEXT_BASE_CLASS = "baseClass";
    public static final String CONTEXT_TEST_NAME = "testName";
    public static final String CONTEXT_TEST_FILE_NAME = "testFileName";

    private static final List<String> AUTO_TRIGGER = List.of("timer", "quartz");

    private Map<String, String> context = new HashMap<>();

    private final String url;
    private final String apiKey;
    private final String modelName;
    private final String file;
    private final String outputDir;
    private boolean generateClass = false;

    protected record InputUnit(String data, String baseClass) {}

    public GenerateTestServiceClient(String url, String apiKey, String modelName, String file, String outputDir) {
        this.url = url;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.file = file;
        this.outputDir = outputDir;
    }

    public String loadFile(String file) {
        File inputFile = new File(file);
        if (!inputFile.exists()) {
            throw new RuntimeException("File not found: " + file);
        }

        final String testName = inputFile.getName().replace(".java", "") + "Test";
        context.put(CONTEXT_TEST_NAME, testName);
        context.put(CONTEXT_TEST_FILE_NAME, testName + ".java");

        try (InputStream stream = new BufferedInputStream(new FileInputStream(inputFile))) {
            return new String(stream.readAllBytes());
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void startChat(Steps.ChatMeta chatMeta) {
        String data = loadFile(file);
        CompilationUnit compilationUnit = StaticJavaParser.parse(data);

        // Get the configure method body
        final MethodDeclaration configure =
                compilationUnit.findAll(MethodDeclaration.class, n -> "configure".equals(n.getNameAsString()))
                        .getFirst();

        final String body = configure.getDeclarationAsString() + " " + configure.getBody().get();
        context.put(CONTEXT_ROUTE_UNDER_TEST, body);

        // Get the interface to follow
        String baseClass;
        try (InputStream inputStream = getClass().getResourceAsStream("CamelTestSupport.java")) {
            final byte[] bytes = inputStream.readAllBytes();
            baseClass = new String(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        chatMeta.setContext(new InputUnit(data, baseClass));
    }

    public int run() throws InterruptedException {
        Steps.InitialStep.start()
                .chat(this::startChat)
                .andThen(this::extractEndpoint)
                .andThen(this::generateSender)
                .andThen(this::generateRoute);


        try {
            createFile();
        } catch (Exception e) {
            System.err.println("Unable to create file: " + e.getMessage());
            return 1;
        }

        return 0;
    }

    public void createFile() throws IOException, CamelException {
        VelocityTemplateParser templateParser = new VelocityTemplateParser();

        final File outputDirFile = new File(outputDir);
        File outputFile = templateParser.getOutputFile(outputDirFile, context.get(CONTEXT_TEST_FILE_NAME));

        try (FileWriter fw = new FileWriter(outputFile)) {
            templateParser.parse(fw, context);
        }
    }

    private void putToContext(String originalResponse, String contextEndpoints, String message) {
        MarkdownParser parser = new MarkdownParser();
        final String parsedResponse = parser.parse(originalResponse);

        context.put(contextEndpoints, parsedResponse);
        System.out.println(message + parsedResponse);
    }

    private Steps.ConversationUnit generate(Supplier<UserMessage> messageSupplier) throws InterruptedException {
        OpenAiStreamingChatModel chatModel = buildConservativeModel(url, apiKey, modelName);
        UserMessage userMessage = messageSupplier.get();

        CountDownLatch latch = new CountDownLatch(1);

        AiMessageStreamingResponseHandler handler = new AiMessageStreamingResponseHandler(latch);
        chatModel.generate(userMessage, handler);
        latch.await(2, TimeUnit.MINUTES);

        String response = handler.getResponse();
        return new Steps.ConversationUnit(userMessage, response);
    }

    /**
     * Extracts the endpoint from the code snippet
     * @param chatMeta
     */
    private void extractEndpoint(Steps.ChatMeta chatMeta) {
        final InputUnit payload = chatMeta.context(InputUnit.class);

        final Steps.ConversationUnit conversationUnit;
        try {
            conversationUnit = generate(() -> generateExtractEndpointPrompt(payload.data(), payload.baseClass()));
            chatMeta.setConversationUnit(conversationUnit);
            putToContext(conversationUnit.response(), CONTEXT_ENDPOINTS, "Generated endpoint response: ");
        } catch (InterruptedException e) {
            chatMeta.setException(e);
        }
    }


    private void generateSender(Steps.ChatMeta chatMeta) {
        final Steps.ConversationUnit previousConversation = chatMeta.conversationUnit();
        final String response = previousConversation.response();

        if (response != null) {
            for (String autoTriggerComponent : AUTO_TRIGGER) {
                if (response.contains(autoTriggerComponent)) {

                    // This route starts by itself (i.e; via timer), so we don't need to generate the sender
                    return;
                }
            }
        }

        try {
            final Steps.ConversationUnit conversationUnit = generate(this::generateSenderQuestionPrompt);
            chatMeta.setConversationUnit(conversationUnit);
            putToContext(conversationUnit.response(), CONTEXT_SENDER, "Generated sender response: ");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void generateRoute(Steps.ChatMeta chatMeta) {
        try {
            final Steps.ConversationUnit conversationUnit = generate(this::generateRouteQuestionPrompt);
            chatMeta.setConversationUnit(conversationUnit);
            putToContext(conversationUnit.response(), CONTEXT_ROUTE_BUILDER, "Generated route response: ");
        } catch (InterruptedException e) {
            chatMeta.setException(e);
        }

    }

    private void generateClass(Steps.ChatMeta chatMeta) {
        try {
            final Steps.ConversationUnit conversationUnit = generate(this::generateClassPrompt);
            chatMeta.setConversationUnit(conversationUnit);
        } catch (InterruptedException e) {
            chatMeta.setException(e);
            return;
        }
//        System.out.println("Generated class response: " + response);
    }



    protected UserMessage generateExtractEndpointPrompt(String route, String interfaceToFollow) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("route", route);

        final Prompt prompt = EXTRACT_ENDPOINT_PROMPT_TEMPLATE.apply(variables);
        return prompt.toUserMessage();
    }

    protected UserMessage generateSenderQuestionPrompt() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("endpoint", context.get(CONTEXT_ENDPOINTS));

        final Prompt prompt = GENERATE_SENDER_PROMPT_TEMPLATE.apply(variables);
        return prompt.toUserMessage();
    }


    protected UserMessage generateRouteQuestionPrompt() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("route", context.get(CONTEXT_ENDPOINTS));

        final Prompt prompt = CREATE_ROUTE_PROMPT_TEMPLATE.apply(variables);
        return prompt.toUserMessage();
    }


    protected UserMessage generateClassPrompt() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("class", context.get(CONTEXT_BASE_CLASS));
        variables.put("route", context.get(CONTEXT_ROUTE_UNDER_TEST));
        variables.put("sender", context.get(CONTEXT_SENDER));
        variables.put("routeBuilder", context.get(CONTEXT_ROUTE_BUILDER));

        final Prompt prompt = GENERATE_CLASS_PROMPT_TEMPLATE.apply(variables);

        return prompt.toUserMessage();
    }

    protected static class AiMessageStreamingResponseHandler implements StreamingResponseHandler<AiMessage> {
        private final CountDownLatch latch;
        private StringBuffer responseBuffer = new StringBuffer();

        public AiMessageStreamingResponseHandler(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void onNext(String s) {
            responseBuffer.append(s);
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

        public String getResponse() {
            return responseBuffer.toString();
        }
    }
}
