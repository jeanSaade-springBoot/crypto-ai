package com.crypto.repository;

import com.crypto.domain.SentimentSignal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SentimentSignalRepository extends JpaRepository<SentimentSignal, Long> {
    List<SentimentSignal> findBySymbolAndObservedAtAfterOrderByObservedAtDesc(String symbol, Instant after);
    List<SentimentSignal> findTop20BySymbolOrderByObservedAtDesc(String symbol);
    boolean existsBySymbolAndSourceAndObservedAtAndSummary(String symbol, String source, Instant observedAt, String summary);
}
