package com.crypto.local;
import org.junit.jupiter.api.Test;
import com.crypto.service.*;
import com.crypto.administration.service.CoinConfigurationService;
import com.crypto.client.config.binance.BinanceMarketDataProperties;
import com.crypto.config.TradingProperties;
import com.crypto.indicator.service.TechnicalIndicatorService;
import com.crypto.repository.TradeSignalRepository;
import java.util.*;
import static org.mockito.Mockito.*;

/** Backfill/restart/new-symbol bootstrap is not an automatic signal or execution entry point in OFF. */
class LocalBootstrapTest {
    @Test void importsOnBothInitialAndRepeatedBootstrapNeverAnalyseOrTrade(){
        var props=new BinanceMarketDataProperties();props.setIntervals(List.of("1m"));
        var trading=mock(TradingProperties.class);when(trading.intervals()).thenReturn(List.of("1m"));
        var coins=mock(CoinConfigurationService.class);var data=mock(MarketDataService.class);
        when(data.importCandles("BTCUSDT","1m",1000)).thenReturn(5,0);
        var indicators=mock(TechnicalIndicatorService.class);var analysis=mock(AnalysisService.class);
        var paper=mock(PaperTradingService.class);var signals=mock(TradeSignalRepository.class);
        var bootstrap=new MarketDataBootstrapService(props,trading,coins,data,indicators,analysis,paper,signals);
        bootstrap.bootstrapSymbol("BTCUSDT");bootstrap.bootstrapSymbol("BTCUSDT");
        verify(data,times(2)).importCandles("BTCUSDT","1m",1000);
        verifyNoInteractions(indicators,analysis,paper,signals);
    }
}
