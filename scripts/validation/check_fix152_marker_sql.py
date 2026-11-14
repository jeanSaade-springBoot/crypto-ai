"""FIX-152 extracted marker CAS/audit rollback check; not MySQL locking proof."""
from pathlib import Path
import re,sqlite3
root=Path(__file__).resolve().parents[2]
source=(root/'src/main/java/com/crypto/shared/Fix150ProtectionRecovery.java').read_text().split('public boolean resumeRejectedMarker')[1]
update=re.search(r'jdbc.update\("(UPDATE shared_market_consumer_state[^"\n]+)"',source).group(1).replace('CURRENT_TIMESTAMP(6)','CURRENT_TIMESTAMP')
audit=re.search(r'jdbc.update\("(INSERT INTO shared_delivery_resolution_audit[^"\n]+)"',source).group(1)
db=sqlite3.connect(':memory:')
db.executescript('''
CREATE TABLE shared_market_consumer_state(symbol TEXT PRIMARY KEY,active_delivery_event_id INTEGER,active_delivery_owner_token TEXT,active_delivery_process_owner TEXT,active_delivery_state TEXT,active_delivery_updated_at TEXT);
CREATE TABLE shared_delivery_resolution_audit(source_event_id INTEGER,symbol TEXT,operation TEXT,old_owner_token TEXT,new_owner_token TEXT,reviewer TEXT CHECK(reviewer<>'REJECT_AUDIT'),evidence TEXT);
INSERT INTO shared_market_consumer_state VALUES('BTCUSDT',3303890,'old-marker','old-process','REVIEW_REQUIRED','original');
''')
with db:
    assert db.execute(update,('new-marker','new-process','BTCUSDT',3303890,'old-marker')).rowcount==1
    assert db.execute(audit,(3303890,'BTCUSDT','old-marker','new-marker','FIX152_SYSTEM','durable rejected protection + exact phase-1 evidence')).rowcount==1
assert db.execute(update,('late-old-token','old-process','BTCUSDT',3303890,'old-marker')).rowcount==0
assert db.execute('SELECT active_delivery_state,active_delivery_owner_token FROM shared_market_consumer_state').fetchone()==('ACTIVE','new-marker')
with db:
    db.execute("UPDATE shared_market_consumer_state SET active_delivery_state='REVIEW_REQUIRED',active_delivery_owner_token='rollback-owner'")
try:
    with db:
        assert db.execute(update,('should-rollback','new-process','BTCUSDT',3303890,'rollback-owner')).rowcount==1
        db.execute(audit,(3303890,'BTCUSDT','rollback-owner','should-rollback','REJECT_AUDIT','test audit rejection'))
except sqlite3.IntegrityError:
    pass
else:
    raise AssertionError('Audit rejection missing')
assert db.execute('SELECT active_delivery_state,active_delivery_owner_token FROM shared_market_consumer_state').fetchone()==('REVIEW_REQUIRED','rollback-owner')
assert db.execute('SELECT COUNT(*) FROM shared_delivery_resolution_audit').fetchone()[0]==1
assert 'UPDATE shared_market_event_delivery' not in source.split('private static Instant timestamp')[0]
print('PASS: exact marker CAS, stale-token refusal, audit success, audit-failure atomic rollback; no delivery/protection evidence UPDATE in helper.')
print('LIMIT: SQLite SQL checks only; Java 21 JUnit/H2, real MySQL old/new-writer races and full Production/Replay parity NOT run.')
