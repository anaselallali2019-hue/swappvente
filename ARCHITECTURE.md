# 🏗️ ShopStream Platform - Architecture Détaillée

## 📖 Table des Matières

1. [Vue d'ensemble](#vue-densemble)
2. [Décisions Techniques par Module](#décisions-techniques-par-module)
3. [Comparaisons Technologies](#comparaisons-technologies)
4. [Patterns Implémentés](#patterns-implémentés)
5. [Problèmes Résolus](#problèmes-résolus)
6. [Guidelines de Code](#guidelines-de-code)

---

## 🎯 Vue d'ensemble

ShopStream est une plateforme e-commerce multi-tenant démontrant **TOUS les concepts avancés** Java/Spring :

### Architecture Globale

```
┌─────────────────────────────────────────────────────────┐
│                      API GATEWAY                         │
│    (Spring Cloud Gateway, JWT, Rate Limiting)            │
└────────────────────┬────────────────────────────────────┘
                     │
        ┌────────────┴────────────┐
        │                         │
    ┌───▼────┐              ┌─────▼─────┐
    │ TENANT │              │  PRODUCT  │
    │SERVICE │              │  CATALOG  │
    └───┬────┘              └─────┬─────┘
        │                         │
        └─────────┬───────────────┘
                  │
            ┌─────▼──────┐
            │   KAFKA    │ (Event Bus)
            │  + STREAMS │
            └─────┬──────┘
                  │
    ┌─────────────┼─────────────────┐
    │             │                 │
┌───▼───┐    ┌────▼────┐    ┌──────▼──────┐
│INVENT │    │  ORDER  │    │  PAYMENT    │
│ ORY   │    │SERVICE  │    │  SERVICE    │
└───────┘    └─────────┘    └─────────────┘
```

### Principe Clé : Event-Driven Architecture

**Problème traditionnel** (Monolithe ou REST microservices):
- Couplage fort service → service
- Synchronous blocking calls
- Cascade failures
- Difficile à scale

**Solution ShopStream** (Event-Driven):
- Services découplés via events Kafka
- Asynchrone, non-blocking
- Resilient (retry, DLQ)
- Horizontal scaling facile

---

## 🔧 Décisions Techniques par Module

### MODULE 1: Tenant-Service

#### Problématique
> "Comment gérer des milliers de tenants (marques) avec recherche complexe et garantir que les events Kafka sont publiés de manière atomique avec les changements DB ?"

#### Décisions

**1. JPA vs Criteria vs Native Query - QUAND UTILISER QUOI**

```java
// UTILISER JPA (simple CRUD)
Tenant tenant = tenantRepository.findById(id);
tenant.setName("New Name");
tenantRepository.save(tenant);
```
✅ **QUAND**: CRUD basique, pas de query complexe  
✅ **POURQUOI**: Simple, type-safe, portable  
❌ **ÉVITER**: Queries dynamiques, agrégations, bulk ops

```java
// UTILISER CRITERIA API (filtres dynamiques)
Specification<Tenant> spec = Specification.where(null);
if (name != null) spec = spec.and(TenantSpecifications.nameLike(name));
if (status != null) spec = spec.and(TenantSpecifications.hasStatus(status));
Page<Tenant> results = repository.findAll(spec, pageable);
```
✅ **QUAND**: 0-N filtres possibles (recherche utilisateur)  
✅ **POURQUOI**: Type-safe, composable, évite 2^N méthodes  
❌ **ÉVITER**: Queries simples (overkill), agrégations complexes

```java
// UTILISER NATIVE QUERY (stats complexes)
@Query(value = """
    WITH monthly_stats AS (
        SELECT 
            DATE_TRUNC('month', created_at) as month,
            COUNT(*) as tenant_count,
            LAG(COUNT(*)) OVER (ORDER BY DATE_TRUNC('month', created_at)) as prev_count
        FROM tenants
        GROUP BY DATE_TRUNC('month', created_at)
    )
    SELECT * FROM monthly_stats WHERE month = CURRENT_DATE
    """, nativeQuery = true)
```
✅ **QUAND**: Window functions, CTEs, JSONB, PostgreSQL-specific  
✅ **POURQUOI**: Puissance SQL complète, performance  
❌ **ÉVITER**: Queries simples (pas portable), perd type-safety

**TABLEAU DÉCISIONNEL:**

| Use Case | JPA | Criteria | Native |
|----------|-----|----------|--------|
| CRUD tenant | ✅ | ❌ | ❌ |
| Recherche avec 5 filtres optionnels | ❌ | ✅ | ❌ |
| Stats dashboard avec LAG/LEAD | ❌ | ❌ | ✅ |
| Update 1 tenant | ✅ | ❌ | ❌ |
| Bulk update 10k rows | ❌ | ❌ | ✅ (ou JOOQ) |

**2. Outbox Pattern - RÉSOUT LE DUAL WRITE PROBLEM**

**Problème (sans Outbox):**
```java
@Transactional
void createTenant(Tenant tenant) {
    tenantRepository.save(tenant);           // 1. DB write
    kafkaTemplate.send("tenant.created", event); // 2. Kafka write
}
// ❌ Si Kafka fail: tenant créé mais pas d'event
// ❌ Si DB rollback: event envoyé mais tenant pas créé
// ❌ PAS D'ATOMICITÉ
```

**Solution (avec Outbox):**
```java
@Transactional
void createTenant(Tenant tenant) {
    tenantRepository.save(tenant);               // 1. DB write
    outboxRepository.save(new OutboxEvent(...)); // 2. DB write (MÊME transaction)
    // Debezium CDC lit outbox table et publie vers Kafka
}
// ✅ Atomicité garantie (DB transaction couvre les 2)
// ✅ Exactly-once delivery
```

**Implémentation:**
1. **Table outbox_events** : stocke events à publier
2. **Debezium CDC** : lit PostgreSQL WAL (Write-Ahead Log)
3. **Kafka Connect** : publie vers Kafka
4. **PostgreSQL logical replication** : `wal_level=logical`

**3. Optimistic vs Pessimistic Locking**

```java
// OPTIMISTIC LOCKING (@Version)
@Entity
public class Tenant {
    @Version
    private Integer version;
}
// SQL: UPDATE tenants SET name=?, version=version+1 WHERE id=? AND version=?
// Si version changed → OptimisticLockException
```
✅ **QUAND**: Concurrence légère, lecture >> écriture  
✅ **POURQUOI**: Pas de lock DB, meilleure performance  
❌ **ÉVITER**: Stock reservation, payment (risque overselling)

```java
// PESSIMISTIC LOCKING (SELECT FOR UPDATE)
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT i FROM Inventory i WHERE i.id = ?1")
Optional<Inventory> findByIdWithLock(UUID id);
// SQL: SELECT * FROM inventory WHERE id=? FOR UPDATE NOWAIT
```
✅ **QUAND**: Concurrence critique (stock, money)  
✅ **POURQUOI**: Garantie absolue (autre transaction bloquée)  
❌ **ÉVITER**: Lectures fréquentes (deadlock risk), long-running transactions

**TABLEAU DÉCISIONNEL:**

| Use Case | Optimistic | Pessimistic |
|----------|------------|-------------|
| Update tenant config | ✅ | ❌ |
| Update product price | ✅ | ❌ |
| Reserve stock (checkout) | ❌ | ✅ |
| Process payment | ❌ | ✅ |
| Update user profile | ✅ | ❌ |

---

### MODULE 2: Product-Catalog-Service

#### Problématique
> "Comment gérer millions de produits avec recherche full-text, typos, synonymes, et faire du bulk update de prix sur 100k produits sans bloquer la DB ?"

#### Décisions

**1. PostgreSQL vs Elasticsearch - QUAND UTILISER QUOI**

**PostgreSQL Full-Text:**
```sql
-- GIN index pour tsvector
CREATE INDEX idx_products_search ON products 
USING gin(to_tsvector('french', name || ' ' || description));

-- Query
SELECT * FROM products 
WHERE to_tsvector('french', name) @@ to_tsquery('french', 'tshirt');
```
✅ **QUAND**: Recherche simple, pas de typos/synonymes complexes  
✅ **POURQUOI**: Pas besoin infrastructure additionnelle  
❌ **LIMITES**: Pas de relevance scoring avancé, pas de facets, scaling limité

**Elasticsearch:**
```json
{
  "query": {
    "multi_match": {
      "query": "tshrt rouge",
      "fields": ["name^3", "description"],
      "fuzziness": "AUTO",
      "type": "best_fields"
    }
  },
  "aggs": {
    "categories": { "terms": { "field": "categories" } },
    "price_ranges": { "range": { "field": "price", "ranges": [...] } }
  }
}
```
✅ **QUAND**: Full-text avancé, typos, synonymes, facets, millions docs  
✅ **POURQUOI**: Relevance scoring, performance, horizontal scaling  
❌ **LIMITES**: Eventual consistency, pas de transactions, ops complexity

**NOTRE CHOIX:**
- **Elasticsearch** pour search (customer-facing)
- **PostgreSQL** pour data of record (source of truth)
- **Synchronisation** : Kafka consumer index dans ES

**2. JOOQ vs JPA pour Bulk Updates**

**Problème:**
> "Tenant veut appliquer -20% sur 100,000 produits d'une catégorie"

**JPA (❌ INEFFICACE):**
```java
List<Product> products = productRepository.findByCategoryId(categoryId);
products.forEach(p -> p.setPrice(p.getPrice().multiply(0.8)));
productRepository.saveAll(products);
// ❌ Charge 100k objets en mémoire
// ❌ 100k UPDATE queries individuels (même avec batch)
// ❌ Lent, bloque DB
```

**JOOQ (✅ OPTIMAL):**
```java
dsl.update(PRODUCTS)
   .set(PRODUCTS.PRICE, PRODUCTS.PRICE.multiply(0.8))
   .where(PRODUCTS.CATEGORY_ID.eq(categoryId))
   .execute();
// ✅ 1 seule requête UPDATE
// ✅ Exécution DB-side (pas de transfer réseau)
// ✅ Type-safe SQL
```

**Native Query alternative:**
```java
@Query("UPDATE Product p SET p.price = p.price * 0.8 WHERE p.categoryId = :categoryId")
@Modifying
void bulkUpdatePrices(@Param("categoryId") UUID categoryId);
```
✅ Simple mais perd type-safety

**NOTRE CHOIX:** JOOQ pour bulk ops car :
- Type-safe (compile-time errors)
- Code generation depuis DB schema
- API fluent (lisible)

**3. Elasticsearch Analyzers - MAGIE DU SEARCH**

**Problème:**
> "Client tape 'tshrt rouge' et doit trouver 'T-shirt vermeil'"

**Solution: Custom Analyzers**

```json
{
  "analyzer": {
    "french_analyzer": {
      "tokenizer": "standard",
      "filter": [
        "lowercase",           // TSHIRT → tshirt
        "asciifolding",        // é → e
        "french_elision",      // l'homme → homme
        "french_stop",         // le, la, de (remove)
        "french_stemmer",      // acheter, acheté → achet
        "french_synonym"       // rouge → [rouge, vermeil, écarlate]
      ]
    },
    "autocomplete_analyzer": {
      "tokenizer": "standard",
      "filter": [
        "lowercase",
        "edge_ngram"           // shirt → [sh, shi, shir, shirt]
      ]
    }
  }
}
```

**MultiField Mapping:**
```java
@MultiField(
    mainField = @Field(type = FieldType.Text, analyzer = "french_analyzer"),
    otherFields = {
        @InnerField(suffix = "keyword", type = FieldType.Keyword),      // exact match
        @InnerField(suffix = "autocomplete", type = FieldType.Text, analyzer = "autocomplete_analyzer")
    }
)
private String name;
```

**Queries:**
```json
// Full-text avec typos
{ "match": { "name": { "query": "tshrt", "fuzziness": "AUTO" } } }

// Exact match
{ "term": { "name.keyword": "T-Shirt Classic" } }

// Autocomplete
{ "match": { "name.autocomplete": "tsh" } }

// Boosting (nom plus important que description)
{ "multi_match": { 
    "query": "shirt", 
    "fields": ["name^3", "description"] 
}}
```

---

## 📊 Comparaisons Technologies

### Cache: Redis vs Caffeine

| Critère | Redis | Caffeine |
|---------|-------|----------|
| **Type** | Distribué (réseau) | Local (in-memory JVM) |
| **Performance** | ~1ms latency | ~0.001ms (1000x plus rapide) |
| **Partage** | Entre instances | Seule instance |
| **Persistance** | Oui (AOF, RDB) | Non |
| **Eviction** | LRU, LFU, TTL | LRU, LFU, TTL, Window TinyLFU |
| **Use cases** | Session, rate limiting, distributed cache | Config, reference data |

**NOTRE CHOIX:**
- **Redis** : Top products, user sessions, rate limiting
- **Caffeine** : Categories, tenant config (change rarement)

### Queue: Kafka vs RabbitMQ

| Critère | Kafka | RabbitMQ |
|---------|-------|----------|
| **Model** | Log-based, append-only | Traditional queue |
| **Throughput** | Millions msg/sec | ~100k msg/sec |
| **Retention** | Configurable (days/weeks) | Jusqu'à ack |
| **Consumers** | Multiple indépendants | Competing consumers |
| **Ordering** | Garantie par partition | Oui mais limité |
| **Use case** | Event streaming, analytics | Task queues, RPC |

**POURQUOI KAFKA pour ShopStream:**
✅ Event sourcing (garder historique)  
✅ Multiple consumers (product.created → inventory, elasticsearch, analytics)  
✅ Kafka Streams pour real-time analytics  
✅ Replay events (re-process depuis début)  

---

## 🎨 Patterns Implémentés

### 1. Outbox Pattern (Transactional Messaging)

**Problème:** Dual Write Problem

**Solution:**
```
┌──────────────────┐
│  APPLICATION     │
│  @Transactional  │
└────────┬─────────┘
         │
    ┌────▼────┬─────────┐
    │         │         │
┌───▼───┐ ┌──▼────┐    │
│TENANT │ │OUTBOX │    │ DB TRANSACTION
│TABLE  │ │TABLE  │    │
└───────┘ └───┬───┘    │
              │        │
         ┌────▼────────▼─┐
         │   DB COMMIT   │
         └────┬──────────┘
              │
         ┌────▼────┐
         │DEBEZIUM │
         │  CDC    │
         └────┬────┘
              │
         ┌────▼────┐
         │  KAFKA  │
         └─────────┘
```

### 2. CQRS (Command Query Responsibility Segregation)

**Problème:** Read/Write patterns très différents

**Solution:**
```
WRITE MODEL (Order-Service)
┌────────────────────────┐
│  Event Sourcing        │
│  - order.created       │
│  - item.added          │
│  - payment.completed   │
│  (Kafka compacted log) │
└────────┬───────────────┘
         │
    ┌────▼────┐
    │  KAFKA  │
    └────┬────┘
         │
         ▼
READ MODEL (PostgreSQL)
┌────────────────────────┐
│ Dénormalisé optimisé   │
│ - order_read_model     │
│ - items dans JSON      │
│ - customer data inline │
└────────────────────────┘
```

### 3. Saga Pattern (Distributed Transactions)

**Problème:** Transaction across microservices

**Solution: Saga Orchestration**
```
┌─────────────────┐
│ SAGA COORDINATOR│
└────────┬────────┘
         │
    ┌────┼────┬─────┬──────┐
    │    │    │     │      │
┌───▼┐ ┌─▼─┐ ┌▼──┐ ┌▼───┐ ┌▼────┐
│VAL-│ │RES│ │PAY│ │CONF│ │NOTIF│
│DATE│ │ERV│ │MNT│ │ IRM│ │ Y   │
└────┘ └───┘ └───┘ └────┘ └─────┘
  │      │     │     │       │
  OK     OK    FAIL  ─       ─
               │
          ┌────▼──────────┐
          │ COMPENSATION  │
          │ - Release inv │
          │ (rollback)    │
          └───────────────┘
```

### 4. Specification Pattern (Dynamic Queries)

**Problème:** Combinaisons infinies de filtres

**Solution:**
```java
// Composable predicates
Specification<Tenant> spec = Specification.where(null);
if (name != null) spec = spec.and(nameLike(name));
if (status != null) spec = spec.and(hasStatus(status));
if (tier != null) spec = spec.and(hasTier(tier));
// SQL généré dynamiquement avec seulement les WHERE nécessaires
```

---

## ⚠️ Problèmes Résolus & Pièges Évités

### 1. N+1 Query Problem

**Problème:**
```java
List<Tenant> tenants = tenantRepository.findAll(); // 1 query
for (Tenant t : tenants) {
    t.getSubscriptions().size(); // N queries ❌
}
// Total: 1 + N queries (si 100 tenants = 101 queries!)
```

**Solution:**
```java
@EntityGraph(attributePaths = {"subscriptions", "users"})
List<Tenant> findAll();
// 1 seule query avec LEFT JOIN
```

### 2. LazyInitializationException

**Problème:**
```java
@Service
class TenantService {
    @Transactional(readOnly = true)
    Tenant getTenant(UUID id) {
        return repository.findById(id).orElseThrow();
    }
}

@Controller
class TenantController {
    TenantResponse get(UUID id) {
        Tenant tenant = service.getTenant(id);
        // ❌ Transaction fermée ici
        tenant.getSubscriptions().size(); // LazyInitializationException!
    }
}
```

**Solutions:**
1. **@EntityGraph** (eager fetch sélectif)
2. **open-in-view: false** + fetch dans transaction
3. **DTO projection** (évite lazy loading)

### 3. Optimistic Lock Conflicts

**Problème:**
```java
// User A
Tenant t = repository.findById(id); // version=1
t.setName("Name A");
repository.save(t); // version=2

// User B (simultané)
Tenant t = repository.findById(id); // version=1
t.setName("Name B");
repository.save(t); // ❌ OptimisticLockException (version mismatch)
```

**Solution:**
```java
@ControllerAdvice
class ErrorHandler {
    @ExceptionHandler(OptimisticLockException.class)
    ResponseEntity<ErrorResponse> handle(OptimisticLockException ex) {
        return ResponseEntity.status(409).body(
            new ErrorResponse("Resource modified by another user. Please refresh.")
        );
    }
}
```

Frontend doit refresh + retry.

### 4. Kafka Duplicate Messages

**Problème:**
- Network timeout → producer retry
- Message envoyé 2 fois

**Solution:**
```yaml
# Producer idempotence
enable.idempotence: true  # Active producer idempotence
acks: all                  # Attend tous replicas
# Kafka garantit exactly-once
```

```java
// Consumer idempotence
@KafkaListener
void handle(OrderEvent event) {
    if (processedEvents.exists(event.eventId)) {
        return; // Skip duplicate
    }
    // Process...
    processedEvents.save(event.eventId);
}
```

---

## 📝 Guidelines de Code

### Nommage

```java
// ✅ BON
class TenantService { }
interface TenantRepository { }
class TenantCreatedEvent { }

// ❌ MAUVAIS
class TenantServiceImpl { } // Éviter "Impl" suffix
interface ITenantRepository { } // Pas de "I" prefix Java
class TenantCreated { } // Event doit finir par "Event"
```

### Layering

```
interfaces/rest/        → Controllers, DTOs, Exception Handlers
├── TenantController
├── dto/
│   ├── CreateTenantRequest
│   └── TenantResponse
└── GlobalExceptionHandler

application/            → Services, Use Cases
├── service/
│   └── TenantService
└── mapper/
    └── TenantMapper

domain/                 → Entities, Value Objects, Domain Events
├── model/
│   ├── Tenant
│   ├── TenantTier
│   └── TenantStatus
├── repository/
│   ├── TenantRepository
│   └── TenantSpecifications
└── event/
    └── TenantCreatedEvent

infrastructure/         → Technical implementations
├── config/
│   └── KafkaConfig
├── persistence/
│   └── JooqProductRepository
└── scheduler/
    └── TenantCleanupScheduler
```

### Transactions

```java
// ✅ BON: @Transactional sur service layer
@Service
class TenantService {
    @Transactional
    void createTenant(CreateRequest req) { }
}

// ❌ MAUVAIS: @Transactional sur controller
@RestController
class TenantController {
    @Transactional // ❌ Transaction trop longue (HTTP → DB → HTTP)
    ResponseEntity<> create() { }
}
```

### Exception Handling

```java
// ✅ BON: Business exception avec message clair
if (repository.existsBySubdomain(subdomain)) {
    throw new IllegalArgumentException(
        "Subdomain '" + subdomain + "' already exists"
    );
}

// ❌ MAUVAIS: Exception technique propagée
try {
    repository.save(tenant);
} catch (DataIntegrityViolationException ex) {
    // ❌ Client reçoit stacktrace SQL
}
```

---

**PROCHAINES ÉTAPES:**
- Inventory-Service (Pessimistic locking, Kafka Streams)
- Order-Service (Event Sourcing, CQRS, Saga)
- Payment-Service (Multi-gateway, webhooks)
- API Gateway (JWT, rate limiting, circuit breaker)
- Tests (Testcontainers, contracts, load)
