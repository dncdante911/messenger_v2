'use strict';

/**
 * Live Location Controller
 *
 * Ретранслює події реал-тайм обміну геолокацією між учасниками
 * приватного чату або групи через Socket.IO.
 *
 * Події (client → server → recipient):
 *   live_location_start  – користувач почав ділитись геолокацією
 *   live_location_update – черговий GPS-апдейт (кожні ~5 с)
 *   live_location_stop   – користувач зупинив передачу геолокації
 *
 * Payload від клієнта:
 *   { to_id: Long, is_group: Boolean, lat?: Double, lng?: Double, accuracy?: Float }
 *
 * Payload до одержувача:
 *   { from_id: Long, lat?: Double, lng?: Double, accuracy?: Float, timestamp: Long }
 */
const LiveLocationController = async (ctx, data, io, socket, event) => {
    const userId = socket.userId;
    if (!userId) return;

    const toId    = data.to_id;
    const isGroup = !!data.is_group;

    if (!toId) return;

    const payload = {
        from_id:   userId,
        lat:       data.lat       ?? null,
        lng:       data.lng       ?? null,
        accuracy:  data.accuracy  ?? null,
        timestamp: Date.now(),
    };

    if (isGroup) {
        // Broadcast to all members of the group room (except the sender's own socket)
        socket.to('group' + toId).emit(event, payload);
    } else {
        // Deliver to the target user's room (all their connected devices)
        io.to(String(toId)).emit(event, payload);
    }

    if (event === 'live_location_update') {
        console.log(
            `📍 [LiveLocation] user=${userId} → ${isGroup ? 'group' : 'user'}=${toId}` +
            ` (${data.lat}, ${data.lng})`
        );
    } else {
        console.log(`📍 [LiveLocation] ${event} user=${userId} → ${isGroup ? 'group' : 'user'}=${toId}`);
    }
};

module.exports = { LiveLocationController };
