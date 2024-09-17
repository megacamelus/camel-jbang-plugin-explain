package org.apache.camel.jbang.ai.generate;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.jbang.ai.types.AlpacaRecord;
import org.apache.commons.io.FileUtils;

public class GenerateTaxonomy {
    private final String author;
    private final String componentName;
    private final String documentRepo;
    private final String documentCommitId;
    private final String documentPath;
    private final String taxonomyPath;

    public GenerateTaxonomy(
            String author, String componentName, String documentRepo, String documentCommitId, String documentPath, String taxonomyPath) {
        this.author = author;
        this.componentName = componentName;
        this.documentRepo = documentRepo;
        this.documentCommitId = documentCommitId;
        this.documentPath = documentPath;
        this.taxonomyPath = taxonomyPath;
    }

    // TODO: convert to velocity
    public void generate() throws IOException {
        final List<String> brokenComponents = List.of("coap+tcp", "coaps+tcp");

        if (componentName == null) {
            final CamelCatalog catalog = new DefaultCamelCatalog(true);
            final List<String> componentNames = catalog.findComponentNames();
            for (String component : componentNames) {
                if (brokenComponents.contains(component)) {
                    continue;
                }

                doGenerate("camel-" + component);
            }
        } else {
            doGenerate(componentName);
        }


    }

    private void doGenerate(String componentName) throws IOException {
        String top = String.format("""
                task_description: 'Answer questions about Apache Camel features'
                created_by: %s
                domain: open source software""", author);


        String document = String.format("""
                document:
                  repo: %s
                  commit: %s
                  patterns:
                    - camel-documentation/%s.md""", documentRepo, documentCommitId, componentName);

        File taxonomyRoot = new File(documentPath).getParentFile();
        File knowledgeDir = new File(taxonomyRoot, "camel-documentation");
        File knowledgeDocument = new File(knowledgeDir, componentName + ".md");
        if (!knowledgeDocument.exists()) {
            System.out.printf("Skipping %s because file %s does not exist.%n", componentName, knowledgeDocument.getAbsolutePath());
            return;
        }


        File dataSetFile = new File(documentPath, componentName + ".json");
        if (!dataSetFile.exists()) {
            throw new IOException(String.format("Dataset file %s does not exist", dataSetFile.getPath()));
        }

        ObjectMapper mapper = new ObjectMapper();

        StringBuilder sb = new StringBuilder();
        final AlpacaRecord[] alpacaRecords = mapper.readValue(dataSetFile, AlpacaRecord[].class);
        for (AlpacaRecord alpacaRecord : alpacaRecords) {
            final String answer = filter(alpacaRecord.getOutput());

            final String instruction = filter(alpacaRecord.getInstruction());

            String seedExample = String.format("""
                        - question: '%s'
                          answer: |\n%7s'%s'
                    """, instruction," ", answer);

            sb.append(seedExample);
        }

        String qna = String.format("%s\n%s\nseed_examples:\n%s", top, document, sb);

        File outputDir = new File(taxonomyPath, componentName);
        outputDir.mkdirs();

        File oldOutputFile = new File(outputDir, componentName + ".yaml");
        if (oldOutputFile.exists()) {
            oldOutputFile.delete();
        }

        File outputFile = new File(outputDir, "qna.yaml");
        FileUtils.writeStringToFile(outputFile, qna, StandardCharsets.UTF_8);
    }

    private String filter(String input) {
        return input.trim()
                .replace("\n", " ".repeat(7))
                .replace("'", "''");
    }
}
