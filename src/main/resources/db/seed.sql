-- =====================================================================================
--  SkillLoop - seed.sql  (OPTIONAL demo data)
--
--  * Re-runnable: every INSERT is written as INSERT IGNORE or INSERT ... SELECT ... WHERE
--    NOT EXISTS, so running the file twice never duplicates or overwrites anything.
--  * No DELETE / TRUNCATE / DROP statements.
--  * The demo accounts are ordinary STUDENT accounts - NOT administrators. There is no
--    default administrator password anywhere in SkillLoop; create the first administrator
--    with:  mvn -q exec:java -Dexec.mainClass=com.skillloop.tools.AdminBootstrapTool
--
--  Demo password for all three demo students:  SkillLoop@123
--  (stored as a BCrypt hash with cost 12 - the plaintext password is not recoverable)
-- =====================================================================================

USE skillloop;

-- -------------------------------------------------------------------------------------
-- Skill catalogue
-- -------------------------------------------------------------------------------------
INSERT IGNORE INTO skills (skill_name, category, description) VALUES
    ('Java Programming',      'Programming',   'Core Java, OOP, collections, exceptions and JDBC basics.'),
    ('Python Programming',    'Programming',   'Python syntax, data structures, scripting and automation.'),
    ('Data Structures',       'Computer Science', 'Arrays, linked lists, stacks, queues, trees and graphs.'),
    ('Database Management',   'Computer Science', 'Relational model, SQL, normalization and transactions.'),
    ('Graphic Design',        'Design',        'Canva/Photoshop basics, colour theory and layout design.'),
    ('UI/UX Design',          'Design',        'Wireframing, prototyping and usability principles.'),
    ('Web Development',       'Programming',   'HTML, CSS, JavaScript and responsive layouts.'),
    ('Machine Learning',      'Computer Science', 'Regression, classification and model evaluation.'),
    ('Public Speaking',       'Soft Skills',   'Structuring a talk, stage presence and handling questions.'),
    ('Digital Marketing',     'Business',      'Social media strategy, content planning and analytics.'),
    ('Statistics',            'Mathematics',   'Descriptive statistics, probability and hypothesis testing.'),
    ('Spoken English',        'Soft Skills',   'Fluency, pronunciation and everyday conversation practice.');

-- -------------------------------------------------------------------------------------
-- Achievements
-- -------------------------------------------------------------------------------------
INSERT IGNORE INTO achievements (code, name, description, icon) VALUES
    ('FIRST_TEACHING_SESSION', 'First Teaching Session', 'Completed your very first verified teaching session.', '*'),
    ('FIVE_SESSIONS_COMPLETED','Five Sessions Completed','Completed five verified teaching sessions.', '*'),
    ('HELPFUL_TEACHER',        'Helpful Teacher',        'Kept an average rating of 4.0 or higher over 5+ reviews.', '*'),
    ('ACTIVE_LEARNER',         'Active Learner',         'Completed five learning sessions.', '*'),
    ('CREDIT_MENTOR',          'Credit Mentor',          'Earned 100 credits by teaching other students.', '*');

-- -------------------------------------------------------------------------------------
-- Demo students (ordinary accounts, password: SkillLoop@123)
--    Rahul -> offers Java, wants Graphic Design
--    Priya -> offers Graphic Design, wants Java          (reciprocal match with Rahul)
--    Arjun -> offers Data Structures, wants Python       (one-way, credit based)
-- -------------------------------------------------------------------------------------
INSERT INTO students (full_name, email, password_hash, department, semester, bio, role, account_status)
SELECT 'Rahul Sharma', 'rahul@skillloop.edu',
       '$2a$12$JVzsnAxAeRjMJtmVb1LAvessGAw0T9vuam3bdnGrHtcCXQhzMJzrW',
       'Computer Science', 5, 'Java enthusiast who wants to learn design.', 'STUDENT', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM students WHERE email = 'rahul@skillloop.edu');

INSERT INTO students (full_name, email, password_hash, department, semester, bio, role, account_status)
SELECT 'Priya Nair', 'priya@skillloop.edu',
       '$2a$12$XqNFGKdJZDjEM6Dhcron1OKaXVbpkhJmvmvpeuNtuqSZTlJCixeky',
       'Design', 4, 'Design student who wants to learn to code.', 'STUDENT', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM students WHERE email = 'priya@skillloop.edu');

INSERT INTO students (full_name, email, password_hash, department, semester, bio, role, account_status)
SELECT 'Arjun Reddy', 'arjun@skillloop.edu',
       '$2a$12$3wo3BhViHm23Vbmq76IMB..iCktqCQyxtTqZ7hB9TMmHA.D4Zay26',
       'Computer Science', 6, 'Loves algorithms and teaching data structures.', 'STUDENT', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM students WHERE email = 'arjun@skillloop.edu');

-- -------------------------------------------------------------------------------------
-- Wallets (0 balance; the one-time starter grant is issued by the application itself)
-- -------------------------------------------------------------------------------------
INSERT IGNORE INTO wallets (student_id, balance, held_credits)
SELECT student_id, 0, 0 FROM students;

-- -------------------------------------------------------------------------------------
-- Skill listings
-- -------------------------------------------------------------------------------------
INSERT IGNORE INTO student_skills (student_id, skill_id, skill_type, proficiency_level, description, is_active)
SELECT s.student_id, k.skill_id, 'OFFER', 'ADVANCED',
       'Core Java, JDBC and Swing. Happy to teach beginners step by step.', TRUE
FROM students s JOIN skills k ON k.skill_name = 'Java Programming'
WHERE s.email = 'rahul@skillloop.edu';

INSERT IGNORE INTO student_skills (student_id, skill_id, skill_type, proficiency_level, description, is_active)
SELECT s.student_id, k.skill_id, 'LEARN', 'BEGINNER',
       'I want to design posters and social media creatives.', TRUE
FROM students s JOIN skills k ON k.skill_name = 'Graphic Design'
WHERE s.email = 'rahul@skillloop.edu';

INSERT IGNORE INTO student_skills (student_id, skill_id, skill_type, proficiency_level, description, is_active)
SELECT s.student_id, k.skill_id, 'OFFER', 'ADVANCED',
       'Canva and Photoshop, branding basics and portfolio reviews.', TRUE
FROM students s JOIN skills k ON k.skill_name = 'Graphic Design'
WHERE s.email = 'priya@skillloop.edu';

INSERT IGNORE INTO student_skills (student_id, skill_id, skill_type, proficiency_level, description, is_active)
SELECT s.student_id, k.skill_id, 'LEARN', 'BEGINNER',
       'Complete beginner who wants to learn Java from scratch.', TRUE
FROM students s JOIN skills k ON k.skill_name = 'Java Programming'
WHERE s.email = 'priya@skillloop.edu';

INSERT IGNORE INTO student_skills (student_id, skill_id, skill_type, proficiency_level, description, is_active)
SELECT s.student_id, k.skill_id, 'OFFER', 'INTERMEDIATE',
       'Trees, graphs and dynamic programming practice sessions.', TRUE
FROM students s JOIN skills k ON k.skill_name = 'Data Structures'
WHERE s.email = 'arjun@skillloop.edu';

INSERT IGNORE INTO student_skills (student_id, skill_id, skill_type, proficiency_level, description, is_active)
SELECT s.student_id, k.skill_id, 'LEARN', 'BEGINNER',
       'Need Python for a college mini project.', TRUE
FROM students s JOIN skills k ON k.skill_name = 'Python Programming'
WHERE s.email = 'arjun@skillloop.edu';

-- -------------------------------------------------------------------------------------
-- Availability (teaching slots starting tomorrow, 2 hours each)
-- -------------------------------------------------------------------------------------
INSERT INTO availability_slots (student_id, start_time, end_time, status)
SELECT s.student_id,
       DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 1 DAY), INTERVAL 15 HOUR),
       DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 1 DAY), INTERVAL 17 HOUR),
       'AVAILABLE'
FROM students s
WHERE s.email IN ('rahul@skillloop.edu', 'priya@skillloop.edu', 'arjun@skillloop.edu')
  AND NOT EXISTS (SELECT 1 FROM availability_slots a WHERE a.student_id = s.student_id);

-- -------------------------------------------------------------------------------------
-- Learning resources
-- -------------------------------------------------------------------------------------
INSERT IGNORE INTO learning_resources (student_id, skill_id, title, description, resource_url)
SELECT s.student_id, k.skill_id, 'Java OOP cheat sheet',
       'Four pages covering classes, inheritance, interfaces and polymorphism.',
       'https://docs.oracle.com/javase/tutorial/java/concepts/'
FROM students s JOIN skills k ON k.skill_name = 'Java Programming'
WHERE s.email = 'rahul@skillloop.edu';

INSERT IGNORE INTO learning_resources (student_id, skill_id, title, description, resource_url)
SELECT s.student_id, k.skill_id, 'Colour theory starter guide',
       'Practical colour palettes for beginners in graphic design.',
       'https://www.canva.com/learn/color-theory/'
FROM students s JOIN skills k ON k.skill_name = 'Graphic Design'
WHERE s.email = 'priya@skillloop.edu';

-- -------------------------------------------------------------------------------------
-- A sample group session (hosted by Arjun)
-- -------------------------------------------------------------------------------------
INSERT INTO group_sessions (teacher_id, skill_id, title, start_time, end_time, capacity, credit_cost_per_learner, status)
SELECT s.student_id, k.skill_id, 'Data Structures crash course',
       DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 2 DAY), INTERVAL 16 HOUR),
       DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 2 DAY), INTERVAL 18 HOUR),
       8, 15, 'OPEN'
FROM students s JOIN skills k ON k.skill_name = 'Data Structures'
WHERE s.email = 'arjun@skillloop.edu'
  AND NOT EXISTS (SELECT 1 FROM group_sessions g WHERE g.teacher_id = s.student_id);

-- =====================================================================================
-- End of seed.sql - no destructive statements.
-- =====================================================================================
