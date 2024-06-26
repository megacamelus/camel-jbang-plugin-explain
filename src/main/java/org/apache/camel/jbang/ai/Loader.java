package org.apache.camel.jbang.ai;

import java.util.List;
import java.util.concurrent.ExecutionException;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;

public final class Loader {

    private final String host;
    private final int port;
    private final String collectionName;
    private final boolean skipCreateCollection;

    private static Collections.Distance distance = Collections.Distance.Cosine;
    private static int dimension = 384;

    public Loader(String host, int port, String collectionName, boolean skipCreateCollection) {
        this.host = host;
        this.port = port;
        this.collectionName = collectionName;
        this.skipCreateCollection = skipCreateCollection;
    }

    public int load() throws ExecutionException, InterruptedException {
        EmbeddingStore<TextSegment> embeddingStore =
                QdrantEmbeddingStore.builder()
                        .host(host)
                        .port(port)
                        .collectionName(collectionName)
                        .build();

        if (!skipCreateCollection) {
            QdrantClient client =
                    new QdrantClient(
                            QdrantGrpcClient.newBuilder(host, port, false)
                                    .build());

            client
                    .createCollectionAsync(
                            collectionName,
                            Collections.VectorParams.newBuilder().setDistance(distance).setSize(dimension).build())
                    .get();
        }

        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        CamelCatalog catalog = new DefaultCamelCatalog(true);

        final List<String> componentNames = catalog.findComponentNames();

        for (String componentName : componentNames) {
            System.out.println("Processing: " + componentName);

            final ComponentModel componentModel = catalog.componentModel(componentName);
            final List<ComponentModel.ComponentOptionModel> componentOptions = componentModel.getComponentOptions();

            for (ComponentModel.ComponentOptionModel optionModel : componentOptions) {
                final String data = toEmbeddableText(componentName, optionModel);

                TextSegment segment1 = TextSegment.from(data);
                Embedding embedding1 = embeddingModel.embed(segment1).content();
                embeddingStore.add(embedding1, segment1);
            }

            final List<ComponentModel.EndpointOptionModel> endpointParameterOptions1 =
                    componentModel.getEndpointParameterOptions();
            for (ComponentModel.EndpointOptionModel endpointParameterModel : endpointParameterOptions1) {
                final String data = toEmbeddableText(componentName, endpointParameterModel);

                TextSegment segment1 = TextSegment.from(data);
                Embedding embedding1 = embeddingModel.embed(segment1).content();
                embeddingStore.add(embedding1, segment1);
            }
        }
        return 0;
    }

    private static String toEmbeddableText(String componentName, BaseOptionModel optionModel) {
        SimpleRequestBuilder request = new SimpleRequestBuilder();

        request.append("component", componentName)
                .append("option", optionModel.getName())
                .append("description", optionModel.getDescription())
                .append("defaultValue", optionModel.getDefaultValue())
                .append("type", optionModel.getType())
                .append("required", optionModel.isRequired())
                .append("groups", optionModel.getGroup());

        return request.build();
    }
}
