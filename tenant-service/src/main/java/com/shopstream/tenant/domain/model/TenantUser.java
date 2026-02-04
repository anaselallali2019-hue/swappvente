package com.shopstream.tenant.domain.model;

import com.shopstream.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * User appartenant à un tenant (TENANT_ADMIN ou SELLER).
 * 
 * NOTE: User authentication est géré par un service séparé (auth-service).
 * Ici on stocke seulement la relation tenant-user et le rôle.
 */
@Entity
@Table(
    name = "tenant_users",
    indexes = {
        @Index(name = "idx_tenant_users_email", columnList = "email"),
        @Index(name = "idx_tenant_users_tenant", columnList = "tenant_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_email", columnNames = "email")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantUser extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @NotBlank
    @Email
    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private TenantUserRole role;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
}
