package org.apache.camel.jbang.ai.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.jbang.ai.data.documentation.ComponentDocumentationVisitor;
import org.apache.camel.jbang.ai.util.CatalogUtil;
import org.apache.camel.jbang.ai.util.parsers.DocumentationParser;
import org.apache.camel.jbang.ai.util.parsers.MarkdownParser;
import org.apache.commons.io.FileUtils;

public class DocumentationProcessor {
    private final String sourcePath;
    private final CamelCatalog catalog;

    public DocumentationProcessor(String sourcePath) {
        this.sourcePath = sourcePath;

        catalog = new DefaultCamelCatalog(true);
    }

    // We only want the documentation from the source code (not the ones copied during build)
    public boolean isAdoc(Path p) {
        return p.toFile().getName().endsWith(".xml.md")
                && p.toAbsolutePath().toString().contains("src/main/docs")
                && p.toAbsolutePath().toString().contains("components")
                && !p.toAbsolutePath().toString().contains("tooling")
                && !p.toAbsolutePath().toString().contains("dsl")
                && isComponent(p);
    }

    // We only want the components
    public boolean isComponent(Path p) {
        return p.toFile().getName().contains("-component");
    }

    public boolean parse(Path doc, String componentName) {
        try {
            final String content = FileUtils.readFileToString(doc.toFile());

            DocumentationParser parser = new MarkdownParser();
            final String parsed = parser.parse(content, new ComponentDocumentationVisitor(catalog, componentName));

            if (parsed != null) {

                // Extracts "kafka" from "kafka-component.adoc"
                CatalogUtil.saveDocumentation(parsed, componentName);
                return true;
            }

            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void process(int startFrom) throws InterruptedException {
        File camelSource = new File(sourcePath);
        final List<Path> docs;

        // Step 1: get all ascii doc files.
        try {
             docs = Files.walk(camelSource.toPath()).filter(this::isAdoc).collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final int documentTotal = docs.size();
        int generatedCount = 0;
        int skippedCount = 0;
        for (int i = startFrom; i < documentTotal; i++) {
            Path doc = docs.get(i);
            final String componentName = doc.getFileName().toString().replaceAll("-component.*", "");

            System.out.printf("[%s] Processing document %d of %d %s for %s: ", CatalogUtil.currentTime(),
                    i + 1, documentTotal, doc.getFileName(), componentName);

            if (parse(doc, componentName)) {
                generatedCount++;
                System.out.printf("done%n");
            } else {
                System.out.printf("skipped due to inconsistent documentation formatting%n");
                skippedCount++;
            }
        }
        System.out.printf("[%s] Generated: %d. Skipped: %d%n", CatalogUtil.currentTime(), generatedCount, skippedCount);
    }
}
