package org.apache.camel.standalone;

import java.util.concurrent.Callable;

import org.apache.camel.jbang.ai.Loader;
import picocli.CommandLine;

@CommandLine.Command(name = "load",
        description = "Load data into a vector DB")
public class LoadStandAlone implements Callable<Integer> {

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

    public Integer call() throws Exception {
        Loader loader = new Loader(host, port, collectionName, skipCreateCollection);

        return loader.load();

    }

    // this example implements Callable, so parsing, error handling and handling user
    // requests for usage help or version help can be done with one line of code.
    public static void main(String... args) {
        int exitCode = new CommandLine(new LoadStandAlone()).execute(args);
        System.exit(exitCode);
    }
}
