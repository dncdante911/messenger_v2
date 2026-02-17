# 🔧 TURN Server Integration Guide for WorldMates

## ✅ Що було зроблено

Інтеграція TURN сервера у ваш існуючий Node.js backend для підтримки WebRTC відеодзвінків через NAT/Firewall.

---

## 📁 Додані файли

### 1. `helpers/turn-credentials.js`
**Призначення**: Генерація динамічних TURN credentials з використанням HMAC-SHA1

**Експортує**:
- `generateTurnCredentials(userId, ttl)` - генерує username/password
- `getIceServers(userId)` - повертає масив ICE серверів (STUN + TURN)
- `getIceConfigForAndroid(userId)` - спрощений формат для Android
- `validateTurnCredentials(username, password)` - валідація credentials

**Використання**:
```javascript
const turnHelper = require('./helpers/turn-credentials');

// Для конкретного користувача
const iceServers = turnHelper.getIceServers(userId);
```

---

## 🔄 Змінені файли

### 1. `main.js` - додано REST API endpoints

#### Додані роути:

**GET `/api/ice-servers/:userId`**
```bash
curl http://worldmates.club:449/api/ice-servers/123
```

Response:
```json
{
  "success": true,
  "iceServers": [
    { "urls": "stun:stun.l.google.com:19302" },
    {
      "urls": ["turn:worldmates.club:3478?transport=udp"],
      "username": "1705488600:123",
      "credential": "base64encodedpassword"
    }
  ],
  "timestamp": 1705488600000
}
```

**POST `/api/turn-credentials`**
```bash
curl -X POST http://worldmates.club:449/api/turn-credentials \
  -H "Content-Type: application/json" \
  -d '{"userId": "123", "ttl": 86400}'
```

**GET `/api/health`**
Health check для моніторингу.

---

### 2. `listeners/calls-listener.js` - автоматична передача TURN credentials

#### Що змінено:

1. **Імпорт helper'а** (line 12):
```javascript
const turnHelper = require('../helpers/turn-credentials');
```

2. **Event `call:incoming`** - тепер включає `iceServers`:
```javascript
const callData = {
    fromId: fromId,
    fromName: "...",
    callType: callType,
    roomName: roomName,
    sdpOffer: sdpOffer,
    iceServers: iceServers  // ✅ ДОДАНО
};
```

3. **Event `call:answer`** - тепер включає `iceServers`:
```javascript
const answerData = {
    roomName: roomName,
    sdpAnswer: sdpAnswer,
    acceptedBy: userId,
    iceServers: iceServers  // ✅ ДОДАНО
};
```

4. **Event `group_call:incoming`** - тепер включає `iceServers` для кожного учасника.

---

## 🚀 Налаштування TURN сервера

### 1. Встановлення coturn

```bash
sudo apt-get update
sudo apt-get install coturn -y
```

### 2. Налаштування `/etc/turnserver.conf`

Використайте готовий конфіг з `server/turnserver.conf`:

```bash
# Backup оригінального
sudo cp /etc/turnserver.conf /etc/turnserver.conf.backup

# Скопіювати новий конфіг
sudo cp /home/user/worldmates_mess_v1.0/server/turnserver.conf /etc/turnserver.conf

# Отримати ваш публічний IP
curl ifconfig.me

# Відредагувати конфіг
sudo nano /etc/turnserver.conf

# Знайти рядок:
# external-ip=YOUR_PUBLIC_IP_HERE
# Замінити на ваш реальний IP
```

**Ключові параметри**:
```ini
listening-port=3478
tls-listening-port=5349
realm=worldmates.club
external-ip=YOUR_PUBLIC_IP       # ⚠️ ОБОВ'ЯЗКОВО замінити!
static-auth-secret=a7f3e9c2d8b4f6a1c5e8d9b2f4a6c8e1d3f5a7b9c2e4f6a8b1d3f5a7c9e2f4a6

# SSL сертифікати
cert=/var/www/httpd-cert/www-root/worldmates.club_le2.crt
pkey=/var/www/httpd-cert/www-root/worldmates.club_le2.key
```

### 3. Увімкнути coturn service

```bash
# Відредагувати /etc/default/coturn
sudo nano /etc/default/coturn

# Розкоментувати:
TURNSERVER_ENABLED=1

# Запустити
sudo systemctl start coturn
sudo systemctl enable coturn

# Перевірити статус
sudo systemctl status coturn
```

### 4. Відкрити порти у firewall

```bash
# TURN порти
sudo ufw allow 3478/tcp
sudo ufw allow 3478/udp
sudo ufw allow 5349/tcp

# Relay порти
sudo ufw allow 49152:65535/udp
sudo ufw allow 49152:65535/tcp

# Node.js порт (якщо ще не відкрито)
sudo ufw allow 449/tcp

sudo ufw reload
```

---

## 🧪 Тестування

### 1. Перевірити що TURN сервер працює

```bash
# Перевірити процес
sudo systemctl status coturn

# Перевірити логи
sudo tail -f /var/log/turnserver.log

# Перевірити порти
sudo netstat -tulpn | grep 3478
```

### 2. Тестувати генерацію credentials

Створіть тестовий файл `test-turn.js`:

```javascript
const turnHelper = require('./helpers/turn-credentials');

const userId = 123;
const credentials = turnHelper.generateTurnCredentials(userId);

console.log('🔐 TURN Credentials:');
console.log('Username:', credentials.username);
console.log('Password:', credentials.password);
console.log('Expires:', credentials.expiresAt);

console.log('\n📡 ICE Servers:');
const iceServers = turnHelper.getIceServers(userId);
console.log(JSON.stringify(iceServers, null, 2));
```

Запустити:
```bash
cd /home/user/worldmates_mess_v1.0/api-server-files/nodejs
node test-turn.js
```

### 3. Перевірити REST API endpoints

```bash
# Запустити Node.js сервер (якщо ще не запущено)
cd /home/user/worldmates_mess_v1.0/api-server-files/nodejs
node main.js

# Тестувати ICE servers endpoint
curl http://localhost:449/api/ice-servers/123

# Тестувати health check
curl http://localhost:449/api/health
```

### 4. Онлайн тест TURN сервера

1. Відкрити: https://webrtc.github.io/samples/src/content/peerconnection/trickle-ice/
2. Згенерувати credentials:
   ```bash
   node test-turn.js
   ```
3. Додати TURN сервер у форму:
   - URLs: `turn:worldmates.club:3478`
   - Username: з виводу test-turn.js
   - Password: з виводу test-turn.js
4. Клацнути "Gather candidates"
5. Шукати `typ relay` - це означає TURN працює! ✅

---

## 📱 Інтеграція з Android App

### WebRTCManager.kt - отримання ICE servers

Замість hardcoded ICE servers, Android app має отримувати їх з сервера:

```kotlin
// У CallsViewModel.kt або WebRTCManager.kt

suspend fun getIceServers(userId: Int): List<PeerConnection.IceServer> {
    return withContext(Dispatchers.IO) {
        try {
            val response = apiService.getIceServers(userId)

            if (response.success && response.iceServers != null) {
                response.iceServers.map { server ->
                    val builder = PeerConnection.IceServer.builder(server.urls)

                    if (server.username != null && server.credential != null) {
                        builder.setUsername(server.username)
                        builder.setPassword(server.credential)
                    }

                    builder.createIceServer()
                }
            } else {
                // Fallback до Google STUN
                listOf(
                    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                        .createIceServer()
                )
            }
        } catch (e: Exception) {
            Log.e("WebRTC", "Failed to get ICE servers: ${e.message}")
            // Fallback
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                    .createIceServer()
            )
        }
    }
}
```

### ApiService.kt - додати endpoint

```kotlin
@GET("api/ice-servers/{userId}")
suspend fun getIceServers(@Path("userId") userId: Int): IceServersResponse

// Data classes
data class IceServersResponse(
    val success: Boolean,
    val iceServers: List<IceServerConfig>?,
    val timestamp: Long
)

data class IceServerConfig(
    val urls: Any,  // String або List<String>
    val username: String?,
    val credential: String?
)
```

### Використання при ініціації дзвінка

```kotlin
// У CallsViewModel.kt

fun initiateCall(recipientId: Int, callType: String) {
    viewModelScope.launch {
        // 1. Отримати ICE servers
        val iceServers = getIceServers(getCurrentUserId())

        // 2. Створити PeerConnection з динамічними ICE servers
        webRTCManager.createPeerConnection(iceServers)

        // 3. Створити offer
        val offer = webRTCManager.createOffer()

        // 4. Відправити через Socket.IO
        socketManager.emit("call:initiate", mapOf(
            "fromId" to getCurrentUserId(),
            "toId" to recipientId,
            "callType" to callType,
            "roomName" to generateRoomName(),
            "sdpOffer" to offer
        ))
    }
}
```

### Обробка вхідного дзвінка з ICE servers

```kotlin
// У CallsViewModel.kt або SocketHandler

socket.on("call:incoming") { args ->
    val data = args[0] as JSONObject

    val fromId = data.getInt("fromId")
    val callType = data.getString("callType")
    val sdpOffer = data.getString("sdpOffer")
    val iceServersJson = data.getJSONArray("iceServers")  // ✅ Отримати з сервера

    // Парсинг ICE servers
    val iceServers = parseIceServers(iceServersJson)

    // Створити PeerConnection з ICE servers
    webRTCManager.createPeerConnection(iceServers)

    // Встановити remote offer
    webRTCManager.setRemoteDescription(sdpOffer)

    // Показати UI вхідного дзвінка
    _callState.value = CallState.Incoming(fromId, callType)
}

fun parseIceServers(jsonArray: JSONArray): List<PeerConnection.IceServer> {
    val servers = mutableListOf<PeerConnection.IceServer>()

    for (i in 0 until jsonArray.length()) {
        val server = jsonArray.getJSONObject(i)
        val urls = server.get("urls")

        val builder = when (urls) {
            is String -> PeerConnection.IceServer.builder(urls)
            is JSONArray -> {
                val urlsList = (0 until urls.length()).map { urls.getString(it) }
                PeerConnection.IceServer.builder(urlsList)
            }
            else -> continue
        }

        if (server.has("username") && server.has("credential")) {
            builder.setUsername(server.getString("username"))
            builder.setPassword(server.getString("credential"))
        }

        servers.add(builder.createIceServer())
    }

    return servers
}
```

---

## 🔐 Безпека

### 1. Змінити static-auth-secret

⚠️ **ВАЖЛИВО**: Змініть секрет на унікальний для вашого сервера!

```bash
# Згенерувати новий секрет
openssl rand -hex 32

# Оновити в /etc/turnserver.conf
sudo nano /etc/turnserver.conf
# Знайти: static-auth-secret=...
# Замінити на новий

# Оновити в helpers/turn-credentials.js
nano /home/user/worldmates_mess_v1.0/api-server-files/nodejs/helpers/turn-credentials.js
# Змінити константу TURN_SECRET

# Перезапустити
sudo systemctl restart coturn
# Перезапустити Node.js (PM2 або вручну)
```

### 2. Обмежити доступ до TURN

У `/etc/turnserver.conf` розкоментуйте:
```ini
# Дозволити тільки з певних IP
allowed-peer-ip=YOUR_APP_SERVER_IP
```

### 3. Rate limiting для API endpoints

Додайте в `main.js`:
```javascript
const rateLimit = require('express-rate-limit');

const limiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 хвилин
    max: 100 // макс 100 запитів з одного IP
});

app.use('/api/', limiter);
```

---

## 📊 Моніторинг

### Логи TURN сервера
```bash
sudo tail -f /var/log/turnserver.log
```

### Логи Node.js
```bash
# Якщо використовуєте PM2
pm2 logs worldmates

# Або напряму
tail -f /path/to/your/server.log
```

### Статистика дзвінків (додати endpoint)

У `main.js` додайте:
```javascript
app.get('/api/admin/calls/stats', async (req, res) => {
    // TODO: Додати аутентифікацію адміністратора

    try {
        const activeCalls = await ctx.wo_calls.findAll({
            where: { status: 'connected' },
            attributes: ['room_name', 'from_id', 'to_id', 'call_type', 'created_at'],
            raw: true
        });

        res.json({
            success: true,
            stats: {
                totalActiveCalls: activeCalls.length,
                calls: activeCalls
            }
        });
    } catch (error) {
        res.status(500).json({
            success: false,
            error: 'Failed to get stats'
        });
    }
});
```

---

## 🚨 Troubleshooting

### TURN сервер не запускається

```bash
# Перевірити логи
sudo journalctl -u coturn -n 50

# Перевірити конфігурацію
sudo turnserver -c /etc/turnserver.conf --log-file=stdout

# Перевірити чи порти зайняті
sudo netstat -tulpn | grep 3478
```

### Node.js не може імпортувати helpers/turn-credentials.js

```bash
# Перевірити що файл існує
ls -la /home/user/worldmates_mess_v1.0/api-server-files/nodejs/helpers/turn-credentials.js

# Перевірити права доступу
chmod 644 /home/user/worldmates_mess_v1.0/api-server-files/nodejs/helpers/turn-credentials.js
```

### Android app не отримує TURN credentials

1. Перевірити що `call:incoming` event містить `iceServers`:
   ```bash
   # У логах Node.js має бути:
   [CALLS] Incoming call sent to user X with TURN credentials
   ```

2. Додати логування у Android:
   ```kotlin
   socket.on("call:incoming") { args ->
       val data = args[0] as JSONObject
       Log.d("WebRTC", "Incoming call data: $data")
       // Перевірити що iceServers присутні
   }
   ```

---

## ✅ Чеклист запуску

- [ ] coturn встановлено
- [ ] `/etc/turnserver.conf` налаштовано з правильним `external-ip`
- [ ] `/etc/default/coturn` має `TURNSERVER_ENABLED=1`
- [ ] TURN secret однаковий в `turnserver.conf` та `turn-credentials.js`
- [ ] Порти відкриті (3478, 5349, 49152-65535, 449)
- [ ] SSL сертифікати існують та шляхи правильні
- [ ] `helpers/turn-credentials.js` створено
- [ ] `main.js` оновлено з REST endpoints
- [ ] `calls-listener.js` оновлено з TURN credentials
- [ ] Node.js сервер перезапущено
- [ ] TURN тест пройшов: https://webrtc.github.io/samples/src/content/peerconnection/trickle-ice/
- [ ] `/api/ice-servers/123` працює: `curl http://localhost:449/api/ice-servers/123`
- [ ] Android app оновлено для отримання ICE servers з сервера

---

## 🎯 Як це працює

1. **Android додаток ініціює дзвінок**:
   - Запитує ICE servers через `GET /api/ice-servers/:userId`
   - Отримує STUN + TURN з динамічними credentials
   - Створює PeerConnection
   - Генерує SDP offer
   - Відправляє через Socket.IO `call:initiate`

2. **Node.js backend**:
   - Отримує `call:initiate` event
   - Генерує TURN credentials для отримувача через `turnHelper.getIceServers()`
   - Відправляє `call:incoming` з `iceServers` до отримувача

3. **Отримувач**:
   - Отримує `call:incoming` з готовими `iceServers`
   - Одразу створює PeerConnection з TURN credentials
   - Приймає дзвінок → генерує SDP answer
   - Відправляє `call:accept`

4. **Ініціатор отримує answer**:
   - Backend відправляє `call:answer` з `iceServers`
   - Встановлює remote description
   - Починається обмін ICE candidates

5. **TURN сервер (coturn)**:
   - Приймає підключення з credentials
   - Валідує через static-auth-secret + HMAC-SHA1
   - Створює relay для NAT traversal
   - Пропускає медіа-трафік між пірами

---

**Готово! Тепер ваш backend повністю підтримує WebRTC відеодзвінки через NAT/Firewall! 🚀📞📹**
