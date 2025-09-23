package dev.snowdrop;

import dev.snowdrop.commands.AnalyzeCommand;
import dev.snowdrop.commands.TransformCommand;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;

@TopCommand
@CommandLine.Command(
    name = "java-analyzer",
    description = "Java Language Server Analyzer",
    subcommands = {
        AnalyzeCommand.class,
        TransformCommand.class,
        CommandLine.HelpCommand.class
    }
)
public class JavaAnalyzerCommand {
}