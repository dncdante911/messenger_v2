// ============================================================
// WorldMates Messenger — Media Utilities
// ============================================================

import { MEDIA_BASE_URL } from '../constants/api';

// ─────────────────────────────────────────────────────────────
// URL HELPERS
// ─────────────────────────────────────────────────────────────

/**
 * Resolve a potentially-relative media path to an absolute URL.
 * If the path already starts with "http", it is returned unchanged.
 */
export function getMediaUrl(path: string): string {
  if (!path) return '';
  if (path.startsWith('http://') || path.startsWith('https://')) return path;
  const base = MEDIA_BASE_URL.endsWith('/') ? MEDIA_BASE_URL : `${MEDIA_BASE_URL}/`;
  const relative = path.startsWith('/') ? path.slice(1) : path;
  return `${base}${relative}`;
}

// ─────────────────────────────────────────────────────────────
// FILE SIZE FORMATTING
// ─────────────────────────────────────────────────────────────

/**
 * Format a byte count into a human-readable file size string.
 *   1024       → "1 KB"
 *   1048576    → "1 MB"
 *   500        → "500 B"
 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

// ─────────────────────────────────────────────────────────────
// FILE TYPE HELPERS
// ─────────────────────────────────────────────────────────────

function getExtension(filename: string): string {
  const parts = filename.split('.');
  return parts.length > 1 ? parts[parts.length - 1].toLowerCase() : '';
}

/**
 * Returns a Feather icon name appropriate for the given filename extension.
 */
export function getFileIcon(filename: string): string {
  const ext = getExtension(filename);
  switch (ext) {
    case 'pdf':
    case 'doc':
    case 'docx':
    case 'txt':
    case 'rtf':
      return 'file-text';
    case 'mp4':
    case 'mov':
    case 'avi':
    case 'mkv':
    case 'webm':
      return 'film';
    case 'mp3':
    case 'ogg':
    case 'wav':
    case 'aac':
    case 'flac':
    case 'm4a':
      return 'music';
    case 'zip':
    case 'rar':
    case '7z':
    case 'tar':
    case 'gz':
      return 'archive';
    default:
      return 'file';
  }
}

/**
 * Returns the MIME type string for the given filename extension.
 */
export function getMimeType(filename: string): string {
  const ext = getExtension(filename);
  const mimeMap: Record<string, string> = {
    // Images
    jpg: 'image/jpeg',
    jpeg: 'image/jpeg',
    png: 'image/png',
    gif: 'image/gif',
    webp: 'image/webp',
    heic: 'image/heic',
    heif: 'image/heif',
    svg: 'image/svg+xml',
    bmp: 'image/bmp',
    // Videos
    mp4: 'video/mp4',
    mov: 'video/quicktime',
    avi: 'video/x-msvideo',
    mkv: 'video/x-matroska',
    webm: 'video/webm',
    // Audio
    mp3: 'audio/mpeg',
    ogg: 'audio/ogg',
    wav: 'audio/wav',
    aac: 'audio/aac',
    flac: 'audio/flac',
    m4a: 'audio/mp4',
    // Documents
    pdf: 'application/pdf',
    doc: 'application/msword',
    docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    xls: 'application/vnd.ms-excel',
    xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    ppt: 'application/vnd.ms-powerpoint',
    pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    txt: 'text/plain',
    csv: 'text/csv',
    // Archives
    zip: 'application/zip',
    rar: 'application/vnd.rar',
    '7z': 'application/x-7z-compressed',
    tar: 'application/x-tar',
    gz: 'application/gzip',
  };
  return mimeMap[ext] ?? 'application/octet-stream';
}

// ─────────────────────────────────────────────────────────────
// TYPE GUARDS
// ─────────────────────────────────────────────────────────────

const IMAGE_EXTS = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'heic', 'heif', 'svg', 'bmp']);
const VIDEO_EXTS = new Set(['mp4', 'mov', 'avi', 'mkv', 'webm', 'm4v', '3gp']);
const AUDIO_EXTS = new Set(['mp3', 'ogg', 'wav', 'aac', 'flac', 'm4a', 'opus']);

/** Returns true if the filename has a known image extension. */
export function isImage(filename: string): boolean {
  return IMAGE_EXTS.has(getExtension(filename));
}

/** Returns true if the filename has a known video extension. */
export function isVideo(filename: string): boolean {
  return VIDEO_EXTS.has(getExtension(filename));
}

/** Returns true if the filename has a known audio extension. */
export function isAudio(filename: string): boolean {
  return AUDIO_EXTS.has(getExtension(filename));
}

// ─────────────────────────────────────────────────────────────
// IMAGE COMPRESSION OPTIONS (expo-image-manipulator)
// ─────────────────────────────────────────────────────────────

/**
 * Default compression options for expo-image-manipulator.
 * Produces JPEG output at 80% quality, capped at 1920 px on the longest edge.
 */
export const compressImageOptions = {
  compress: 0.8,
  format: 'jpeg' as const,
  resize: {
    width: 1920,
    height: 1920,
  },
};

// ─────────────────────────────────────────────────────────────
// VIDEO THUMBNAIL
// ─────────────────────────────────────────────────────────────

/**
 * Generate a thumbnail URI for a local video file using expo-video-thumbnails.
 * Returns the thumbnail URI on success, or an empty string on failure.
 */
export async function generateThumbnailUri(videoUri: string): Promise<string> {
  try {
    // Dynamic import so the module is not required on platforms where it is unavailable.
    const VideoThumbnails = await import('expo-video-thumbnails');
    const { uri } = await VideoThumbnails.getThumbnailAsync(videoUri, { time: 0 });
    return uri;
  } catch {
    return '';
  }
}
