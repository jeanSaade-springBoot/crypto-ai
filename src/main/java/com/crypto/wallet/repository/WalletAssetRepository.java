package com.crypto.wallet.repository;
import com.crypto.wallet.domain.WalletAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface WalletAssetRepository extends JpaRepository<WalletAsset, Long> {
    Optional<WalletAsset> findBySymbol(String symbol);
    List<WalletAsset> findAllByOrderBySymbolAsc();
}
