package org.apache.camel.jbang.ai.data;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.jbang.ai.types.AlpacaRecord;
import org.apache.camel.jbang.ai.util.CatalogUtil;
import org.apache.camel.tooling.model.DataFormatModel;

public class DataFormatCatalogProcessor extends CatalogProcessor {
    public DataFormatCatalogProcessor(OpenAiStreamingChatModel chatModel, CamelCatalog catalog) {
        super(chatModel, catalog);
    }

    @Override
    public void process(int startFrom) throws InterruptedException {

        final List<String> componentNames = catalog.findDataFormatNames();
        final int totalComponents = componentNames.size();

        processRecords(startFrom, componentNames, totalComponents);
    }

    protected void processRecord(List<String> componentNames, int i, int totalComponents)
            throws InterruptedException {
        final String componentName = componentNames.get(i);

        final List<AlpacaRecord> alpacaRecords = new ArrayList<>(1024);
        System.out.printf("[%s] Processing data format %d of %d: %s%n", CatalogUtil.currentTime(), i, totalComponents, componentName);

        final DataFormatModel componentModel = catalog.dataFormatModel(componentName);

        final List<DataFormatModel.DataFormatOptionModel> componentOptions = componentModel.getOptions();
        processOption(alpacaRecords, componentName, componentOptions, "component");

        CatalogUtil.saveRecords(alpacaRecords, componentName);
    }
}
