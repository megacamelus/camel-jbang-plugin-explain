package org.apache.camel.jbang.ai.util.parsers;

import org.commonmark.node.AbstractVisitor;

public interface DocumentationParser {

    String parse(String text, AbstractVisitor visitor);
}
