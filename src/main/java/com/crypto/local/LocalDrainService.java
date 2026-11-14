package com.crypto.local;

import com.crypto.indicator.event.CandleAnalysisDispatcher;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Controlled local replacement: stop admission, wait for owned work, then exit. */
@Service
public class LocalDrainService {
    private final AtomicBoolean draining=new AtomicBoolean();
    private final AtomicInteger inputs=new AtomicInteger();
    private final AtomicInteger reconciliations=new AtomicInteger();
    private final CandleAnalysisDispatcher dispatcher;
    private final ConfigurableApplicationContext context;
    private final LocalProcessExit processExit;
    public LocalDrainService(CandleAnalysisDispatcher dispatcher,ConfigurableApplicationContext context,LocalProcessExit processExit){this.dispatcher=dispatcher;this.context=context;this.processExit=processExit;}
    public boolean beginInput(){if(draining.get())return false;inputs.incrementAndGet();if(draining.get()){inputs.decrementAndGet();return false;}return true;}
    public void completeInput(){inputs.decrementAndGet();}
    /** Background candle repair participates in drain but never queues or
     * blocks ordinary work outside an active drain. */
    public boolean beginReconciliation(){if(draining.get())return false;reconciliations.incrementAndGet();if(draining.get()){reconciliations.decrementAndGet();return false;}return true;}
    public void completeReconciliation(){reconciliations.decrementAndGet();}
    public boolean reconciliationIdle(){return reconciliations.get()==0;}
    public boolean draining(){return draining.get();}
    public void request(Duration timeout){
        if(!draining.compareAndSet(false,true))return;
        CompletableFuture.runAsync(()->{
            Instant deadline=Instant.now().plus(timeout);
            while(Instant.now().isBefore(deadline)){
                if(inputs.get()==0 && reconciliations.get()==0 && dispatcher.idleAll()){
                    org.slf4j.LoggerFactory.getLogger(getClass()).info("[FIX-153][DRAIN_COMPLETE] inputs=0 lanesIdle=true; closing application and exiting JVM");
                    int status=SpringApplication.exit(context,()->0);
                    processExit.exit(status);return;
                }
                try{Thread.sleep(100);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}
            }
            draining.set(false);
            org.slf4j.LoggerFactory.getLogger(getClass()).error("[FIX-153][DRAIN_TIMEOUT] inputs={}, reconciliations={}, lanesIdle={}; application remains running and admission resumed",inputs.get(),reconciliations.get(),dispatcher.idleAll());
        });
    }
}
