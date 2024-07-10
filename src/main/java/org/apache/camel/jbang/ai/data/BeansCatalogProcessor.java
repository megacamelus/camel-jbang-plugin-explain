package org.apache.camel.jbang.ai.data;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.jbang.ai.types.AlpacaRecord;
import org.apache.camel.jbang.ai.util.CatalogUtil;
import org.apache.camel.tooling.model.PojoBeanModel;

public class BeansCatalogProcessor extends CatalogProcessor {
    public BeansCatalogProcessor(OpenAiStreamingChatModel chatModel, CamelCatalog catalog) {
        super(chatModel, catalog);
    }

    @Override
    public void process(int startFrom) throws InterruptedException {

        final List<String> names = catalog.findBeansNames();
        final int totalComponents = names.size();

        processRecords(startFrom, names, totalComponents);
    }

    protected void processRecord(List<String> componentNames, int i, int totalComponents)
            throws InterruptedException {
        final String componentName = componentNames.get(i);

        final List<AlpacaRecord> alpacaRecords = new ArrayList<>(1024);
        System.out.printf("[%s] Processing languages %d of %d: %s%n", CatalogUtil.currentTime(), i, totalComponents, componentName);

        final PojoBeanModel componentModel = catalog.pojoBeanModel(componentName);

        final List<PojoBeanModel.PojoBeanOptionModel> componentOptions = componentModel.getOptions();
        processOption(alpacaRecords, componentName, componentOptions, "bean");

        CatalogUtil.saveRecords(alpacaRecords, componentName);
    }
}
