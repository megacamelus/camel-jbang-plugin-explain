package org.apache.camel.jbang.ai.data;

import java.util.List;

import org.apache.camel.catalog.CamelCatalog;

public class DependenciesProcessor extends CatalogProcessor {
    public DependenciesProcessor(CamelCatalog catalog) {
        super(null, catalog);
    }

    @Override
    public void process(int startFrom) throws InterruptedException {
        final List<String> componentNames = catalog.findComponentNames();
        final int totalComponents = componentNames.size();

        processRecords(startFrom, componentNames, totalComponents);
    }

    @Override
    protected void processRecord(List<String> componentNames, int i, int totalComponents) throws InterruptedException {

    }

    @Override
    protected void processRecords(int startFrom, List<String> componentNames, int totalComponents) throws InterruptedException {
        for (String componentName : componentNames) {
            System.out.printf("%s:camel-%s:%s%n","org.apache.camel", componentName, catalog.getCatalogVersion());
        }

    }
}
