/**
 * ⏱️ Adaptive Throttle - Динамічне обмеження частоти повідомлень
 *
 * Обмежує частоту відправки typing indicators та інших non-critical подій
 * в залежності від якості з'єднання клієнта.
 */

class AdaptiveThrottle {
    constructor() {
        // Зберігаємо timestamp останньої події для кожного користувача
        this.lastEventTime = new Map();

        // Мінімальний інтервал між typing indicators (мілісекунди)
        this.typingThrottleMs = 3000; // 3 секунди за замовчуванням

        // Мінімальний інтервал між online status updates
        this.onlineStatusThrottleMs = 10000; // 10 секунд
    }

    /**
     * Перевірити чи дозволено відправити typing indicator
     * @param {string} userId - ID користувача
     * @param {string} chatId - ID чату
     * @returns {boolean} - true якщо дозволено
     */
    canSendTyping(userId, chatId) {
        const key = `typing_${userId}_${chatId}`;
        const now = Date.now();
        const lastTime = this.lastEventTime.get(key) || 0;

        if (now - lastTime >= this.typingThrottleMs) {
            this.lastEventTime.set(key, now);
            return true;
        }

        return false;
    }

    /**
     * Перевірити чи дозволено відправити online status
     * @param {string} userId - ID користувача
     * @returns {boolean} - true якщо дозволено
     */
    canSendOnlineStatus(userId) {
        const key = `online_${userId}`;
        const now = Date.now();
        const lastTime = this.lastEventTime.get(key) || 0;

        if (now - lastTime >= this.onlineStatusThrottleMs) {
            this.lastEventTime.set(key, now);
            return true;
        }

        return false;
    }

    /**
     * Встановити інтервал throttle для typing
     * @param {number} ms - Інтервал в мілісекундах
     */
    setTypingThrottle(ms) {
        this.typingThrottleMs = ms;
    }

    /**
     * Встановити інтервал throttle для online status
     * @param {number} ms - Інтервал в мілісекундах
     */
    setOnlineStatusThrottle(ms) {
        this.onlineStatusThrottleMs = ms;
    }

    /**
     * Очистити дані користувача (при disconnect)
     * @param {string} userId - ID користувача
     */
    clearUser(userId) {
        // Видаляємо всі ключі пов'язані з користувачем
        for (const [key, _] of this.lastEventTime.entries()) {
            if (key.includes(userId)) {
                this.lastEventTime.delete(key);
            }
        }
    }

    /**
     * Отримати статистику
     * @returns {Object} - Статистика throttle
     */
    getStats() {
        return {
            trackedEvents: this.lastEventTime.size,
            typingThrottleMs: this.typingThrottleMs,
            onlineStatusThrottleMs: this.onlineStatusThrottleMs
        };
    }
}

// Singleton instance
const throttle = new AdaptiveThrottle();

module.exports = throttle;
