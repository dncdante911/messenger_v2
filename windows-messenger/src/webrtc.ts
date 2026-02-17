import { TURN_FALLBACK } from './config';

export async function createPeerConnection(servers: RTCIceServer[]) {
  const iceServers = servers.length > 0 ? servers : TURN_FALLBACK;
  return new RTCPeerConnection({ iceServers });
}

export async function createLocalAudioStream() {
  return navigator.mediaDevices.getUserMedia({ audio: true, video: false });
}

export async function createLocalVideoStream() {
  return navigator.mediaDevices.getUserMedia({ audio: true, video: true });
}
