const encoder = new TextEncoder();
const decoder = new TextDecoder();

async function deriveKey(secret: string): Promise<CryptoKey> {
  const material = await crypto.subtle.importKey('raw', encoder.encode(secret), 'PBKDF2', false, ['deriveKey']);
  return crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt: encoder.encode('worldmates-desktop-aes-gcm'),
      iterations: 120000,
      hash: 'SHA-256'
    },
    material,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt']
  );
}

function toBase64(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes));
}

function fromBase64(value: string): Uint8Array {
  return Uint8Array.from(atob(value), (char) => char.charCodeAt(0));
}

export async function encryptAesGcm(plainText: string, secret: string): Promise<string> {
  const key = await deriveKey(secret);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const encrypted = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, encoder.encode(plainText));

  const payload = {
    alg: 'AES-256-GCM',
    iv: toBase64(iv),
    data: toBase64(new Uint8Array(encrypted))
  };

  return `enc:${btoa(JSON.stringify(payload))}`;
}

export async function tryDecryptAesGcm(cipherText: string, secret: string): Promise<string> {
  if (!cipherText.startsWith('enc:')) {
    return cipherText;
  }

  try {
    const json = atob(cipherText.slice(4));
    const payload = JSON.parse(json) as { iv: string; data: string };
    const key = await deriveKey(secret);
    const decrypted = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: fromBase64(payload.iv) },
      key,
      fromBase64(payload.data)
    );
    return decoder.decode(decrypted);
  } catch {
    return '[Encrypted message: decrypt failed]';
  }
}
