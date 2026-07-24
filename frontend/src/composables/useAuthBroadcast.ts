/**
 * 跨标签页认证状态同步。频道仅传类型，不含敏感字段。
 */
export type AuthBroadcastMessage =
  | { type: 'LOGOUT' }
  | { type: 'SESSION_REVOKED' }

const CHANNEL = 'online-safe-auth'
const STORAGE_KEY = 'online-safe-auth-broadcast'

type Listener = (message: AuthBroadcastMessage) => void

let channel: BroadcastChannel | null = null
const listeners = new Set<Listener>()

function parseMessage(raw: unknown): AuthBroadcastMessage | null {
  if (!raw || typeof raw !== 'object') return null
  const type = (raw as { type?: unknown }).type
  if (type === 'LOGOUT' || type === 'SESSION_REVOKED') {
    return { type }
  }
  return null
}

function ensureChannel() {
  if (typeof window === 'undefined') return
  if (typeof BroadcastChannel !== 'undefined' && !channel) {
    channel = new BroadcastChannel(CHANNEL)
    channel.onmessage = (event) => {
      const message = parseMessage(event.data)
      if (message) listeners.forEach((fn) => fn(message))
    }
  }
}

export function publishAuthBroadcast(message: AuthBroadcastMessage) {
  ensureChannel()
  channel?.postMessage(message)
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...message, at: Date.now() }))
    localStorage.removeItem(STORAGE_KEY)
  } catch {
    // 隐私模式等忽略
  }
}

export function subscribeAuthBroadcast(listener: Listener): () => void {
  ensureChannel()
  listeners.add(listener)

  function onStorage(event: StorageEvent) {
    if (event.key !== STORAGE_KEY || !event.newValue) return
    try {
      const message = parseMessage(JSON.parse(event.newValue))
      if (message) listener(message)
    } catch {
      // ignore
    }
  }

  window.addEventListener('storage', onStorage)
  return () => {
    listeners.delete(listener)
    window.removeEventListener('storage', onStorage)
  }
}
