package uz.usta.platform.catalog.internal.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.catalog")
public record CatalogProperties(int maxCategoryDepth, int maxServicesPerMaster, int maxPortfolioItems) {

    public CatalogProperties {
        maxCategoryDepth = maxCategoryDepth <= 0 ? 3 : maxCategoryDepth;
        maxServicesPerMaster = maxServicesPerMaster <= 0 ? 50 : maxServicesPerMaster;
        maxPortfolioItems = maxPortfolioItems <= 0 ? 30 : maxPortfolioItems;
    }
}
