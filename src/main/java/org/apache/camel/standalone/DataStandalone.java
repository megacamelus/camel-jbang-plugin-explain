package org.apache.camel.standalone;

import java.util.concurrent.Callable;

import org.apache.camel.jbang.ai.DataServiceClient;
import picocli.CommandLine;

@CommandLine.Command(name = "data",
        description = "Generate training data data for Apache Camel")
public class DataStandalone implements Callable<Integer> {
    @CommandLine.Option(names = {
            "--collection-name" }, description = "The Qdrant collection name", defaultValue = "camel-jbang", arity = "1..1", required = true)
    private String collectionName;

    @CommandLine.Option(names = {"--url"}, description = "The API URL", defaultValue = "http://localhost:8000/v1/", arity = "0..1", required = true)
    private String url;

    @CommandLine.Option(names = {"--api-key"}, description = "The API key", defaultValue = "no_key", arity = "0..1", required = true)
    private String apiKey;

    @CommandLine.Option(names = {"--model-name"}, description = "The model name to use", arity = "0..1", required = true)
    private String modelName;

    @Override
    public Integer call() throws Exception {
        DataServiceClient serviceClient = new DataServiceClient(url, apiKey, modelName);

        return serviceClient.run();
    }


}
