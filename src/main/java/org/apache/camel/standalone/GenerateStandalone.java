package org.apache.camel.standalone;

import java.util.concurrent.Callable;

import picocli.CommandLine;

@CommandLine.Command(name = "generate",
        description = "Generate Camel code using AI", subcommands = { GenerateCodeCommand.class, GenerateTestCommand.class})
public class GenerateStandalone implements Callable<Integer> {
    @Override
    public Integer call() throws Exception {
        return 0;
    }
}
