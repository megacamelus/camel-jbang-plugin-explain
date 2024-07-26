package org.apache.camel.standalone;

import java.util.concurrent.Callable;

import org.apache.camel.jbang.ai.data.DocumentationProcessor;
import picocli.CommandLine;

@SuppressWarnings("unused")
@CommandLine.Command(name = "dump",
        description = "Dump training data data from Apache Camel")
public class DataDumpStandalone implements Callable<Integer> {

    @CommandLine.Option(names = {"--data-type"}, description = "The what data type to dump [component, dataformat, etc]", arity = "0..1", required = true)
    private String dataType;

    @CommandLine.Option(names = {"--source-path"}, description = "Augment data generation using the Apache Camel code located in the given directory",
            arity = "0..1", required = true)
    private String sourcePath;

    @CommandLine.Option(names = {"--start-from"}, description = "Start again from the given index",
            arity = "0..1", defaultValue = "0", required = true)
    private int startFrom;

    @Override
    public Integer call() throws Exception {
        if (dataType.equals("component-documentation")) {
            final DocumentationProcessor documentationProcessor = new DocumentationProcessor(sourcePath);

            documentationProcessor.process(startFrom);
            return 0;
        }

        return 1;
    }


}
