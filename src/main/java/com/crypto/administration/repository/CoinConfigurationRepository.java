package com.crypto.administration.repository;

import com.crypto.administration.domain.CoinConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CoinConfigurationRepository extends JpaRepository<CoinConfiguration, Long> {
    Optional<CoinConfiguration> findBySymbol(String symbol);
    List<CoinConfiguration> findAllByOrderBySystemDefaultDescSymbolAsc();
    List<CoinConfiguration> findByEnabledTrueOrderBySymbolAsc();
}
