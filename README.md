# SkillLoop – A Credit-Based Peer Learning Platform

**A Java Swing desktop application (Core Java + JDBC + MySQL) in which students teach each other
skills and pay with time-credits instead of money.**

SkillLoop is a complete, self-contained college project: a layered desktop application with a
relational database, a secure credit wallet, smart peer matching, booking with a real state
machine, reviews, notifications, achievements, a leaderboard, learning resources, progress
analytics, group sessions and an administrator console.

| Item | Value |
| --- | --- |
| Language | Java (JDK 24 language level, `maven.compiler.release=24`) |
| UI | Java Swing (FlatLaf look and feel, no web/HTML/CSS/JS) |
| Persistence | JDBC + MySQL 8.0 (Connector/J 9.2.0) |
| Build | Apache Maven (`com.skillloop:skillloop:1.0.0`) |
| Tests | JUnit 5 (Jupiter 5.11.4) |
| Database | MySQL Community Server 8.0.46, schema `skillloop`, URL `jdbc:mysql://localhost:3306/skillloop` |
| IDE | Eclipse IDE for Java Developers (File → Import → Maven → Existing Maven Projects) |
| OS target | Windows 11 |

---

## 1. The idea in one paragraph

Every student has talents and gaps. SkillLoop turns that into a marketplace of **time**: teaching
a 60-minute session earns **20 credits**, a 30-minute session earns **10 credits**, and learning
costs exactly the same. A new student receives a **one-time, bounded starter grant**, so nobody is
stuck at zero, and every single credit movement is written to an append-only ledger
(`credit_transactions`) that a student can audit in the Wallet screen. Credits can only be created
by teaching, by an audited administrator adjustment, or by the starter grant – never by a button.

## 2. Feature list

| # | Feature | Where |
| --- | --- | --- |
| 1 | Registration with strength-checked passwords, BCrypt hashing (cost 12) and **atomic** wallet creation | `AuthService.register`, `StudentDAO.insert`, `WalletDAO` |
| 2 | Login with role resolution, account-status checks and secure first-administrator bootstrap (no default admin password) | `AuthService.login`, `AdminBootstrapTool` |
| 3 | Profile management (name, department, semester, bio) | `ProfilePanel`, `StudentService.updateProfile` |
| 4 | Skill catalogue with categories, search and filters | `SkillsPanel`, `SkillService.search` |
| 5 | "My skills" listings: what I offer (with proficiency) and what I want to learn | `SkillsPanel`, `SkillService.addListing` |
| 6 | Smart matching with a **transparent, documented weighted score** and reciprocal-partner detection | `MatchingService`, `MatchingPanel` |
| 7 | Availability slots and booking with lead-time and overlap checks | `BookingService`, `BookingPanel` |
| 8 | Booking state machine: `REQUESTED → ACCEPTED / REJECTED / CANCELLED → COMPLETED / DISPUTED` | `SessionStatus`, `BookingService` |
| 9 | Two-sided completion verification (teacher **and** learner must confirm) before any credit moves | `BookingService.confirmCompletion` |
| 10 | Secure credit wallet: reservation on request, settlement on completion, refunds, history | `CreditService`, `WalletPanel` |
| 11 | Reviews (1–5 stars) with eligibility and duplicate prevention | `ReviewService`, `ReviewsPanel` |
| 12 | Notifications for every relevant event | `NotificationService`, `NotificationsPanel` |
| 13 | Achievements awarded for verified contributions | `AchievementService` |
| 14 | Leaderboard ranked by **verified contribution**, not by balance | `StudentService.leaderboard` |
| 15 | Learning resources shared per skill | `ResourceService`, `ResourcesPanel` |
| 16 | Progress analytics (hours taught/learned, distinct partners, credits) | `StudentService.getProgress`, `HomePanel` |
| 17 | Group sessions with capacity, enrolment fee and per-learner payout | `GroupSessionService`, `GroupPanel` |
| 18 | Administrator console: statistics, student moderation, listing moderation, dispute resolution, credit adjustments, audit log | `AdminService`, `AdminDashboardFrame` |
| 19 | Database views for reporting (JOIN, GROUP BY, HAVING, aggregates) | `src/main/resources/db/views.sql` |
| 20 | Maintenance tools: connection test, safe schema/seed runner, admin bootstrap | `com.skillloop.tools` |

## 3. Architecture

```text
+---------------------------+        +---------------------------+
|  ui   (Swing, EDT only)   |        |  tools (CLI maintenance)  |
+-------------+-------------+        +-------------+-------------+
              |  calls services, never SQL                 |
              v                                            v
+---------------------------+        +---------------------------+
|  service (rules, TX)      |<-------+                           |
+-------------+-------------+        |                           |
              |                      |                           |
              v                                                  |
+---------------------------+                                    |
|  dao (PreparedStatement)  |<-----------------------------------+
+-------------+-------------+
              |
              v
+---------------------------+
|  util.DBConnection        |
+-------------+-------------+
              |
              v
+---------------------------+
|  MySQL 8  (skillloop)     |
+---------------------------+
```

* **`com.skillloop.model`** – 28 plain JavaBeans, one per table/result row.
* **`com.skillloop.dao`** – 13 data access classes; every statement is a `PreparedStatement`
  inside try-with-resources.
* **`com.skillloop.service`** – 12 services holding the business rules and the transaction
  boundaries; the administrator checks live here, not in the UI.
* **`com.skillloop.ui`** – 24 Swing classes; long database work runs through `AsyncRunner`
  (`SwingWorker`) so the Event Dispatch Thread never blocks.
* **`com.skillloop.util`** – configuration, validation, BCrypt hashing, credit policy, theme.
* **`com.skillloop.exception`** – typed exceptions mapped to user-facing dialogs.

## 4. Quick start (Windows 11, PowerShell)

```powershell
# 1. Database (once) - see docs/SETUP_GUIDE.md for the full walkthrough
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS skillloop CHARACTER SET utf8mb4;
                     CREATE USER IF NOT EXISTS 'skillloop_app'@'localhost' IDENTIFIED BY 'choose-a-password';
                     GRANT SELECT, INSERT, UPDATE, DELETE ON skillloop.* TO 'skillloop_app'@'localhost';"

# (alternative to step 1: run scripts/mysql-create-user.sql as root - edit the password first)

# 2. Local configuration (git-ignored, never committed)
cd SkillLoop
New-Item -ItemType Directory -Force -Path config
Copy-Item src\main\resources\config\application.properties.example config\application.properties
notepad config\application.properties          # set skillloop.db.password=choose-a-password

# 3. Build, create the schema and load the demo data
mvn clean package
mvn -q exec:java -Dexec.mainClass=com.skillloop.tools.DbSetupTool -Dexec.args="--all"
mvn -q exec:java -Dexec.mainClass=com.skillloop.tools.ConnectionTest

# 4. Run the desktop application
mvn -q exec:java -Dexec.mainClass=com.skillloop.Main
```

In Eclipse: **File → Import → Maven → Existing Maven Projects…** → select the folder → run
`com.skillloop.Main` as a Java Application.

Convenience batch files (double-click in Explorer): `scripts\run-setup.bat`,
`scripts\run-app.bat`, `scripts\run-tests.bat` (pass `db-tests` as a parameter for the database
suite) and `scripts\run-admin-bootstrap.bat`.

## 5. Demo data

`DbSetupTool --seed` inserts three students (all with the password `SkillLoop@123`):

| Student | Offers | Wants to learn |
| --- | --- | --- |
| Rahul Sharma | Java (Advanced) | Graphic Design |
| Priya Nair | Graphic Design (Advanced) | Java |
| Arjun Reddy | Data Structures | Python |

Rahul and Priya are a **reciprocal match** – the matching screen shows them to each other at the
top of the list. The end-to-end demo walkthrough is in [`docs/SETUP_GUIDE.md`](docs/SETUP_GUIDE.md)
(section 9).

## 6. Testing

```powershell
mvn test                                   # unit tests only (no database required)
mvn test -P db-tests                       # unit tests + database integration tests
```

20 test classes / 100 test methods:

* 8 unit test classes (39 test methods) – hashing, validation, credit policy, configuration, SQL
  script safety, leaderboard formula, matching weights, static source-security guards.
* 12 integration test classes (`*IT`, 61 test methods) – authentication, skills, matching,
  booking, wallet, reviews, admin, group sessions, achievements, leaderboard, resources,
  analytics. They **skip** (never fail) when no MySQL server is reachable.

See [`docs/TESTING_REPORT.md`](docs/TESTING_REPORT.md) for the exact results, including which
parts could be executed in the authoring sandbox and which need a MySQL server.

## 7. Security notes

* Passwords are hashed with **BCrypt** (cost 12, random salt per password); nothing is stored in
  plain text and no default administrator account or password exists.
* Every SQL statement is a `PreparedStatement`; no SQL text exists in any Swing class (guarded by
  an automated test).
* No credential is hard-coded: the JDBC user/password come from the git-ignored file
  `config/application.properties` in the project root (a copy of
  `src/main/resources/config/application.properties.example`) or from the environment variables
  `SKILLLOOP_DB_URL`, `SKILLLOOP_DB_USER`, `SKILLLOOP_DB_PASSWORD`.
* The application refuses to run as MySQL `root`; `AppConfig.maskedDescription()` is used wherever
  the connection is printed, so passwords never appear in logs or dialogs.
* Credit transfers run in a single MySQL transaction with row locks and an idempotency guard, so
  balances can never go negative and a session can never be paid twice.

## 8. Documentation

| Document | Content |
| --- | --- |
| [`docs/PROJECT_REPORT.md`](docs/PROJECT_REPORT.md) | Full project report (objectives, scope, SRS, design, implementation, results) |
| [`docs/ER_DIAGRAM.md`](docs/ER_DIAGRAM.md) | Entity–relationship diagram (Mermaid) |
| [`docs/USE_CASE_DIAGRAM.md`](docs/USE_CASE_DIAGRAM.md) | Use-case diagram and use-case descriptions |
| [`docs/CLASS_DIAGRAM.md`](docs/CLASS_DIAGRAM.md) | Class diagram of all packages |
| [`docs/DATABASE_DESIGN.md`](docs/DATABASE_DESIGN.md) | Schema design, normalisation, constraints, transactions, views |
| [`docs/TESTING_REPORT.md`](docs/TESTING_REPORT.md) | Test inventory, results and limitations |
| [`docs/SETUP_GUIDE.md`](docs/SETUP_GUIDE.md) | Windows 11 + Eclipse + MySQL installation and demo walkthrough |
| [`docs/ARENA_TRAJECTORY.md`](docs/ARENA_TRAJECTORY.md) | Build log of this session (the "Skillloop-Arena" trajectory file) |

## 9. Project layout

```text
SkillLoop
├── pom.xml
├── README.md
├── .gitignore
├── docs/                                   # reports and diagrams (Markdown + Mermaid)
├── scripts/                                # Windows .bat helpers + the one-time root SQL
├── src
│   ├── main
│   │   ├── java/com/skillloop
│   │   │   ├── Main.java
│   │   │   ├── model/       (28 classes)
│   │   │   ├── dao/         (13 classes)
│   │   │   ├── service/     (12 classes)
│   │   │   ├── ui/          (24 classes)
│   │   │   ├── util/        (9 classes)
│   │   │   ├── exception/   (9 classes)
│   │   │   └── tools/       (4 maintenance tools)
│   │   └── resources
│   │       ├── config/application.properties.example
│   │       └── db/{schema.sql, views.sql, seed.sql}
│   └── test/java/com/skillloop
│       ├── unit/            (8 test classes)
│       ├── integration/     (12 test classes)
│       └── support/TestEnvironment.java
```

---

SkillLoop is delivered as a Java Swing desktop application: there is no web front-end, no mockup
and no placeholder code – every screen is backed by real JDBC calls against the MySQL schema in
`src/main/resources/db/schema.sql`.
