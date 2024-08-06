package org.apache.camel.standalone;

import java.util.concurrent.Callable;

import org.apache.camel.jbang.ai.generate.GenerateTaxonomy;
import picocli.CommandLine;

@CommandLine.Command(name = "taxonomy",
        description = "Generate InstructLab Taxonomy for a Camel component", subcommands = { GenerateCodeCommand.class, GenerateTestCommand.class})
public class GenerateTaxonomyStandalone implements Callable<Integer> {
    @CommandLine.Option(names = {"--author"}, description = "The author for the taxonomy", arity = "0..1")
    private String author;

    @CommandLine.Option(names = {"--component-name"}, description = "The component name for which to generate the taxonomy. If not provided, will generate for all components", arity = "0..1")
    private String componentName;

    @CommandLine.Option(names = {"--document-repo"}, description = "The document repository to use", required = true, arity = "0..1")
    private String documentRepo;

    @CommandLine.Option(names = {"--document-commit"}, description = "The commit ID on the repository to use", required = true, arity = "0..1")
    private String documentCommitId;

    @CommandLine.Option(names = {"--document-path"}, description = "The path to the document source", required = true, arity = "0..1")
    private String documentPath;

    @CommandLine.Option(names = {"--taxonomy-path"}, description = "The path to the taxonomy", required = true, arity = "0..1")
    private String taxonomyPath;

    @Override
    public Integer call() throws Exception {
        GenerateTaxonomy generateTaxonomy = new GenerateTaxonomy(author, componentName, documentRepo, documentCommitId, documentPath, taxonomyPath);

        generateTaxonomy.generate();
        return 0;
    }
}
