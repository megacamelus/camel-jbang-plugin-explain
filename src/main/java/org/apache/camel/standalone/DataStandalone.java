package org.apache.camel.standalone;

import java.util.concurrent.Callable;

import org.apache.camel.jbang.ai.DataDump;
import picocli.CommandLine;

@SuppressWarnings("unused")
@CommandLine.Command(name = "data",
        description = "Generate training data data for Apache Camel", subcommands = {DataGenerateStandalone.class, DataDumpStandalone.class})
public class DataStandalone implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        return 0;
    }
}
