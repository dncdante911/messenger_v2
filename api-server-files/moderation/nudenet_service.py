#!/usr/bin/env python3
"""
NudeNet HTTP Сервис — WorldMates Messenger
==========================================
Запускается как отдельный процесс рядом с Node.js сервером.
Принимает изображение, возвращает классификацию контента.

Эндпоинты:
  POST /analyze   — анализ изображения (multipart или raw bytes)
  GET  /health    — проверка работоспособности

Запуск:
  python3 nudenet_service.py
  или через systemd: см. nudenet.service

Порт: 5001 (можно изменить через NUDENET_PORT)
"""

import os
import sys
import io
import json
import logging
import hashlib
from typing import Optional

# Настройка логирования
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [NudeNet] %(levelname)s: %(message)s'
)
log = logging.getLogger(__name__)

# Проверяем зависимости
try:
    from flask import Flask, request, jsonify
except ImportError:
    log.error("Flask не установлен. Запустите: pip install flask")
    sys.exit(1)

try:
    from nudenet import NudeDetector
except ImportError:
    log.error("nudenet не установлен. Запустите: pip install nudenet")
    sys.exit(1)

try:
    from PIL import Image
except ImportError:
    log.error("Pillow не установлен. Запустите: pip install Pillow")
    sys.exit(1)

try:
    import imagehash
    PHASH_AVAILABLE = True
except ImportError:
    log.warning("ImageHash не установлен — pHash отключён. Запустите: pip install ImageHash")
    PHASH_AVAILABLE = False

# ─── Конфиг ───────────────────────────────────────────────────────────────────

PORT          = int(os.environ.get('NUDENET_PORT', 5001))
HOST          = os.environ.get('NUDENET_HOST', '127.0.0.1')
MAX_IMG_BYTES = 25 * 1024 * 1024  # 25 MB — соответствует лимиту Node.js

# Метки которые считаются "опасным" контентом (полная обнажённость)
EXPLICIT_LABELS = {
    'EXPOSED_GENITALIA_F',
    'EXPOSED_GENITALIA_M',
    'EXPOSED_BREAST_F',
    'EXPOSED_ANUS_F',
    'EXPOSED_ANUS_M',
    'EXPOSED_BUTTOCKS',
}

# Метки "частичной" обнажённости (для news-каналов → blur)
PARTIAL_LABELS = {
    'COVERED_GENITALIA_F',
    'COVERED_GENITALIA_M',
    'EXPOSED_BELLY',
    'EXPOSED_BREAST_M',
}

# ─── Инициализация NudeNet ────────────────────────────────────────────────────

log.info("Загружаю модель NudeNet (~80MB, первый раз долго)...")
try:
    detector = NudeDetector()
    log.info("NudeNet модель загружена успешно.")
except Exception as e:
    log.error(f"Не удалось загрузить модель NudeNet: {e}")
    sys.exit(1)

# ─── Flask приложение ─────────────────────────────────────────────────────────

app = Flask(__name__)


@app.route('/health', methods=['GET'])
def health():
    """Проверка работоспособности сервиса."""
    return jsonify({'status': 'ok', 'service': 'nudenet'})


@app.route('/analyze', methods=['POST'])
def analyze():
    """
    Анализ изображения.

    Принимает:
      - multipart/form-data с полем 'image'
      - или application/octet-stream (raw bytes)

    Возвращает:
    {
      "ok": true,
      "detections": [
        {"label": "EXPOSED_BREAST_F", "score": 0.92, "box": [x1, y1, x2, y2]},
        ...
      ],
      "summary": {
        "has_explicit": true,
        "has_partial": false,
        "max_score": 0.92,
        "explicit_labels": ["EXPOSED_BREAST_F"],
        "partial_labels": []
      },
      "sha256": "abc123..."
    }
    """
    try:
        # Получаем байты изображения
        img_bytes = _get_image_bytes(request)
        if img_bytes is None:
            return jsonify({'ok': False, 'error': 'Изображение не получено'}), 400

        if len(img_bytes) > MAX_IMG_BYTES:
            return jsonify({'ok': False, 'error': 'Файл слишком большой'}), 400

        # SHA-256 хэш для блэклиста
        sha256 = hashlib.sha256(img_bytes).hexdigest()

        # Проверяем что это валидное изображение и вычисляем pHash
        try:
            img = Image.open(io.BytesIO(img_bytes))
            img.verify()
            # После verify() объект нужно переоткрыть для pHash
            img = Image.open(io.BytesIO(img_bytes))
        except Exception:
            return jsonify({'ok': False, 'error': 'Невалидное изображение'}), 400

        # Perceptual hash (pHash) — 64-битный, устойчив к ресайзу/JPG-пересжатию
        phash_str = None
        phash_int = None
        if PHASH_AVAILABLE:
            try:
                ph = imagehash.phash(img)
                phash_str = str(ph)           # 16-символьный hex
                phash_int = int(str(ph), 16)  # BIGINT для Hamming-сравнения
            except Exception as e:
                log.warning(f"pHash не вычислен: {e}")

        # Запускаем NudeNet детекцию
        # NudeNet работает с путём к файлу, временно сохраняем в память
        import tempfile
        with tempfile.NamedTemporaryFile(suffix='.jpg', delete=False) as tmp:
            tmp.write(img_bytes)
            tmp_path = tmp.name

        try:
            detections_raw = detector.detect(tmp_path)
        finally:
            os.unlink(tmp_path)

        # Формируем результат
        detections = []
        explicit_found = []
        partial_found  = []
        max_score      = 0.0

        for det in (detections_raw or []):
            label = det.get('class', '')
            score = float(det.get('score', 0))
            box   = det.get('box', [])

            detections.append({
                'label': label,
                'score': round(score, 4),
                'box':   box
            })

            if score > max_score:
                max_score = score

            if label in EXPLICIT_LABELS and score >= 0.4:
                explicit_found.append(label)
            elif label in PARTIAL_LABELS and score >= 0.4:
                partial_found.append(label)

        result = {
            'ok': True,
            'sha256': sha256,
            'phash':  phash_str,   # 16-char hex или null
            'phash_int': phash_int, # BIGINT для Hamming distance на стороне Node.js
            'detections': detections,
            'summary': {
                'has_explicit':    len(explicit_found) > 0,
                'has_partial':     len(partial_found) > 0,
                'max_score':       round(max_score, 4),
                'explicit_labels': list(set(explicit_found)),
                'partial_labels':  list(set(partial_found)),
            }
        }

        log.info(
            f"Анализ: sha256={sha256[:12]}... "
            f"explicit={result['summary']['has_explicit']} "
            f"partial={result['summary']['has_partial']} "
            f"max_score={max_score:.2f}"
        )

        return jsonify(result)

    except Exception as e:
        log.exception(f"Ошибка анализа: {e}")
        return jsonify({'ok': False, 'error': 'Внутренняя ошибка сервиса'}), 500


def _get_image_bytes(req) -> Optional[bytes]:
    """Извлекает байты изображения из запроса (multipart или raw)."""
    content_type = req.content_type or ''

    if 'multipart/form-data' in content_type:
        f = req.files.get('image')
        if f:
            return f.read()
        return None

    # raw bytes (application/octet-stream или без content-type)
    data = req.get_data()
    return data if data else None


# ─── Запуск ───────────────────────────────────────────────────────────────────

if __name__ == '__main__':
    log.info(f"NudeNet сервис запущен на {HOST}:{PORT}")
    # use_reloader=False важно — иначе модель загружается дважды
    app.run(host=HOST, port=PORT, debug=False, use_reloader=False, threaded=True)
