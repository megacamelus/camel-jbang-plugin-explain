package org.apache.camel.jbang.ai.util;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;

public class MarkdownParser implements ResponseParser {
    @Override
    public String parse(String response) {
        if (!response.contains("```")) {
            return response;
        }

        Parser parser = Parser.builder().build();

        final Node node = parser.parse(response);
        if (node != null) {
            final Node firstChild = node.getFirstChild();
            if (firstChild != null) {
                final Node firstSourceNode = firstChild.getNext();
                if (firstSourceNode != null) {
                    // The first child is the paragraph. The next node to it the source code.
                    return TextContentRenderer.builder().build().render(firstSourceNode);
                } else {
                    return TextContentRenderer.builder().build().render(firstChild);
                }
            }
        }
        return response;
    }
}
