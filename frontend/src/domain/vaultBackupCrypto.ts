import {
  BACKUP_FORMAT,
  BACKUP_FORMAT_VERSION,
  DEFAULT_PBKDF2_ITERATIONS,
  MAX_BACKUP_FILE_BYTES,
  assertSafeContext,
  innerManifestSchema,
  outerEnvelopeSchema,
  type InnerManifest,
  type OuterEnvelope,
} from '@/domain/vaultBackupEnvelope'

const TEXT_ENCODER = new TextEncoder()
const TEXT_DECODER = new TextDecoder()

export const BACKUP_PASSWORD_ERROR = '备份密码不正确或文件已损坏。'

function toBase64(bytes: ArrayBuffer | Uint8Array): string {
  const view = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes)
  let binary = ''
  for (let i = 0; i < view.length; i += 1) binary += String.fromCharCode(view[i]!)
  return btoa(binary)
}

function fromBase64(value: string): Uint8Array {
  const binary = atob(value)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i)
  return bytes
}

async function deriveKey(password: string, salt: Uint8Array, iterations: number): Promise<CryptoKey> {
  const material = await crypto.subtle.importKey(
    'raw',
    TEXT_ENCODER.encode(password),
    'PBKDF2',
    false,
    ['deriveKey'],
  )
  const saltBuffer = salt.buffer.slice(salt.byteOffset, salt.byteOffset + salt.byteLength) as ArrayBuffer
  return crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt: saltBuffer,
      iterations,
      hash: 'SHA-256',
    },
    material,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt'],
  )
}

export async function encryptBackupManifest(
  manifest: InnerManifest,
  password: string,
): Promise<OuterEnvelope> {
  assertSafeContext()
  if (!password || password.length < 12) {
    throw new Error('备份密码至少 12 位')
  }
  const salt = crypto.getRandomValues(new Uint8Array(16))
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const key = await deriveKey(password, salt, DEFAULT_PBKDF2_ITERATIONS)
  const plain = TEXT_ENCODER.encode(JSON.stringify(manifest))
  const ciphertext = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, plain)
  return {
    format: BACKUP_FORMAT,
    formatVersion: BACKUP_FORMAT_VERSION,
    kdf: {
      name: 'PBKDF2-SHA-256',
      iterations: DEFAULT_PBKDF2_ITERATIONS,
      salt: toBase64(salt),
    },
    cipher: {
      name: 'AES-256-GCM',
      iv: toBase64(iv),
    },
    ciphertext: toBase64(ciphertext),
  }
}

export async function decryptBackupFile(file: File, password: string): Promise<InnerManifest> {
  assertSafeContext()
  if (file.size > MAX_BACKUP_FILE_BYTES) {
    throw new Error('备份文件过大')
  }
  const text = await file.text()
  let parsed: unknown
  try {
    parsed = JSON.parse(text)
  } catch {
    throw new Error('备份文件格式不正确')
  }
  const outer = outerEnvelopeSchema.safeParse(parsed)
  if (!outer.success) {
    if (
      typeof parsed === 'object'
      && parsed !== null
      && 'formatVersion' in parsed
      && (parsed as { formatVersion?: unknown }).formatVersion !== BACKUP_FORMAT_VERSION
    ) {
      throw new Error('暂不支持该备份版本')
    }
    throw new Error('备份文件格式不正确')
  }
  try {
    const salt = fromBase64(outer.data.kdf.salt)
    const iv = fromBase64(outer.data.cipher.iv)
    const ciphertext = fromBase64(outer.data.ciphertext)
    const key = await deriveKey(password, salt, outer.data.kdf.iterations)
    const ivBuffer = iv.buffer.slice(iv.byteOffset, iv.byteOffset + iv.byteLength) as ArrayBuffer
    const cipherBuffer = ciphertext.buffer.slice(
      ciphertext.byteOffset,
      ciphertext.byteOffset + ciphertext.byteLength,
    ) as ArrayBuffer
    const plain = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: ivBuffer }, key, cipherBuffer)
    const innerJson = JSON.parse(TEXT_DECODER.decode(plain)) as unknown
    const inner = innerManifestSchema.safeParse(innerJson)
    if (!inner.success) {
      throw new Error('备份内容校验失败')
    }
    return inner.data
  } catch (error) {
    if (error instanceof Error && (
      error.message === '备份内容校验失败'
      || error.message === '暂不支持该备份版本'
      || error.message === '备份文件格式不正确'
      || error.message === '备份文件过大'
    )) {
      throw error
    }
    throw new Error(BACKUP_PASSWORD_ERROR)
  }
}

export function downloadOsVault(envelope: OuterEnvelope, createdAt = new Date()) {
  const pad = (n: number) => String(n).padStart(2, '0')
  const name = `online-safe-backup-${createdAt.getFullYear()}${pad(createdAt.getMonth() + 1)}${pad(createdAt.getDate())}-${pad(createdAt.getHours())}${pad(createdAt.getMinutes())}${pad(createdAt.getSeconds())}.osvault`
  const blob = new Blob([JSON.stringify(envelope)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = name
  anchor.click()
  URL.revokeObjectURL(url)
}
