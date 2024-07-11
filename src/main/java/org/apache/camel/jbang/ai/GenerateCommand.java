package org.apache.camel.jbang.ai;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.jbang.ai.generate.GenerateCodeServiceClient;
import picocli.CommandLine;

@CommandLine.Command(name = "generate",
        description = "Generate Camel code using AI", subcommands = { GenerateCodeServiceClient.class, GenerateTestCommand.class})
public abstract class GenerateCommand extends CamelCommand {

    public GenerateCommand(CamelJBangMain main) {
        super(main);
    }


}
