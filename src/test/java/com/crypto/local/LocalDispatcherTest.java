package com.crypto.local;

import com.crypto.indicator.event.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** FIX-153: admission/reservation tests exercise callback handoff, not merely collection shape. */
class LocalDispatcherTest {
    final Instant base=Instant.parse("2026-10-06T10:00:00Z");
    CandleClosedEvent event(int n,String interval){return new CandleClosedEvent("BTCUSDT",interval,base.plusSeconds(n*60),base.plusSeconds(n*60+59));}
    @Test void latestPendingReplacesOlderAndReleasesItsReservation(){
        List<Runnable> tasks=new ArrayList<>();var worker=mock(CandleClosedAnalysisWorker.class);
        var d=new CandleAnalysisDispatcher(tasks::add,worker);
        d.submit(event(0,"1m"));d.submit(event(1,"1m"));d.submit(event(2,"1m"));d.submit(event(2,"1m"));
        assertEquals(1,tasks.size());tasks.removeFirst().run();tasks.removeFirst().run();
        verify(worker).processShared(event(0,"1m"),event(0,"1m").closeTime(),true);
        verify(worker).processShared(event(2,"1m"),event(2,"1m").closeTime(),true);
        verifyNoMoreInteractions(worker);assertTrue(d.idle("BTCUSDT","1m"));
        assertTrue(d.submitDetected(event(1,"1m"),()->true),"replaced reservation can be reconsidered");
    }
    @Test void rejectionReleasesReservationAndDetectorCanRetry(){
        var worker=mock(CandleClosedAnalysisWorker.class);List<Runnable> tasks=new ArrayList<>();
        var reject=new java.util.concurrent.atomic.AtomicBoolean(true);
        var d=new CandleAnalysisDispatcher(r->{if(reject.get())throw new RejectedExecutionException();tasks.add(r);},worker);
        assertFalse(d.submitDetected(event(0,"1m"),()->true));verifyNoInteractions(worker);
        reject.set(false);assertTrue(d.submitDetected(event(0,"1m"),()->true));tasks.removeFirst().run();
        verify(worker).processShared(any(),any(),eq(true));
    }
    @Test void higherTimeframeFifoIsBoundedAndDetectorDoesNotDisplaceLive(){
        List<Runnable> tasks=new ArrayList<>();var worker=mock(CandleClosedAnalysisWorker.class);
        var d=new CandleAnalysisDispatcher(tasks::add,worker);
        for(int n=0;n<7;n++)d.submit(event(n,"5m"));
        assertFalse(d.submitDetected(event(8,"5m"),()->fail("busy detector must not query")));
        while(!tasks.isEmpty())tasks.removeFirst().run();
        var order=inOrder(worker);for(int n=0;n<5;n++)order.verify(worker).processShared(event(n,"5m"),event(n,"5m").closeTime(),true);
        verifyNoMoreInteractions(worker);
    }
    @Test void detectorDbReadDoesNotBlockLiveAdmissionAndCannotEvictIt()throws Exception{
        List<Runnable> tasks=new CopyOnWriteArrayList<>();var worker=mock(CandleClosedAnalysisWorker.class);
        var d=new CandleAnalysisDispatcher(tasks::add,worker);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newSingleThreadExecutor()){
            var result=pool.submit(()->d.submitDetected(event(0,"1m"),()->{entered.countDown();try{assertTrue(release.await(3,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}return true;}));
            assertTrue(entered.await(3,TimeUnit.SECONDS));d.submit(event(1,"1m"));assertEquals(1,tasks.size());
            release.countDown();assertFalse(result.get(3,TimeUnit.SECONDS));
        }finally{release.countDown();}
        tasks.getFirst().run();verify(worker).processShared(event(1,"1m"),event(1,"1m").closeTime(),true);verifyNoMoreInteractions(worker);
    }
    @Test void synchronousExecutorReturnsActualAdmissionAndMissingEvidenceReleasesReservation(){
        var worker=mock(CandleClosedAnalysisWorker.class);var d=new CandleAnalysisDispatcher(Runnable::run,worker);
        assertFalse(d.submitDetected(event(0,"1m"),()->false));assertTrue(d.submitDetected(event(0,"1m"),()->true));
        verify(worker).processShared(any(),any(),eq(true));d.stop();assertFalse(d.submitDetected(event(1,"1m"),()->true));
    }
}
