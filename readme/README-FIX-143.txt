FIX-143: Allowed BUY — not executed

Merge the included src folder into your existing crypto-ai-trader project.
The upload contained src only; keep your existing pom.xml and project settings.
Run mvn -B -ntp clean verify on Java 21, restart, then refresh the browser.
Select Type: Allowed BUY — not executed in Trade Activity or Dashboard.
Choose a time period covering the signal.

Validation here: 3 JavaScript regressions passed; JavaScript syntax checks passed.
Java service regression included but not executed here (Java 17, no Maven/pom).
The JPQL repository query still requires validation with your build and local MySQL.
No database migration or trading behavior change. See src/main/resources/md/FIX-143.md.

Includes FIX-144 / V100: paper_position reason columns widened to MEDIUMTEXT.
Flyway applies this on restart even if LIVE validation subsequently rejects unresolved work.
Do not edit earlier migration files or clear review rows. See md/FIX-144.md.
