import { TURN_FALLBACK } from './config';

export async function createPeerConnection(servers: RTCIceServer[]) {
  const iceServers = servers.length > 0 ? servers : TURN_FALLBACK;
  return new RTCPeerConnection({
    iceServers,
    bundlePolicy:  'max-bundle',
    rtcpMuxPolicy: 'require',
    iceTransportPolicy: 'all',
  });
}

export async function createLocalAudioStream() {
  return navigator.mediaDevices.getUserMedia({ audio: true, video: false });
}

export async function createLocalVideoStream() {
  return navigator.mediaDevices.getUserMedia({ audio: true, video: true });
}

export async function createScreenShareStream(): Promise<MediaStream> {
  // getDisplayMedia captures the screen / window / tab
  const stream = await (navigator.mediaDevices as MediaDevices & {
    getDisplayMedia(constraints?: MediaStreamConstraints): Promise<MediaStream>;
  }).getDisplayMedia({ video: true, audio: true });
  return stream;
}

/** Swap the video track in a peer connection for screen share (or back to camera). */
export function replaceVideoTrack(
  pc: RTCPeerConnection,
  newTrack: MediaStreamTrack,
): void {
  const sender = pc.getSenders().find(s => s.track?.kind === 'video');
  if (sender) sender.replaceTrack(newTrack);
}
