package com.crypto.wallet.repository;
import com.crypto.wallet.domain.WalletCashFlow;
import org.springframework.data.jpa.repository.*;
import java.util.List;
public interface WalletCashFlowRepository extends JpaRepository<WalletCashFlow, Long> {
    List<WalletCashFlow> findTop100ByOrderByOccurredAtDesc();
}
