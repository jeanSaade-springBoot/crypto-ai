package com.crypto.audit.repository;

import com.crypto.audit.domain.ProductionExitAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductionExitAuditRepository extends JpaRepository<ProductionExitAudit, Long> {
    Optional<ProductionExitAudit> findTopByPaperPositionIdOrderByAuditedAtDesc(Long paperPositionId);
}
