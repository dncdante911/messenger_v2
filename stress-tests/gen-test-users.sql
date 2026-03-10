-- ============================================================
-- gen-test-users.sql
-- Генерация тестовых сессий для k6 stress-тестов
-- Запускать на тестовой БД, не на проде!
-- ============================================================

-- 1. Выбрать N реальных пользователей и их сессии
SELECT
    u.id          AS user_id,
    s.session_id  AS access_token
FROM
    wo_users u
    INNER JOIN wo_appssessions s ON s.user_id = u.id
WHERE
    u.active = 1
    AND s.session_id IS NOT NULL
    AND s.session_id != ''
ORDER BY
    u.id ASC
LIMIT 2000;

-- 2. Для генерации users.json выполните в MySQL-клиенте:
-- mysql -u social -p socialhub < gen-test-users.sql \
--   | python3 -c "
-- import sys, json
-- rows = [l.strip().split('\t') for l in sys.stdin if l.strip() and not l.startswith('user_id')]
-- users = []
-- ids = [r[0] for r in rows]
-- for i, row in enumerate(rows):
--     contacts = [ids[j] for j in range(max(0,i-3), min(len(ids),i+4)) if ids[j] != row[0]][:5]
--     users.append({'user_id': row[0], 'access_token': row[1], 'contacts': contacts, 'group_ids': []})
-- print(json.dumps(users, ensure_ascii=False, indent=2))
-- " > users.json

-- 3. Получить группы для пользователей:
SELECT
    gcu.user_id,
    GROUP_CONCAT(gcu.group_id ORDER BY gcu.group_id SEPARATOR ',') AS group_ids
FROM
    wo_groupchatusers gcu
    INNER JOIN wo_groupchat g ON g.id = gcu.group_id AND g.active = 1
GROUP BY
    gcu.user_id
HAVING
    COUNT(*) > 0
LIMIT 2000;
