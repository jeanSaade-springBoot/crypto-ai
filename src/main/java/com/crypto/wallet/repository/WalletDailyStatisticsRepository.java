package com.crypto.wallet.repository;

import com.crypto.wallet.domain.WalletDailyStatistics;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface WalletDailyStatisticsRepository extends JpaRepository<WalletDailyStatistics, Long> {

    Optional<WalletDailyStatistics> findByTradeDate(LocalDate tradeDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from WalletDailyStatistics d where d.tradeDate = :tradeDate")
    Optional<WalletDailyStatistics> findForUpdateByTradeDate(@Param("tradeDate") LocalDate tradeDate);
}
