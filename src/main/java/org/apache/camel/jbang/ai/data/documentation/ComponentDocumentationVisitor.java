package org.apache.camel.jbang.ai.data.documentation;

import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.util.StringHelper;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Document;
import org.commonmark.node.Emphasis;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;

public class ComponentDocumentationVisitor extends AbstractVisitor {
    private final CamelCatalog catalog;
    private final String componentName;

    public ComponentDocumentationVisitor(CamelCatalog catalog, String componentName) {
        this.catalog = catalog;
        this.componentName = componentName;
    }

    @Override
    public void visit(Emphasis emphasis) {
        super.visit(emphasis);
    }

    @Override
    public void visit(CustomNode customNode) {
        super.visit(customNode);
    }

    @Override
    public void visit(Code code) {
        super.visit(code);
    }

    @Override
    public void visit(Text text) {
        super.visit(text);
    }

    @Override
    public void visit(Document document) {
        super.visit(document);

        final Heading title = new Heading();

        title.setLevel(1);
        Text text = new Text(StringHelper.capitalize(componentName));
        title.appendChild(text);

        document.prependChild(title);

        createConfigurationTable(document);
    }

    private void createConfigurationTable(Document document) {
        final Heading componentConfiguration = new Heading();

        componentConfiguration.setLevel(2);
        componentConfiguration.appendChild(new Text("Component Configurations"));

        document.appendChild(componentConfiguration);

        final ComponentModel componentModel = catalog.componentModel(componentName);

        if (componentModel != null) {
            final List<ComponentModel.ComponentOptionModel> componentOptions = componentModel.getComponentOptions();

            if (componentOptions != null) {
                final TableBlock tableBlock = createConfigurationTable(document, componentOptions);
                document.appendChild(tableBlock);
            } else {
                componentConfiguration.appendChild(new Text("There are no configurations for this component"));
            }
        } else {
            componentConfiguration.appendChild(new Text("There are no configurations for this component (null model)"));
        }
    }

    private static TableBlock createConfigurationTable(Document document, List<ComponentModel.ComponentOptionModel> componentOptions) {
        final TableBlock tableBlock = new TableBlock();
        final TableHead tableHead = createTableHead();
        tableBlock.appendChild(tableHead);

        final TableBody tableBody = new TableBody();

        componentOptions.forEach(opt -> createConfigurationRow(opt, tableBody));

        tableBlock.appendChild(tableBody);
        document.appendChild(new HardLineBreak());
        return tableBlock;
    }

    private static void createConfigurationRow(ComponentModel.ComponentOptionModel opt, TableBody tableBody) {
        TableRow dataRow = new TableRow();

        addToRow(opt.getName(), dataRow);
        addToRow(opt.getDescription(), dataRow);
        String val;
        final Object defaultValue = opt.getDefaultValue();
        if (defaultValue != null) {
            val = String.valueOf(opt.getDefaultValue());
        } else {
            val = "";
        }

        addToRow(val, dataRow);
        addToRow(opt.getType(), dataRow);

        tableBody.appendChild(dataRow);
    }

    private static TableHead createTableHead() {
        final TableHead tableHead = new TableHead();
        TableRow tableRow = new TableRow();

        addToRow("Name", tableRow);
        addToRow("Description", tableRow);
        addToRow("Default", tableRow);
        addToRow("Type", tableRow);

        tableHead.appendChild(tableRow);
        return tableHead;
    }

    private static void addToRow(String text, TableRow tableRow) {
        TableCell typeCell = new TableCell();
        typeCell.appendChild(new Text(text));
        tableRow.appendChild(typeCell);
    }
}
