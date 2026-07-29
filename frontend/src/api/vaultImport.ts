import { requestForm, requestJson } from './client'

export type ImportCandidate = {
  id: string
  rowIndex: number
  confidence: number
  bucket: 'READY' | 'NEEDS_REVIEW' | 'SKIPPED'
  issues: string[]
  duplicateAction: 'SKIP' | 'CREATE' | null
  payload: Record<string, unknown>
}

export type ImportSessionStatus =
  | 'UPLOADING'
  | 'PARSING'
  | 'AI_MAPPING'
  | 'READY'
  | 'COMMITTING'
  | 'COMMITTED'
  | 'FAILED'
  | 'DISCARDED'

export type ImportMode = 'FAST' | 'AI'

export type ImportSession = {
  sessionId: string
  status: ImportSessionStatus | string
  progressMessage: string | null
  errorCode: string | null
  recognitionMode: ImportMode | string | null
  fileName: string
  fileFormat: string
  byteSize: number
  rowCount: number
  readyCount: number
  needsReviewCount: number
  skippedCount: number
  confidenceThreshold: number
  modelName: string | null
  modelProvider: string | null
  expiresAt: string
  candidates: ImportCandidate[]
}

export type ImportCommitResult = {
  sessionId: string
  succeededCount: number
  failedCount: number
  failures: Array<{ candidateId: string; rowIndex: number; errorCode: string }>
}

export const vaultImportApi = {
  createSession(file: File, mode: ImportMode = 'FAST') {
    const form = new FormData()
    form.append('file', file)
    form.append('mode', mode)
    return requestForm<ImportSession>('/api/v1/vault/import/sessions', form)
  },
  getSession(id: string) {
    return requestJson<ImportSession>(`/api/v1/vault/import/sessions/${id}`)
  },
  patchCandidate(
    sessionId: string,
    candidateId: string,
    body: {
      payload?: Record<string, unknown>
      duplicateAction?: 'SKIP' | 'CREATE'
      confidence?: number
      forceReady?: boolean
    },
  ) {
    return requestJson<ImportSession>(
      `/api/v1/vault/import/sessions/${sessionId}/candidates/${candidateId}`,
      { method: 'PATCH', body: JSON.stringify(body) },
    )
  },
  commit(sessionId: string, candidateIds?: string[]) {
    return requestJson<ImportCommitResult>(`/api/v1/vault/import/sessions/${sessionId}/commit`, {
      method: 'POST',
      body: JSON.stringify({ candidateIds: candidateIds ?? null }),
    })
  },
  discard(sessionId: string) {
    return requestJson<void>(`/api/v1/vault/import/sessions/${sessionId}`, { method: 'DELETE' })
  },
  reauth(password: string) {
    return requestJson<{ verifiedUntil: string }>('/api/v1/security/reauth', {
      method: 'POST',
      body: JSON.stringify({ password }),
    })
  },
}
