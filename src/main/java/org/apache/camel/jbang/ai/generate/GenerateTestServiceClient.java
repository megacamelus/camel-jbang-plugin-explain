package org.apache.camel.jbang.ai.generate;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
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

    private Map<String, String> context = new HashMap<>();

    private final String url;
    private final String apiKey;
    private final String modelName;
    private final String file;
    private final String outputDir;
    private boolean generateClass = false;

    protected record InputMeta(UserMessage userMessage, String information) {}

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

    public int run() throws InterruptedException {
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
        context.put(CONTEXT_BASE_CLASS, baseClass);

        extractEndpoint(body, baseClass);
        generateSender();
        generateRoute();

        if (generateClass) {
            generateClass();
        }

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

    private String generate(Supplier<InputMeta> promptSupplier) throws InterruptedException {
        OpenAiStreamingChatModel chatModel = buildConservativeModel(url, apiKey, modelName);
        InputMeta inputMeta = promptSupplier.get();

        CountDownLatch latch = new CountDownLatch(1);

        AiMessageStreamingResponseHandler handler = new AiMessageStreamingResponseHandler(latch);
        chatModel.generate(inputMeta.userMessage, handler);
        latch.await(2, TimeUnit.MINUTES);

        return handler.getResponse();
    }

    private void extractEndpoint(String body, String interfaceToFollow) throws InterruptedException {
        final String originalResponse = generate(() -> generateExtractEndpointPrompt(body, interfaceToFollow));
        putToContext(originalResponse, CONTEXT_ENDPOINTS, "Generated endpoint response: ");
    }

    private void generateSender() throws InterruptedException {
        final String originalResponse = generate(this::generateSenderQuestionPrompt);
        putToContext(originalResponse, CONTEXT_SENDER, "Generated sender response: ");
    }

    private void generateRoute() throws InterruptedException {
        final String originalResponse = generate(this::generateRouteQuestionPrompt);
        putToContext(originalResponse, CONTEXT_ROUTE_BUILDER, "Generated route response: ");
    }

    private void generateClass() throws InterruptedException {
        final String response = generate(this::generateClassPrompt);
        System.out.println("Generated class response: " + response);
    }



    protected InputMeta generateExtractEndpointPrompt(String route, String interfaceToFollow) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("route", route);

        final Prompt prompt = EXTRACT_ENDPOINT_PROMPT_TEMPLATE.apply(variables);

        return new InputMeta(prompt.toUserMessage(), interfaceToFollow);
    }

    protected InputMeta generateSenderQuestionPrompt() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("endpoint", context.get(CONTEXT_ENDPOINTS));

        final Prompt prompt = GENERATE_SENDER_PROMPT_TEMPLATE.apply(variables);

        return new InputMeta(prompt.toUserMessage(), null);
    }


    protected InputMeta generateRouteQuestionPrompt() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("route", context.get(CONTEXT_ENDPOINTS));

        final Prompt prompt = CREATE_ROUTE_PROMPT_TEMPLATE.apply(variables);

        return new InputMeta(prompt.toUserMessage(), null);
    }


    protected InputMeta generateClassPrompt() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("class", context.get(CONTEXT_BASE_CLASS));
        variables.put("route", context.get(CONTEXT_ROUTE_UNDER_TEST));
        variables.put("sender", context.get(CONTEXT_SENDER));
        variables.put("routeBuilder", context.get(CONTEXT_ROUTE_BUILDER));

        final Prompt prompt = GENERATE_CLASS_PROMPT_TEMPLATE.apply(variables);

        return new InputMeta(prompt.toUserMessage(), null);
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
