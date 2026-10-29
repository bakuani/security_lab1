# Проверка API через curl

Все запросы выполнены скриптом scripts/smoke-test.py.
Пароли и JWT в протокол не включены.

| Проверка | Запрос | HTTP |
|---|---|---:|
| no JWT | GET /api/data | 401 |
| login | POST /auth/login | 200 |
| wrong password | POST /auth/login | 401 |
| SQL injection rejected | POST /auth/login | 401 |
| owner-scoped list | GET /api/data | 200 |
| change own task | PATCH /api/data/1/status | 200 |
| status saved | GET /api/data | 200 |
| second user login | POST /auth/login | 200 |
| second user's list | GET /api/data | 200 |
| foreign task denied | PATCH /api/data/3/status | 404 |
| status validation | PATCH /api/data/1/status | 400 |
| invalid JWT | GET /api/data | 401 |
