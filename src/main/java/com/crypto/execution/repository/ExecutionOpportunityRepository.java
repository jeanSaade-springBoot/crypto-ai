package com.crypto.execution.repository;

import com.crypto.execution.domain.ExecutionOpportunity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExecutionOpportunityRepository extends JpaRepository<ExecutionOpportunity, Long> {
    Optional<ExecutionOpportunity> findTopBySymbolAndDirectionAndStatusInOrderByUpdatedAtDesc(
            String symbol, String direction, Collection<String> statuses
    );

    List<ExecutionOpportunity> findTop50ByOrderByUpdatedAtDesc();

    List<ExecutionOpportunity> findTop50ByStatusInOrderByUpdatedAtDesc(Collection<String> statuses);
}

