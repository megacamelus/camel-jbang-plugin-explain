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

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
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

    public static final String CONTEXT_ROUTE_UNDER_TEST = "routeUnderTest";
    public static final String CONTEXT_ENDPOINTS = "endpoints";
    public static final String CONTEXT_SENDER = "senderMethod";
    public static final String CONTEXT_ROUTE_BUILDER = "routeBuilder";
    public static final String CONTEXT_TEST_NAME = "testName";
    public static final String CONTEXT_TEST_FILE_NAME = "testFileName";

    private static final List<String> AUTO_TRIGGER = List.of("timer", "quartz");

    private Map<String, String> context = new HashMap<>();

    private final String url;
    private final String apiKey;
    private final String modelName;
    private final String file;
    private final String outputDir;

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
        Steps.ChatStep
                .using(buildConservativeModel(url, apiKey, modelName))
                .withContext(this::startChat)
                .usingPrompt(this::generateExtractEndpointPrompt).chat().andThen(this::saveEndpointContext)
                .usingPrompt(this::generateSenderPrompt).chat().andThen(this::saveSenderContext)
                .usingPrompt(this::routeQuestionPrompt).chat().andThen(this::saveRouteBuilderContext);

        try {
            final File outputFile = VelocityTemplateParser.toOutputFile(outputDir, context.get(CONTEXT_TEST_FILE_NAME));
            VelocityTemplateParser.createFile(outputFile, context);
        } catch (Exception e) {
            System.err.println("Unable to create file: " + e.getMessage());
            return 1;
        }

        return 0;
    }


    private void putToContext(String originalResponse, String contextEndpoints, String message) {
        MarkdownParser parser = new MarkdownParser();
        final String parsedResponse = parser.parse(originalResponse);

        context.put(contextEndpoints, parsedResponse);
        System.out.println(message + parsedResponse);
    }

    /**
     * Extracts the endpoint from the code snippet
     * @param chatMeta
     */
    private UserMessage generateExtractEndpointPrompt(Steps.ChatMeta chatMeta) {
        final InputUnit payload = chatMeta.context(InputUnit.class);

        return generateExtractEndpointPrompt(payload.data());
    }

    private void saveEndpointContext(Steps.ChatMeta chatMeta) {
        putToContext(chatMeta.conversationUnit().response(), CONTEXT_ENDPOINTS, "Generated endpoint response: ");
    }

    private UserMessage generateSenderPrompt(Steps.ChatMeta chatMeta) {
        final Steps.ConversationUnit previousConversation = chatMeta.conversationUnit();
        final String response = previousConversation.response();

        if (response != null) {
            for (String autoTriggerComponent : AUTO_TRIGGER) {
                if (response.contains(autoTriggerComponent)) {

                    // This route starts by itself (i.e; via timer), so we don't need to generate the sender
                    return null;
                }
            }
        }

        return generateSenderQuestionPrompt();
    }

    private void saveSenderContext(Steps.ChatMeta chatMeta) {
        if (chatMeta.conversationUnit().response() != null) {
            putToContext(chatMeta.conversationUnit().response(), CONTEXT_SENDER, "Generated sender response: ");
        }
    }

    private UserMessage routeQuestionPrompt(Steps.ChatMeta chatMeta) {
        return generateRouteQuestionPrompt();
    }

    private void saveRouteBuilderContext(Steps.ChatMeta chatMeta) {
        putToContext(chatMeta.conversationUnit().response(), CONTEXT_ROUTE_BUILDER, "Generated route response: ");
    }

    protected UserMessage generateExtractEndpointPrompt(String route) {
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
}
