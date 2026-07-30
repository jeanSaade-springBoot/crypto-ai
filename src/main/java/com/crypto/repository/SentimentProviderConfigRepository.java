package com.crypto.repository;

import com.crypto.domain.SentimentProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SentimentProviderConfigRepository extends JpaRepository<SentimentProviderConfig, Long> {
    Optional<SentimentProviderConfig> findByProviderCodeIgnoreCase(String providerCode);
    List<SentimentProviderConfig> findAllByOrderByDisplayNameAsc();
}
