-- =====================================================================================
--  SkillLoop - A Credit-Based Peer Learning Platform
--  schema.sql  ::  MySQL 8.0.x (target: MySQL Community Server 8.0.46, InnoDB)
--
--  SAFE INITIALIZATION
--    * Every object is created with CREATE DATABASE/TABLE IF NOT EXISTS.
--    * This script contains NO DROP, TRUNCATE or DELETE statement, therefore it is safe
--      to run against an existing database: existing tables and rows are left untouched.
--    * Indexes are declared inline (MySQL 8 has no "CREATE INDEX IF NOT EXISTS"), which
--      keeps the whole script re-runnable.
--
--  Run with:      mysql -u root -p < src/main/resources/db/schema.sql
--  or:            mvn -q exec:java -Dexec.mainClass=com.skillloop.tools.DbSetupTool -Dexec.args="--init"
-- =====================================================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS skillloop
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE skillloop;

-- -------------------------------------------------------------------------------------
-- students : every registered user (learners, teachers and administrators)
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS students (
    student_id      BIGINT       AUTO_INCREMENT PRIMARY KEY,
    full_name       VARCHAR(120) NOT NULL,
    email           VARCHAR(190) NOT NULL,
    password_hash   VARCHAR(72)  NOT NULL,             -- BCrypt hash (60 chars, 72 byte cap)
    department      VARCHAR(100) NOT NULL,
    semester        INT          NOT NULL,
    bio             VARCHAR(500) NULL,
    role            ENUM('STUDENT','ADMIN')                 NOT NULL DEFAULT 'STUDENT',
    account_status  ENUM('ACTIVE','SUSPENDED','INACTIVE')   NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_students_email       UNIQUE (email),
    CONSTRAINT chk_students_semester   CHECK (semester BETWEEN 1 AND 12),
    CONSTRAINT chk_students_email      CHECK (email LIKE '%_@_%._%'),
    CONSTRAINT chk_students_name       CHECK (CHAR_LENGTH(TRIM(full_name)) >= 2),
    INDEX idx_students_role (role, account_status),
    INDEX idx_students_dept (department)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- skills : the global skill catalogue (shared, not owned by a single student)
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS skills (
    skill_id     BIGINT       AUTO_INCREMENT PRIMARY KEY,
    skill_name   VARCHAR(100) NOT NULL,
    category     VARCHAR(60)  NOT NULL,
    description  VARCHAR(500) NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_skills_name UNIQUE (skill_name),
    INDEX idx_skills_category (category)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- student_skills : M:N relationship student <-> skill, typed OFFER or LEARN
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS student_skills (
    student_skill_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id         BIGINT NOT NULL,
    skill_id           BIGINT NOT NULL,
    skill_type         ENUM('OFFER','LEARN')                      NOT NULL,
    proficiency_level  ENUM('BEGINNER','INTERMEDIATE','ADVANCED') NOT NULL DEFAULT 'BEGINNER',
    description        VARCHAR(500) NULL,
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ss_student FOREIGN KEY (student_id) REFERENCES students (student_id) ON DELETE CASCADE,
    CONSTRAINT fk_ss_skill   FOREIGN KEY (skill_id)   REFERENCES skills   (skill_id)   ON DELETE CASCADE,
    CONSTRAINT uq_student_skill_type UNIQUE (student_id, skill_id, skill_type),
    INDEX idx_ss_lookup (skill_id, skill_type, is_active)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- wallets : one credit wallet per student (1:1)
--   balance      = spendable credits
--   held_credits = credits reserved by pending (not yet completed) bookings
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wallets (
    wallet_id      BIGINT  AUTO_INCREMENT PRIMARY KEY,
    student_id     BIGINT  NOT NULL,
    balance        INT     NOT NULL DEFAULT 0,
    held_credits   INT     NOT NULL DEFAULT 0,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_wallets_student UNIQUE (student_id),
    CONSTRAINT fk_wallets_student FOREIGN KEY (student_id) REFERENCES students (student_id) ON DELETE CASCADE,
    CONSTRAINT chk_wallets_balance CHECK (balance >= 0),
    CONSTRAINT chk_wallets_held    CHECK (held_credits >= 0)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- learning_sessions : a booking of one teacher + one learner for one skill
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS learning_sessions (
    session_id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id           BIGINT NOT NULL,
    learner_id           BIGINT NOT NULL,
    skill_id             BIGINT NOT NULL,
    start_time           DATETIME NOT NULL,
    end_time             DATETIME NOT NULL,
    duration_minutes     INT      NOT NULL,
    credit_cost          INT      NOT NULL,
    slot_id              BIGINT   NULL,       -- availability slot the booking was made against
    session_status       ENUM('REQUESTED','ACCEPTED','REJECTED','CANCELLED','COMPLETED','DISPUTED')
                                  NOT NULL DEFAULT 'REQUESTED',
    teacher_confirmation BOOLEAN  NOT NULL DEFAULT FALSE,
    learner_confirmation BOOLEAN  NOT NULL DEFAULT FALSE,
    learner_note         VARCHAR(500) NULL,
    resolution_note      VARCHAR(500) NULL,
    resolved_by          BIGINT   NULL,
    settled_at           DATETIME NULL,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_teacher  FOREIGN KEY (teacher_id)  REFERENCES students (student_id),
    CONSTRAINT fk_session_learner  FOREIGN KEY (learner_id)  REFERENCES students (student_id),
    CONSTRAINT fk_session_skill    FOREIGN KEY (skill_id)    REFERENCES skills   (skill_id),
    CONSTRAINT fk_session_resolver FOREIGN KEY (resolved_by) REFERENCES students (student_id),
    CONSTRAINT chk_session_parties  CHECK (teacher_id <> learner_id),
    CONSTRAINT chk_session_time     CHECK (end_time > start_time),
    CONSTRAINT chk_session_duration CHECK (duration_minutes IN (30,45,60,90,120)),
    CONSTRAINT chk_session_cost     CHECK (credit_cost >= 0),
    INDEX idx_session_slot    (slot_id),
    INDEX idx_session_teacher (teacher_id, session_status),
    INDEX idx_session_learner (learner_id, session_status),
    INDEX idx_session_start   (start_time)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- availability_slots : time windows a student publishes for teaching
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS availability_slots (
    slot_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id  BIGINT   NOT NULL,
    start_time  DATETIME NOT NULL,
    end_time    DATETIME NOT NULL,
    status      ENUM('AVAILABLE','BOOKED','BLOCKED') NOT NULL DEFAULT 'AVAILABLE',
    session_id  BIGINT   NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_slot_student FOREIGN KEY (student_id) REFERENCES students (student_id) ON DELETE CASCADE,
    CONSTRAINT fk_slot_session FOREIGN KEY (session_id) REFERENCES learning_sessions (session_id) ON DELETE SET NULL,
    CONSTRAINT chk_slot_time CHECK (end_time > start_time),
    INDEX idx_slot_lookup (student_id, status, start_time)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- credit_transactions : immutable append-only ledger of every credit movement
--   amount      = effect on spendable balance (positive = credit, negative = debit)
--   held_amount = effect on reserved credits
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS credit_transactions (
    transaction_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id       BIGINT NOT NULL,
    session_id       BIGINT NULL,
    amount           INT    NOT NULL DEFAULT 0,
    held_amount      INT    NOT NULL DEFAULT 0,
    balance_after    INT    NOT NULL DEFAULT 0,
    held_after       INT    NOT NULL DEFAULT 0,
    transaction_type ENUM('STARTER_GRANT','HOLD','RELEASE','SPEND','EARN','REFUND',
                          'ADMIN_ADJUSTMENT','GROUP_EARNING') NOT NULL,
    txn_reference    VARCHAR(80) NOT NULL,
    description      VARCHAR(255) NULL,
    created_by       BIGINT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_txn_reference UNIQUE (txn_reference),
    CONSTRAINT fk_txn_student FOREIGN KEY (student_id) REFERENCES students (student_id) ON DELETE CASCADE,
    CONSTRAINT fk_txn_session FOREIGN KEY (session_id) REFERENCES learning_sessions (session_id) ON DELETE SET NULL,
    CONSTRAINT fk_txn_actor   FOREIGN KEY (created_by) REFERENCES students (student_id),
    CONSTRAINT chk_txn_movement CHECK (amount <> 0 OR held_amount <> 0),
    INDEX idx_txn_student (student_id, created_at),
    INDEX idx_txn_session (session_id)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- reviews : one review per (session, reviewer) - both sides may review each other
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reviews (
    review_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id   BIGINT NOT NULL,
    reviewer_id  BIGINT NOT NULL,
    reviewee_id  BIGINT NOT NULL,
    rating       INT      NOT NULL,
    comment      VARCHAR(1000) NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_review_session  FOREIGN KEY (session_id)  REFERENCES learning_sessions (session_id) ON DELETE CASCADE,
    CONSTRAINT fk_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES students (student_id) ON DELETE CASCADE,
    CONSTRAINT fk_review_reviewee FOREIGN KEY (reviewee_id) REFERENCES students (student_id) ON DELETE CASCADE,
    CONSTRAINT uq_review_unique UNIQUE (session_id, reviewer_id),
    CONSTRAINT chk_review_rating  CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT chk_review_parties CHECK (reviewer_id <> reviewee_id),
    INDEX idx_review_reviewee (reviewee_id, rating)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- notifications : in-app inbox for every student
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifications (
    notification_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id        BIGINT NOT NULL,
    title             VARCHAR(120) NOT NULL,
    message           VARCHAR(500) NOT NULL,
    notification_type ENUM('BOOKING_REQUEST','BOOKING_ACCEPTED','BOOKING_REJECTED',
                           'SESSION_CANCELLED','SESSION_COMPLETED','CREDIT_TRANSFER',
                           'REVIEW_RECEIVED','ACHIEVEMENT','DISPUTE_RAISED',
                           'DISPUTE_RESOLVED','ADMIN_MESSAGE','GROUP_SESSION') NOT NULL,
    is_read           BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_student FOREIGN KEY (student_id) REFERENCES students (student_id) ON DELETE CASCADE,
    INDEX idx_notification_student (student_id, is_read, created_at)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- learning_resources : notes / links / practice material shared by students
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS learning_resources (
    resource_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id    BIGINT NOT NULL,
    skill_id      BIGINT NOT NULL,
    title         VARCHAR(150) NOT NULL,
    description   VARCHAR(500) NULL,
    resource_url  VARCHAR(500) NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_resource_student FOREIGN KEY (student_id) REFERENCES students (student_id) ON DELETE CASCADE,
    CONSTRAINT fk_resource_skill   FOREIGN KEY (skill_id)   REFERENCES skills   (skill_id)   ON DELETE CASCADE,
    INDEX idx_resource_skill (skill_id, created_at)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- group_sessions / group_participants : one teacher, many learners
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS group_sessions (
    group_id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    teacher_id              BIGINT NOT NULL,
    skill_id                BIGINT NOT NULL,
    title                   VARCHAR(150) NOT NULL,
    start_time              DATETIME NOT NULL,
    end_time                DATETIME NOT NULL,
    capacity                INT NOT NULL DEFAULT 5,
    credit_cost_per_learner INT NOT NULL DEFAULT 10,
    status                  ENUM('OPEN','CLOSED','COMPLETED','CANCELLED') NOT NULL DEFAULT 'OPEN',
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_group_teacher FOREIGN KEY (teacher_id) REFERENCES students (student_id),
    CONSTRAINT fk_group_skill   FOREIGN KEY (skill_id)   REFERENCES skills   (skill_id),
    CONSTRAINT chk_group_time     CHECK (end_time > start_time),
    CONSTRAINT chk_group_capacity CHECK (capacity BETWEEN 2 AND 25),
    CONSTRAINT chk_group_cost     CHECK (credit_cost_per_learner >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS group_participants (
    participant_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id       BIGINT NOT NULL,
    student_id     BIGINT NOT NULL,
    status         ENUM('JOINED','CANCELLED','COMPLETED') NOT NULL DEFAULT 'JOINED',
    joined_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_gp_group   FOREIGN KEY (group_id)   REFERENCES group_sessions (group_id) ON DELETE CASCADE,
    CONSTRAINT fk_gp_student FOREIGN KEY (student_id) REFERENCES students (student_id) ON DELETE CASCADE,
    CONSTRAINT uq_gp_member UNIQUE (group_id, student_id)
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- achievements / student_achievements : gamification
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS achievements (
    achievement_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code           VARCHAR(40)  NOT NULL,
    name           VARCHAR(80)  NOT NULL,
    description    VARCHAR(255) NOT NULL,
    icon           VARCHAR(8)   NOT NULL DEFAULT '*',
    CONSTRAINT uq_achievement_code UNIQUE (code)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS student_achievements (
    student_id     BIGINT NOT NULL,
    achievement_id BIGINT NOT NULL,
    awarded_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_student_achievement PRIMARY KEY (student_id, achievement_id),
    CONSTRAINT fk_sa_student     FOREIGN KEY (student_id)     REFERENCES students (student_id) ON DELETE CASCADE,
    CONSTRAINT fk_sa_achievement FOREIGN KEY (achievement_id) REFERENCES achievements (achievement_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -------------------------------------------------------------------------------------
-- admin_audit_events : audit trail of every privileged administrator action
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admin_audit_events (
    event_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id     BIGINT NOT NULL,
    action       VARCHAR(60)  NOT NULL,
    target_type  VARCHAR(40)  NULL,
    target_id    BIGINT       NULL,
    details      VARCHAR(500) NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_admin FOREIGN KEY (admin_id) REFERENCES students (student_id),
    INDEX idx_audit_created (created_at)
) ENGINE=InnoDB;

-- =====================================================================================
-- End of schema.sql - no DROP / TRUNCATE / DELETE anywhere in this file.
-- =====================================================================================
