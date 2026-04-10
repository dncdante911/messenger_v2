'use strict';
/**
 * Generates build/icon.ico from pure Node.js — no external tools needed.
 * Produces a multi-resolution ICO (16, 32, 48, 256 px) with a blue circle
 * matching the in-app tray icon colour (#1f6feb).
 *
 * Run: node scripts/gen-icon.cjs
 */

const zlib = require('node:zlib');
const fs   = require('node:fs');
const path = require('node:path');

// ─── PNG builder (same logic as electron/main.cjs) ────────────────────────────

function buildCrc32Table() {
  const t = new Uint32Array(256);
  for (let i = 0; i < 256; i++) {
    let c = i;
    for (let j = 0; j < 8; j++) c = (c & 1) ? (0xedb88320 ^ (c >>> 1)) : (c >>> 1);
    t[i] = c >>> 0;
  }
  return t;
}
const CRC_TABLE = buildCrc32Table();

function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = (CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8)) >>> 0;
  return (c ^ 0xffffffff) >>> 0;
}

function pngChunk(type, data) {
  const typeBuf = Buffer.from(type, 'ascii');
  const lenBuf  = Buffer.alloc(4); lenBuf.writeUInt32BE(data.length);
  const crcBuf  = Buffer.alloc(4); crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])));
  return Buffer.concat([lenBuf, typeBuf, data, crcBuf]);
}

function buildRgbaPng(w, h, pixelFn) {
  const sig  = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 6; // bit-depth=8, RGBA

  const rows = [];
  for (let y = 0; y < h; y++) {
    const row = Buffer.alloc(1 + w * 4);
    row[0] = 0;
    for (let x = 0; x < w; x++) {
      const [r, g, b, a] = pixelFn(x, y, w, h);
      row[1 + x * 4]     = r;
      row[1 + x * 4 + 1] = g;
      row[1 + x * 4 + 2] = b;
      row[1 + x * 4 + 3] = a;
    }
    rows.push(row);
  }
  const idat = zlib.deflateSync(Buffer.concat(rows));
  return Buffer.concat([sig, pngChunk('IHDR', ihdr), pngChunk('IDAT', idat), pngChunk('IEND', Buffer.alloc(0))]);
}

function makeCirclePng(size, r, g, b) {
  const cx = (size - 1) / 2, cy = (size - 1) / 2, radius = size / 2 - 1.5;
  return buildRgbaPng(size, size, (x, y) => {
    const dx = x - cx, dy = y - cy;
    const dist = Math.sqrt(dx * dx + dy * dy);
    const alpha = dist <= radius     ? 255
                : dist <= radius + 1 ? Math.round((radius + 1 - dist) * 255)
                : 0;
    return [r, g, b, alpha];
  });
}

// ─── ICO builder ──────────────────────────────────────────────────────────────
// ICO = ICONDIR header + ICONDIRENTRY[] + image data (PNG blobs for Vista+)

const SIZES = [16, 32, 48, 256];
const [R, G, B] = [31, 111, 235]; // #1f6feb

const pngs = SIZES.map(s => makeCirclePng(s, R, G, B));

const HEADER_SIZE = 6;                         // ICONDIR
const ENTRY_SIZE  = 16;                        // ICONDIRENTRY per image
const DATA_START  = HEADER_SIZE + ENTRY_SIZE * SIZES.length;

// ICONDIR
const header = Buffer.alloc(6);
header.writeUInt16LE(0,            0); // reserved
header.writeUInt16LE(1,            2); // type: 1 = ICO
header.writeUInt16LE(SIZES.length, 4);

// ICONDIRENTRYs
const entries = [];
let offset = DATA_START;
for (let i = 0; i < SIZES.length; i++) {
  const size = SIZES[i];
  const png  = pngs[i];
  const entry = Buffer.alloc(16);
  entry[0] = size === 256 ? 0 : size; // width  (0 encodes 256)
  entry[1] = size === 256 ? 0 : size; // height (0 encodes 256)
  entry[2] = 0;                       // colorCount (0 = no palette)
  entry[3] = 0;                       // reserved
  entry.writeUInt16LE(1,          4); // planes
  entry.writeUInt16LE(32,         6); // bitCount (32 = RGBA)
  entry.writeUInt32LE(png.length, 8); // size of image data
  entry.writeUInt32LE(offset,    12); // offset from file start
  offset += png.length;
  entries.push(entry);
}

const ico = Buffer.concat([header, ...entries, ...pngs]);

const outPath = path.join(__dirname, '../build/icon.ico');
fs.mkdirSync(path.dirname(outPath), { recursive: true });
fs.writeFileSync(outPath, ico);
console.log(`✓ Generated ${outPath} (${ico.length} bytes, sizes: ${SIZES.join(', ')}px)`);
