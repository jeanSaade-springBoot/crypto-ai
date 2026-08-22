package com.crypto.wallet.repository;

import com.crypto.wallet.domain.WalletAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.*;

public interface WalletAssetRepository extends JpaRepository<WalletAsset, Long> {
    Optional<WalletAsset> findBySymbol(String symbol);
    List<WalletAsset> findAllByOrderBySymbolAsc();

    /**
     * FIX-037: Apply wallet cash credits inside MySQL instead of reading a balance into
     * Java and writing a replacement value. This makes concurrent SELL/BUY executions
     * additive and prevents a stale writer from erasing a just-completed credit.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update WalletAsset a set a.quantity = a.quantity + :amount where a.symbol = :symbol")
    int creditQuantity(@Param("symbol") String symbol, @Param("amount") BigDecimal amount);

    /**
     * FIX-037: Debit atomically and enforce sufficient funds in the same SQL statement.
     * The affected-row count is the authoritative success result.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update WalletAsset a set a.quantity = a.quantity - :amount "
            + "where a.symbol = :symbol and a.quantity >= :amount")
    int debitQuantityIfSufficient(@Param("symbol") String symbol, @Param("amount") BigDecimal amount);
}
