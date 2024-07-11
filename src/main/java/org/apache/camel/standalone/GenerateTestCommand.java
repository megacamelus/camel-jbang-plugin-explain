package org.apache.camel.standalone;

import java.util.concurrent.Callable;

import org.apache.camel.jbang.ai.generate.GenerateTestServiceClient;
import picocli.CommandLine;

@CommandLine.Command(name = "test",
        description = "Generate Camel code using AI")
public class GenerateTestCommand implements Callable<Integer> {
    @CommandLine.Option(names = {
            "--output-dir" }, description = "The output dir", arity = "1..1", required = true)
    private String outputDir;

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
    private String file;

    @Override
    public Integer call() throws Exception {
        GenerateTestServiceClient
                serviceClient = new GenerateTestServiceClient(url, apiKey, modelName, file, outputDir);

        return serviceClient.run();
    }
}
