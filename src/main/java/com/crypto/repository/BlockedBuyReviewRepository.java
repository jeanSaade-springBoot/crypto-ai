package com.crypto.repository;

import com.crypto.domain.BlockedBuyReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BlockedBuyReviewRepository extends JpaRepository<BlockedBuyReview, Long> {
    List<BlockedBuyReview> findBySignalIdIn(Collection<Long> signalIds);
    List<BlockedBuyReview> findBySuspiciousTrueOrderByMarkedAtDesc();
}
