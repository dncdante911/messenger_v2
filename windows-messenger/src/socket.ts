import { io, Socket } from 'socket.io-client';
import { SOCKET_URL } from './config';
import type { MessageItem } from './types';

export type SocketHandlers = {
  onMessage: (message: MessageItem) => void;
  onStatus: (status: string) => void;
  onCallSignal?: (payload: unknown) => void;
};

export function createChatSocket(token: string, handlers: SocketHandlers): Socket {
  const socket = io(SOCKET_URL, {
    transports: ['websocket'],
    auth: { token }
  });

  socket.on('connect', () => {
    handlers.onStatus('Socket connected');
    socket.emit('join', { access_token: token });
  });

  socket.on('disconnect', () => {
    handlers.onStatus('Socket disconnected');
  });

  socket.on('private_message', (data: MessageItem) => {
    handlers.onMessage(data);
  });

  socket.on('new_message', (data: MessageItem) => {
    handlers.onMessage(data);
  });

  socket.on('call_signal', (payload: unknown) => {
    handlers.onCallSignal?.(payload);
  });

  return socket;
}
