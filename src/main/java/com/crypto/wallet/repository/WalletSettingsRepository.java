package com.crypto.wallet.repository;
import com.crypto.wallet.domain.WalletSettings;
import org.springframework.data.jpa.repository.JpaRepository;
public interface WalletSettingsRepository extends JpaRepository<WalletSettings, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from WalletSettings s where s.id=1")
    java.util.Optional<WalletSettings> findCurrentForUpdate();
}
