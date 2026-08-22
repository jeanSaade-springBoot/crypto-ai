package com.crypto.audit.repository;

import com.crypto.audit.domain.ProductionExitAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface ProductionExitAuditRepository extends JpaRepository<ProductionExitAudit, Long> {
    Optional<ProductionExitAudit> findTopByPaperPositionIdOrderByAuditedAtDesc(Long paperPositionId);

    // FIX-038: production-exit table reads immutable audit rows newest first.
    List<ProductionExitAudit> findAllByOrderByAuditedAtDesc(Pageable pageable);
}
