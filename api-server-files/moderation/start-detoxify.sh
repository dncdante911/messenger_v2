#!/bin/bash
# ─── Запуск Detoxify сервиса ──────────────────────────────────────────────────
# Использование: ./start-detoxify.sh [prod|dev]
# Использует тот же venv что и start.sh (NudeNet).
# Если venv ещё не создан — запустите сначала ./start.sh один раз.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV_DIR="$SCRIPT_DIR/venv"
SERVICE_FILE="$SCRIPT_DIR/detoxify_service.py"

# Создаём virtualenv если нет (на случай если запускается отдельно)
if [ ! -d "$VENV_DIR" ]; then
    echo "[setup] Создаю виртуальное окружение..."
    python3 -m venv "$VENV_DIR"
fi

source "$VENV_DIR/bin/activate"

# Устанавливаем зависимости
echo "[Detoxify] Устанавливаю зависимости..."
pip install -q -r "$SCRIPT_DIR/requirements-detoxify.txt"

MODE="${1:-prod}"
PORT="${DETOXIFY_PORT:-5002}"
HOST="${DETOXIFY_HOST:-127.0.0.1}"
MODEL="${DETOXIFY_MODEL:-multilingual}"

if [ "$MODE" = "prod" ]; then
    echo "[Detoxify] Запуск в режиме prod (gunicorn) на $HOST:$PORT (модель: $MODEL)..."
    exec gunicorn \
        --bind "$HOST:$PORT" \
        --workers 1 \
        --threads 4 \
        --timeout 60 \
        --log-level info \
        --access-logfile - \
        detoxify_service:app
else
    echo "[Detoxify] Запуск в режиме dev на $HOST:$PORT..."
    exec python3 "$SERVICE_FILE"
fi
