# SkillLoop – Testing Report

**Project:** SkillLoop – A Credit-Based Peer Learning Platform
**Stack:** Java 24 (Swing) · JDBC · MySQL 8.0.46 · Maven · JUnit 5.11.4
**Date of this report:** 2026-10-25

---

## 0. Honest summary (read this first)

| Question | Answer |
| --- | --- |
| How many tests exist? | **20 test classes, 100 test methods** (8 unit classes / 39 methods, 12 integration classes / 61 methods). |
| Which tests were actually **executed**? | All **39 unit tests** were executed and **all 39 passed**. |
| Which tests were **not** executed? | All **61 database integration tests**. They compiled, but the authoring sandbox contains **no MySQL server**, so they skipped (JUnit's assumption mechanism: skipped, never failed). |
| Which test runner produced the passing result? | Not Maven/JUnit itself: Maven Central is unreachable from the sandbox, so `junit-jupiter` could not be downloaded. The sources were compiled with the Eclipse Java Compiler (ECJ 3.46.100, `-source/-target 24`) and executed on a JDK 25 runtime with a **minimal local stand-in** for `org.junit.jupiter.api.Assertions` / `Assumptions` (≈150 lines, sandbox-only, not part of the deliverable). The test sources are unchanged and use the real JUnit 5 API, so they compile and run under Maven/Surefire on any machine with dependency access. |
| Was anything fabricated? | No. No screenshot, no "green" result for a test that never ran, no invented database numbers. The raw console output of the sandbox run is stored verbatim in [`sandbox-test-run.txt`](sandbox-test-run.txt). |
| What must be re-run on the target machine? | `mvn test` (unit) and `mvn test -P db-tests` (unit + database). See section 6. |

---

## 1. Test strategy

### 1.1 Separation of concerns

| Kind | Package / naming | Needs MySQL? | Runs in `mvn test`? | Purpose |
| --- | --- | --- | --- | --- |
| Unit tests | `com.skillloop.unit.*Test` | No | Yes | Pure logic: hashing, validation, credit policy, configuration, ranking formulas, static source guards, SQL script safety. |
| Integration tests | `com.skillloop.integration.*IT` | Yes | No (excluded by Surefire; enabled by the `db-tests` profile) | Real JDBC behaviour against a real MySQL schema: transactions, locks, state machine, idempotency, constraints. |

The split is enforced in `pom.xml`: Surefire excludes `**/*IT.java`, and the profile `db-tests`
includes them:

```xml
<configuration><excludes><exclude>**/*IT.java</exclude></excludes></configuration>
...
<profile><id>db-tests</id> ... <includes><include>**/*Test.java</include><include>**/*IT.java</include></includes> ...</profile>
```

### 1.2 Skip, never fail

Every `*IT` class starts with

```java
@BeforeAll static void requireDatabase() { TestEnvironment.assumeDatabase(); }
```

`TestEnvironment.assumeDatabase()` calls `Assumptions.assumeTrue(isDatabaseAvailable(), …)`, so
the tests report as **skipped** when no server is reachable. A laptop without MySQL can therefore
run `mvn test` successfully, while a machine with MySQL runs the full suite.

### 1.3 Never destructive

* `TestEnvironment.deleteStudent(long)` is the only clean-up path. It deletes **only** the
  students created by the test (identified by id) and the rows that belong to them
  (`learning_sessions` of that student, their group sessions, audit rows, nulling
  `resolved_by` / `created_by` references). No `TRUNCATE`, no `DROP`, no global `DELETE`.
* Test accounts use the domain `@example.test` with a UUID, so they can never collide with real
  accounts or with the demo seed data.
* Sessions that must be in the past (completion, review, settlement) are created through the
  ordinary service (which correctly refuses past appointments) and then **back-dated** by a
  single `UPDATE` on that one row (`TestEnvironment.acceptedPastSession`). Everything else –
  credit reservation, notification, state machine – is the unmodified production code.
* The scripts themselves are covered by `DatabaseScriptTest`: `schema.sql`, `views.sql` and
  `seed.sql` must not contain `DROP TABLE`, `DROP DATABASE`, `TRUNCATE` or `DELETE FROM`.

---

## 2. Test inventory

### 2.1 Unit tests – 8 classes, 39 methods – **all executed, all passing**

| # | Test class | Methods | Area under test | What is asserted |
| --- | --- | --- | --- | --- |
| 1 | `PasswordUtilTest` | 4 | Password hashing | BCrypt `hash`/`verify` round-trip; every hash uses a different salt (identical passwords hash differently); malformed/empty stored hashes never throw; weak passwords are reported with a reason (length + digit). |
| 2 | `InputValidatorTest` | 8 | Input validation | Email format and normalisation, name/department/semester rules, rating 1–5, allowed durations (30/45/60/90/120), URL validation, `<`/`>` rejection, and `registrationProblems` collecting **all** problems at once. |
| 3 | `CreditsPolicyTest` | 5 | Credit policy | 30 min = 10 credits, 60 min = 20 credits, all allowed durations are proportional (20 credits/hour), unsupported durations rejected, starter grant is bounded/one-time/configurable, ledger references are unique per session and purpose (`HOLD:`, `SPEND:`, `EARN:`, `REFUND:`, `STARTER:`). |
| 4 | `AppConfigTest` | 4 | Configuration | Default URL/driver point to a local MySQL instance; default user is **not** `root`; system properties override credit settings; `maskedDescription()` never leaks the password. |
| 5 | `DatabaseScriptTest` | 5 | SQL scripts | Non-destructive `schema.sql` and `seed.sql` (`DROP TABLE`/`DROP DATABASE`/`TRUNCATE`/`DELETE FROM` forbidden, cascading FKs required); all 15 tables use `CREATE TABLE IF NOT EXISTS`; integrity rules present (PK/FK/UNIQUE/CHECK); views use JOIN/GROUP BY/HAVING/aggregates; seed inserts only when missing and creates no administrator. |
| 6 | `LeaderboardScoreTest` | 4 | Leaderboard formula | The documented weights are reproduced exactly (61.0 for 4 sessions / 3 h / 2 learners / 4.0 rating / 100 credits), the score is capped at 100, a contributor outranks an empty profile **even with a huge balance**. |
| 7 | `MatchingWeightsTest` | 3 | Matching model | Weights sum to 100 (reciprocal 30 + proficiency ≤ 20 + availability 15 + rating 15 + experience 10 + interest 10); a teacher with no rating gets the neutral 7.5 points; reciprocity is the strongest single signal. |
| 8 | `SourceSecurityTest` | 6 | Static security guards | No `createStatement(` anywhere in `src/main/java`; every DAO class uses `prepareStatement(` **and** try-with-resources; no Swing class contains SQL; no JDBC URL with an inline password; the example configuration contains only placeholders; the local `application.properties` is git-ignored and absent. |

### 2.2 Integration tests – 12 classes, 61 methods – **compiled, skipped without MySQL**

| # | Test class | Methods | Area under test | Key scenarios |
| --- | --- | --- | --- | --- |
| 9 | `AuthIT` | 8 | Registration & login | Atomic registration + wallet creation, duplicate email rejection, weak password rejection, role defaults to `STUDENT`, login with wrong password, login of a suspended account, profile/password change flows. |
| 10 | `SkillMatchingIT` | 7 | Skills & matching | Catalogue CRUD, duplicate skill rejection, listing add/update/deactivate, search + category filter, reciprocal partners are ranked first, teachers filtered by minimum proficiency, matching score ordering. |
| 11 | `BookingIT` | 9 | Booking rules | No self-booking, lead-time rule (≥ 5 min), overlapping session rejected, unknown teacher/skill rejected, only the teacher accepts/rejects, no double accept, rejection and cancellation release the reservation, completion requires **both** confirmations and only after the start time. |
| 12 | `CreditWalletIT` | 7 | Credit wallet | Reservation on request, funds check, settlement transfers exactly the policy cost, no negative balance, idempotency (a second settlement does not pay twice), refund releases the hold, ledger history is complete and ordered. |
| 13 | `ReviewIT` | 4 | Reviews | Only participants of a completed session may review, rating must be 1–5, both sides may review once, duplicate review rejected, average rating and count are aggregated, reviewable list empties after reviewing. |
| 14 | `AdminIT` | 8 | Administrator | Service-layer authorization (a normal student cannot read stats/audit/adjust/disputes), live statistics, account suspend → login blocked → reactivate, audit entries written, dispute resolved in favour of the teacher pays the teacher, in favour of the learner refunds the reservation, credit adjustments are limited + audited, listing moderation deactivates without deleting, promotion audited. |
| 15 | `GroupSessionIT` | 3 | Group sessions | Only for skills the host offers, capacity enforced, host cannot join own group, enrolment charges the learner, completion pays the teacher per participant. |
| 16 | `PersistenceIT` | 4 | Persistence | Profile updates are readable after a reconnect, availability slots persist and future slots are bookable, booking notifies the teacher and the notification persists, sessions are visible to both sides in the correct role. |
| 17 | `AchievementIT` | 2 | Achievements | Badge catalogue is present; the first verified teaching session awards `FIRST_TEACHING_SESSION`; a second settled session does not duplicate the badge. |
| 18 | `LeaderboardIT` | 3 | Leaderboard over real data | A teacher with verified sessions ranks above a student who only holds credits; the board is sorted by score, ranks are 1..n and the limit is respected; progress analytics (sessions/hours) match the created session. |
| 19 | `ResourceIT` | 3 | Learning resources | Share → list → filter by skill → `mine`; validation rejects empty titles and malformed URLs; only the author may delete a resource. |
| 20 | `AnalyticsIT` | 3 | Analytics | Dashboard counters follow the rows the test creates (students, skills, transferred credits), monthly volume report is well-formed, most-active-teachers report is ordered and capped. |

Shared helper: `com.skillloop.support.TestEnvironment` (database probe, unique addresses,
back-dated session fixture, targeted clean-up).

---

## 3. Results obtained in the authoring sandbox

Command sequence (see [`sandbox-test-run.txt`](sandbox-test-run.txt) for the raw output):

```text
ECJ 3.46.100  -24  src/main/java/**/*.java        → 0 errors
ECJ 3.46.100  -24  src/test/java/**/*.java        → 0 errors
java … TestRunner                                  → Tests run: 100, passed: 39, failed: 0, skipped: 61
```

| Suite | Tests | Passed | Failed | Skipped |
| --- | --- | --- | --- | --- |
| Unit (`com.skillloop.unit`) | 39 | **39** | 0 | 0 |
| Integration (`com.skillloop.integration`) | 61 | 0 | 0 | **61** (no MySQL server) |
| **Total** | **100** | **39** | **0** | **61** |

Per class (sandbox run):

```text
AdminIT          8 skipped      AppConfigTest         4 passed
AuthIT           8 skipped      CreditsPolicyTest     5 passed
BookingIT        9 skipped      DatabaseScriptTest    5 passed
CreditWalletIT   7 skipped      InputValidatorTest    8 passed
GroupSessionIT   3 skipped      LeaderboardScoreTest  4 passed
PersistenceIT    4 skipped      MatchingWeightsTest   3 passed
ReviewIT         4 skipped      PasswordUtilTest      4 passed
SkillMatchingIT  7 skipped      SourceSecurityTest    6 passed
AchievementIT    2 skipped
LeaderboardIT    3 skipped
ResourceIT       3 skipped
AnalyticsIT      3 skipped
```

### Defects found and fixed while writing the tests

1. `DBConnection.testConnection()` and `ConnectionTest.describeTable()` used a plain
   `java.sql.Statement`. Both were converted to `PreparedStatement` (the table name in
   `ConnectionTest` is taken from a fixed whitelist and quoted as an identifier, because an
   identifier cannot be bound as a JDBC parameter). The `SourceSecurityTest` guard now prevents a
   regression.
2. The non-destructive-script guard initially matched the words `DROP`/`DELETE` inside comments
   and inside `ON DELETE CASCADE` clauses. The test was corrected to strip comments and to look
   for real statements (`DROP TABLE`, `DROP DATABASE`, `TRUNCATE`, `DELETE FROM`), which is a
   stronger check, not a weaker one.
3. `PasswordUtil.validateStrength("abc")` returns 2 problems (length, digit), not 3; the
   expectation was corrected to the documented rules.
4. `AppConfigTest` asserted that the masked description does not contain the password; with an
   empty (unset) password the assertion was vacuous and failed. It now only asserts when a
   password is configured.

---

## 4. Known limitations

1. **No MySQL server in the authoring environment.** The 61 integration tests and every runtime
   behaviour that needs a live connection (settlement transaction, row locks, view queries, UI
   screens) could not be executed here. They must be run on the target machine; section 6 gives
   the commands.
2. **No Maven Central access in the authoring environment.** `mysql-connector-j`, `flatlaf` and
   `junit-jupiter` could not be downloaded, so `mvn test` itself was never run here. Compilation
   was verified with ECJ; the dependency coordinates in `pom.xml` are standard Maven Central
   coordinates and resolve on any connected machine.
3. **No GUI automation.** Swing screens are not covered by automated tests (no headless display
   and no AssertJ-Swing dependency). Correctness of the UI relies on: all logic living in the
   service layer, DB calls running through `AsyncRunner` (`SwingWorker`) off the EDT, and the
   static guard that no UI class may contain SQL. Manual test scripts for every screen are in
   [`SETUP_GUIDE.md`](SETUP_GUIDE.md) §9.
4. **Concurrency.** `CreditWalletIT` contains a concurrency/idempotency scenario, but it was not
   executed; concurrent settlement is additionally protected in the database by
   `SELECT … FOR UPDATE` row locks taken in a deterministic order (lowest wallet id first) and by
   the unique ledger references.

---

## 5. How the tests map to the requirements

| Requirement | Covered by |
| --- | --- |
| Atomic registration + wallet | `AuthIT`, `AdminIT` |
| No insecure default admin | `DatabaseScriptTest` (seed has no `ADMIN` row), `AdminIT` (authorization enforced in the service layer) |
| Skill CRUD / search / filter | `SkillMatchingIT`, `ResourceIT` |
| Transparent weighted matching | `MatchingWeightsTest`, `SkillMatchingIT` |
| Booking statuses & rules | `BookingIT`, `AdminIT` |
| Two-sided completion | `BookingIT.completionRequiresBothSides`, `ReviewIT` |
| Secure credit wallet, no negatives, idempotent | `CreditWalletIT`, `CreditsPolicyTest`, `AdminIT` |
| Reviews with eligibility + duplicate prevention | `ReviewIT` |
| Admin authorization | `AdminIT.authorizationIsEnforcedInTheServiceLayer` |
| Achievements / leaderboard / notifications / resources / analytics / groups | `AchievementIT`, `LeaderboardIT`, `PersistenceIT`, `ResourceIT`, `AnalyticsIT`, `GroupSessionIT` |
| PreparedStatement everywhere, no SQL in UI | `SourceSecurityTest` |
| Non-destructive scripts | `DatabaseScriptTest` |
| EDT correctness (DB off the UI thread) | Static review + `AsyncRunner`; see limitation 3 |

---

## 6. Reproducing the tests on Windows 11

```powershell
cd C:\path\to\SkillLoop

# a) unit tests only - works without a database
mvn clean test

# b) full suite against MySQL (create the schema first, see SETUP_GUIDE.md)
mvn -q exec:java -Dexec.mainClass=com.skillloop.tools.DbSetupTool -Dexec.args="--init"
mvn test -P db-tests
```

Expected on a correctly configured machine: `Tests run: 100, Failures: 0, Errors: 0,
Skipped: 0`. If MySQL is not running, Surefire reports `Skipped: 61` and the build still succeeds.
The report files land in `target/surefire-reports/`.

---

## 7. Conclusion

The project ships a complete, non-fabricated test suite that separates pure-logic tests from
database tests, refuses to touch data it did not create, and can be executed on any machine with
Maven and (optionally) MySQL. **39 of 100 tests were executed in the authoring environment and all
passed; the remaining 61 require a MySQL server and were skipped, not faked.** The suite is
designed so that the first `mvn test -P db-tests` on the target machine fills that gap without any
code change.
