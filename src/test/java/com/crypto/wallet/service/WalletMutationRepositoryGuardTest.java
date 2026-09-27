package com.crypto.wallet.service;

import com.crypto.wallet.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class WalletMutationRepositoryGuardTest {
    @Test void productionRepositoryDecoratorRejectsSkippedGuardBeforeAnyWrite() {
        var raw=mock(WalletAssetRepository.class);
        var guarded=(WalletAssetRepository)new WalletMutationRepositoryGuard().postProcessAfterInitialization(raw,"walletAssetRepository");
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(ds);jdbc.execute("CREATE TABLE wallet_mutation_coordination(id INT PRIMARY KEY)");jdbc.update("INSERT INTO wallet_mutation_coordination VALUES(1)");
        var manager=new DataSourceTransactionManager(ds);var tx=new TransactionTemplate(manager);var guard=new WalletTransactionCoordination(jdbc,manager);
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(t->guarded.creditQuantity("USDT",BigDecimal.ONE)));
        verifyNoInteractions(raw);
        tx.executeWithoutResult(t->{guard.mutation();guarded.creditQuantity("USDT",BigDecimal.ONE);
            assertThrows(IllegalStateException.class,()->guard.symbol("ETHUSDT"));});
        verify(raw).creditQuantity("USDT",BigDecimal.ONE);
        assertThrows(IllegalStateException.class,()->guarded.debitQuantityIfSufficient("USDT",BigDecimal.ONE));
        assertFalse(WalletTransactionCoordination.mutationHeld());
    }
    @Test void everySharedWalletRankHasAnEnforcedRepositoryBoundary() {
        for(String repository:List.of("walletAssetRepository","walletSettingsRepository","walletDailyStatisticsRepository","walletTradeRepository","walletCashFlowRepository","walletSnapshotRepository"))
            for(String method:List.of("save","saveAll","saveAndFlush","deleteAll","flush","findCurrentForUpdate"))
                assertThrows(IllegalStateException.class,()->WalletMutationRepositoryGuard.check(repository,method),repository+"."+method);
        assertThrows(IllegalStateException.class,()->WalletMutationRepositoryGuard.check("walletDailyStatisticsRepository","findForUpdateByTradeDate"));
    }
    @Test void rawSqlMustNotBypassTheReviewedWalletRepositoryBoundary() throws Exception {
        var sql=java.util.regex.Pattern.compile("(?i)(?:UPDATE|INSERT\\s+INTO|DELETE\\s+FROM)\\s+wallet_(?:asset|daily_statistics|settings|trade|cash_flow|snapshot)\\b");
        try(var files=Files.walk(Path.of("src/main/java"))) {
            for(Path file:files.filter(f->f.toString().endsWith(".java")).toList())
                assertFalse(sql.matcher(Files.readString(file)).find(),"Unreviewed raw wallet writer: "+file);
        }
    }
}
