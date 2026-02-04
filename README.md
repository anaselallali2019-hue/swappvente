# 🛍️ ShopStream Platform

> Plateforme SaaS B2B2C multi-tenant pour marques créant leurs boutiques en ligne

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Kafka](https://img.shields.io/badge/Kafka-3.6.0-black.svg)](https://kafka.apache.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue.svg)](https://www.postgresql.org/)

## 📋 Table des Matières

- [Vue d'ensemble](#vue-densemble)
- [Architecture](#architecture)
- [Technologies](#technologies)
- [Microservices](#microservices)
- [Patterns & Concepts](#patterns--concepts)
- [Getting Started](#getting-started)
- [Documentation Technique](#documentation-technique)

---

## 🎯 Vue d'ensemble

ShopStream est une **plateforme e-commerce multi-tenant complète** permettant à des marques de créer et gérer leurs boutiques en ligne. Ce projet est conçu pour **maîtriser TOUS les concepts avancés** demandés sur le marché du développement Java/Spring.

### Objectifs Pédagogiques

✅ **Event-Driven Architecture** (Kafka, Event Sourcing, CQRS)  
✅ **Patterns Avancés** (Saga, Outbox, Strangler, CQRS)  
✅ **Bases de Données** (JPA, Criteria API, Native Queries, JOOQ)  
✅ **Cache Distribué** (Redis avec différentes stratégies)  
✅ **Search** (Elasticsearch avec full-text, facets, ML)  
✅ **Observabilité** (Prometheus, Grafana, Jaeger)  
✅ **Testing** (Testcontainers, Contract, Load)  

---

## 🏗️ Architecture

### Architecture Globale

```
┌─────────────┐
│  API GATEWAY│ (Spring Cloud Gateway, JWT, Rate Limiting)
└──────┬──────┘
       │
       ├─────────────────────────────────────────┐
       │                                         │
┌──────▼──────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
│   TENANT    │  │ PRODUCT  │  │INVENTORY │  │  ORDER   │
│   SERVICE   │  │ CATALOG  │  │ SERVICE  │  │ SERVICE  │
└──────┬──────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘
       │              │              │              │
       └──────────────┴──────────────┴──────────────┘
                           │
                      ┌────▼────┐
                      │  KAFKA  │ (Event Bus)
                      └────┬────┘
                           │
       ┌───────────────────┼───────────────────┐
       │                   │                   │
┌──────▼──────┐  ┌─────────▼─────┐  ┌─────────▼─────┐
│  PAYMENT    │  │ NOTIFICATION  │  │  ANALYTICS    │
│  SERVICE    │  │   SERVICE     │  │   SERVICE     │
└─────────────┘  └───────────────┘  └───────────────┘
```

### Flux de Données

1. **Command Side** (Write): Requêtes modifient état → Events Kafka
2. **Query Side** (Read): Projections optimisées pour lecture
3. **Event Streaming**: Kafka Streams pour analytics temps réel
4. **CDC**: Debezium capture changements DB → Kafka (Outbox Pattern)

---

## 💻 Technologies

### Core Stack

| Catégorie | Technologie | Version | Pourquoi |
|-----------|-------------|---------|----------|
| **Language** | Java | 21 | Virtual Threads, Pattern Matching, Records |
| **Framework** | Spring Boot | 3.2.0 | Écosystème complet, production-ready |
| **Event Streaming** | Apache Kafka | 3.6.0 | Event-driven architecture, exactly-once semantics |
| **Database** | PostgreSQL | 15 | JSONB, window functions, logical replication |
| **Cache** | Redis | 7 | Cache distribué, rate limiting, pub/sub |
| **Search** | Elasticsearch | 8.11 | Full-text search, faceted search, ML |
| **Time-Series** | TimescaleDB | Latest | GPS tracking haute fréquence |
| **OLAP** | ClickHouse | Latest | Analytics complexes, columnar storage |

### Libraries & Tools

- **MapStruct** 1.5.5: Mapping DTO↔Entity (génération compile-time)
- **Hibernate Envers**: Audit trail automatique
- **Testcontainers** 1.19: Tests intégration avec Docker
- **JOOQ** 3.18: Type-safe SQL pour bulk operations
- **Drools** 8.44: Rules engine pour pricing dynamique
- **Resilience4j**: Circuit breaker, retry, rate limiting
- **Debezium** 2.5: Change Data Capture (CDC)

---

## 🧩 Microservices

### 1️⃣ Tenant-Service (Port: 8081)

**Responsabilité**: Gestion multi-tenant (marques/entreprises)

**Technologies Utilisées**:
- ✅ **JPA** pour CRUD basique
- ✅ **Criteria API + Specifications** pour filtres dynamiques
- ✅ **Native Query** pour statistiques (window functions, CTEs)
- ✅ **Hibernate Envers** pour audit trail
- ✅ **Outbox Pattern** (Debezium CDC) pour events Kafka
- ✅ **Redis Cache** pour résultats recherche
- ✅ **Optimistic Locking** (@Version)
- ✅ **Soft Delete** avec @SQLDelete

**Endpoints**:
```
POST   /api/v1/tenants           # Créer tenant
GET    /api/v1/tenants           # Recherche avec filtres (Criteria API)
GET    /api/v1/tenants/{id}      # Détail tenant
PUT    /api/v1/tenants/{id}      # Mise à jour (Optimistic Lock)
DELETE /api/v1/tenants/{id}      # Soft delete
GET    /api/v1/tenants/stats     # Dashboard stats (Native Query)
```

**Events Kafka**:
- `tenant.created` → product-catalog, inventory, notification services
- `tenant.updated` → tous services concernés
- `tenant.deleted` → archivage données

**Database Schema**:
```sql
tenants (id, name, subdomain, tier, status, settings JSONB, version, deleted_at)
tenant_subscriptions (tenant_id, plan_name, monthly_revenue, start_date, end_date)
tenant_users (tenant_id, email, role)
outbox_events (aggregate_id, event_type, payload JSONB, processed_at)
```

**Patterns Implémentés**:
- **Factory Pattern**: TenantFactory pour création avec config initiale
- **Strategy Pattern**: Comportements différents par tier (FREE, PRO, ENTERPRISE)
- **Specifications Pattern**: Filtres dynamiques composables
- **Outbox Pattern**: Atomicité DB + Kafka garantie

### 2️⃣ Product-Catalog-Service (Port: 8082)

**Responsabilité**: Catalogue produits avec variantes, recherche intelligente

**Technologies Utilisées**:
- ✅ **JPA** avec @OneToMany cascade (produit → variantes)
- ✅ **Elasticsearch** pour search (full-text, facets, typos, synonymes)
- ✅ **Kafka Streams** pour top produits temps réel
- ✅ **Redis** pour cache recommendations
- ✅ **JOOQ** pour bulk price updates
- ✅ **Materialized View** PostgreSQL pour produits populaires

**Fonctionnalités Clés**:
- Produits avec variantes (taille × couleur = SKUs auto-générés)
- Recherche intelligente (typos, synonymes, boosting)
- Faceted search (filtres catégorie, prix, stock, rating)
- Recommendations (More Like This, ML-based)
- Bulk operations (update prix 10,000 produits)

**Events Kafka**:
- `product.created` → inventory, elasticsearch
- `product.updated` → elasticsearch sync
- `product.viewed` → kafka streams (top products)

### 3️⃣ Inventory-Service (Port: 8083)

**Responsabilité**: Gestion stock temps réel, multi-entrepôts

**Technologies Utilisées**:
- ✅ **Pessimistic Locking** (SELECT FOR UPDATE NOWAIT)
- ✅ **Kafka Streams** pour alertes stock bas
- ✅ **PostGIS** pour calcul distance entrepôts
- ✅ **Event Sourcing** pour historique mouvements
- ✅ **Redis Sorted Sets** pour prédictions rupture

**Fonctionnalités Clés**:
- Réservation stock avec timeout (15 min panier)
- Multi-entrepôts avec routing géographique
- Alertes stock bas temps réel (Kafka Streams)
- Historique traçable (Event Sourcing)
- Prédiction rupture (ML simple)

**Events Kafka**:
- `inventory.reserved` → order service
- `inventory.updated` → product catalog (enrichment)
- `inventory.low-stock-alert` → notification service

### 4️⃣ Order-Service (Port: 8084)

**Responsabilité**: Gestion commandes avec Event Sourcing + CQRS + Saga

**Technologies Utilisées**:
- ✅ **Event Sourcing** pur (tous changements = events)
- ✅ **CQRS** (write model ≠ read model)
- ✅ **Saga Orchestration** (workflow order → payment → shipping)
- ✅ **Kafka Compacted Topics** comme Event Store
- ✅ **Kafka Streams** pour détection fraude temps réel
- ✅ **Native Query** avec window functions (analytics)

**Fonctionnalités Clés**:
- Event Sourcing complet (rebuild aggregate)
- Read model dénormalisé (PostgreSQL)
- Saga workflow avec compensation
- Détection fraude temps réel
- Analytics avancées (cohort, MRR, churn)

**Events Kafka**:
- `order.created` → saga orchestrator
- `order.completed` → analytics, recommendations
- `fraud.detected` → manual review

### 5️⃣ Payment-Service (Port: 8085)

**Responsabilité**: Intégration multi-gateway (Stripe, PayPal)

**Technologies Utilisées**:
- ✅ **Strategy Pattern** pour différents gateways
- ✅ **Idempotency Keys** (critical pour paiements)
- ✅ **Webhook Signature Verification** (HMAC)
- ✅ **Resilience4j** (retry, circuit breaker, fallback)
- ✅ **Outbox Pattern** pour atomicité

**Fonctionnalités Clés**:
- Multi-gateway avec fallback automatique
- Webhooks sécurisés (signature verification)
- Remboursements avec saga compensation
- Multi-devises avec cache taux change
- Retry intelligent avec backoff exponentiel

### 6️⃣ Pricing-Service (Port: 8086)

**Technologies**: Drools rules engine, ML dynamic pricing, A/B testing

### 7️⃣ Recommendation-Service (Port: 8087)

**Technologies**: Collaborative filtering, Kafka Streams, Redis vectors

### 8️⃣ Notification-Service (Port: 8088)

**Technologies**: Multi-canal (Email, SMS, Push, WebSocket), rate limiting, templates

### 9️⃣ Delivery-Tracking-Service (Port: 8089)

**Technologies**: WebSocket GPS streaming, TimescaleDB, PostGIS, geofencing

### 🔟 Analytics-Service (Port: 8090)

**Technologies**: Kafka Streams, ClickHouse, SSE real-time dashboards

### 1️⃣1️⃣ API Gateway (Port: 8080)

**Technologies**: Spring Cloud Gateway, JWT, rate limiting, circuit breaker

---

## 🎓 Patterns & Concepts Avancés

### Event-Driven Patterns

| Pattern | Où | Pourquoi |
|---------|-----|----------|
| **Outbox Pattern** | tenant, order, payment | Atomicité DB + Kafka (pas dual-write) |
| **Event Sourcing** | order, inventory | Audit complet, temporal queries, replay |
| **CQRS** | order | Read/Write models séparés, scaling indépendant |
| **Saga Orchestration** | order | Workflow multi-services avec rollback |
| **Saga Choreography** | payment refund | Event-driven, pas de coordinator central |

### Database Patterns

| Technique | Quand Utiliser | Exemple |
|-----------|----------------|---------|
| **JPA** | CRUD simple | Tenant CRUD |
| **Criteria API** | Filtres dynamiques (0-N critères) | Tenant search |
| **Native Query** | Window functions, CTEs, JSONB | Tenant stats |
| **JOOQ** | Bulk updates (milliers rows) | Product price update |
| **Materialized View** | Agrégations pré-calculées | Popular products |
| **Pessimistic Lock** | Concurrence critique (stock) | Inventory reservation |
| **Optimistic Lock** | Concurrence légère | Tenant config update |

### Cache Strategies

| Strategy | Où | TTL | Pourquoi |
|----------|-----|-----|----------|
| **Cache-Aside** | Tenant search | 5 min | Queries fréquentes, données changent peu |
| **Write-Through** | Product details | - | Cohérence forte |
| **Write-Behind** | Page views | - | Performance write |
| **Sorted Sets** | Leaderboards | 1h | Top products, recommendations |

### Resilience Patterns

- **Circuit Breaker**: Stripe payment (fallback PayPal)
- **Retry avec Backoff**: Kafka producer, external APIs
- **Rate Limiting**: API Gateway (Redis sliding window)
- **Bulkhead**: Thread pools isolés par service
- **Timeout**: Toutes external calls (30s max)

---

## 🚀 Getting Started

### Prérequis

- **Java 21** (OpenJDK ou Oracle)
- **Maven 3.9+**
- **Docker & Docker Compose**
- **Git**

### Installation

```bash
# 1. Clone repository
git clone https://github.com/votre-username/shopstream-platform.git
cd shopstream-platform

# 2. Démarrer infrastructure (Kafka, PostgreSQL, Redis, etc.)
docker-compose up -d

# 3. Vérifier que tous services sont UP
docker-compose ps

# 4. Build tous les microservices
mvn clean install -DskipTests

# 5. Lancer tenant-service
cd tenant-service
mvn spring-boot:run

# 6. Tester API
curl http://localhost:8081/api/v1/tenants
```

### Configuration Debezium CDC (Outbox Pattern)

```bash
# Créer Debezium connector pour tenant-service
curl -X POST http://localhost:8083/connectors -H "Content-Type: application/json" -d '{
  "name": "tenant-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres-tenant",
    "database.port": "5432",
    "database.user": "postgres",
    "database.password": "postgres",
    "database.dbname": "tenant_db",
    "table.include.list": "public.outbox_events",
    "topic.prefix": "tenant",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.type": "event_type",
    "transforms.outbox.table.field.event.payload": "payload"
  }
}'
```

### Accès Interfaces

- **Kafka UI**: http://localhost:8080
- **Kibana** (Elasticsearch): http://localhost:5601
- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **Jaeger** (Tracing): http://localhost:16686

---

## 📚 Documentation Technique

### Pour Chaque Fonctionnalité

Le code contient des commentaires détaillés expliquant:

✅ **Quelle technologie utiliser** (JPA vs Criteria vs Native Query)  
✅ **POURQUOI cette approche** et pas une autre  
✅ **Quelles bonnes pratiques** appliquer  
✅ **Quels patterns avancés** implémenter  
✅ **Quels pièges éviter**  

### Exemples de Questions Répondues dans le Code

**Q: Pourquoi Criteria API au lieu de Query Methods?**
> R: Filtres dynamiques (0-10 critères possibles). Query Methods créeraient 2^10 = 1024 méthodes!

**Q: Pourquoi Outbox Pattern au lieu de Kafka direct?**
> R: Dual Write Problem. Sans Outbox: si Kafka fail après DB commit → event perdu. Outbox garantit atomicité.

**Q: Pourquoi Pessimistic Lock pour stock?**
> R: Éviter overselling (2 users achètent dernier item simultanément). Optimistic Lock insuffisant ici.

**Q: Pourquoi Materialized View au lieu de query à chaque fois?**
> R: Stats agrégées changent peu, calcul coûteux. MV refresh toutes les 5 min = 99% queries instantanées.

---

## 🧪 Tests

### Types de Tests Implémentés

```bash
# Tests unitaires (JUnit 5, Mockito)
mvn test

# Tests intégration (Testcontainers)
mvn verify -P integration-tests

# Tests contrats (Spring Cloud Contract)
mvn verify -P contract-tests

# Tests charge (Gatling)
mvn gatling:test
```

### Stratégie Testing

- **Unitaires**: Logique métier, validations
- **Intégration**: API endpoints, DB, Kafka (Testcontainers)
- **Contrats**: Consumer-driven contracts entre services
- **E2E**: Workflow complets (création commande → paiement → livraison)
- **Charge**: Performance sous 10k req/s

---

## 📊 Observabilité

### Metrics (Prometheus + Grafana)

- **Golden Signals**: Latency, Traffic, Errors, Saturation
- **Business Metrics**: Orders/sec, Revenue, Conversion rate
- **JVM**: Heap, GC, Threads
- **Kafka**: Lag, throughput, errors

### Tracing (Jaeger)

- Distributed tracing automatique (OpenTelemetry)
- Visualisation flux requêtes cross-services
- Identification bottlenecks

### Logs (ELK Stack)

- Structured logging (JSON)
- Correlation IDs
- Log aggregation centralisé

---

## 🎯 Compétences Maîtrisées

Après ce projet, vous maîtrisez:

✅ **Spring Boot 3** + Spring Cloud  
✅ **Kafka** (Producer, Consumer, Streams, Connect)  
✅ **PostgreSQL** avancé (window functions, CTEs, partitioning, JSONB, PostGIS)  
✅ **Elasticsearch** (full-text, aggregations, ML)  
✅ **Redis** (caching, rate limiting, pub/sub, sorted sets)  
✅ **Event Sourcing + CQRS**  
✅ **Saga Pattern** (Orchestration + Choreography)  
✅ **Outbox Pattern** (Debezium CDC)  
✅ **Patterns Avancés** (Factory, Strategy, Specification, Template Method)  
✅ **Observabilité** (OpenTelemetry, Prometheus, Grafana, Jaeger)  
✅ **Security** (OAuth2, JWT, mTLS)  
✅ **Testing** (Testcontainers, Contract, Load)  
✅ **DevOps** (Docker, Kubernetes, CI/CD)  

---

## 📄 License

MIT License - voir [LICENSE](LICENSE) pour détails

---

## 🤝 Contribution

Les contributions sont bienvenues! Voir [CONTRIBUTING.md](CONTRIBUTING.md)

---

## 📧 Contact

- **Email**: contact@shopstream.dev
- **GitHub**: github.com/shopstream/platform

---

**Made with ❤️ for learning advanced Java/Spring concepts**
