package com.shopstream.tenant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Main application class pour Tenant Service.
 * 
 * ANNOTATIONS SPRING BOOT:
 * 
 * @SpringBootApplication:
 * - @Configuration: classe de configuration Spring
 * - @EnableAutoConfiguration: config automatique (DB, Web, etc.)
 * - @ComponentScan: scan @Component, @Service, @Repository
 * 
 * @EnableJpaAuditing:
 * - Active audit automatique (created_at, updated_at, etc.)
 * - Nécessaire pour @CreatedDate, @LastModifiedDate dans BaseEntity
 * 
 * @EnableCaching:
 * - Active cache Spring (@Cacheable, @CacheEvict)
 * - Intégré avec Redis (config dans application.yml)
 * 
 * @EnableScheduling:
 * - Active scheduled tasks (@Scheduled)
 * - Utilisé pour cleanup tenants deleted > 30 jours
 * 
 * @EnableTransactionManagement:
 * - Active @Transactional (normalement auto-enabled)
 * - Gestion transactions JPA
 */
@SpringBootApplication(
    scanBasePackages = {
        "com.shopstream.tenant",
        "com.shopstream.common" // Scan module commun
    }
)
@EnableJpaAuditing
@EnableCaching
@EnableScheduling
@EnableTransactionManagement
public class TenantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TenantServiceApplication.class, args);
    }
}
