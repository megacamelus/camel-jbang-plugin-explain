package org.apache.camel.jbang.ai.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.jbang.ai.types.AlpacaRecord;
import org.apache.camel.jbang.ai.util.CatalogUtil;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.EipModel;

public class EipsCatalogProcessor extends CatalogProcessor {
    private static final PromptTemplate QUESTION_GENERATOR_PROMPT_TEMPLATE = PromptTemplate.from(
            "Please write a question about the Apache Camel pattern {{pattern}} option named {{optionName}} that can be answered by the following information: {{information}}");

    public EipsCatalogProcessor(OpenAiStreamingChatModel chatModel, CamelCatalog catalog) {
        super(chatModel, catalog);
    }

    @Override
    public void process(int startFrom) throws InterruptedException {
        final List<String> names = catalog.findModelNames()
                .stream()
                .filter(n -> catalog.eipModel(n).getLabel().contains("eip"))
                .collect(Collectors.toList());

        final int totalComponents = names.size();

        processRecords(startFrom, names, totalComponents);
    }


    protected void processRecord(List<String> componentNames, int i, int totalComponents)
            throws InterruptedException {
        final String componentName = componentNames.get(i);

        final List<AlpacaRecord> alpacaRecords = new ArrayList<>(1024);
        System.out.printf("[%s] Processing EIP %d of %d: %s%n", CatalogUtil.currentTime(), i, totalComponents, componentName);

        final EipModel componentModel = catalog.eipModel(componentName);

        final List<EipModel.EipOptionModel> componentOptions = componentModel.getOptions();
        processOption(alpacaRecords, componentName, componentOptions, "EIP");

        CatalogUtil.saveRecords(alpacaRecords, componentName);
    }

    // We want to use a custom prompt, so we override the generator from the parent class
    @Override
    protected QuestionMeta generateQuestionPrompt(
            String name, BaseOptionModel optionModel) {
        final String data = CatalogUtil.toEmbeddableText(name, optionModel);

        Map<String, Object> variables = new HashMap<>();
        variables.put("pattern", name);
        variables.put("optionName", optionModel.getName());
        variables.put("information", data);

        final Prompt prompt = QUESTION_GENERATOR_PROMPT_TEMPLATE.apply(variables);

        return new QuestionMeta(prompt.toUserMessage(), data);
    }
}
