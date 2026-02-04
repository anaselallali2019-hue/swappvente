# ShopStream Platform

Multi-module Spring Boot structure for the ShopStream multi-tenant ecommerce platform.

## Modules

- tenant-service
- product-catalog-service
- inventory-service
- order-service
- payment-service
- pricing-service
- recommendation-service
- notification-service
- delivery-tracking-service
- analytics-service
- gateway-service
- testing-guide

## Where the requirements are documented in code

Each module contains a `feature/*FeatureGuide.java` class that documents:
- exact technology/approach to use (JPA vs Criteria vs Native vs JOOQ, etc)
- why this approach (and not another)
- best practices
- advanced patterns
- pitfalls to avoid

These comments are the primary design guide for implementation.