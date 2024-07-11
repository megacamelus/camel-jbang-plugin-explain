package org.apache.camel.standalone;

import java.util.concurrent.Callable;

import org.apache.camel.jbang.ai.generate.GenerateCodeServiceClient;
import picocli.CommandLine;

@CommandLine.Command(name = "code",
        description = "Generate Camel code using AI")
public class GenerateCodeCommand implements Callable<Integer> {

    @CommandLine.Option(names = {
            "--host" }, description = "The Qdrant host", defaultValue = "localhost", arity = "1..1", required = true)
    private String host;

    @CommandLine.Option(names = {
            "--port" }, description = "The Qdrant port", defaultValue = "6334", arity = "0..1", required = true)
    private int port;

    @CommandLine.Option(names = {
            "--collection-name" }, description = "The Qdrant collection name", defaultValue = "camel-jbang", arity = "1..1", required = true)
    private String collectionName;

    @CommandLine.Option(names = {"--url"}, description = "The API URL", defaultValue = "http://localhost:8000/v1/", arity = "0..1", required = true)
    private String url;

    @CommandLine.Option(names = {"--api-key"}, description = "The API key", defaultValue = "no_key", arity = "0..1", required = true)
    private String apiKey;

    @CommandLine.Option(names = {"--model-name"}, description = "The model name to use", arity = "0..1", required = true)
    private String modelName;

    @CommandLine.Option(names = {"--system-prompt"}, description = "An optional system prompt to use",
            defaultValue = "You are a coding assistant specialized in Apache Camel", arity = "0..1")
    private String systemPrompt;

    @CommandLine.Parameters(paramLabel = "description", description = "Please explain what the code has to do (also known as 'user prompt')")
    private String description;

    @Override
    public Integer call() throws Exception {
        GenerateCodeServiceClient
                serviceClient = new GenerateCodeServiceClient(url, apiKey, modelName, systemPrompt, description,
                host, port, collectionName);

        return serviceClient.run();
    }
}
