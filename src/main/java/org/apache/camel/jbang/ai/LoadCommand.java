package org.apache.camel.jbang.ai;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import picocli.CommandLine;

@CommandLine.Command(name = "load",
        description = "Load data into a vector DB")
public class LoadCommand extends CamelCommand {

    @CommandLine.Option(names = {
            "--host" }, description = "The Qdrant host", defaultValue = "localhost", arity = "1..1", required = true)
    private String host;

    @CommandLine.Option(names = {
            "--port" }, description = "The Qdrant port", defaultValue = "6334", arity = "0..1", required = true)
    private int port;

    @CommandLine.Option(names = {
            "--collection-name" }, description = "The Qdrant collection name", defaultValue = "camel-jbang", arity = "1..1", required = true)
    private String collectionName;

    @CommandLine.Option(names = {
            "--skip-create-collection" }, description = "The Qdrant collection name to create", defaultValue = "false", arity = "0..1")
    private boolean skipCreateCollection;

    public LoadCommand(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        Loader loader = new Loader(host, port, collectionName, skipCreateCollection);

        return loader.load();
    }
}
