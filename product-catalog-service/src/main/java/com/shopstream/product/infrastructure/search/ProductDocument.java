package com.shopstream.product.infrastructure.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Elasticsearch Document pour recherche produits.
 * 
 * F2.2: Recherche avec Elasticsearch (full-text, facets, typos).
 * 
 * POURQUOI ELASTICSEARCH (vs PostgreSQL full-text):
 * 
 * ✅ Full-text search avancé (typos, synonymes, stemming)
 * ✅ Faceted search (agrégations rapides)
 * ✅ Scoring (relevance ranking)
 * ✅ Performance sur millions de documents
 * ✅ Horizontal scaling
 * 
 * ❌ Pas de transactions (eventual consistency)
 * ❌ Pas de relations (dénormaliser)
 * 
 * SYNCHRONISATION:
 * - Kafka Consumer écoute product.created, product.updated
 * - Index dans Elasticsearch de manière asynchrone
 * - Bulk indexing pour performance
 * 
 * ANALYZERS CUSTOM:
 * - french_analyzer: stemming français, stopwords
 * - autocomplete_analyzer: edge ngrams pour suggestion
 * - synonym_filter: rouge → vermeil, écarlate
 */
@Document(indexName = "products")
@Setting(
    settingPath = "/elasticsearch/product-settings.json"
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDocument {

    @Id
    private String id;

    private UUID tenantId;

    /**
     * Nom produit avec analyzer custom.
     * 
     * @MultiField:
     * - name (analyzed): pour full-text search
     * - name.keyword (not analyzed): pour exact match, sorting
     * - name.autocomplete: pour suggestions
     */
    @MultiField(
        mainField = @Field(
            type = FieldType.Text,
            analyzer = "french_analyzer",
            searchAnalyzer = "french_analyzer"
        ),
        otherFields = {
            @InnerField(suffix = "keyword", type = FieldType.Keyword),
            @InnerField(suffix = "autocomplete", type = FieldType.Text, analyzer = "autocomplete_analyzer")
        }
    )
    private String name;

    /**
     * Description avec analyzer français.
     */
    @Field(type = FieldType.Text, analyzer = "french_analyzer")
    private String description;

    /**
     * Prix pour range queries et sorting.
     */
    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Keyword)
    private String currency;

    /**
     * Brand pour faceted search.
     */
    @Field(type = FieldType.Keyword)
    private String brand;

    /**
     * Status pour filtering.
     */
    @Field(type = FieldType.Keyword)
    private String status;

    /**
     * Catégories pour faceted search (multi-valued).
     */
    @Field(type = FieldType.Keyword)
    private Set<String> categories;

    /**
     * Tags pour recherche additionnelle.
     */
    @Field(type = FieldType.Text, analyzer = "french_analyzer")
    private Set<String> tags;

    /**
     * Rating pour sorting et filtering.
     */
    @Field(type = FieldType.Double)
    private BigDecimal ratingAvg;

    @Field(type = FieldType.Integer)
    private Integer ratingCount;

    /**
     * View count pour popularity ranking.
     */
    @Field(type = FieldType.Integer)
    private Integer viewCount;

    /**
     * Stock disponible (synchronisé depuis inventory-service).
     */
    @Field(type = FieldType.Integer)
    private Integer stockAvailable;

    /**
     * Variantes dénormalisées (pour affichage rapide).
     */
    @Field(type = FieldType.Nested)
    private Set<VariantDocument> variants;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantDocument {
        private String sku;
        private String size;
        private String color;
        private BigDecimal price;
        private String imageUrl;
    }
}
