#!/usr/bin/env python3
"""
Detoxify HTTP Сервис — WorldMates Messenger
============================================
Запускается как отдельный процесс рядом с Node.js сервером.
Принимает текст, возвращает оценку токсичности.

Модели (в порядке предпочтения):
  - 'multilingual' — поддерживает RU/EN/DE/FR/ES и ещё 6 языков
  - 'original'     — только EN, быстрее

Эндпоинты:
  POST /analyze   { "text": "...", "lang": "ru" }
  GET  /health    — проверка работоспособности

Запуск:
  python3 detoxify_service.py
  или через systemd: см. detoxify.service

Порт: 5002 (можно изменить через DETOXIFY_PORT)
"""

import os
import sys
import json
import logging

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [Detoxify] %(levelname)s: %(message)s'
)
log = logging.getLogger(__name__)

try:
    from flask import Flask, request, jsonify
except ImportError:
    log.error("Flask не установлен. Запустите: pip install flask")
    sys.exit(1)

try:
    from detoxify import Detoxify
except ImportError:
    log.error("detoxify не установлен. Запустите: pip install detoxify")
    sys.exit(1)

# ─── Конфиг ───────────────────────────────────────────────────────────────────

PORT         = int(os.environ.get('DETOXIFY_PORT', 5002))
HOST         = os.environ.get('DETOXIFY_HOST', '127.0.0.1')
MODEL_NAME   = os.environ.get('DETOXIFY_MODEL', 'multilingual')  # multilingual / original
MAX_TEXT_LEN = 2000  # символов — detoxify лучше работает на коротких текстах

# Порог: если toxicity >= BLOCK_THRESHOLD — блокировать
BLOCK_THRESHOLD = float(os.environ.get('DETOXIFY_BLOCK_THRESHOLD', '0.85'))
# Порог: если toxicity >= WARN_THRESHOLD — логировать и ставить в очередь
WARN_THRESHOLD  = float(os.environ.get('DETOXIFY_WARN_THRESHOLD', '0.60'))

# ─── Инициализация Detoxify ───────────────────────────────────────────────────

log.info(f"Загружаю модель Detoxify '{MODEL_NAME}' (первый раз: ~300MB скачивается)...")
try:
    model = Detoxify(MODEL_NAME)
    log.info(f"Detoxify модель '{MODEL_NAME}' загружена успешно.")
except Exception as e:
    log.error(f"Не удалось загрузить модель Detoxify: {e}")
    sys.exit(1)

# ─── Flask приложение ─────────────────────────────────────────────────────────

app = Flask(__name__)


@app.route('/health', methods=['GET'])
def health():
    return jsonify({'status': 'ok', 'service': 'detoxify', 'model': MODEL_NAME})


@app.route('/analyze', methods=['POST'])
def analyze():
    """
    Анализ текста на токсичность.

    Принимает JSON: { "text": "текст", "lang": "ru" }

    Возвращает:
    {
      "ok": true,
      "scores": {
        "toxicity":            0.95,
        "severe_toxicity":     0.02,
        "obscene":             0.87,
        "identity_attack":     0.01,
        "insult":              0.91,
        "threat":              0.03,
        "sexual_explicit":     0.10
      },
      "is_toxic":     true,
      "max_score":    0.95,
      "top_category": "toxicity",
      "action":       "block"   // block | warn | allow
    }
    """
    try:
        data = request.get_json(silent=True) or {}
        text = str(data.get('text', '')).strip()

        if not text:
            return jsonify({'ok': False, 'error': 'Текст не передан'}), 400

        # Обрезаем до MAX_TEXT_LEN — модель не любит очень длинные тексты
        if len(text) > MAX_TEXT_LEN:
            text = text[:MAX_TEXT_LEN]

        # Detoxify predict
        raw_scores = model.predict(text)

        # Нормализуем: значения могут быть float32 (numpy) — конвертируем в Python float
        scores = {k: round(float(v), 4) for k, v in raw_scores.items()}

        max_score    = max(scores.values()) if scores else 0.0
        top_category = max(scores, key=scores.get) if scores else 'unknown'

        if max_score >= BLOCK_THRESHOLD:
            action = 'block'
        elif max_score >= WARN_THRESHOLD:
            action = 'warn'
        else:
            action = 'allow'

        result = {
            'ok':           True,
            'scores':       scores,
            'is_toxic':     max_score >= WARN_THRESHOLD,
            'max_score':    round(max_score, 4),
            'top_category': top_category,
            'action':       action,
        }

        log.info(
            f"Анализ: action={action} top={top_category} "
            f"max_score={max_score:.2f} text_len={len(text)}"
        )

        return jsonify(result)

    except Exception as e:
        log.exception(f"Ошибка анализа текста: {e}")
        return jsonify({'ok': False, 'error': 'Внутренняя ошибка сервиса'}), 500


# ─── Запуск ───────────────────────────────────────────────────────────────────

if __name__ == '__main__':
    log.info(f"Detoxify сервис запущен на {HOST}:{PORT} (модель: {MODEL_NAME})")
    app.run(host=HOST, port=PORT, debug=False, use_reloader=False, threaded=True)
