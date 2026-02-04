package com.shopstream.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base Entity avec audit fields automatiques.
 * 
 * POURQUOI cette approche:
 * - Évite duplication des champs created_at, updated_at, etc. dans chaque entité
 * - Utilise JPA @EntityListeners pour population automatique (pas de code manuel)
 * - UUID pour PK: permet sharding futur, pas de collision multi-DB
 * - @Version pour Optimistic Locking: évite conflits concurrents sans lock DB
 * 
 * PATTERN: Template Method (champs communs dans classe mère)
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    /**
     * Version pour Optimistic Locking.
     * 
     * POURQUOI Optimistic Locking:
     * - Meilleure performance que Pessimistic (pas de lock DB)
     * - Idéal pour opérations lecture > écriture
     * - Évite blocages (deadlocks)
     * 
     * QUAND L'UTILISER:
     * ✅ Mise à jour configuration tenant (rare)
     * ✅ Mise à jour produit (pas critique)
     * 
     * QUAND NE PAS L'UTILISER:
     * ❌ Réservation stock (utiliser Pessimistic + SELECT FOR UPDATE)
     * ❌ Paiement (risque de double charge)
     */
    @Version
    @Column(name = "version")
    private Integer version;

    /**
     * Soft delete timestamp.
     * 
     * POURQUOI Soft Delete:
     * - RGPD: garder données 30 jours avant suppression définitive
     * - Audit: traçabilité complète
     * - Recovery: annuler suppression accidentelle
     * 
     * IMPLÉMENTATION:
     * - Utiliser @SQLDelete de Hibernate pour override DELETE
     * - Utiliser @Where pour filtrer automatiquement
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public void restore() {
        this.deletedAt = null;
    }
}
