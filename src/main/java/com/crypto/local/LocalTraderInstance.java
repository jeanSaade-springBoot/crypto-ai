package com.crypto.local;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
import java.nio.channels.*;
import java.nio.file.*;

/** FIX-153: single-host process exclusion, never a DB lease or trading row fence.
 * All starts against this DB must use the same absolute path, same host. Multi-host
 * operation remains unsupported and must be prevented by deployment orchestration. */
@Component("localTraderInstance")
public class LocalTraderInstance {
    // Keep channels strongly reachable through context shutdown: old callbacks must not
    // overlap a new process just because Spring has begun destruction. OS exit releases locks.
    private static final java.util.Set<FileChannel> PROCESS_CHANNELS=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private FileChannel channel;private FileLock lock;
    public LocalTraderInstance(Environment env)throws java.io.IOException {
        if(!"OFF".equals(env.getProperty("shared-market.mode","OFF")))return;
        String configured=env.getProperty("local-candle.instance-lock-path");
        if(configured==null || configured.isBlank())
            throw new IllegalStateException("FIX-153 local-candle.instance-lock-path must be explicitly configured as an absolute path");
        Path requested=Path.of(configured);
        if(!requested.isAbsolute())
            throw new IllegalStateException("FIX-153 local-candle.instance-lock-path must be absolute: "+configured);
        Path file=requested.normalize();
        Files.createDirectories(file.getParent());channel=FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        try {lock=channel.tryLock();if(lock==null)throw new IllegalStateException("FIX-153 another trader holds the configured instance lock: "+file);}
        catch(RuntimeException failure){channel.close();throw failure;}
        PROCESS_CHANNELS.add(channel);
        org.slf4j.LoggerFactory.getLogger(getClass()).info("[FIX-153][LOCAL_INSTANCE_ACQUIRED] lockPath={}, pid={}, mode=OFF; same-host same-path exclusion, not distributed fencing",file,ProcessHandle.current().pid());
    }
    @PreDestroy public void shuttingDown(){
        if(lock!=null)org.slf4j.LoggerFactory.getLogger(getClass()).info("[FIX-153][INSTANCE_SHUTDOWN] pid={}; instance lock retained until JVM exit, wait for process exit before next launch",ProcessHandle.current().pid());
    }
    /** Explicit fixture cleanup only; normal Spring destruction retains the OS lock. */
    public void close()throws java.io.IOException {if(lock!=null)lock.release();if(channel!=null){channel.close();PROCESS_CHANNELS.remove(channel);}}
}
