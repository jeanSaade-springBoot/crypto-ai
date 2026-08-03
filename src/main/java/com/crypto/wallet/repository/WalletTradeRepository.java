package com.crypto.wallet.repository;
import com.crypto.wallet.domain.WalletTrade;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
public interface WalletTradeRepository extends JpaRepository<WalletTrade, Long> {
    List<WalletTrade> findTop100ByOrderByExecutedAtDesc();
    @Query("select coalesce(sum(t.realizedPnlUsdt), 0) from WalletTrade t where t.status='EXECUTED'")
    BigDecimal totalRealizedPnl();
    boolean existsByExecutionKey(String executionKey);
}
