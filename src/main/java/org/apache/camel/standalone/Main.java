package org.apache.camel.standalone;

import java.util.concurrent.Callable;

import picocli.CommandLine;

@CommandLine.Command(name = "whatis",
        description = "Explain things using AI")
public class Main implements Callable<Integer> {
    @Override
    public Integer call() throws Exception {
        return 0;
    }

    // this example implements Callable, so parsing, error handling and handling user
    // requests for usage help or version help can be done with one line of code.
    public static void main(String... args) {
        int exitCode = new CommandLine(new Main())
                .addSubcommand("load", new LoadMain())
                .addSubcommand("whatis", new WhatIsMain())
                .addSubcommand("generate", new GenerateStandalone())
                .execute(args);

        System.exit(exitCode);
    }
}
