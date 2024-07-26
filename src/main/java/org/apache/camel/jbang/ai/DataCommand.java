package org.apache.camel.jbang.ai;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.jbang.ai.data.DocumentationProcessor;
import picocli.CommandLine;

@CommandLine.Command(name = "data", description = "Generate training data for Apache Camel")
public class DataCommand extends CamelCommand {

    @CommandLine.Option(names = {"--url"}, description = "The API URL", defaultValue = "http://localhost:8000/v1/", arity = "0..1", required = true)
    private String url;

    @CommandLine.Option(names = {"--api-key"}, description = "The API key", defaultValue = "no_key", arity = "0..1", required = true)
    private String apiKey;

    @CommandLine.Option(names = {"--model-name"}, description = "The model name to use", arity = "0..1", required = true)
    private String modelName;

    @CommandLine.Option(names = {"--data-type"}, description = "The what data type to dump [component, dataformat, etc]", arity = "0..1", required = true)
    private String dataType;

    @CommandLine.Option(names = {"--source-path"}, description = "Augment data generation using the Apache Camel code located in the given directory",
            arity = "0..1", required = true)
    private String sourcePath;

    @CommandLine.Option(names = {"--start-from"}, description = "Start again from the given index",
            arity = "0..1", defaultValue = "0", required = true)
    private int startFrom;

    public DataCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (dataType.equals("component-documentation")) {
            final DocumentationProcessor documentationProcessor = new DocumentationProcessor(sourcePath);

            documentationProcessor.process(startFrom);
            return 0;
        } else {
            DataServiceClient serviceClient = new DataServiceClient(url, apiKey, modelName, dataType, startFrom, sourcePath);
            return serviceClient.run();
        }
    }

}
