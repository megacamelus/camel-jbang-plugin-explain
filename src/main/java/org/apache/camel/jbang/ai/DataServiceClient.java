package org.apache.camel.jbang.ai;

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.jbang.ai.data.CatalogProcessor;
import org.apache.camel.jbang.ai.data.ComponentCatalogProcessor;
import org.apache.camel.jbang.ai.data.DataFormatCatalogProcessor;
import org.apache.camel.jbang.ai.data.LanguageCatalogProcessor;
import org.apache.camel.jbang.ai.data.BeansCatalogProcessor;

import static org.apache.camel.jbang.ai.util.ModelUtil.buildModel;

public class DataServiceClient {

    private final String url;
    private final String apiKey;
    private final String modelName;
    private final String dataType;
    private final int startFrom;

    public DataServiceClient(String url, String apiKey, String modelName, String dataType, int startFrom) {
        this.url = url;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.dataType = dataType;
        this.startFrom = startFrom;
    }

    public int run() throws InterruptedException {
        OpenAiStreamingChatModel chatModel = buildModel(url, apiKey, modelName);

        createSyntheticQuestions(chatModel);

        return 0;
    }

    private void createSyntheticQuestions(OpenAiStreamingChatModel chatModel) throws InterruptedException {
        CatalogProcessor catalogProcessor = buildCatalogProcessor();

        catalogProcessor.process(startFrom);
    }

    private CatalogProcessor buildCatalogProcessor() {
        final CamelCatalog catalog = new DefaultCamelCatalog(true);
        final OpenAiStreamingChatModel chatModel = buildModel(url, apiKey, modelName);

        return switch (dataType) {
            case "dataformat" -> new DataFormatCatalogProcessor(chatModel, catalog);
            case "language" -> new LanguageCatalogProcessor(chatModel, catalog);
            case "beans" -> new BeansCatalogProcessor(chatModel, catalog);
            case "component" -> new ComponentCatalogProcessor(chatModel, catalog);
            default -> throw new RuntimeException("Invalid data type: " + dataType);
        };
    }


}
