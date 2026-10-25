-- ---------------------------------------------------------------------------
--  SkillLoop - one-time database bootstrap (run ONCE as root)
--
--      mysql -u root -p < scripts/mysql-create-user.sql
--
--  Choose your own password below. The application itself never connects as
--  root: it uses skillloop_app, which may only read/write the skillloop schema
--  from localhost. The tables are created later by DbSetupTool --init.
-- ---------------------------------------------------------------------------

CREATE DATABASE IF NOT EXISTS skillloop
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'skillloop_app'@'localhost'
  IDENTIFIED BY 'change-me-in-the-file';

GRANT SELECT, INSERT, UPDATE, DELETE
  ON skillloop.* TO 'skillloop_app'@'localhost';

FLUSH PRIVILEGES;

SELECT 'Database and application user ready.' AS status;
