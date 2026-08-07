package com.crypto.wallet.repository;

import com.crypto.wallet.domain.WalletCashFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface WalletCashFlowRepository extends JpaRepository<WalletCashFlow, Long> {
    List<WalletCashFlow> findTop100ByOrderByOccurredAtDesc();

    @Query("select coalesce(sum(case when f.flowType = 'DEPOSIT' then f.amountUsdt else -f.amountUsdt end), 0) from WalletCashFlow f")
    BigDecimal netInvestedUsdt();
}
