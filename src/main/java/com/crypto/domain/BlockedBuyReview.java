package com.crypto.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Operator research metadata only; deliberately not reachable from execution services. */
@Entity
@Table(name = "blocked_buy_review")
@Getter @Setter @NoArgsConstructor
public class BlockedBuyReview {
    @Id
    @Column(name = "signal_id")
    private Long signalId;
    @Column(nullable = false)
    private boolean suspicious;
    @Column(name = "marked_at", nullable = false)
    private Instant markedAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
