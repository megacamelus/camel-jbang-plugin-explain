package org.apache.camel.jbang.ai.data;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.jbang.ai.types.AlpacaRecord;
import org.apache.camel.jbang.ai.util.CatalogUtil;
import org.apache.camel.tooling.model.ComponentModel;

public class ComponentCatalogProcessor extends CatalogProcessor {

    public ComponentCatalogProcessor(OpenAiStreamingChatModel chatModel, CamelCatalog catalog) {
        super(chatModel, catalog);
    }

    @Override
    public void process(int startFrom) throws InterruptedException {

        final List<String> componentNames = catalog.findComponentNames();
        final int totalComponents = componentNames.size();

        processRecords(startFrom, componentNames, totalComponents);
    }

    @Override
    protected void processRecord(List<String> componentNames, int i, int totalComponents)
            throws InterruptedException {
        final String componentName = componentNames.get(i);

        final List<AlpacaRecord> alpacaRecords = new ArrayList<>(1024);
        System.out.printf("[%s] Processing component %d of %d: %s%n", CatalogUtil.currentTime(), i, totalComponents, componentName);

        final ComponentModel componentModel = catalog.componentModel(componentName);

        final List<ComponentModel.ComponentOptionModel> componentOptions = componentModel.getComponentOptions();
        processOption(alpacaRecords, componentName, componentOptions, "component");

        final List<ComponentModel.EndpointOptionModel> endpointParameterOptions =
                componentModel.getEndpointParameterOptions();
        processOption(alpacaRecords, componentName, endpointParameterOptions, "endpoint");

        CatalogUtil.saveRecords(alpacaRecords, componentName);
    }
}
