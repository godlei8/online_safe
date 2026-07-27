import { requestJson } from '@/api/client'

export type DataSecuritySummary = {
  encryption: { status: string; lastCheckedAt: string | null }
  backup: { lastSnapshotAt: string | null }
  trash: {
    itemCount: number
    templateCount: number
    nearestPurgeAt: string | null
    retentionDays: number
  }
}

export type TrashAsset = {
  id: string
  type: 'ITEM' | 'PRIVATE_TEMPLATE'
  name: string
  summary: string
  deletedAt: string
  remainingDays: number
}

export type TrashPage = {
  content: TrashAsset[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type RestorePreflightResponse = {
  createCount: number
  restoreDeletedCount: number
  skipActiveConflictCount: number
  reassignIdCount: number
  invalidCount: number
  trashInBackupCount: number
  decisions: Array<{ type: string; sourceId: string; action: string }>
}

export type RestoreOperation = {
  operationId: string
  status: string
  totalCount: number
  processedCount: number
  createdCount: number
  restoredCount: number
  skippedCount: number
  failedCount: number
  errorCode: string | null
  expiresAt: string | null
  startedAt: string | null
  finishedAt: string | null
}

export type RestoreBatchEntry = {
  type: string
  sourceId: string
  payload: unknown
  deletedInBackup: boolean
  createdAt?: string
  updatedAt?: string
}

export type BackupManifest = {
  backupId: string
  schemaVersion: number
  createdAt: string
  source: { application: string; applicationVersion: string; accountId: string }
  scope: { includesTrash: boolean }
  items: Array<{
    id: string
    payload: unknown
    revision: number
    createdAt: string
    updatedAt: string
    deletedAt: string | null
  }>
  privateTemplates: Array<{
    id: string
    payload: unknown
    revision: number
    createdAt: string
    updatedAt: string
    deletedAt: string | null
  }>
}

export const dataSecurityApi = {
  summary: () => requestJson<DataSecuritySummary>('/api/v1/data-security/summary'),

  reauth: (password: string) =>
    requestJson<{ verifiedUntil: string }>('/api/v1/security/reauth', {
      method: 'POST',
      body: JSON.stringify({ password }),
    }),

  listTrash: (params: { keyword?: string; type?: string; page?: number; size?: number }) => {
    const query = new URLSearchParams()
    if (params.keyword) query.set('keyword', params.keyword)
    if (params.type) query.set('type', params.type)
    query.set('page', String(params.page ?? 0))
    query.set('size', String(params.size ?? 20))
    return requestJson<TrashPage>(`/api/v1/data-security/trash?${query}`)
  },

  restoreTrash: (type: string, id: string) =>
    requestJson<void>(`/api/v1/data-security/trash/${type}/${id}/restore`, { method: 'POST' }),

  purgeTrashOne: (type: string, id: string) =>
    requestJson<void>(`/api/v1/data-security/trash/${type}/${id}`, { method: 'DELETE' }),

  purgeTrashAll: () =>
    requestJson<{ deletedItems: number; deletedTemplates: number }>(
      '/api/v1/data-security/trash/purge',
      { method: 'POST' },
    ),

  createSnapshot: (includeTrash = true) =>
    requestJson<{ snapshot: BackupManifest }>('/api/v1/data-security/backup-snapshots', {
      method: 'POST',
      body: JSON.stringify({ includeTrash }),
    }),

  integrityScan: () =>
    requestJson<{ status: string; lastCheckedAt: string | null }>(
      '/api/v1/data-security/integrity-scan',
      { method: 'POST' },
    ),

  preflight: (body: {
    sourceBackupId: string
    formatVersion: number
    entries: Array<{ type: string; sourceId: string; deletedInBackup: boolean }>
  }) =>
    requestJson<RestorePreflightResponse>('/api/v1/data-security/restores/preflight', {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  createRestoreOperation: (body: {
    sourceBackupId: string
    formatVersion: number
    totalCount: number
    includeTrashAssets: boolean
  }) =>
    requestJson<RestoreOperation>('/api/v1/data-security/restore-operations', {
      method: 'POST',
      body: JSON.stringify(body),
    }),

  submitRestoreBatch: (operationId: string, batchNo: number, body: {
    includeTrashAssets: boolean
    entries: RestoreBatchEntry[]
  }) =>
    requestJson<{
      batchNo: number
      status: string
      createdCount: number
      restoredCount: number
      skippedCount: number
      failedCount: number
    }>(`/api/v1/data-security/restore-operations/${operationId}/batches/${batchNo}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),

  completeRestore: (operationId: string) =>
    requestJson<RestoreOperation>(
      `/api/v1/data-security/restore-operations/${operationId}/complete`,
      { method: 'POST' },
    ),
}
