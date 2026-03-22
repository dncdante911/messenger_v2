#!/bin/bash
# ─── Запуск NudeNet сервиса ───────────────────────────────────────────────────
# Использование: ./start.sh [prod|dev]
# Для продакшена используется gunicorn (многопоточный, стабильный)
# Для разработки — встроенный Flask сервер

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV_DIR="$SCRIPT_DIR/venv"
SERVICE_FILE="$SCRIPT_DIR/nudenet_service.py"

# Создаём virtualenv если нет
if [ ! -d "$VENV_DIR" ]; then
    echo "[NudeNet] Создаю виртуальное окружение..."
    python3 -m venv "$VENV_DIR"
fi

# Активируем
source "$VENV_DIR/bin/activate"

# Обновляем зависимости
echo "[NudeNet] Устанавливаю зависимости..."
pip install -q -r "$SCRIPT_DIR/requirements.txt"

MODE="${1:-prod}"
PORT="${NUDENET_PORT:-5001}"
HOST="${NUDENET_HOST:-127.0.0.1}"

if [ "$MODE" = "prod" ]; then
    echo "[NudeNet] Запуск в режиме prod (gunicorn) на $HOST:$PORT..."
    exec gunicorn \
        --bind "$HOST:$PORT" \
        --workers 2 \
        --threads 2 \
        --timeout 30 \
        --log-level info \
        --access-logfile - \
        nudenet_service:app
else
    echo "[NudeNet] Запуск в режиме dev на $HOST:$PORT..."
    exec python3 "$SERVICE_FILE"
fi
