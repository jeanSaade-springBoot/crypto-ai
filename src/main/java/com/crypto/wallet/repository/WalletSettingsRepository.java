package com.crypto.wallet.repository;
import com.crypto.wallet.domain.WalletSettings;
import org.springframework.data.jpa.repository.JpaRepository;
public interface WalletSettingsRepository extends JpaRepository<WalletSettings, Long> {}
