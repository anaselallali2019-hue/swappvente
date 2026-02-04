package com.shopstream.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class pour Product Catalog Service.
 * 
 * @EnableElasticsearchRepositories:
 * - Scan repositories Elasticsearch
 * - Package séparé de JPA repositories
 * 
 * @EnableKafkaStreams:
 * - Active Kafka Streams pour top products temps réel
 * - State stores pour aggregations
 * 
 * @EnableAsync:
 * - Elasticsearch indexing asynchrone
 * - Évite bloquer requêtes HTTP
 */
@SpringBootApplication(
    scanBasePackages = {
        "com.shopstream.product",
        "com.shopstream.common"
    }
)
@EnableJpaRepositories(basePackages = "com.shopstream.product.domain.repository")
@EnableElasticsearchRepositories(basePackages = "com.shopstream.product.infrastructure.search")
@EnableJpaAuditing
@EnableCaching
@EnableScheduling
@EnableKafka
@EnableKafkaStreams
@EnableAsync
public class ProductCatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductCatalogServiceApplication.class, args);
    }
}
