package com.shopstream.tenant.domain.model;

import com.shopstream.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Subscription d'un tenant à un plan.
 * 
 * POURQUOI table séparée:
 * - Historique: un tenant peut avoir plusieurs subscriptions dans le temps
 * - Query: "subscriptions actives", "revenue mensuel", "churn rate"
 * - Analytics: cohort analysis, MRR (Monthly Recurring Revenue)
 */
@Entity
@Table(
    name = "tenant_subscriptions",
    indexes = {
        @Index(name = "idx_subscriptions_tenant", columnList = "tenant_id"),
        @Index(name = "idx_subscriptions_dates", columnList = "start_date, end_date")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantSubscription extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "plan_name", length = 100)
    private String planName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /**
     * Revenue mensuel pour ce tenant.
     * 
     * POURQUOI stocker:
     * - Analytics rapides (MRR, ARR)
     * - Éviter recalcul permanent
     * - Snapshot historique (prix peut changer)
     */
    @Column(name = "monthly_revenue", precision = 10, scale = 2)
    private BigDecimal monthlyRevenue;

    @Column(name = "max_products")
    private Integer maxProducts;

    @Column(name = "max_orders_per_month")
    private Integer maxOrdersPerMonth;

    /**
     * Check si subscription est actuellement active.
     */
    public boolean isActive() {
        LocalDate now = LocalDate.now();
        return !now.isBefore(startDate) && 
               (endDate == null || !now.isAfter(endDate));
    }
}
