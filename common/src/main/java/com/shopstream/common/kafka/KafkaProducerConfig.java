package com.shopstream.common.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration Kafka Producer optimisée pour PRODUCTION.
 * 
 * DÉCISIONS TECHNIQUES ET POURQUOI:
 * 
 * 1. IDEMPOTENCE (enable.idempotence=true):
 *    - Garantit qu'un message envoyé plusieurs fois ne sera écrit qu'une fois
 *    - Critical pour retry automatiques (network issues)
 *    - Active automatiquement: acks=all, retries=MAX_INT, max.in.flight=5
 * 
 * 2. ACKS=all:
 *    - Producer attend confirmation de TOUS les replicas ISR (In-Sync Replicas)
 *    - Durabilité maximale (pas de perte de message)
 *    - Trade-off: latence +10-20ms vs acks=1
 *    - QUAND NE PAS UTILISER: logs non-critiques, metrics (préférer acks=1)
 * 
 * 3. COMPRESSION lz4:
 *    - Réduit taille messages ~50-70%
 *    - lz4 = meilleur ratio perf/compression (vs gzip, snappy)
 *    - CPU overhead minimal (2-3% broker CPU)
 *    - QUAND: messages > 1KB (sinon overhead > gain)
 * 
 * 4. TRANSACTIONAL ID:
 *    - Pour exactly-once semantics avec DB transactions
 *    - Permet rollback Kafka si DB rollback
 *    - MUST BE UNIQUE per producer instance
 *    - Format: service-name-${instance-id}
 * 
 * 5. MAX.IN.FLIGHT.REQUESTS=5:
 *    - Nombre de requêtes en parallèle sans attendre ACK
 *    - Avec idempotence: 5 est optimal (performance + ordering garantis)
 *    - Sans idempotence: MUST BE 1 pour ordering strict
 * 
 * ALTERNATIVES:
 * - Avro Serializer pour Schema Registry (production-grade)
 * - String Serializer pour dev/debug simple
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.application.name:unknown-service}")
    private String applicationName;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();

        // Connection
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Serialization
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Reliability (CRITICAL pour production)
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        // Performance
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 32 * 1024); // 32KB batches
        config.put(ProducerConfig.LINGER_MS_CONFIG, 10); // Wait 10ms pour batch

        // Timeouts
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);

        // Transactions (pour exactly-once)
        // NOTE: Sera override par @Transactional(transactionManager = "kafkaTransactionManager")
        config.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, applicationName + "-producer");

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
