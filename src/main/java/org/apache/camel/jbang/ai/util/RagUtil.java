package org.apache.camel.jbang.ai.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;

import static java.util.stream.Collectors.joining;

public final class RagUtil {
    private static final PromptTemplate PROMPT_TEMPLATE = PromptTemplate.from(
            "Answer the following question to the best of your ability:\n"
                    + "\n"
                    + "Question:\n"
                    + "{{question}}\n"
                    + "\n"
                    + "Base your answer on the following information:\n"
                    + "{{information}}");

    public static List<EmbeddingMatch<TextSegment>> findRelevant(String host, int port, String collectionName, String searchTerm) {
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        Embedding questionEmbedding = embeddingModel.embed(searchTerm).content();

        EmbeddingStore<TextSegment> embeddingStore =
                QdrantEmbeddingStore.builder()
                        .host(host)
                        .port(port)
                        .collectionName(collectionName)
                        .build();

        int maxResults = 4;
        double minScore = 0.7;
        List<EmbeddingMatch<TextSegment>> relevantEmbeddings
                = embeddingStore.findRelevant(questionEmbedding, maxResults, minScore);
        return relevantEmbeddings;
    }

    public static Prompt toPrompt(List<EmbeddingMatch<TextSegment>> relevantEmbeddings, String question) {
        String information = relevantEmbeddings.stream()
                .map(match -> match.embedded().text())
                .collect(joining("\n\n"));

        Map<String, Object> variables = new HashMap<>();
        variables.put("question", question);
        variables.put("information", information);

        Prompt prompt = PROMPT_TEMPLATE.apply(variables);
        return prompt;
    }
}
