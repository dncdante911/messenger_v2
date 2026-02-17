/**
 * 📊 Connection Monitor - Моніторинг стану з'єднань та латентності
 *
 * Відстежує:
 * - Кількість активних з'єднань
 * - Латентність для кожного клієнта
 * - Якість з'єднання (EXCELLENT/GOOD/POOR/OFFLINE)
 * - Середню латентність по всіх клієнтах
 */

class ConnectionMonitor {
    constructor() {
        // Map: socketId → {latency, quality, lastPing, transport}
        this.connections = new Map();

        // Статистика
        this.stats = {
            total: 0,
            active: 0,
            peak: 0,
            avgLatency: 0,
            byQuality: {
                excellent: 0,
                good: 0,
                poor: 0,
                offline: 0
            },
            byTransport: {
                websocket: 0,
                polling: 0
            }
        };

        // Thresholds для визначення якості
        this.EXCELLENT_THRESHOLD = 200;   // < 200ms
        this.GOOD_THRESHOLD = 500;        // 200-500ms
        this.POOR_THRESHOLD = 2000;       // 500-2000ms
        // > 2000ms = OFFLINE
    }

    /**
     * Зареєструвати нове з'єднання
     * @param {string} socketId - ID сокета
     * @param {string} transport - Тип транспорту (websocket/polling)
     */
    registerConnection(socketId, transport) {
        this.connections.set(socketId, {
            latency: 0,
            quality: 'EXCELLENT',
            lastPing: Date.now(),
            transport: transport,
            connectedAt: Date.now()
        });

        this.stats.total++;
        this.stats.active++;
        this.stats.byTransport[transport] = (this.stats.byTransport[transport] || 0) + 1;

        if (this.stats.active > this.stats.peak) {
            this.stats.peak = this.stats.active;
        }

        this.updateQualityStats();
    }

    /**
     * Оновити латентність для з'єднання
     * @param {string} socketId - ID сокета
     * @param {number} latency - Латентність в мілісекундах
     */
    updateLatency(socketId, latency) {
        const conn = this.connections.get(socketId);
        if (!conn) return;

        conn.latency = latency;
        conn.lastPing = Date.now();

        // Визначаємо якість на основі латентності
        if (latency < this.EXCELLENT_THRESHOLD) {
            conn.quality = 'EXCELLENT';
        } else if (latency < this.GOOD_THRESHOLD) {
            conn.quality = 'GOOD';
        } else if (latency < this.POOR_THRESHOLD) {
            conn.quality = 'POOR';
        } else {
            conn.quality = 'OFFLINE';
        }

        this.updateQualityStats();
        this.updateAvgLatency();
    }

    /**
     * Оновити транспорт (при upgrade з polling на websocket)
     * @param {string} socketId - ID сокета
     * @param {string} newTransport - Новий транспорт
     */
    updateTransport(socketId, newTransport) {
        const conn = this.connections.get(socketId);
        if (!conn) return;

        const oldTransport = conn.transport;
        conn.transport = newTransport;

        // Оновлюємо статистику
        this.stats.byTransport[oldTransport]--;
        this.stats.byTransport[newTransport] = (this.stats.byTransport[newTransport] || 0) + 1;
    }

    /**
     * Видалити з'єднання (при disconnect)
     * @param {string} socketId - ID сокета
     */
    removeConnection(socketId) {
        const conn = this.connections.get(socketId);
        if (!conn) return;

        this.stats.active--;
        this.stats.byTransport[conn.transport]--;
        this.connections.delete(socketId);

        this.updateQualityStats();
        this.updateAvgLatency();
    }

    /**
     * Отримати якість з'єднання для клієнта
     * @param {string} socketId - ID сокета
     * @returns {string} - EXCELLENT/GOOD/POOR/OFFLINE
     */
    getConnectionQuality(socketId) {
        const conn = this.connections.get(socketId);
        return conn ? conn.quality : 'OFFLINE';
    }

    /**
     * Отримати латентність для клієнта
     * @param {string} socketId - ID сокета
     * @returns {number} - Латентність в мілісекундах
     */
    getLatency(socketId) {
        const conn = this.connections.get(socketId);
        return conn ? conn.latency : 0;
    }

    /**
     * Оновити статистику по якості з'єднань
     */
    updateQualityStats() {
        const counts = {
            excellent: 0,
            good: 0,
            poor: 0,
            offline: 0
        };

        for (const [_, conn] of this.connections.entries()) {
            const quality = conn.quality.toLowerCase();
            counts[quality] = (counts[quality] || 0) + 1;
        }

        this.stats.byQuality = counts;
    }

    /**
     * Оновити середню латентність
     */
    updateAvgLatency() {
        if (this.connections.size === 0) {
            this.stats.avgLatency = 0;
            return;
        }

        let totalLatency = 0;
        for (const [_, conn] of this.connections.entries()) {
            totalLatency += conn.latency;
        }

        this.stats.avgLatency = Math.round(totalLatency / this.connections.size);
    }

    /**
     * Отримати повну статистику
     * @returns {Object} - Статистика
     */
    getStats() {
        return {
            ...this.stats,
            connections: Array.from(this.connections.entries()).map(([id, conn]) => ({
                id,
                latency: conn.latency,
                quality: conn.quality,
                transport: conn.transport,
                uptime: Date.now() - conn.connectedAt
            }))
        };
    }

    /**
     * Виведення статистики в консоль (для логування)
     */
    logStats() {
        console.log('========================================');
        console.log('📊 Connection Monitor Stats');
        console.log(`🔌 Active: ${this.stats.active} | Peak: ${this.stats.peak} | Total: ${this.stats.total}`);
        console.log(`⏱️ Avg Latency: ${this.stats.avgLatency}ms`);
        console.log(`📶 Quality:`);
        console.log(`   ✅ EXCELLENT: ${this.stats.byQuality.excellent}`);
        console.log(`   🟡 GOOD: ${this.stats.byQuality.good}`);
        console.log(`   🟠 POOR: ${this.stats.byQuality.poor}`);
        console.log(`   🔴 OFFLINE: ${this.stats.byQuality.offline}`);
        console.log(`📡 Transport:`);
        console.log(`   🚀 WebSocket: ${this.stats.byTransport.websocket || 0}`);
        console.log(`   📮 Polling: ${this.stats.byTransport.polling || 0}`);
        console.log('========================================');
    }
}

// Singleton instance
const monitor = new ConnectionMonitor();

module.exports = monitor;
