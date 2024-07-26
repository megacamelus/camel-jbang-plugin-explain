package org.apache.camel.jbang.ai.util.parsers;

import java.util.List;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.markdown.MarkdownRenderer;

public class MarkdownParser implements DocumentationParser {

    @Override
    public String parse(String content, AbstractVisitor visitor) {
        List<Extension> extensions = List.of(TablesExtension.create());
        Parser parser = Parser.builder()
                .extensions(extensions)
                .build();

        final Node node = parser.parse(content);

        node.accept(visitor);

        final MarkdownRenderer renderer = MarkdownRenderer
                .builder()
                .extensions(extensions)
                .build();

        return renderer.render(node);
    }
}
