package com.crypto.infrastructure.diagnostics;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.*;
import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.Logger;
import org.slf4j.LoggerFactory;

/** FIX-142: JDBC boundary timing for local JPA/JdbcTemplate and collector reads.
 * Only redacted SQL templates are emitted; bind values are never emitted. A bounded, nonblocking log queue isolates
 * trading threads from log I/O; dropped diagnostics are reported, never hidden.
 * Execute, result traversal, acquisition and commit are separate measurements.
 * These timings are NOT rows examined, server lock waits, or buffer-pool reads. */
public final class JdbcTimingDataSource implements DataSource, AutoCloseable {
    private static final org.slf4j.Logger LOG=LoggerFactory.getLogger("FIX142.DatabaseTiming");
    private static final org.slf4j.Logger STATUS_LOG=LoggerFactory.getLogger(JdbcTimingDataSource.class);
    private static final ArrayBlockingQueue<String> EVENTS=new ArrayBlockingQueue<>(2048);
    private static final AtomicLong DROPPED=new AtomicLong();
    private static final AtomicLong IDS=new AtomicLong();
    private static final AtomicLong OMITTED_AGGREGATES=new AtomicLong();
    private static final AtomicLong TOTAL_DROPPED=new AtomicLong();
    private static final ConcurrentHashMap<String,Aggregate> AGGREGATES=new ConcurrentHashMap<>();
    private static final ThreadLocal<String> CONTEXT=ThreadLocal.withInitial(()->"NONE");
    private static final class Aggregate {
        final LongAdder count=new LongAdder(),failed=new LongAdder(),elapsedMs=new LongAdder();
    }
    /** Bounded labels; never use SQL/event IDs as metric keys. */
    public static java.util.Map<String,Object> statistics() {
        var result=new java.util.LinkedHashMap<String,Object>();
        AGGREGATES.forEach((key,value)->result.put(key,java.util.Map.of("count",value.count.sum(),"failed",value.failed.sum(),"totalMs",value.elapsedMs.sum())));
        result.put("aggregateSamplesOmitted",OMITTED_AGGREGATES.get());result.put("queueCapacity",2048);result.put("queueDepth",EVENTS.size());result.put("droppedTotal",TOTAL_DROPPED.get());return result;
    }
    public interface Scope extends AutoCloseable { void close(); }
    public static Scope context(String detail) {
        String previous=CONTEXT.get();CONTEXT.set(detail);return ()->CONTEXT.set(previous);
    }
    public static void diagnostic(String line){offer(line);}
    private static void offer(String line){if(line.length()>4096)line=line.substring(0,4096)+" detailTruncated=true";if(!EVENTS.offer(line)){DROPPED.incrementAndGet();TOTAL_DROPPED.incrementAndGet();}}

    static {
        Thread writer=new Thread(()->{
            while(!Thread.currentThread().isInterrupted()) {
                try {
                    String item=EVENTS.poll(1,TimeUnit.SECONDS);
                    if(item!=null)LOG.info("{}",item);
                    long lost=DROPPED.getAndSet(0);
                    if(lost>0)STATUS_LOG.warn("[FIX-142][DIAGNOSTICS_DROPPED] count={}; log queue saturated",lost);
                } catch(InterruptedException e){Thread.currentThread().interrupt();}
                  catch(RuntimeException ignored){DROPPED.incrementAndGet();}
            }
        },"fix142-db-log-writer"); writer.setDaemon(true);writer.start();
    }
    private final DataSource delegate;
    private final String pool;
    private final long slowMs;
    public JdbcTimingDataSource(DataSource delegate,String pool,long slowMs) {
        this.delegate=delegate;this.pool=pool;this.slowMs=slowMs;
    }
    private void emit(String event,String id,String tx,Instant start,long nanos,Throwable failure,String detail) {
        long duration=(System.nanoTime()-nanos)/1_000_000;
        String state=failure instanceof SQLException s?s.getSQLState():failure==null?"NONE":failure.getClass().getSimpleName();
        Instant end=Instant.now();
        String line="[FIX-142]["+event+"] id="+id+" pool="+pool+" transaction="+tx+
            " startUtc="+start+" endUtc="+end+" startKsa="+start.atOffset(java.time.ZoneOffset.ofHours(3))+" endKsa="+end.atOffset(java.time.ZoneOffset.ofHours(3))+" durationMs="+duration+
            " context="+CONTEXT.get()+" outcome="+(failure==null?"SUCCESS":"FAILED")+" sqlState="+state+" "+detail+
            (duration>=slowMs?" [FIX-142][SLOW_QUERY]":"");
        String key=pool+":"+event+":"+detail.replaceAll(" fingerprint=.*", "").replaceAll(" (?:rowsReturned|batchEntries|affectedRows)=.*", "");
        Aggregate aggregate=AGGREGATES.get(key);
        if(aggregate==null) {
            synchronized(AGGREGATES){aggregate=AGGREGATES.get(key);if(aggregate==null && AGGREGATES.size()<1024){aggregate=new Aggregate();AGGREGATES.put(key,aggregate);}}
        }
        if(aggregate!=null){aggregate.count.increment();aggregate.elapsedMs.add(duration);if(failure!=null)aggregate.failed.increment();}else OMITTED_AGGREGATES.incrementAndGet();
        offer(line);
    }
    private void started(String event,String id,String tx,Instant start,String detail) {
        offer("[FIX-142]["+event+"_START] id="+id+" pool="+pool+
                " transaction="+tx+" startUtc="+start+" startKsa="+start.atOffset(java.time.ZoneOffset.ofHours(3))+" context="+CONTEXT.get()+" "+detail);
    }
    private static Object call(Object target,Method method,Object[] args)throws Throwable {
        try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}
    }
    private static String component() {
        return StackWalker.getInstance().walk(s->s.filter(f->f.getClassName().startsWith("com.crypto.")
            && !f.getClassName().startsWith("com.crypto.infrastructure.diagnostics."))
            .findFirst().map(f->f.getClassName()+"#"+f.getMethodName()).orElse("FRAMEWORK"));
    }
    static String safeTemplate(String sql) {
        if(sql==null)return "UNKNOWN";
        StringBuilder text=new StringBuilder();
        for(int i=0;i<sql.length();) {
            char c=sql.charAt(i);
            if(c=='\''||c=='"') {
                char quote=c;text.append('?');i++;
                while(i<sql.length()) {
                    char value=sql.charAt(i++);
                    if(value=='\\'&&i<sql.length()){i++;continue;}
                    if(value==quote){if(i<sql.length()&&sql.charAt(i)==quote){i++;continue;}break;}
                }
            } else if(i+1<sql.length()&&c=='/'&&sql.charAt(i+1)=='*') {
                int end=sql.indexOf("*/",i+2);i=end<0?sql.length():end+2;text.append(' ');
            } else if(c=='#'||(i+1<sql.length()&&c=='-'&&sql.charAt(i+1)=='-')) {
                int end=sql.indexOf('\n',i);i=end<0?sql.length():end+1;text.append(' ');
            } else {text.append(c);i++;}
        }
        return text.toString().replaceAll("(?i)\\b0x[0-9a-f]+\\b","?")
            .replaceAll("\\b[0-9]+(?:\\.[0-9]+)?\\b","?").replaceAll("\\s+"," ").strip();
    }
    private static String shape(String sql) {
        String clean=safeTemplate(sql);
        String operation=clean.isEmpty()?"UNKNOWN":clean.split("\\s+",2)[0].toUpperCase(java.util.Locale.ROOT);
        if(!java.util.Set.of("SELECT","INSERT","UPDATE","DELETE","ALTER","CREATE","SET","WITH").contains(operation))operation="OTHER";
        var matcher=java.util.regex.Pattern.compile("(?i)\\b(?:FROM|INTO|UPDATE|TABLE)\\s+((?:`?[a-zA-Z_][a-zA-Z0-9_]{0,80}`?\\.)?`?[a-zA-Z_][a-zA-Z0-9_]{0,80}`?)").matcher(clean);
        return "operation="+operation+" table="+(matcher.find()?matcher.group(1):"UNKNOWN")
            +" fingerprint="+Integer.toHexString(clean.hashCode())+" sqlTemplate="+clean.substring(0,Math.min(512,clean.length()));
    }
    private Connection acquire(String user,String password,boolean supplied)throws SQLException {
        String id=Long.toString(IDS.incrementAndGet());Instant start=Instant.now();long n=System.nanoTime();
        started("CONNECTION_ACQUIRE",id,"NONE",start,"");
        Connection c=null;
        try {
            c=supplied?delegate.getConnection(user,password):delegate.getConnection();
            Connection wrapped=wrap(c,id);emit("CONNECTION_ACQUIRE",id,"NONE",start,n,null,"");return wrapped;
        }catch(SQLException e){
            if(c!=null)try{c.close();}catch(SQLException close){e.addSuppressed(close);}
            emit("CONNECTION_ACQUIRE",id,"NONE",start,n,e,"");throw e;
        }
    }
    private Connection wrap(Connection connection,String connectionId) throws SQLException {
        class Handler implements InvocationHandler {
            boolean autoCommit;
            String tx="NONE";Instant txStart;long txNanos;
            Handler() throws SQLException {autoCommit=connection.getAutoCommit();}
            String transactionId() {
                if(autoCommit)return "AUTOCOMMIT";
                if(txStart==null){tx=UUID.randomUUID().toString();txStart=Instant.now();txNanos=System.nanoTime();started("TRANSACTION",connectionId,tx,txStart,"component="+component());}
                return tx;
            }
            void finish(Throwable failure,String resolution) {
                if(txStart!=null)emit("TRANSACTION",connectionId,tx,txStart,txNanos,failure,"resolution="+resolution);
                txStart=null;tx="NONE";
            }
            public Object invoke(Object proxy,Method method,Object[] args)throws Throwable {
                String name=method.getName();
                if(name.equals("unwrap")&&((Class<?>)args[0]).isInstance(proxy))return proxy;
                if(name.equals("isWrapperFor")&&((Class<?>)args[0]).isInstance(proxy))return true;
                if(name.equals("setAutoCommit")) {
                    boolean next=(Boolean)args[0];
                    // Keep the original span identity after finish() clears the active transaction.
                    String current=tx;
                    Instant start=Instant.now();long n=System.nanoTime();Throwable failure=null;
                    started("AUTOCOMMIT_CHANGE",connectionId,current,start,"enabled="+next);
                    try {
                        Object result=call(connection,method,args);
                        if(next&&!autoCommit)finish(null,"COMMITTED_BY_AUTOCOMMIT_CHANGE");
                        autoCommit=next;return result;
                    }catch(Throwable error){failure=error;throw error;}
                    finally{emit("AUTOCOMMIT_CHANGE",connectionId,current,start,n,failure,"enabled="+next);}

                }
                if(name.equals("commit")||name.equals("rollback")) {
                    boolean savepoint=name.equals("rollback")&&args!=null&&args.length==1;
                    String event=savepoint?"ROLLBACK_TO_SAVEPOINT":name.toUpperCase();
                    Instant start=Instant.now();long n=System.nanoTime();Throwable failure=null;
                    String current=transactionId();started(event,connectionId,current,start,"component="+component());
                    try{return call(connection,method,args);}catch(Throwable e){failure=e;throw e;}
                    finally {
                        String resolution=failure!=null?"UNKNOWN":savepoint?"SAVEPOINT_ONLY":name.equals("commit")?"COMMITTED":"ROLLED_BACK";
                        emit(event,connectionId,current,start,n,failure,"resolution="+resolution);
                        if(!savepoint)finish(failure,resolution);
                    }
                }
                if(name.equals("close")) {
                    // A failed close must not be reported as a successful unresolved closure.
                    Throwable failure=null;
                    try{return call(connection,method,args);}catch(Throwable error){failure=error;throw error;}
                    finally{finish(failure,"CONNECTION_CLOSED_UNRESOLVED");}
                }
                Object result=call(connection,method,args);
                if(result instanceof Statement st && (name.equals("prepareStatement")||name.equals("prepareCall")||name.equals("createStatement"))) {
                    String sql=args!=null&&args.length>0&&args[0] instanceof String text?text:null;
                    return statement(st,sql,this::transactionId,(Connection)proxy);
                }
                return result;
            }
        }
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},new Handler());
    }
    private Statement statement(Statement statement,String prepared,java.util.function.Supplier<String> tx,Connection connection) {
        Class<?> type=statement instanceof CallableStatement?CallableStatement.class:statement instanceof PreparedStatement?PreparedStatement.class:Statement.class;
        java.util.Map<ResultSet,ResultSet> results=new java.util.IdentityHashMap<>();
        java.util.List<String> batches=new java.util.ArrayList<>();
        AtomicLong batchCount=new AtomicLong();
        return (Statement)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(proxy,method,args)->{
            if(method.getName().equals("unwrap")&&((Class<?>)args[0]).isInstance(proxy))return proxy;
            if(method.getName().equals("isWrapperFor")&&((Class<?>)args[0]).isInstance(proxy))return true;
            if(method.getName().equals("addBatch")) {
                Object value=call(statement,method,args);
                batchCount.incrementAndGet();if(batches.size()<8)batches.add(shape(prepared!=null?prepared:args!=null&&args.length>0?(String)args[0]:null));
                return value;
            }
            if(method.getName().equals("clearBatch")){Object value=call(statement,method,args);batches.clear();batchCount.set(0);return value;}
            if(method.getName().equals("getConnection"))return connection;
            if(method.getName().equals("close")) {
                // JDBC closes result sets implicitly with their statement. Complete fetch spans
                // without adding extra driver close calls or changing the driver's exception.
                Throwable failure=null;
                try{return call(statement,method,args);}catch(Throwable error){failure=error;throw error;}
                finally{
                    for(ResultSet wrapped:results.values())((FetchCompletion)Proxy.getInvocationHandler(wrapped)).complete(failure);
                    results.clear();
                }
            }
            if(method.getName().startsWith("execute")) {
                String sql=prepared!=null?prepared:args!=null&&args.length>0&&args[0] instanceof String s?s:null;
                String detail="component="+component()+" "+shape(sql)+(method.getName().contains("Batch")?" batchEntries="+batchCount.get()+" templatesFirst8="+String.join(";",batches):"");String currentTx=tx.get();String id=Long.toString(IDS.incrementAndGet());
                Instant start=Instant.now();long n=System.nanoTime();Throwable failure=null;
                final String fetchDetail=detail;
                started("SQL",id,currentTx,start,detail);
                try {Object result=call(statement,method,args);if(result instanceof Number count)detail+=" affectedRows="+count;else if(result instanceof int[] batchCounts)detail+=" affectedRows="+java.util.Arrays.toString(batchCounts);return result instanceof ResultSet r?results.computeIfAbsent(r,key->resultSet(key,id,currentTx,fetchDetail,(Statement)proxy)):result;}
                catch(Throwable e){failure=e;throw e;}finally{emit("SQL",id,currentTx,start,n,failure,detail);if(method.getName().contains("Batch")){batches.clear();batchCount.set(0);}}
            }
            Object result=call(statement,method,args);
            return result instanceof ResultSet r?results.computeIfAbsent(r,key->resultSet(key,Long.toString(IDS.incrementAndGet()),tx.get(),shape(prepared),(Statement)proxy)):result;
        });
    }
    private interface FetchCompletion { void complete(Throwable failure); }
    private ResultSet resultSet(ResultSet result,String id,String tx,String detail,Statement owner) {
        Instant start=Instant.now();started("FETCH",id,tx,start,detail);long n=System.nanoTime();AtomicLong rows=new AtomicLong();AtomicBoolean ended=new AtomicBoolean();
        class FetchHandler implements InvocationHandler,FetchCompletion {
            public void complete(Throwable failure) {
                if(ended.compareAndSet(false,true))emit("FETCH",id,tx,start,n,failure,detail+" rowsReturned="+rows.get());
            }
            public Object invoke(Object proxy,Method method,Object[] args)throws Throwable {
            // Preserve the decorated JDBC object graph so getStatement() cannot bypass timing.
            if(method.getName().equals("getStatement"))return owner;
            if(method.getName().equals("unwrap")&&((Class<?>)args[0]).isInstance(proxy))return proxy;
            if(method.getName().equals("isWrapperFor")&&((Class<?>)args[0]).isInstance(proxy))return true;
            try {
                Object value=call(result,method,args);
                if(method.getName().equals("next")&&Boolean.TRUE.equals(value))rows.incrementAndGet();
                if(method.getName().equals("close")||(method.getName().equals("next")&&Boolean.FALSE.equals(value)))complete(null);
                return value;
            }catch(Throwable e){complete(e);throw e;}
            }
        }
        return (ResultSet)Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},new FetchHandler());
    }
    public Connection getConnection()throws SQLException{return acquire(null,null,false);}
    public Connection getConnection(String u,String p)throws SQLException{return acquire(u,p,true);}
    public PrintWriter getLogWriter()throws SQLException{return delegate.getLogWriter();}
    public void setLogWriter(PrintWriter p)throws SQLException{delegate.setLogWriter(p);}
    public void setLoginTimeout(int t)throws SQLException{delegate.setLoginTimeout(t);}
    public int getLoginTimeout()throws SQLException{return delegate.getLoginTimeout();}
    public Logger getParentLogger()throws SQLFeatureNotSupportedException{return delegate.getParentLogger();}
    public <T>T unwrap(Class<T> c)throws SQLException{return c.isInstance(this)?c.cast(this):delegate.unwrap(c);}
    public boolean isWrapperFor(Class<?> c)throws SQLException{return c.isInstance(this)||delegate.isWrapperFor(c);}
    public void close()throws Exception{if(delegate instanceof AutoCloseable c)c.close();}
}
