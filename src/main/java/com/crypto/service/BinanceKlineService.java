package com.crypto.service;

import com.crypto.indicator.event.CandleClosedEvent;
import com.crypto.repository.CandleRepository;
import com.crypto.position.service.LivePositionProtectionService;
import com.crypto.debug.monitor.service.PriceMoveMonitorService;
import com.crypto.market.service.MarketPriceEventService;
import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import com.crypto.infrastructure.transaction.KlineTransactionCoordinator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

@Service
public class BinanceKlineService {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(BinanceKlineService.class);
    @org.springframework.beans.factory.annotation.Value("#{'${shared-market.mode:OFF}' == 'LIVE'}")
    private boolean sharedMarketEnabled;

    private final KlineTransactionCoordinator transactions;
    private final CandleRepository candleRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LivePositionProtectionService livePositionProtectionService;
    private final PriceMoveMonitorService priceMoveMonitorService;
    private final MarketPriceEventService marketPriceEventService;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.crypto.local.LocalDrainService drain;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.crypto.local.LocalReconciliationAuditRepository reconciliationAudit;

    public BinanceKlineService(
            CandleRepository candleRepository,
            ApplicationEventPublisher eventPublisher,
            LivePositionProtectionService livePositionProtectionService,
            PriceMoveMonitorService priceMoveMonitorService,
            MarketPriceEventService marketPriceEventService,
            KlineTransactionCoordinator transactions
    ) {
        this.transactions = transactions;
        this.candleRepository = candleRepository;
        this.eventPublisher = eventPublisher;
        this.livePositionProtectionService = livePositionProtectionService;
        this.priceMoveMonitorService = priceMoveMonitorService;
        this.marketPriceEventService = marketPriceEventService;
    }

    // FIX-124: no suspended caller transaction may retain market-data locks.
    @Transactional(propagation = Propagation.NEVER)
    public boolean processKline(JsonNode root) {
        if (sharedMarketEnabled) { throw new IllegalStateException("FIX-132: Trader kline ingestion is disabled"); }
        if(drain!=null && !drain.beginInput()){log.info("[FIX-153][KLINE_REJECTED_DRAINING]");return false;}
        try {

        JsonNode data = root.has("data")
                ? root.path("data")
                : root;

        JsonNode kline = data.path("k");

        if (kline.isMissingNode() || kline.isNull()) {
            throw new IllegalArgumentException(
                    "Binance message does not contain kline data"
            );
        }

        String symbol = kline.path("s")
                .asText()
                .trim()
                .toUpperCase(Locale.ROOT);

        String intervalCode = kline.path("i")
                .asText()
                .trim();

        Instant openTime = Instant.ofEpochMilli(
                kline.path("t").asLong()
        );

        Instant closeTime = Instant.ofEpochMilli(
                kline.path("T").asLong()
        );

        boolean closed = kline.path("x")
                .asBoolean(false);
        BigDecimal livePrice = MarketPriceEventService.normalizePrice(decimal(kline, "c"));

        // FIX-153: an absent Binance event timestamp is not equivalent to a fresh receipt.
        Instant observedAt = data.path("E").asLong(0L)>0 ? Instant.ofEpochMilli(data.path("E").asLong()) : null;
        boolean canonical="1m".equals(intervalCode);
        String token=java.util.UUID.randomUUID().toString();
        java.util.concurrent.atomic.AtomicReference<CandleRepository.Transition> transition=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Long> priceId=new java.util.concurrent.atomic.AtomicReference<>();
        long started=System.nanoTime();
        try {
            transactions.process(symbol,intervalCode,openTime,observedAt,livePrice,
                ()->{
                    transition.set(candleRepository.upsertRuntime(symbol,intervalCode,openTime,closeTime,
                        decimal(kline,"o"),decimal(kline,"h"),decimal(kline,"l"),livePrice,
                        decimal(kline,"v"),decimal(kline,"q"),kline.path("n").asLong(),decimal(kline,"V"),decimal(kline,"Q"),closed,token));
                    if(canonical)priceId.set(marketPriceEventService.recordPending(symbol,livePrice,observedAt));
                },
                canonical ? ()->{if(priceId.get()!=null)livePositionProtectionService.onPrice(symbol,livePrice,observedAt,priceId.get());} : null,
                canonical ? ()->{if(priceId.get()!=null)priceMoveMonitorService.onPrice(symbol,livePrice,observedAt);} : null,
                closed ? ()->{var result=transition.get();if(result!=null && result.transitionedToClosed())
                    eventPublisher.publishEvent(new CandleClosedEvent(symbol,intervalCode,openTime,result.closeTime()));} : null);

            var completedTransition=transition.get();
            if(closed && completedTransition!=null && !completedTransition.transitionedToClosed()) {
                com.crypto.local.LocalRuntimeDiagnostics.count(com.crypto.local.LocalRuntimeDiagnostics.Event.DUPLICATE_CLOSE);
                log.info("[FIX-153][DUPLICATE_CLOSE] receipt={}, symbol={}, interval={}, openUtc={}, existingToken={}",
                    token,symbol,intervalCode,openTime,completedTransition.token());
                // This audit is intentionally after the ingestion transaction and
                // fail-safe: an audit problem must never make a Binance receipt fail.
                if(reconciliationAudit!=null)try {reconciliationAudit.markLateLiveClose(symbol,intervalCode,openTime);}
                catch(RuntimeException ignored){log.warn("[FIX-153][LATE_LIVE_CLOSE_AUDIT_FAILED] symbol={}, interval={}, openUtc={}",symbol,intervalCode,openTime);}
            } else if(!closed && completedTransition!=null && completedTransition.alreadyClosed()) {
                com.crypto.local.LocalRuntimeDiagnostics.count(com.crypto.local.LocalRuntimeDiagnostics.Event.LATE_OPEN_IGNORED);
                log.info("[FIX-153][LATE_OPEN_IGNORED] receipt={}, symbol={}, interval={}, openUtc={}",token,symbol,intervalCode,openTime);
            }

            log.debug("[FIX-153][KLINE_PHASES_RETURNED] receipt={}, event={}, symbol={}, interval={}, openUtc={}, observedUtc={}, transitionedToClosed={}, elapsedIncludingTransactionsMs={}; protection outcome is in price evidence",
                token,priceId.get(),symbol,intervalCode,openTime,observedAt,transition.get()!=null&&transition.get().transitionedToClosed(),(System.nanoTime()-started)/1_000_000);
        }catch(RuntimeException failure){
            com.crypto.local.LocalRuntimeDiagnostics.count(com.crypto.local.LocalRuntimeDiagnostics.Event.PIPELINE_FAILED_OR_UNCONFIRMED);
            log.error("[FIX-153][KLINE_PHASE_FAILED_OR_UNCONFIRMED] receipt={}, event={}, symbol={}, interval={}, openUtc={}; inspect durable evidence, do not infer no effects",token,priceId.get(),symbol,intervalCode,openTime,failure);
            throw failure;
        }finally{if(priceId.get()!=null)marketPriceEventService.release(priceId.get());}

        return closed;
        } finally {if(drain!=null)drain.completeInput();}
    }

    private BigDecimal decimal(
            JsonNode node,
            String field
    ) {
        String value = node.path(field).asText();

        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }

        return new BigDecimal(value);
    }
}
