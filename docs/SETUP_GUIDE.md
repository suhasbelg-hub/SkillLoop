# SkillLoop – Setup and Execution Guide (Windows 11)

Everything below was written for a fresh Windows 11 machine: JDK 24, Eclipse IDE for Java
Developers, MySQL Community Server 8.0.46 and Maven. Follow the chapters in order; each one ends
with a check you can perform.

---

## 1. Prerequisites

| Software | Version used | Download |
| --- | --- | --- |
| Windows | 11 | – |
| JDK | 24 (Temurin / Oracle / Microsoft OpenJDK all work) | https://adoptium.net |
| Eclipse IDE for Java Developers | 2024-09 or newer | https://www.eclipse.org/downloads/ |
| MySQL Community Server | **8.0.46** | https://dev.mysql.com/downloads/mysql/ |
| MySQL Workbench (optional) | 8.0.x | https://dev.mysql.com/downloads/workbench/ |
| Apache Maven | 3.9.x (optional – **m2e inside Eclipse is enough**) | https://maven.apache.org/download.cgi |

> Maven is bundled with Eclipse ("m2e"), so you can do everything from the IDE. A standalone Maven
> is only needed for the command-line commands in chapter 6.

### 1.1 Install the JDK

1. Run the installer, keep the default path (`C:\Program Files\Eclipse Adoptium\jdk-24…`).
2. Open **PowerShell** and verify:

```powershell
java -version
# openjdk version "24.0.x" ...
```

If `java` is not found, add the JDK `bin` folder to the `Path` environment variable
(System Properties → Advanced → Environment Variables → Path → New →
`C:\Program Files\Eclipse Adoptium\jdk-24.x.x\bin`).

### 1.2 Install Eclipse

Run the Eclipse installer, choose **Eclipse IDE for Java Developers**, start it and select a
workspace, for example `C:\Users\<you>\eclipse-workspace`.

---

## 2. Install and configure MySQL 8.0.46

1. Run the MySQL Installer, choose **Server only** (or "Developer Default" if you also want
   Workbench).
2. In the configuration steps:
   * **Networking:** port `3306`, "Open Windows Firewall port for network access" → **leave
     unchecked** (the database must stay local).
   * **Authentication:** keep `caching_sha2_password` (Connector/J 9.2 supports it; the JDBC URL
     sets `allowPublicKeyRetrieval=true`).
   * **Root password:** choose a strong one and keep it private – SkillLoop will **not** use root.
   * **Windows Service:** configure MySQL as a service named `MySQL80`, start at system startup.
3. Verify:

```powershell
mysql -u root -p -e "SELECT VERSION();"
# +-----------+
# | 8.0.46    |
```

### 2.1 Create the database and the application user (the only step done as root)

The statements below are also shipped as `scripts/mysql-create-user.sql` – edit the password, then
run `mysql -u root -p < scripts\mysql-create-user.sql`.

```sql
-- paste into:  mysql -u root -p
CREATE DATABASE IF NOT EXISTS skillloop
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'skillloop_app'@'localhost'
  IDENTIFIED BY 'choose-a-strong-password';

GRANT SELECT, INSERT, UPDATE, DELETE
  ON skillloop.* TO 'skillloop_app'@'localhost';

FLUSH PRIVILEGES;
```

`skillloop_app` is the account the application uses. It cannot create or drop tables and exists
only on `localhost`.

> The schema itself is **not** created here – `DbSetupTool` does it in chapter 5 with the
> application's own non-destructive `schema.sql`.

---

## 3. Get the project into Eclipse

**File → Import… → Maven → Existing Maven Projects → Next → Browse…**
→ select the folder that contains `pom.xml` (`…\SkillLoop`) → **Finish**.

Eclipse now resolves `mysql-connector-j`, `flatlaf` and `junit-jupiter` from Maven Central (an
internet connection is required the first time) and compiles with `maven.compiler.release=24`.

*First import is slow (dependency download). A progress indicator runs in the bottom-right corner;
wait until it finishes.*

If Eclipse complains about the JDK: **Window → Preferences → Java → Installed JREs** → add the
JDK 24 folder and tick it, then **Project → Properties → Java Build Path → Libraries** →
replace the JRE System Library with the JDK 24 execution environment (`JavaSE-24`).

---

## 4. Configure the database connection (without putting secrets in the code)

Copy the template and fill in your own values:

```powershell
cd C:\Users\<you>\eclipse-workspace\SkillLoop
New-Item -ItemType Directory -Force -Path config
Copy-Item src\main\resources\config\application.properties.example config\application.properties
notepad config\application.properties
```

```properties
skillloop.db.url=jdbc:mysql://localhost:3306/skillloop?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
skillloop.db.user=skillloop_app
skillloop.db.password=the-password-you-chose-above
skillloop.db.driver=com.mysql.cj.jdbc.Driver

skillloop.credits.per.hour=20
skillloop.credits.starter.enabled=true
skillloop.credits.starter.amount=20
skillloop.credits.admin.max=100
```

Order of precedence (highest wins): system properties → **environment variables** → the external
file `config/application.properties` → the classpath template → built-in defaults.

**Alternative without any file** (useful in a lab):

```powershell
$env:SKILLLOOP_DB_URL      = "jdbc:mysql://localhost:3306/skillloop?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:SKILLLOOP_DB_USER     = "skillloop_app"
$env:SKILLLOOP_DB_PASSWORD = "the-password-you-chose-above"
```

`config/application.properties` is listed in `.gitignore`, so your password can never be committed
or end up inside the submitted archive.

---

## 5. Create the schema, views and demo data

The tools live in `com.skillloop.tools` and are ordinary `main` classes – run them from Eclipse
(**Run → Run As → Java Application**) or from the command line:

```powershell
# 1. connection check (prints the masked configuration and the row count of every table)
mvn -q exec:java -Dexec.mainClass=com.skillloop.tools.ConnectionTest

# 2. create tables + views + demo data (all scripts are IF NOT EXISTS - nothing is ever dropped)
mvn -q exec:java -Dexec.mainClass=com.skillloop.tools.DbSetupTool -Dexec.args="--all"
```

Individual steps: `--check` (connectivity only), `--init` (tables), `--views` (reporting views),
`--seed` (demo data), `--help`.

Expected output of `DbSetupTool --all` (abridged):

```text
SkillLoop - database setup
Configuration : jdbc:mysql://localhost:3306/skillloop (user=skillloop_app, password=***)
[ok]   schema.sql   - 15 tables created or already present
[ok]   views.sql    - 11 views created
[ok]   seed.sql     - 12 skills, 5 achievements, 3 demo students, 6 listings
Done. No table was dropped, truncated or deleted.
```

> Running it twice is safe: every statement is `CREATE … IF NOT EXISTS` / `INSERT IGNORE`, so an
> existing database is left untouched.

---

## 6. Build, test and run

### 6.1 From the command line

```powershell
mvn clean package          # compiles, runs the unit tests, builds target\skillloop.jar
mvn test                   # unit tests only (no database needed)
mvn test -P db-tests       # unit tests + database integration tests
java -jar target\skillloop.jar          # or: mvn -q exec:java -Dexec.mainClass=com.skillloop.Main
```

### 6.2 From Eclipse

* **Run the application:** right-click `src/main/java/com/skillloop/Main.java` →
  **Run As → Java Application** (or **Run → Run Configurations… → Java Application → Main**).
  Make sure the working directory is the project root so that `config/application.properties` is
  found.
* **Run a tool:** right-click `DbSetupTool.java` → **Run As → Run Configurations… → Arguments →
  Program arguments:** `--all`.
* **Run the tests:** right-click the project → **Run As → Maven test** (for the DB profile use
  **Run As → Maven build…** with the goal `test -P db-tests`).

---

## 7. Create the first administrator

There is **no default administrator account and no default administrator password**. The first one
is created interactively:

```powershell
mvn -q exec:java -Dexec.mainClass=com.skillloop.tools.AdminBootstrapTool
```

```text
SkillLoop - first administrator
Full name      : Suhas Belg
E-mail         : suhas@college.edu
Password       : ********        (hidden, must satisfy the strength rules)
Confirm        : ********
Role ADMIN granted. Log in and use "Administrator console" in the dashboard.
```

Afterwards, further administrators can be promoted inside the application
(Administrator console → Students → Promote), and every promotion is written to
`admin_audit_events`.

---

## 8. The demo scenario (Rahul ↔ Priya)

`DbSetupTool --seed` inserts three students, all with the password **`SkillLoop@123`**:

| Student | E-mail | Offers | Wants to learn |
| --- | --- | --- | --- |
| Rahul Sharma | `rahul@skillloop.edu` | Java (Advanced) | Graphic Design |
| Priya Nair | `priya@skillloop.edu` | Graphic Design (Advanced) | Java |
| Arjun Reddy | `arjun@skillloop.edu` | Data Structures (Advanced) | Python |

Rahul and Priya are a **reciprocal pair** – each offers exactly what the other wants.

### Walkthrough (about five minutes)

1. **Start the app** and log in as **Rahul** (`rahul@skillloop.edu` / `SkillLoop@123`).
2. **Home** – shows his wallet (20 starter credits), upcoming sessions and progress.
3. **Skills → My skills** – Java is listed as *offered*, Graphic Design as *wanted*.
4. **Explore** – search "Graphic"; Priya's listing appears.
5. **Matching** – Priya is ranked first with the breakdown
   *reciprocity 30 + proficiency 20 + availability 15 + neutral rating 7.5 + interest 10 = 82.5*.
6. **Sessions → Book a session** – pick Priya, 60 minutes, tomorrow 17:00 → **20 credits are
   reserved** (`held_credits = 20`, spendable balance drops to 0).
7. **Log out, log in as Priya** – the notification *"New booking request"* is in her inbox;
   **Sessions → Requests → Accept**.
8. **Availability** (Priya) – add a slot if you want to see the calendar block; the accepted
   session now blocks the time for both.
9. **Both sides confirm completion** – Priya confirms first: the status stays `ACCEPTED` and
   nothing is paid. Rahul confirms: the status becomes `COMPLETED`, **20 credits move from Rahul
   to Priya**, two ledger rows (`SPEND:…` / `EARN:…`) are written and both receive a
   `CREDIT_TRANSFER` notification.
10. **Wallet** – Rahul: 0 credits, Priya: 40 credits, both with a complete statement.
11. **Reviews** – each leaves one review for the other (5 stars). Priya's average rating becomes
    5.0 and appears on her profile and in the leaderboard.
12. **Leaderboard** – Priya now ranks above students who merely hold credits; the ranking uses
    verified sessions, hours, distinct learners and rating – never the balance.
13. **Administrator console** (log in as the administrator created in chapter 7): overview
    statistics, students, listings, disputes and the audit log; adjust a wallet and watch the
    audited `ADMIN_ADJUSTMENT` entry appear in that student's statement.

### Things that are supposed to fail (negative checks)

| Action | Expected result |
| --- | --- |
| Book a session with yourself | "You cannot book a session with yourself." |
| Book two sessions in the same time window | "You already have a session in this time window." |
| Book with 0 spendable credits | "Not enough credits …" – and no session is created |
| Learner clicks "Accept" | Only the teacher may accept or reject |
| Confirm completion before the start time | "A session can only be confirmed after its start time." |
| Review the same session twice | "You have already reviewed this session." |
| Open the administrator console as a student | The menu entry is not offered, and the service refuses anyway |

---

## 9. Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `Communications link failure` | MySQL is not running: **Win + R → services.msc → MySQL80 → Start**; or the port in the URL is wrong. |
| `Access denied for user 'skillloop_app'@'localhost'` | Wrong password in `config/application.properties`, or the user does not exist – recreate it (chapter 2.1). |
| `Unknown database 'skillloop'` | Run `DbSetupTool --init` (or create the database as root). |
| `Public Key Retrieval is not allowed` | The URL is missing `allowPublicKeyRetrieval=true`; copy the URL from the example file. |
| `The server time zone value … is unrecognized` | The URL is missing `serverTimezone=UTC`. |
| `Driver com.mysql.cj.jdbc.Driver not found` | Dependencies were not downloaded: right-click the project → **Maven → Update Project** (or `mvn clean package`). |
| Eclipse shows "release 24 not supported" | Install a JDK 24 and set it as the project's execution environment (chapter 3). |
| The application starts but every screen is empty | Wrong database or empty schema – run `ConnectionTest`, then `DbSetupTool --all`. |
| `DbSetupTool` refuses to run | It detected `root`. Use `skillloop_app`, or – only for creating the database – set `SKILLLOOP_ALLOW_ROOT=YES`. |

---

## 10. Uninstall / reset

```powershell
# remove the build output
mvn clean

# start from an empty database (DESTRUCTIVE - your own decision, never done by the app)
mysql -u root -p -e "DROP DATABASE skillloop;"
# then repeat chapters 2.1 and 5
```

A backup before any experiment:

```powershell
mysqldump -u root -p --single-transaction --routines skillloop > skillloop-backup.sql
```
