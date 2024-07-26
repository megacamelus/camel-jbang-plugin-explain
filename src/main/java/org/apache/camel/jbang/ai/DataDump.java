package org.apache.camel.jbang.ai;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.jbang.ai.data.DocumentationProcessor;
import picocli.CommandLine;

public class DataDump extends CamelCommand {
    @CommandLine.Option(names = {"--data-type"}, description = "The what data type to dump [component, dataformat, etc]", arity = "0..1", required = true)
    private String dataType;

    @CommandLine.Option(names = {"--source-path"}, description = "Augment data generation using the Apache Camel code located in the given directory",
            arity = "0..1", required = true)
    private String sourcePath;

    @CommandLine.Option(names = {"--start-from"}, description = "Start again from the given index",
            arity = "0..1", defaultValue = "0", required = true)
    private int startFrom;

    public DataDump(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        final DocumentationProcessor documentationProcessor = new DocumentationProcessor(sourcePath);

        documentationProcessor.process(startFrom);
        return 0;
    }
}
