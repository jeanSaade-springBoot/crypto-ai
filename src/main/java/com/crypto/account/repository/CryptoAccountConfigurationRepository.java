package com.crypto.account.repository;

import com.crypto.account.domain.CryptoAccountConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CryptoAccountConfigurationRepository extends JpaRepository<CryptoAccountConfiguration, Long> {
    Optional<CryptoAccountConfiguration> findByUserIdAndExchangeCode(Long userId, String exchangeCode);
}
