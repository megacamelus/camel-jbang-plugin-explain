package org.apache.camel.jbang.ai;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CamelJBangPlugin;
import org.apache.camel.dsl.jbang.core.common.Plugin;
import picocli.CommandLine;

@CamelJBangPlugin(name = "camel-jbang-plugin-explain", firstVersion = "4.7.0-SNAPSHOT")
public class ExplainPlugin implements Plugin {
    @Override
    public void customize(CommandLine commandLine, CamelJBangMain main) {
        var cmd = new picocli.CommandLine(new ExplainCommand(main));

        commandLine.addSubcommand("explain", cmd);
    }
}
