package com.crypto.wallet.service;

import com.crypto.client.binance.BinanceMarketDataClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BinanceMinimumExecutionPolicyTest {

    @Test
    void belowBinanceMinimumSkipsHarvest() {
        BinanceMarketDataClient client = mock(BinanceMarketDataClient.class);
        when(client.getMinimumExecutableNotional("TESTUSDT")).thenReturn(new BigDecimal("5.00"));
        BinanceMinimumExecutionPolicy policy = new BinanceMinimumExecutionPolicy(client);

        BinanceMinimumExecutionPolicy.Evaluation result =
                policy.evaluate("TESTUSDT", new BigDecimal("2"), new BigDecimal("2.49"));

        assertThat(result.executable()).isFalse();
        assertThat(result.code()).isEqualTo("BELOW_BINANCE_MINIMUM");
        assertThat(result.requestedNotional()).isEqualByComparingTo("4.98");
        assertThat(result.minimumNotional()).isEqualByComparingTo("5.00");
    }

    @Test
    void amountEqualToBinanceMinimumIsExecutable() {
        BinanceMarketDataClient client = mock(BinanceMarketDataClient.class);
        when(client.getMinimumExecutableNotional("TESTUSDT")).thenReturn(new BigDecimal("5.00"));
        BinanceMinimumExecutionPolicy policy = new BinanceMinimumExecutionPolicy(client);

        BinanceMinimumExecutionPolicy.Evaluation result =
                policy.evaluate("TESTUSDT", new BigDecimal("2"), new BigDecimal("2.50"));

        assertThat(result.executable()).isTrue();
        assertThat(result.code()).isEqualTo("EXECUTABLE");
    }

    @Test
    void unavailableBinanceMinimumFailsSafeAndSkipsHarvest() {
        BinanceMarketDataClient client = mock(BinanceMarketDataClient.class);
        when(client.getMinimumExecutableNotional("TESTUSDT"))
                .thenThrow(new IllegalStateException("exchangeInfo unavailable"));
        BinanceMinimumExecutionPolicy policy = new BinanceMinimumExecutionPolicy(client);

        BinanceMinimumExecutionPolicy.Evaluation result =
                policy.evaluate("TESTUSDT", new BigDecimal("10"), BigDecimal.ONE);

        assertThat(result.executable()).isFalse();
        assertThat(result.code()).isEqualTo("BINANCE_MINIMUM_UNAVAILABLE");
    }
}
