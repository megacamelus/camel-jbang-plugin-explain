package org.apache.camel.jbang.ai.data.documentation;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.util.StringHelper;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Document;
import org.commonmark.node.Heading;
import org.commonmark.node.Text;

public class GenericDocumentationVisitor extends AbstractVisitor {
    private final CamelCatalog catalog;
    private final String componentName;

    public GenericDocumentationVisitor(CamelCatalog catalog, String componentName) {
        this.catalog = catalog;
        this.componentName = componentName;
    }

    @Override
    public void visit(Document document) {
        super.visit(document);

        final Heading title = new Heading();

        title.setLevel(1);
        Text text = new Text(StringHelper.capitalize(componentName));
        title.appendChild(text);

        document.prependChild(title);
    }
}
