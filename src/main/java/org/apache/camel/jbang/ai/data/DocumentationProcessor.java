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
import org.apache.camel.jbang.ai.data.documentation.GenericDocumentationVisitor;
import org.apache.camel.jbang.ai.util.CatalogUtil;
import org.apache.camel.jbang.ai.util.parsers.DocumentationParser;
import org.apache.camel.jbang.ai.util.parsers.MarkdownParser;
import org.apache.commons.io.FileUtils;
import org.commonmark.node.AbstractVisitor;

public class DocumentationProcessor {
    private final String sourcePath;
    private final CamelCatalog catalog;

    public DocumentationProcessor(String sourcePath) {
        this.sourcePath = sourcePath;

        catalog = new DefaultCamelCatalog(true);
    }

    // We only want the documentation from the source code (not the ones copied during build)
    public boolean isAdoc(Path p) {
        return p.toFile().getName().endsWith(".md")
                && p.toAbsolutePath().toString().contains("src/main/docs");
    }

    // We only want the components
    public boolean isComponent(Path p) {
        return p.toFile().getName().contains("-component");
    }

    public boolean parse(Path doc, String componentName, AbstractVisitor visitor) {
        try {
            final String content = FileUtils.readFileToString(doc.toFile());

            DocumentationParser parser = new MarkdownParser();
            final String parsed = parser.parse(content, visitor);

            if (parsed != null) {
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
            final String fileName = doc.getFileName().toString();

            final String componentName = fileName.replaceAll("-component.*", "");

            System.out.printf("[%s] Processing document %d of %d %s for %s: ", CatalogUtil.currentTime(),
                    i + 1, documentTotal, doc.getFileName(), componentName);

            AbstractVisitor visitor = getVisitorForDocumentType(fileName, componentName);
            if (parse(doc, componentName, visitor)) {
                generatedCount++;
                System.out.printf("done%n");
            } else {
                System.out.printf("skipped due to inconsistent documentation formatting%n");
                skippedCount++;
            }
        }
        System.out.printf("[%s] Generated: %d. Skipped: %d%n", CatalogUtil.currentTime(), generatedCount, skippedCount);
    }

    private AbstractVisitor getVisitorForDocumentType(String fileName, String componentName) {
        if (fileName.contains("-component")) {
            return new ComponentDocumentationVisitor(catalog, componentName);
        }

        return new GenericDocumentationVisitor(catalog, componentName);
    }
}
