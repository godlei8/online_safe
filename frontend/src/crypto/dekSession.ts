/**
 * 标签页会话内暂存 DEK，供刷新后恢复解密能力。
 * 使用 sessionStorage（关闭标签页即清除）；禁止写入 localStorage。
 */

const STORAGE_KEY_PREFIX = 'online-safe.dek.'

function storageKey(userId: string): string {
  return `${STORAGE_KEY_PREFIX}${userId}`
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = ''
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return btoa(binary)
}

function base64ToBytes(value: string): Uint8Array {
  const binary = atob(value)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}

export function saveDekToSession(userId: string, dek: Uint8Array): void {
  if (!userId || dek.length === 0) return
  try {
    sessionStorage.setItem(storageKey(userId), bytesToBase64(dek))
  } catch {
    // 隐私模式配额等异常时忽略，仅影响刷新恢复
  }
}

export function loadDekFromSession(userId: string): Uint8Array | null {
  if (!userId) return null
  try {
    const encoded = sessionStorage.getItem(storageKey(userId))
    if (!encoded) return null
    const dek = base64ToBytes(encoded)
    return dek.length > 0 ? dek : null
  } catch {
    return null
  }
}

export function clearDekFromSession(userId?: string | null): void {
  try {
    if (userId) {
      sessionStorage.removeItem(storageKey(userId))
      return
    }
    const keys: string[] = []
    for (let i = 0; i < sessionStorage.length; i += 1) {
      const key = sessionStorage.key(i)
      if (key?.startsWith(STORAGE_KEY_PREFIX)) keys.push(key)
    }
    for (const key of keys) sessionStorage.removeItem(key)
  } catch {
    // ignore
  }
}
