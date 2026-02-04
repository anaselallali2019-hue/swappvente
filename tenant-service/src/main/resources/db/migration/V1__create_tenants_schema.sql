-- ===================================================================
-- SHOPSTREAM TENANT-SERVICE DATABASE SCHEMA
-- ===================================================================
-- Version: 1.0
-- Description: Tables pour gestion multi-tenant
-- Date: 2024
--
-- DÉCISIONS TECHNIQUES:
-- 1. UUID pour PK (sharding-ready, pas de guessing IDs)
-- 2. Indexes sur colonnes fréquemment queryées
-- 3. Partial indexes (WHERE clause) pour optimisation
-- 4. JSONB pour settings flexibles
-- 5. Soft delete (deleted_at)
-- 6. Audit fields (created_at, updated_at, version)
-- ===================================================================

-- Enable extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ===================================================================
-- TABLE: tenants
-- Aggregate Root pour multi-tenancy
-- ===================================================================
CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    subdomain VARCHAR(100) NOT NULL UNIQUE,
    custom_domain VARCHAR(255) UNIQUE,
    tier VARCHAR(50) NOT NULL DEFAULT 'FREE',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    settings JSONB,
    
    -- Audit fields (managed by JPA Auditing)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    
    -- Soft delete (RGPD compliance)
    deleted_at TIMESTAMP,
    
    -- Optimistic locking
    version INTEGER NOT NULL DEFAULT 0,
    
    -- Constraints
    CONSTRAINT chk_tier CHECK (tier IN ('FREE', 'PRO', 'ENTERPRISE')),
    CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED', 'TRIAL_EXPIRED', 'PENDING_SETUP'))
);

-- Indexes (POURQUOI ces index spécifiques)
-- 1. Recherche par status (fréquent dans dashboard)
CREATE INDEX idx_tenants_status ON tenants(status) WHERE deleted_at IS NULL;

-- 2. Recherche par tier (stats, filtres)
CREATE INDEX idx_tenants_tier ON tenants(tier) WHERE deleted_at IS NULL;

-- 3. Recherche par date création (tri, filtres temporels)
CREATE INDEX idx_tenants_created_at ON tenants(created_at DESC);

-- 4. Soft delete cleanup (scheduled task recherche deleted_at < 30 jours)
CREATE INDEX idx_tenants_deleted ON tenants(deleted_at) WHERE deleted_at IS NOT NULL;

-- 5. GIN index pour recherche dans JSONB settings
CREATE INDEX idx_tenants_settings ON tenants USING gin(settings);

-- 6. Composite index pour queries complexes (tier + status)
CREATE INDEX idx_tenants_tier_status ON tenants(tier, status) WHERE deleted_at IS NULL;

-- Comments pour documentation
COMMENT ON TABLE tenants IS 'Aggregate Root: Représente une marque/entreprise utilisant la plateforme';
COMMENT ON COLUMN tenants.subdomain IS 'Subdomain unique: {subdomain}.shopstream.com';
COMMENT ON COLUMN tenants.custom_domain IS 'Custom domain optionnel: www.mybrand.com';
COMMENT ON COLUMN tenants.settings IS 'Configuration flexible en JSONB (theme, features, etc.)';
COMMENT ON COLUMN tenants.version IS 'Optimistic locking: auto-incrémenté par JPA à chaque update';

-- ===================================================================
-- TABLE: tenant_subscriptions
-- Historique des abonnements (plusieurs lignes par tenant possible)
-- ===================================================================
CREATE TABLE tenant_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    plan_name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE, -- NULL = unlimited (Free plan)
    monthly_revenue NUMERIC(10, 2) NOT NULL DEFAULT 0,
    max_products INTEGER NOT NULL,
    max_orders_per_month INTEGER NOT NULL,
    
    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version INTEGER NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP
);

-- Indexes
CREATE INDEX idx_subscriptions_tenant ON tenant_subscriptions(tenant_id);
CREATE INDEX idx_subscriptions_dates ON tenant_subscriptions(start_date, end_date);

-- Index pour query "subscriptions actives"
CREATE INDEX idx_subscriptions_active ON tenant_subscriptions(tenant_id, end_date)
WHERE deleted_at IS NULL AND (end_date IS NULL OR end_date > CURRENT_DATE);

COMMENT ON TABLE tenant_subscriptions IS 'Historique des abonnements pour analytics (MRR, churn, etc.)';

-- ===================================================================
-- TABLE: tenant_users
-- Users appartenant à un tenant (TENANT_ADMIN, SELLER)
-- ===================================================================
CREATE TABLE tenant_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL UNIQUE,
    role VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    
    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version INTEGER NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP,
    
    CONSTRAINT chk_role CHECK (role IN ('TENANT_ADMIN', 'SELLER', 'SUPPORT'))
);

-- Indexes
CREATE INDEX idx_tenant_users_tenant ON tenant_users(tenant_id);
CREATE INDEX idx_tenant_users_email ON tenant_users(email);

COMMENT ON TABLE tenant_users IS 'Users du tenant (pas auth, juste relation tenant-user)';

-- ===================================================================
-- TABLE: outbox_events
-- Transactional Outbox Pattern pour garantie atomicité DB + Kafka
-- ===================================================================
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    
    -- Index pour Debezium CDC
    CONSTRAINT chk_aggregate_type CHECK (aggregate_type IN ('Tenant', 'Subscription', 'User'))
);

-- CRITICAL INDEX: Debezium lit events non traités
-- Partial index = seulement rows WHERE processed_at IS NULL
-- Performance: évite scan full table
CREATE INDEX idx_outbox_not_processed ON outbox_events(created_at)
WHERE processed_at IS NULL;

-- Index pour cleanup (supprimer events traités > 7 jours)
CREATE INDEX idx_outbox_cleanup ON outbox_events(processed_at)
WHERE processed_at IS NOT NULL;

COMMENT ON TABLE outbox_events IS 'Outbox Pattern: events publiés vers Kafka via Debezium CDC';
COMMENT ON COLUMN outbox_events.aggregate_id IS 'ID de l''entité concernée (tenant_id, subscription_id, etc.)';
COMMENT ON COLUMN outbox_events.payload IS 'Payload JSON de l''event (sérialisation DomainEvent)';
COMMENT ON COLUMN outbox_events.processed_at IS 'Timestamp quand event a été publié vers Kafka (optionnel)';

-- ===================================================================
-- AUDIT TABLES (Hibernate Envers)
-- Tables générées automatiquement par Envers, mais on crée les index manuellement
-- ===================================================================

-- Envers crée automatiquement:
-- - tenants_audit
-- - tenant_subscriptions_audit
-- - revinfo (table des révisions)

-- On crée index sur tenant_audit pour queries historiques
CREATE INDEX IF NOT EXISTS idx_tenants_audit_id_rev 
ON tenants_audit(id, revision_id);

-- ===================================================================
-- MATERIALIZED VIEW: tenant_stats_mv
-- Pré-calcul des statistiques pour dashboard admin (F1.3)
-- ===================================================================

CREATE MATERIALIZED VIEW tenant_stats_mv AS
WITH monthly_stats AS (
    SELECT 
        DATE_TRUNC('month', t.created_at) as month,
        COUNT(*) as tenant_count,
        COUNT(*) FILTER (WHERE t.tier = 'FREE') as free_count,
        COUNT(*) FILTER (WHERE t.tier = 'PRO') as pro_count,
        COUNT(*) FILTER (WHERE t.tier = 'ENTERPRISE') as enterprise_count,
        SUM(s.monthly_revenue) as total_revenue
    FROM tenants t
    LEFT JOIN tenant_subscriptions s ON t.id = s.tenant_id 
        AND (s.end_date IS NULL OR s.end_date > CURRENT_DATE)
    WHERE t.deleted_at IS NULL
    GROUP BY DATE_TRUNC('month', t.created_at)
)
SELECT 
    month,
    tenant_count,
    free_count,
    pro_count,
    enterprise_count,
    total_revenue,
    LAG(total_revenue) OVER (ORDER BY month) as prev_month_revenue,
    CASE 
        WHEN LAG(total_revenue) OVER (ORDER BY month) > 0
        THEN ((total_revenue - LAG(total_revenue) OVER (ORDER BY month)) 
              / LAG(total_revenue) OVER (ORDER BY month) * 100)
        ELSE NULL
    END as growth_percentage
FROM monthly_stats
ORDER BY month DESC
LIMIT 12; -- 12 derniers mois

-- Index sur materialized view (REQUIRED pour REFRESH CONCURRENTLY)
CREATE UNIQUE INDEX idx_tenant_stats_mv_month ON tenant_stats_mv(month);

COMMENT ON MATERIALIZED VIEW tenant_stats_mv IS 'Stats pré-calculées pour dashboard (refresh toutes les 5 min)';

-- ===================================================================
-- FUNCTIONS & TRIGGERS
-- ===================================================================

-- Function: Update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger: Auto-update updated_at
CREATE TRIGGER trigger_tenants_updated_at
    BEFORE UPDATE ON tenants
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_subscriptions_updated_at
    BEFORE UPDATE ON tenant_subscriptions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_users_updated_at
    BEFORE UPDATE ON tenant_users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ===================================================================
-- INITIAL DATA (pour dev/test)
-- ===================================================================

-- Seed: Tenant FREE pour tests
INSERT INTO tenants (id, name, subdomain, tier, status, settings) VALUES
(
    '00000000-0000-0000-0000-000000000001',
    'Demo Store',
    'demo',
    'FREE',
    'ACTIVE',
    '{"theme": {"primaryColor": "#007bff"}, "features": {"recommendations": true}}'
);

-- Seed: Subscription pour demo tenant
INSERT INTO tenant_subscriptions (tenant_id, plan_name, start_date, end_date, monthly_revenue, max_products, max_orders_per_month) VALUES
(
    '00000000-0000-0000-0000-000000000001',
    'Free Plan',
    CURRENT_DATE,
    NULL,
    0,
    100,
    1000
);
