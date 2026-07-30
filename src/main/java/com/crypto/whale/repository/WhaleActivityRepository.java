package com.crypto.whale.repository;

import com.crypto.whale.domain.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WhaleActivityRepository extends JpaRepository<WhaleActivity, Long> {
    boolean existsByBlockchainAndTransactionHashAndWalletAddressAndEvaluationHorizon(
            String blockchain, String transactionHash, String walletAddress, WhaleEvaluationHorizon horizon);

    List<WhaleActivity> findByEvaluationResultAndEvaluationDueAtLessThanEqualOrderByEvaluationDueAtAsc(
            WhaleEvaluationResult result, Instant dueAt, Pageable pageable);

    List<WhaleActivity> findByWalletAddressAndSymbolAndEvaluationHorizonAndEvaluationResultIn(
            String walletAddress, String symbol, WhaleEvaluationHorizon horizon, List<WhaleEvaluationResult> results);

    Optional<WhaleActivity> findFirstByWalletAddressAndSymbolAndEvaluationHorizonAndEvaluationResultInOrderByEvaluatedAtDesc(
            String walletAddress, String symbol, WhaleEvaluationHorizon horizon, List<WhaleEvaluationResult> results);

    List<WhaleActivity> findBySymbolAndEvaluationHorizonAndObservedAtAfterOrderByObservedAtDesc(
            String symbol, WhaleEvaluationHorizon horizon, Instant after);
}
