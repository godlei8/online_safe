export type ApiError = {
  status: number
  code?: string
  message?: string
  fieldErrors?: Record<string, string>
}

export class ApiRequestError extends Error {
  readonly status: number
  readonly code?: string
  readonly fieldErrors: Record<string, string>

  constructor(error: ApiError) {
    super(error.message || '请求未能完成，请稍后再试。')
    this.name = 'ApiRequestError'
    this.status = error.status
    this.code = error.code
    this.fieldErrors = error.fieldErrors ?? {}
  }
}

type CsrfToken = { token: string; headerName: string; parameterName: string }
type CsrfResponse = { cookieName: string; headerName: string; parameterName: string }
let csrfToken: CsrfToken | undefined
let handlingUnauthorized = false

/** 登录失败等预期 401，不应当作会话过期 */
function shouldTreatAsSessionExpiry(path: string): boolean {
  if (path === '/api/csrf') return false
  if (path === '/api/auth/login' || path === '/api/admin/auth/login') return false
  if (path.startsWith('/api/auth/register')) return false
  if (path.startsWith('/api/auth/password-reset')) return false
  if (path === '/api/auth/sms/send') return false
  return path.startsWith('/api/')
}

async function handleSessionExpired(path: string): Promise<void> {
  if (handlingUnauthorized) return
  handlingUnauthorized = true
  try {
    const [{ useVaultStore }, { useAuthStore }] = await Promise.all([
      import('@/stores/vault'),
      import('@/stores/auth'),
    ])
    useVaultStore().clearSessionData()
    useAuthStore().$patch({
      session: { authenticated: false, userId: null, username: null, role: null },
      ready: true,
    })
    const onAdminSurface = path.startsWith('/api/admin') || window.location.pathname.startsWith('/admin')
    const loginPath = onAdminSurface ? '/admin/login' : '/login'
    if (!window.location.pathname.startsWith(loginPath)) {
      const redirect = encodeURIComponent(window.location.pathname + window.location.search)
      window.location.assign(`${loginPath}?redirect=${redirect}`)
    }
  } finally {
    handlingUnauthorized = false
  }
}

export async function refreshCsrfToken(): Promise<void> {
  const response = await fetch('/api/csrf', { credentials: 'include' })
  if (!response.ok) throw await toApiError(response)
  const payload = await response.json() as CsrfResponse
  const token = readCookie(payload.cookieName)
  if (!token) throw new Error('未获取到 CSRF Cookie，请刷新页面后重试。')
  csrfToken = { token, headerName: payload.headerName, parameterName: payload.parameterName }
}

export async function requestJson<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase()
  if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method) && !csrfToken) {
    await refreshCsrfToken()
  }

  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (options.body) headers.set('Content-Type', 'application/json')
  if (csrfToken && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
    headers.set(csrfToken.headerName, csrfToken.token)
  }

  const response = await fetch(path, { ...options, method, headers, credentials: 'include' })
  if (!response.ok) {
    if (response.status === 401 && shouldTreatAsSessionExpiry(path)) {
      void handleSessionExpired(path)
    }
    throw await toApiError(response)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

async function toApiError(response: Response): Promise<ApiRequestError> {
  let body: ApiError = { status: response.status }
  try { body = await response.json() as ApiError } catch { /* response has no JSON body */ }
  return new ApiRequestError(body)
}

function readCookie(name: string): string | undefined {
  const prefix = `${encodeURIComponent(name)}=`
  for (const cookie of document.cookie.split('; ')) {
    if (cookie.startsWith(prefix)) return decodeURIComponent(cookie.slice(prefix.length))
  }
  return undefined
}
