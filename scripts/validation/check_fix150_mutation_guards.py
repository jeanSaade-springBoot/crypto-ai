"""Execute extracted FIX-150 mutation SQL on SQLite: guard/atomicity smoke only.
Not a MySQL, JDBC, JPA, Spring or business-parity test.
"""
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[2]
s = (ROOT / 'src/main/java/com/crypto/shared/Fix150ProtectionRecovery.java').read_text()
sqls = re.findall(r'jdbc\.update\("([^"]+)"', s)
delivery = next(x for x in sqls if x.startswith('UPDATE shared_market_event_delivery'))
marker = next(x for x in sqls if x.startswith('UPDATE shared_market_consumer_state'))
audit = next(x for x in sqls if x.startswith('INSERT INTO shared_delivery_resolution_audit'))

def db():
    c = sqlite3.connect(':memory:')
    c.executescript('''
    CREATE TABLE shared_market_event_delivery(source_event_id INTEGER PRIMARY KEY,protection_owner TEXT,protection_started_at TEXT,protection_completed_at TEXT,protection_outcome TEXT,protection_apply_state TEXT,phase INTEGER,status TEXT,analysis_status TEXT,observer_status TEXT,last_error TEXT);
    CREATE TABLE shared_market_consumer_state(symbol TEXT PRIMARY KEY,active_delivery_event_id INTEGER,active_delivery_owner_token TEXT,active_delivery_process_owner TEXT,active_delivery_state TEXT,active_delivery_started_at TEXT,active_delivery_updated_at TEXT);
    CREATE TABLE shared_delivery_resolution_audit(source_event_id INTEGER,symbol TEXT,operation TEXT CHECK(operation <> 'DENIED'),old_owner_token TEXT,reviewer TEXT,evidence TEXT);
    INSERT INTO shared_market_event_delivery VALUES(1,'protection-owner','old-start',NULL,NULL,'LEGACY_UNKNOWN',1,'REVIEW_REQUIRED','WAITING','PENDING','original exception');
    INSERT INTO shared_market_consumer_state VALUES('BTCUSDT',1,'marker-owner','old-process','REVIEW_REQUIRED','old-start','old-update');
    ''')
    return c

# SQLite lacks MySQL's fractional CURRENT_TIMESTAMP syntax; only that spelling changes.
q = lambda x: x.replace('CURRENT_TIMESTAMP(6)', 'CURRENT_TIMESTAMP')
c = db()
assert c.execute(q(delivery), ('ABANDONED','ABANDONED',1,'wrong-owner','old-start',1)).rowcount == 0
assert c.execute(q(delivery), ('ABANDONED','ABANDONED',1,'protection-owner','different-start',1)).rowcount == 0
assert c.execute(q(delivery), ('ABANDONED','ABANDONED',1,'protection-owner','old-start',0)).rowcount == 0
assert c.execute(q(delivery), ('ABANDONED','ABANDONED',1,'protection-owner','old-start',1)).rowcount == 1
assert c.execute(q(marker), ('BTCUSDT',999,'marker-owner','REVIEW_REQUIRED')).rowcount == 0
assert c.execute(q(marker), ('BTCUSDT',1,'wrong-owner','REVIEW_REQUIRED')).rowcount == 0
assert c.execute(q(marker), ('BTCUSDT',1,'marker-owner','REVIEW_REQUIRED')).rowcount == 1
assert c.execute(audit, (1,'BTCUSDT','PRICE_ABANDONED_FLAT','protection-owner','FIX150_SYSTEM','UNKNOWN_RETAINED')).rowcount == 1
c.commit()
assert c.execute('SELECT phase,protection_outcome,protection_owner,last_error,analysis_status FROM shared_market_event_delivery').fetchone() == (4,'ABANDONED','protection-owner','original exception','NOT_APPLICABLE')
assert c.execute(q(delivery), ('ABANDONED','ABANDONED',1,'protection-owner','old-start',1)).rowcount == 0
c = db()
try:
    c.execute('BEGIN')
    c.execute(q(delivery), ('ABANDONED','ABANDONED',1,'protection-owner','old-start',1))
    c.execute(q(marker), ('BTCUSDT',1,'marker-owner','REVIEW_REQUIRED'))
    c.execute(audit, (1,'BTCUSDT','DENIED','protection-owner','FIX150_SYSTEM','UNKNOWN_RETAINED'))
    raise AssertionError('audit must fail')
except sqlite3.IntegrityError:
    c.rollback()
assert c.execute('SELECT phase,protection_completed_at,last_error FROM shared_market_event_delivery').fetchone() == (1,None,'original exception')
assert c.execute('SELECT active_delivery_state FROM shared_market_consumer_state').fetchone() == ('REVIEW_REQUIRED',)
print('PASS: 12 extracted mutation SQL guard/atomicity assertions on SQLite; NOT MySQL/Spring proof.')
