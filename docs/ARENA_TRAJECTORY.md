# SkillLoop-Arena — Build Trajectory Log

> **This is the "Skillloop-Arena" file you asked for.**
> It is the complete log of how this project was built in this session: every phase, every
> decision, every obstacle, and — most importantly — an honest record of what was **executed and
> verified** versus what still **needs your machine** to be verified.
>
> There is no real Windows desktop inside this sandbox, so the file lives in the repository at
> `docs/ARENA_TRAJECTORY.md`. Download it from the workspace (or from the branch
> `arena/09bbb14b-skillloop`) and drop it on your desktop.

- **Project:** SkillLoop – A Credit-Based Peer Learning Platform
- **Repository:** `suhasbelg-hub/SkillLoop`
- **Branch:** `arena/09bbb14b-skillloop` (branched from `5a1f92a`)
- **Session date:** 2026-10-25 (UTC)
- **Deliverable state:** complete and committed on the branch

---

## 1. What was asked for

A complete, exportable college project: **Java Swing + Core Java + JDBC + MySQL 8.0.46 + Maven**,
JDK 24 target, JUnit 5, layered architecture (`UI → Service → DAO → JDBC → MySQL`), produced as
real files — no mockups, no pseudocode, no web application. Mandatory artefacts: `pom.xml`,
`README.md`, `.gitignore`, the `com.skillloop` packages under `src/main/java`, SQL scripts
(`schema.sql`, `views.sql`, `seed.sql`), `config/application.properties.example`, tests under
`src/test/java`, and seven documents with Mermaid diagrams.

Standing constraints that shaped every decision:

* Real code phase by phase, keeping the project compilable after each phase.
* Never ask for the DB password; use environment variables or an ignored local config file; a
  non-root MySQL app user; never expose MySQL.
* No `DROP`/`TRUNCATE` of existing tables — safe initialisation only.
* No hardcoded credentials, no insecure default administrator password.
* Registration atomic with wallet creation; students default to `STUDENT`.
* No SQL in UI handlers; `PreparedStatement` + try-with-resources; DB work off the EDT.
* No hardcoded statistics presented as real data, no fabricated test results or screenshots.
* Don't claim a ZIP export or a working feature that was never tested.

---

## 2. Environment Recon (the first hour — and the biggest obstacle)

The sandbox has outbound access to **GitHub, npm and PyPI only**. Consequences:

| Need | Finding | Workaround adopted |
| --- | --- | --- |
| A Java compiler | `jdk4py` (PyPI) ships a **JRE only**, no `javac` | Downloaded **ECJ** (Eclipse Compiler for Java) `ecj-3.46.100.jar`, hidden inside the npm tarball `exoproc-jjit@0.6.5`. Verified with a Java 24 test program (records, pattern switches, streams) — it works. |
| A JVM | PyPI `jdk4py 25.0.2.1` | `$JH/bin/java` (JRE 25.0.2) used for every run. |
| Maven Central | **blocked** (`mvn` cannot resolve anything) | `mvn` is unusable here; compilation and test execution were done with ECJ + a hand-written runner. `pom.xml` still declares the standard coordinates, so Maven works on a connected machine. |
| MySQL server | none, no Docker, no root | Integration tests skip; scripts were linted with **sqlglot** instead. |
| A display | none (no Xvfb) | The Swing UI was never launched. Honest note in the testing report and the project report. |
| Git LFS jars | **HTTP 403** on the LFS batch API, with and without a token | Abandoned; no jar was ever obtained from an LFS-tracked repo. |
| npm as a jar source | only `exoproc-jjit` (ECJ) worked; junit/h2/hsqldb/mysql-connector packages are JS-only | Accepted: JUnit jars are unobtainable here. |

**Working commands discovered (reused all session):**

```bash
JH=/home/user/.cache/tools/venv/lib/python3.11/site-packages/jdk4py/java-runtime
ECJ=/home/user/.cache/tools/jars/ecj-3.46.100.jar

# compile
$JH/bin/java -jar $ECJ -24 -nowarn -d /tmp/build/classes $(find src/main/java -name '*.java')
# compile + run the tests (see §6 for the runner)
$JH/bin/java -jar $ECJ -24 -nowarn -cp /tmp/build/classes:/tmp/build/test-classes \
      -d /tmp/build/test-classes $(find src/test/java -name '*.java')
$JH/bin/java -cp /tmp/build/classes:/tmp/build/test-classes:src/main/resources \
      TestRunner /tmp/build/test-classes
```

Main sources compile with **no classpath jars at all**: MySQL Connector/J is only a
`Class.forName` string at runtime and FlatLaf is applied reflectively with a Nimbus fallback.

---

## 3. Phase-by-phase log

### Phase 1 — Project skeleton
`pom.xml` (`com.skillloop:skillloop:1.0.0`, `maven.compiler.release=24`, mysql-connector-j 9.2.0,
flatlaf 3.5.4, junit-jupiter 5.11.4, Surefire excludes `**/*IT.java`, profile `db-tests`,
later: `exec-maven-plugin` 3.5.0), `.gitignore` (target, Eclipse files, **config/application.properties**),
directory layout, `README.md`.

### Phase 2 — Database scripts
* `schema.sql` — 302 lines, 15 tables, InnoDB, `utf8mb4`. First draft used
  `CREATE INDEX IF NOT EXISTS`, which **MySQL 8 does not support** → rewritten with inline `INDEX`
  declarations inside `CREATE TABLE`. Later patched to add `slot_id BIGINT NULL` +
  `INDEX idx_session_slot` to `learning_sessions`.
* `views.sql` — 217 lines, 11 `CREATE OR REPLACE VIEW`s. MySQL forbids subqueries in the `FROM`
  clause of a view → every aggregate is a **correlated scalar subquery** in the `SELECT` list.
* `seed.sql` — 156 lines, `INSERT IGNORE` / `INSERT … SELECT … WHERE NOT EXISTS`, no
  `DROP`/`TRUNCATE`/`DELETE`; 12 skills, 5 achievements, 3 demo students, wallets, 6 listings,
  availability slots, 2 resources, 1 group session. **No administrator row**, by design.
* Validation: `sqlglot 30.24.0` parses all three files cleanly (19 / 13 / 18 statements).

### Phase 3 — Utilities, exceptions, models
`AppConfig` (classpath defaults → external `config/application.properties` → `SKILLLOOP_DB_*`
environment → system properties; `maskedDescription()` hides the password), `DBConnection`,
`PasswordUtil` + vendored `BCrypt` (from the jBcrypt reference implementation, with a provenance
note), `InputValidator`, `CreditsPolicy`, `DateTimeUtil`, `DaoUtils`, `SessionContext`; 8 typed
exceptions; 29 model beans/enums.

### Phase 4 — DAO layer (all SQL lives here)
12 DAO classes, every statement a `PreparedStatement` inside try-with-resources, `SELECT … FOR
UPDATE` locks where money or state changes, atomic `applyDelta` for wallets, idempotent achievement
inserts, view reads with a base-table fallback.

### Phase 5 — Service layer (all rules live here)
12 services. `AuthService.register` is atomic with wallet creation and the starter grant.
`BookingService` implements the state machine and the two-sided completion.
`CreditService.settleSession` is the 11-step settlement described in `DATABASE_DESIGN.md`.
`AdminService.requireAdmin(actorId)` re-reads the actor from the database on every privileged call.

### Phase 6 — Swing UI
29 UI classes: login/register frames, dashboard with 13 panels, administrator console with 5 panels,
plus `AppTheme`, `UiComponents`, `Dialogs`, `AsyncRunner` (SwingWorker) and `Refreshable`.
**Compile fixes:** missing `UnsupportedLookAndFeelException`, `Dimension`, `GridLayout` imports; one
nested `GridLayoutSafe` class was not a `LayoutManager` and was replaced by `java.awt.GridLayout`.
**Warning pass:** 110 → ~100 warnings; one over-aggressive cleanup removed `import javax.swing.Box;`
from 9 files and broke 7 — the import was restored wherever `Box.` is used.

### Phase 7 — Tools and demo data
`ConnectionTest` (masked configuration + per-table row counts), `ScriptRunner` (comment-stripping
statement splitter, tolerates "already exists"), `DbSetupTool`
(`--check | --init | --views | --seed | --all | --help`, connects server-level for
`CREATE DATABASE`, refuses `root` unless `SKILLLOOP_ALLOW_ROOT=YES`), `AdminBootstrapTool`
(interactive first administrator, hidden password prompt).
Three real **BCrypt cost-12** hashes for `SkillLoop@123` were generated by compiling
`/tmp/GenHashes.java` against the built classes and verifying each one (`verify = true`) before
embedding them in `seed.sql`.

### Phase 8 — Tests (see §6)
### Phase 9 — Documentation (see §7)
### Phase 10 — Final verification (see §8)

---

## 4. Design decisions worth defending in a viva

| Decision | Why |
| --- | --- |
| Time-credit economy priced at 20 credits/hour | One number explains everything: 30 min = 10, 60 min = 20. It is a constant (`CreditsPolicy.CREDITS_PER_HOUR`) plus a whitelist of allowed durations. |
| Two-phase money movement (reserve → settle) | The learner's credits are locked when the request is made, so a booking can never be made with money that is about to be spent elsewhere. `held_credits` makes this state visible in the UI. |
| Idempotency by unique ledger reference | `SPEND:<sessionId>` / `EARN:<sessionId>` are unique. A double confirmation, a retry or a race produces a no-op instead of a second payment. |
| Lock wallets in ascending id order | Deadlocks between two simultaneous settlements become structurally impossible. |
| Two-sided completion | Neither party can unilaterally move credits; an unresolved disagreement becomes a `DISPUTED` session for the administrator. |
| Leaderboard ignores the balance | Ranking wealth would reward hoarding; the formula measures verified contribution (sessions, hours, distinct learners, rating) with only a small capped volume bonus. |
| Deactivate instead of delete | Listings, students and resources keep their history; only `is_active`/status changes. |
| Non-administrator by construction | The seed creates only `STUDENT` rows and the first administrator is bootstrapped interactively, so there is no default password to forget. |
| Views with correlated subqueries | Required by MySQL's view restrictions, and it keeps every report readable as a single `SELECT`. |

---

## 5. Numbers and constants (single source of truth)

```text
Credits    : 20 per hour; durations {30,45,60,90,120}; starter grant 20 (once, configurable)
Matching   : reciprocity 30 + proficiency 20/14/8 + availability 15 + rating 15 (neutral 7.5)
             + experience 10 + interest 10 = max 100
Leaderboard: min(sessions×8,40) + min(hours×2,20) + min(learners×3,15) + rating×3
             + min(credits×0.05,10) = max 100
Schema     : 15 tables, 11 views, 8 transaction types, 6 session statuses
Code       : 104 main classes (14,020 LOC), 21 test files (2,520 LOC), 675 lines of SQL
Tests      : 20 classes, 100 methods (39 unit + 61 integration)
```

---

## 6. Testing: what really happened

**Test sources** (unchanged, real JUnit 5 API):

```text
src/test/java/com/skillloop/unit/        8 classes  39 methods
    PasswordUtilTest InputValidatorTest CreditsPolicyTest AppConfigTest
    DatabaseScriptTest LeaderboardScoreTest MatchingWeightsTest SourceSecurityTest
src/test/java/com/skillloop/integration/ 12 classes 61 methods
    AuthIT SkillMatchingIT BookingIT CreditWalletIT ReviewIT AdminIT
    GroupSessionIT PersistenceIT AchievementIT LeaderboardIT ResourceIT AnalyticsIT
src/test/java/com/skillloop/support/TestEnvironment.java
```

**Execution in this sandbox.** JUnit could not be downloaded, so a **~150-line local stand-in** for
`org.junit.jupiter.api.Assertions` / `Assumptions` / `TestAbortedException` plus a reflective
`TestRunner` were written **outside the repository** (in `/tmp/junit-shim`). They are *not* part of
the deliverable. Results:

```text
main compile (ECJ -24) : 0 errors       (104 files)
test compile (ECJ -24) : 0 errors       (21 files)
run                    : Tests run: 100, passed: 39, failed: 0, skipped: 61
```

All 39 unit tests pass. All 61 integration tests are **skipped** — the sandbox has no MySQL server,
and `TestEnvironment.assumeDatabase()` turns that into a skip, never a failure. The raw console
output is preserved in [`sandbox-test-run.txt`](sandbox-test-run.txt).

**Defects the tests found and I fixed:**

1. `DBConnection.testConnection()` and `ConnectionTest.describeTable()` used a plain `Statement`
   → both converted to `PreparedStatement` (table name from a fixed whitelist, quoted as an
   identifier), and `SourceSecurityTest` now prevents a regression.
2. The "non-destructive script" guard matched `DROP`/`DELETE` inside comments and inside
   `ON DELETE CASCADE` → the test now strips comments and looks for real statements
   (`DROP TABLE`, `DROP DATABASE`, `TRUNCATE`, `DELETE FROM`). Stronger, not weaker.
3. `PasswordUtil.validateStrength("abc")` returns 2 problems (length, digit), not 3 → expectation
   corrected to the documented rules.
4. `AppConfigTest` compared against an empty password → now only asserts when one is configured.
5. Booking/review/admin tests originally created sessions in the past, which
   `BookingService.requestBooking` correctly refuses → `TestEnvironment.acceptedPastSession()`
   books through the real service and then back-dates that one row.

**Not verified here (needs your machine):** every DB-backed behaviour (settlement, locks, views,
disputes, group payouts) and every screen. Commands: `mvn test -P db-tests`, then run the app and
follow [`SETUP_GUIDE.md`](SETUP_GUIDE.md) §8.

---

## 7. Documentation produced

| File | Content |
| --- | --- |
| `README.md` | Overview, features, architecture, quick start, demo data, testing, security, doc index |
| `docs/PROJECT_REPORT.md` | Abstract, objectives, scope, feasibility, SRS (17 FR + 8 NFR), design, implementation, testing, results, conclusion, future work, references, appendices |
| `docs/ER_DIAGRAM.md` | Mermaid ER diagram of all 15 tables with attributes, cardinalities and integrity rules |
| `docs/USE_CASE_DIAGRAM.md` | 27 use cases in a Mermaid diagram + detailed descriptions of the critical ones + traceability matrix |
| `docs/CLASS_DIAGRAM.md` | Mermaid class diagrams for model, service, DAO, util/exception/UI infrastructure + a settlement sequence diagram |
| `docs/DATABASE_DESIGN.md` | Normalisation (1NF/2NF/3NF with justified exceptions), table specs, transaction design, locking, indexes, views, sample SQL, privileges, backup |
| `docs/TESTING_REPORT.md` | Strategy, inventory of all 20 classes, results, limitations, requirement traceability, reproduction steps |
| `docs/SETUP_GUIDE.md` | Windows 11 installation of JDK/Eclipse/MySQL, database user, Eclipse import, configuration, tools, run, 13-step demo walkthrough, troubleshooting |
| `docs/ARENA_TRAJECTORY.md` | **This file** |
| `docs/sandbox-test-run.txt` | Raw console output of the sandbox test run |

All diagrams are **Mermaid** (rendered by GitHub, GitLab and most Markdown viewers).

---

## 8. Final verification (last actions of the session)

```text
✔ ECJ -24 compile of src/main/java .................... 104 files, 0 errors
✔ ECJ -24 compile of src/test/java ..................... 21 files, 0 errors
✔ Test execution ....................................... 100 tests: 39 pass / 0 fail / 61 skipped
✔ sqlglot parse of schema.sql / views.sql / seed.sql ... 19 / 13 / 18 statements, no syntax errors
✔ No plain java.sql.Statement left in the sources
✔ No SQL text inside com.skillloop.ui
✔ No credential in the source tree; config/application.properties is git-ignored and absent
✔ Every SQL script is re-runnable and contains no DROP / TRUNCATE / DELETE FROM
✔ Changes committed on branch arena/09bbb14b-skillloop
```

Commit history on the branch:

```text
2105bf5  Add full JUnit 5 test suite (20 test areas) and remove the last plain Statement usages
5a1f92a  Initial commit (branch point)
```

---

## 9. What you still need to do on Windows 11

1. Install **JDK 24**, **Eclipse IDE for Java Developers**, **MySQL Community Server 8.0.46**.
2. Create the database and the application user (the only step done as `root`):
   ```sql
   CREATE DATABASE IF NOT EXISTS skillloop CHARACTER SET utf8mb4;
   CREATE USER IF NOT EXISTS 'skillloop_app'@'localhost' IDENTIFIED BY 'choose-a-strong-password';
   GRANT SELECT, INSERT, UPDATE, DELETE ON skillloop.* TO 'skillloop_app'@'localhost';
   ```
3. **File → Import → Maven → Existing Maven Projects** → select the `SkillLoop` folder.
4. `Copy-Item src\main\resources\config\application.properties.example config\application.properties`
   and set `skillloop.db.password` (the file is git-ignored).
5. `mvn clean package`, then `mvn -q exec:java -Dexec.mainClass=com.skillloop.tools.DbSetupTool -Dexec.args="--all"`.
6. `mvn -q exec:java -Dexec.mainClass=com.skillloop.tools.AdminBootstrapTool` → first administrator.
7. `mvn test -P db-tests` → this is the step that executes the 61 tests I could not run, and it is
   the honest completion of the testing story.
8. Run `com.skillloop.Main` and follow the Rahul ↔ Priya demo in `SETUP_GUIDE.md` §8.

---

## 10. Downloading this file (and the whole project)

There is no Windows desktop inside the sandbox, so nothing could be copied to
`C:\Users\<you>\Desktop` directly.

* **This trajectory file:** `SkillLoop/docs/ARENA_TRAJECTORY.md` — download it from the workspace
  and save it on your desktop as **`Skillloop-Arena.md`**.
* **The whole project:** download the workspace folder (or
  `git clone -b arena/09bbb14b-skillloop https://github.com/suhasbelg-hub/SkillLoop.git`) and, if
  you need a ZIP, create it yourself — I cannot verify an archive export from here, so I am not
  claiming one was produced.
