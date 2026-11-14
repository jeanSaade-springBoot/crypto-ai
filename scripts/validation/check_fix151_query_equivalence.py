"""FIX-151: executable SQL semantics checks; SQLite is NOT MySQL lock/performance proof."""
from pathlib import Path
import random
import re
import sqlite3

root = Path(__file__).resolve().parents[2]
java = (root/'src/main/java/com/crypto/shared/Fix142DeliveryProtocol.java').read_text()
tests = (root/'src/test/java/com/crypto/shared/Fix142DeliveryTest.java').read_text()
new = re.search(r'EXPOSURE_SQL="""(.*?)"""', java, re.S).group(1)
old = re.search(r'PRE_FIX151_EXPOSURE_SQL="""(.*?)"""', tests, re.S).group(1)
db = sqlite3.connect(':memory:')
db.executescript('''
CREATE TABLE shared_market_consumer_state(symbol TEXT PRIMARY KEY,active_delivery_event_id INTEGER,active_delivery_state TEXT);
CREATE TABLE shared_market_event_delivery(source_event_id INTEGER PRIMARY KEY,symbol TEXT,interval_code TEXT,phase INTEGER,status TEXT,analysis_status TEXT,closed INTEGER,candle_close_time INTEGER,observed_at INTEGER,protection_started_at INTEGER,protection_completed_at INTEGER,symbol_sequence INTEGER,
phase_walk_pending INTEGER GENERATED ALWAYS AS (CASE WHEN phase<4 AND status<>'REVIEW_REQUIRED' THEN 1 ELSE 0 END) VIRTUAL);
CREATE INDEX idx_fix151_pending_sequence ON shared_market_event_delivery(symbol,phase_walk_pending,symbol_sequence);
CREATE INDEX idx_fix151_exposure_close ON shared_market_event_delivery(symbol,interval_code,closed,candle_close_time,phase,analysis_status);
''')
rng = random.Random(151)
now = 1_000_000
statuses = ('PENDING','PROCESSING','COMPLETED','REVIEW_REQUIRED','HISTORICAL_ONLY')
analysis = ('PENDING','HISTORICAL_DEFERRED','RUNNING','EXECUTION_RETRY','REVIEW_REQUIRED','COMPLETED','WAITING')
intervals = ('1m','5m','15m','1h','4h','1d')
# Boundary/future/null values exercise inclusivity, unknown times and expired rows.
deltas = (None,-1,0,1,15,16,90,91,120,121,300,301,600,601,1800,1801,7200)
def moment():
    delta = rng.choice(deltas)
    return None if delta is None else now-delta
for i in range(1,6001):
    db.execute('INSERT INTO shared_market_event_delivery(source_event_id,symbol,interval_code,phase,status,analysis_status,closed,candle_close_time,observed_at,protection_started_at,protection_completed_at,symbol_sequence) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)',
        (i,rng.choice(('BTCUSDT','ETHUSDT')),rng.choice(intervals),rng.randrange(-1,6),rng.choice(statuses),rng.choice(analysis),rng.randrange(2),moment(),moment(),moment(),moment(),i))
# Deliberate overlap of four branches: it must count exactly once.
db.execute("UPDATE shared_market_event_delivery SET symbol='BTCUSDT',interval_code='1m',phase=1,closed=1,observed_at=?,candle_close_time=?,protection_started_at=1,protection_completed_at=NULL WHERE source_event_id=1",(now,now))
checks = 0
for state in ('NONE','ACTIVE','REVIEW_REQUIRED'):
    for marker in (1,6001,None):
        db.execute('INSERT OR REPLACE INTO shared_market_consumer_state VALUES(?,?,?)',('BTCUSDT',marker,state))
        for symbol in ('BTCUSDT','ETHUSDT','MISSING'):
            for interval,grace in zip(intervals,(90,120,120,300,600,1800)):
                for clock in (now,now+16,now+7201):
                    begin,price = clock-grace,clock-15
                    expected = db.execute(old,(symbol,interval,begin,clock,price,clock,begin,clock)).fetchone()[0]
                    actual = db.execute(new,(symbol,interval,symbol,interval,begin,clock,symbol,symbol,interval,symbol,interval,symbol,interval,price,clock,symbol,interval,begin,clock)).fetchone()[0]
                    assert expected == actual, (symbol,interval,state,marker,clock,expected,actual)
                    checks += 1
original = "SELECT source_event_id FROM shared_market_event_delivery WHERE symbol=? AND phase<4 AND status<>'REVIEW_REQUIRED' ORDER BY symbol_sequence LIMIT 1"
optimized = "SELECT source_event_id FROM shared_market_event_delivery WHERE symbol=? AND phase_walk_pending=1 ORDER BY symbol_sequence LIMIT 1"
for symbol in ('BTCUSDT','ETHUSDT','MISSING'):
    assert db.execute(original,(symbol,)).fetchone() == db.execute(optimized,(symbol,)).fetchone()
for phase in (-1,0,1,2,3,4,5):
    for status in statuses:
        db.execute('UPDATE shared_market_event_delivery SET phase=?,status=? WHERE source_event_id=1',(phase,status))
        flag = db.execute('SELECT phase_walk_pending FROM shared_market_event_delivery WHERE source_event_id=1').fetchone()[0]
        assert flag == int(phase<4 and status!='REVIEW_REQUIRED')
plan = list(db.execute('EXPLAIN QUERY PLAN '+optimized,('BTCUSDT',)))
assert any('idx_fix151_pending_sequence' in row[3] for row in plan), plan
assert not any('TEMP B-TREE' in row[3] for row in plan), plan
print(f'PASS: {checks} old/new exposure comparisons on 6000 rows; overlaps, uncertain states, expiry boundaries, future/null times and lane isolation.')
print('PASS: oldest-selection equivalence; 35 phase/status transitions; SQLite indexed selector without sort.')
print('LIMIT: SQLite semantics/access check only. Java 21 Maven, H2/JUnit, MySQL migration/plans/races and full load NOT executed.')
