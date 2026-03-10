# WorldMates Messenger — k6 Stress Test Suite

Набор из трёх скриптов для нагрузочного тестирования сервера,
HAProxy и nftables мессенджера WorldMates.

---

## Скрипты

| Файл | Цель | Макс. нагрузка | Длительность |
|------|------|---------------|--------------|
| `k6-normal-load.js`  | Обычный дневной трафик  | 2 000 VU  | ~30 мин |
| `k6-stress-test.js`  | Серьёзный стресс-тест   | 10 000 VU | ~30 мин |
| `k6-breakpoint.js`   | Поиск предела (breakpoint) | 5 000 RPS | ~37 мин |

---

## Установка k6

```bash
# Linux (Debian/Ubuntu)
sudo gpg -k
sudo gpg --no-default-keyring \
    --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
    --keyserver hkp://keyserver.ubuntu.com:80 \
    --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
    | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6

# macOS
brew install k6
```

---

## Подготовка данных

### Создать `users.json`

Скрипты загружают пользователей из `users.json` — массив реальных сессий из БД.

```bash
# 1. Выполнить SQL и получить список пользователей
mysql -u social -p socialhub < gen-test-users.sql \
  | python3 -c "
import sys, json
rows = [l.strip().split('\t') for l in sys.stdin
        if l.strip() and not l.startswith('user_id')]
ids = [r[0] for r in rows]
users = []
for i, row in enumerate(rows):
    contacts = [ids[j] for j in range(max(0,i-3), min(len(ids),i+4))
                if ids[j] != row[0]][:5]
    users.append({'user_id': row[0], 'access_token': row[1],
                  'contacts': contacts, 'group_ids': []})
print(json.dumps(users, ensure_ascii=False, indent=2))
" > users.json

# 2. Проверить
wc -l users.json
head -c 300 users.json
```

Смотри `users.example.json` для примера структуры.

> **Важно:** `access_token` — это `session_id` из таблицы `wo_appssessions`.
> Используй реальных пользователей или создай тестовых через `/api/node/auth/quick-register`.

---

## Запуск

### Script 1 — Нормальная нагрузка (2 000 пользователей)

```bash
k6 run \
  --env USERS_FILE=./users.json \
  --out json=results/normal-$(date +%s).json \
  k6-normal-load.js
```

Ожидаемые результаты:
- `http_req_duration` p95 < 500 мс ✅
- `http_req_failed` rate < 1% ✅
- Сервер не показывает деградацию

---

### Script 2 — Стресс-тест (до 10 000 VU)

```bash
k6 run \
  --env USERS_FILE=./users.json \
  --out json=results/stress-$(date +%s).json \
  k6-stress-test.js
```

Ожидаемые результаты:
- Допускается замедление до p95 < 2 000 мс
- Error rate < 5%
- HAProxy и Node.js должны выдержать без OOM и перезапуска

---

### Script 3 — Поиск предела (breakpoint)

```bash
# Рекомендуется + Grafana для мониторинга в реальном времени
k6 run \
  --env USERS_FILE=./users.json \
  --out json=results/breakpoint-$(date +%s).json \
  k6-breakpoint.js
```

> ⚠️ **Запускайте только в окно обслуживания!**
> Скрипт намеренно давит до отказа — сервер **может** упасть.

Что искать в результатах:
1. При каком RPS начинает расти p95 (inflection point)?
2. При каком RPS error rate превысил 5%? 10%? 30%?
3. Сколько WebSocket-соединений выдержал HAProxy?
4. Какой компонент упал первым: Node.js, Redis, MySQL, HAProxy?

---

## Мониторинг в реальном времени (Grafana + InfluxDB)

```bash
# Запустить InfluxDB + Grafana через Docker
docker run -d -p 8086:8086 --name=influxdb influxdb:1.8
docker run -d -p 3000:3000 --name=grafana \
  -e "GF_AUTH_ANONYMOUS_ENABLED=true" \
  grafana/grafana

# Запустить тест с выводом в InfluxDB
k6 run \
  --out influxdb=http://localhost:8086/k6 \
  --env USERS_FILE=./users.json \
  k6-breakpoint.js
```

Импортируй дашборд k6 в Grafana: ID `2587` (k6 Load Testing Results).

---

## Полезные флаги k6

```bash
# Тихий режим (без progress bar)
k6 run --quiet ...

# Уменьшить нагрузку для быстрой проверки
k6 run --vus 50 --duration 2m ...

# Запустить только один сценарий из файла
k6 run --scenario rest_users k6-stress-test.js

# Посмотреть сводку после теста
k6 run ... 2>&1 | tee results/run.log
```

---

## Структура файлов

```
stress-tests/
├── k6-normal-load.js      # Script 1: 2 000 VU — дневная нагрузка
├── k6-stress-test.js      # Script 2: 10 000 VU — серьёзный стресс
├── k6-breakpoint.js       # Script 3: поиск предела (RPS nарастает до отказа)
├── helpers.js             # Общие утилиты (Socket.IO, шифрование, think-time)
├── users.example.json     # Пример структуры users.json
├── gen-test-users.sql     # SQL для генерации тестовых пользователей из БД
└── README.md              # Эта документация
```

---

## Что тестируется

| Компонент | Script 1 | Script 2 | Script 3 |
|-----------|----------|----------|----------|
| HAProxy TCP tunnel | ✅ | ✅ | ✅ |
| nftables (rate limit) | ✅ | ✅ | ✅ |
| Node.js HTTP REST | ✅ | ✅ | ✅ |
| Node.js Socket.IO (WS) | ✅ | ✅ | ✅ |
| Redis Pub/Sub | ✅ | ✅ | ✅ |
| MySQL (DB read/write) | ✅ | ✅ | ✅ |
| Signal Protocol keys | — | ✅ | ✅ |
| WebRTC ICE servers | — | ✅ | ✅ |
| Group messaging | — | ✅ | ✅ |
| WS reconnect storm | — | частично | ✅ |
| RPS saturation | — | — | ✅ |
