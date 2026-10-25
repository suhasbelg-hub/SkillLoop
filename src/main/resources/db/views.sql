-- =====================================================================================
--  SkillLoop - views.sql
--  Reporting / analytics views (JOINs, aggregates, GROUP BY, HAVING, subqueries).
--
--  Notes
--    * Every view is created with CREATE OR REPLACE VIEW, so the file is re-runnable.
--    * MySQL does not allow subqueries in the FROM clause of a view, therefore all
--      subqueries used here are correlated scalar subqueries in the SELECT list.
--
--  Run with:   mysql -u skillloop_app -p skillloop < src/main/resources/db/views.sql
--  or:         mvn -q exec:java -Dexec.mainClass=com.skillloop.tools.DbSetupTool -Dexec.args="--views"
-- =====================================================================================

USE skillloop;

-- -------------------------------------------------------------------------------------
-- v_student_directory : student master data + wallet position
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_student_directory AS
SELECT s.student_id,
       s.full_name,
       s.email,
       s.department,
       s.semester,
       s.bio,
       s.role,
       s.account_status,
       s.created_at,
       COALESCE(w.balance, 0)      AS balance,
       COALESCE(w.held_credits, 0) AS held_credits
FROM students s
LEFT JOIN wallets w ON w.student_id = s.student_id;

-- -------------------------------------------------------------------------------------
-- v_teacher_ratings : average rating and review count per teacher (GROUP BY + HAVING)
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_teacher_ratings AS
SELECT rev.reviewee_id            AS teacher_id,
       COUNT(*)                   AS review_count,
       ROUND(AVG(rev.rating), 2)  AS avg_rating,
       MAX(rev.rating)            AS highest_rating,
       MIN(rev.rating)            AS lowest_rating
FROM reviews rev
GROUP BY rev.reviewee_id
HAVING COUNT(*) >= 1;

-- -------------------------------------------------------------------------------------
-- v_teaching_stats : verified teaching contribution per student (JOIN + aggregates)
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_teaching_stats AS
SELECT s.student_id,
       s.full_name,
       s.department,
       COUNT(ls.session_id)                        AS completed_sessions,
       COALESCE(SUM(ls.duration_minutes), 0)       AS teaching_minutes,
       ROUND(COALESCE(SUM(ls.duration_minutes), 0) / 60.0, 2) AS teaching_hours,
       COALESCE(SUM(ls.credit_cost), 0)            AS credits_earned,
       COUNT(DISTINCT ls.learner_id)               AS distinct_learners
FROM students s
LEFT JOIN learning_sessions ls
       ON ls.teacher_id = s.student_id
      AND ls.session_status = 'COMPLETED'
GROUP BY s.student_id, s.full_name, s.department;

-- -------------------------------------------------------------------------------------
-- v_learning_stats : learning activity per student
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_learning_stats AS
SELECT s.student_id,
       s.full_name,
       s.department,
       COUNT(ls.session_id)                        AS completed_sessions,
       COALESCE(SUM(ls.duration_minutes), 0)       AS learning_minutes,
       ROUND(COALESCE(SUM(ls.duration_minutes), 0) / 60.0, 2) AS learning_hours,
       COALESCE(SUM(ls.credit_cost), 0)            AS credits_spent,
       COUNT(DISTINCT ls.teacher_id)               AS distinct_teachers
FROM students s
LEFT JOIN learning_sessions ls
       ON ls.learner_id = s.student_id
      AND ls.session_status = 'COMPLETED'
GROUP BY s.student_id, s.full_name, s.department;

-- -------------------------------------------------------------------------------------
-- v_session_details : human readable session list (multi-table JOIN)
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_session_details AS
SELECT ls.session_id,
       ls.teacher_id,
       t.full_name   AS teacher_name,
       ls.learner_id,
       l.full_name   AS learner_name,
       ls.skill_id,
       sk.skill_name,
       sk.category,
       ls.start_time,
       ls.end_time,
       ls.duration_minutes,
       ls.credit_cost,
       ls.session_status,
       ls.teacher_confirmation,
       ls.learner_confirmation,
       ls.settled_at,
       ls.created_at
FROM learning_sessions ls
JOIN students t  ON t.student_id = ls.teacher_id
JOIN students l  ON l.student_id = ls.learner_id
JOIN skills   sk ON sk.skill_id  = ls.skill_id;

-- -------------------------------------------------------------------------------------
-- v_wallet_summary : wallet + lifetime earned / spent totals (correlated subqueries)
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_wallet_summary AS
SELECT w.student_id,
       w.balance,
       w.held_credits,
       (SELECT COALESCE(SUM(ct.amount), 0)
          FROM credit_transactions ct
         WHERE ct.student_id = w.student_id
           AND ct.amount > 0)                                    AS credits_earned_total,
       (SELECT COALESCE(SUM(-ct.amount), 0)
          FROM credit_transactions ct
         WHERE ct.student_id = w.student_id
           AND ct.transaction_type = 'SPEND')                    AS credits_spent_total,
       (SELECT COUNT(*)
          FROM credit_transactions ct
         WHERE ct.student_id = w.student_id)                     AS transaction_count,
       w.updated_at
FROM wallets w;

-- -------------------------------------------------------------------------------------
-- v_skill_demand : how many students offer / want each skill
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_skill_demand AS
SELECT sk.skill_id,
       sk.skill_name,
       sk.category,
       (SELECT COUNT(*) FROM student_skills ss
         WHERE ss.skill_id = sk.skill_id AND ss.skill_type = 'OFFER'  AND ss.is_active = TRUE) AS teachers_offering,
       (SELECT COUNT(*) FROM student_skills ss
         WHERE ss.skill_id = sk.skill_id AND ss.skill_type = 'LEARN'  AND ss.is_active = TRUE) AS learners_wanting,
       (SELECT COUNT(*) FROM learning_sessions ls
         WHERE ls.skill_id = sk.skill_id AND ls.session_status = 'COMPLETED')                   AS completed_sessions,
       (SELECT COUNT(*) FROM learning_sessions ls
         WHERE ls.skill_id = sk.skill_id AND ls.session_status = 'REQUESTED')                   AS pending_requests
FROM skills sk;

-- -------------------------------------------------------------------------------------
-- v_leaderboard : ranked by verified contribution, not by wallet balance
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_leaderboard AS
SELECT ts.student_id,
       ts.full_name,
       ts.department,
       ts.completed_sessions,
       ts.teaching_hours,
       ts.credits_earned,
       ts.distinct_learners,
       COALESCE(tr.avg_rating, 0)   AS avg_rating,
       COALESCE(tr.review_count, 0) AS review_count,
       (SELECT COUNT(*) FROM learning_sessions ls2
         WHERE ls2.learner_id = ts.student_id AND ls2.session_status = 'COMPLETED') AS learning_sessions
FROM v_teaching_stats ts
LEFT JOIN v_teacher_ratings tr ON tr.teacher_id = ts.student_id;

-- -------------------------------------------------------------------------------------
-- v_monthly_credit_volume : monthly credit movement (GROUP BY + HAVING)
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_monthly_credit_volume AS
SELECT DATE_FORMAT(ct.created_at, '%Y-%m')            AS period,
       ct.transaction_type,
       COUNT(*)                                       AS transaction_count,
       SUM(CASE WHEN ct.amount > 0 THEN ct.amount ELSE 0 END)  AS credits_credited,
       SUM(CASE WHEN ct.amount < 0 THEN -ct.amount ELSE 0 END) AS credits_debited
FROM credit_transactions ct
GROUP BY DATE_FORMAT(ct.created_at, '%Y-%m'), ct.transaction_type
HAVING COUNT(*) > 0;

-- -------------------------------------------------------------------------------------
-- v_open_availability : future free slots of active teachers
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_open_availability AS
SELECT av.slot_id,
       av.student_id,
       s.full_name   AS teacher_name,
       av.start_time,
       av.end_time,
       TIMESTAMPDIFF(MINUTE, av.start_time, av.end_time) AS slot_minutes
FROM availability_slots av
JOIN students s ON s.student_id = av.student_id
WHERE av.status = 'AVAILABLE'
  AND av.start_time > NOW()
  AND s.account_status = 'ACTIVE';

-- -------------------------------------------------------------------------------------
-- v_group_session_overview : group sessions with enrolment counters
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_group_session_overview AS
SELECT gs.group_id,
       gs.title,
       gs.teacher_id,
       t.full_name        AS teacher_name,
       gs.skill_id,
       sk.skill_name,
       gs.start_time,
       gs.end_time,
       gs.capacity,
       gs.credit_cost_per_learner,
       gs.status,
       (SELECT COUNT(*) FROM group_participants gp
         WHERE gp.group_id = gs.group_id AND gp.status IN ('JOINED','COMPLETED')) AS enrolled
FROM group_sessions gs
JOIN students t  ON t.student_id  = gs.teacher_id
JOIN skills   sk ON sk.skill_id   = gs.skill_id;

-- =====================================================================================
-- End of views.sql
-- =====================================================================================
