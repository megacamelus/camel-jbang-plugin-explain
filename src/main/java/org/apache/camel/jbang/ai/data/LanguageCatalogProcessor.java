package org.apache.camel.jbang.ai.data;

import java.util.ArrayList;
import java.util.List;

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.jbang.ai.types.AlpacaRecord;
import org.apache.camel.jbang.ai.util.CatalogUtil;
import org.apache.camel.tooling.model.LanguageModel;

public class LanguageCatalogProcessor extends CatalogProcessor {
    public LanguageCatalogProcessor(OpenAiStreamingChatModel chatModel, CamelCatalog catalog) {
        super(chatModel, catalog);
    }

    @Override
    public void process(int startFrom) throws InterruptedException {

        final List<String> names = catalog.findLanguageNames();
        final int totalComponents = names.size();

        processRecords(startFrom, names, totalComponents);
    }

    protected void processRecord(List<String> componentNames, int i, int totalComponents)
            throws InterruptedException {
        final String componentName = componentNames.get(i);

        final List<AlpacaRecord> alpacaRecords = new ArrayList<>(1024);
        System.out.printf("[%s] Processing languages %d of %d: %s%n", CatalogUtil.currentTime(), i, totalComponents, componentName);

        final LanguageModel componentModel = catalog.languageModel(componentName);

        final List<LanguageModel.LanguageOptionModel> componentOptions = componentModel.getOptions();
        processOption(alpacaRecords, componentName, componentOptions, "language");

        CatalogUtil.saveRecords(alpacaRecords, componentName);
    }
}
