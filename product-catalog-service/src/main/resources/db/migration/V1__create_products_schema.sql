-- ===================================================================
-- PRODUCT-CATALOG-SERVICE DATABASE SCHEMA
-- ===================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ===================================================================
-- TABLE: products
-- ===================================================================
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(500) NOT NULL,
    description TEXT,
    base_price NUMERIC(10, 2),
    currency VARCHAR(3) DEFAULT 'EUR',
    brand VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    rating_avg NUMERIC(3, 2) DEFAULT 0,
    rating_count INTEGER DEFAULT 0,
    view_count INTEGER DEFAULT 0,
    
    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,
    
    CONSTRAINT chk_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED', 'OUT_OF_STOCK'))
);

-- Indexes
CREATE INDEX idx_products_tenant ON products(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_products_status ON products(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_products_brand ON products(brand);
CREATE INDEX idx_products_rating ON products(rating_avg DESC);
CREATE INDEX idx_products_views ON products(view_count DESC);
CREATE INDEX idx_products_created ON products(created_at DESC);

-- Full-text search (si pas Elasticsearch)
CREATE INDEX idx_products_search ON products 
USING gin(to_tsvector('french', name || ' ' || COALESCE(description, '')));

-- ===================================================================
-- TABLE: product_variants
-- ===================================================================
CREATE TABLE product_variants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sku VARCHAR(100) NOT NULL UNIQUE,
    size VARCHAR(50),
    color VARCHAR(50),
    price NUMERIC(10, 2),
    currency VARCHAR(3),
    image_url VARCHAR(500),
    barcode VARCHAR(100),
    weight_grams INTEGER,
    
    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version INTEGER NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP
);

-- Indexes
CREATE INDEX idx_variants_product ON product_variants(product_id);
CREATE UNIQUE INDEX idx_variants_sku ON product_variants(sku);
CREATE INDEX idx_variants_size_color ON product_variants(size, color);

-- ===================================================================
-- TABLE: product_categories (ElementCollection)
-- ===================================================================
CREATE TABLE product_categories (
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    category_id UUID NOT NULL,
    PRIMARY KEY (product_id, category_id)
);

CREATE INDEX idx_product_categories_category ON product_categories(category_id);

-- ===================================================================
-- TABLE: product_tags (ElementCollection)
-- ===================================================================
CREATE TABLE product_tags (
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    tag VARCHAR(100) NOT NULL,
    PRIMARY KEY (product_id, tag)
);

CREATE INDEX idx_product_tags_tag ON product_tags(tag);

-- ===================================================================
-- TABLE: product_reviews (pour rating_avg calculation)
-- ===================================================================
CREATE TABLE product_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL,
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_reviews_product ON product_reviews(product_id);
CREATE INDEX idx_reviews_rating ON product_reviews(rating);

-- ===================================================================
-- MATERIALIZED VIEW: popular_products
-- Top 100 produits plus vendus (30 derniers jours)
-- ===================================================================
CREATE MATERIALIZED VIEW popular_products AS
SELECT 
    p.id as product_id,
    p.name,
    p.brand,
    p.base_price,
    COUNT(DISTINCT oi.order_id) as order_count,
    SUM(oi.quantity) as total_sold,
    SUM(oi.quantity * oi.unit_price) as total_revenue
FROM products p
LEFT JOIN order_items oi ON p.id = oi.product_id
WHERE p.deleted_at IS NULL
  AND p.status = 'ACTIVE'
  AND (oi.created_at IS NULL OR oi.created_at > NOW() - INTERVAL '30 days')
GROUP BY p.id, p.name, p.brand, p.base_price
ORDER BY total_sold DESC NULLS LAST
LIMIT 100;

CREATE UNIQUE INDEX idx_popular_products_id ON popular_products(product_id);
CREATE INDEX idx_popular_products_sold ON popular_products(total_sold DESC);

COMMENT ON MATERIALIZED VIEW popular_products IS 'Top 100 products by sales (refresh hourly)';

-- ===================================================================
-- FUNCTIONS & TRIGGERS
-- ===================================================================

-- Trigger: update rating_count when review added
CREATE OR REPLACE FUNCTION update_product_rating_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE products 
        SET rating_count = rating_count + 1
        WHERE id = NEW.product_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE products 
        SET rating_count = rating_count - 1
        WHERE id = OLD.product_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_product_rating_count
    AFTER INSERT OR DELETE ON product_reviews
    FOR EACH ROW
    EXECUTE FUNCTION update_product_rating_count();

-- Trigger: update updated_at
CREATE TRIGGER trigger_products_updated_at
    BEFORE UPDATE ON products
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_variants_updated_at
    BEFORE UPDATE ON product_variants
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ===================================================================
-- SEED DATA
-- ===================================================================
INSERT INTO products (id, tenant_id, name, description, base_price, currency, brand, status) VALUES
(
    '10000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001', -- demo tenant
    'T-Shirt Classic',
    'T-shirt en coton 100% biologique, coupe classique',
    19.99,
    'EUR',
    'Demo Brand',
    'ACTIVE'
);

INSERT INTO product_variants (id, product_id, sku, size, color, price, currency) VALUES
('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'TSHIRT-RED-S', 'S', 'Red', 19.99, 'EUR'),
('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'TSHIRT-RED-M', 'M', 'Red', 19.99, 'EUR'),
('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'TSHIRT-BLUE-S', 'S', 'Blue', 19.99, 'EUR');
