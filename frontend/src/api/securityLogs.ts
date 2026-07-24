import { requestJson } from './client'

export type SecurityLogStats = {
  loginFailuresLast24Hours: number
  highRiskLast7Days: number
  adminActionsLast7Days: number
  blockedLast24Hours: number
}

export type SecurityLogCategory =
  | 'AUTH'
  | 'ACCOUNT'
  | 'SESSION'
  | 'INVITATION'
  | 'ANNOUNCEMENT'
  | 'TEMPLATE'
  | 'SETTINGS'
  | 'SYSTEM'

export type SecurityLogRiskLevel = 'INFO' | 'WARNING' | 'HIGH'
export type SecurityLogResult = 'SUCCESS' | 'FAILED' | 'BLOCKED'
export type SecurityLogActorType = 'USER' | 'ADMIN' | 'ANONYMOUS' | 'SYSTEM'

export type SecurityLogListItem = {
  id: string
  occurredAt: string
  category: SecurityLogCategory
  eventType: string
  eventLabelZh: string
  riskLevel: SecurityLogRiskLevel
  result: SecurityLogResult
  actorType: SecurityLogActorType
  actorLabel: string
  targetLabel: string
  sourceSummary: string
  occurrenceCount: number
}

export type SecurityLogDetail = {
  id: string
  occurredAt: string
  category: SecurityLogCategory
  eventType: string
  eventLabelZh: string
  riskLevel: SecurityLogRiskLevel
  result: SecurityLogResult
  actorType: SecurityLogActorType
  actorLabel: string
  actorId: string | null
  targetLabel: string
  targetType: string | null
  targetId: string | null
  errorCode: string | null
  requestId: string | null
  routeTemplate: string | null
  httpMethod: string | null
  ipMasked: string | null
  browserFamily: string | null
  osFamily: string | null
  deviceType: string | null
  occurrenceCount: number
  metadata: Record<string, unknown> | null
}

export type SecurityLogPage = {
  content: SecurityLogListItem[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export type SecurityLogQuery = {
  from?: string
  to?: string
  category?: SecurityLogCategory
  eventType?: string
  riskLevel?: SecurityLogRiskLevel
  result?: SecurityLogResult
  actorType?: SecurityLogActorType
  q?: string
  page?: number
  size?: number
}

export type SecurityLogEventDictionaryItem = {
  code: string
  labelZh: string
  category: SecurityLogCategory
  defaultRisk: SecurityLogRiskLevel
}

export type SecurityLogEnumOption = {
  value: string
  label: string
}

export type SecurityLogEventTypes = {
  events: Record<string, SecurityLogEventDictionaryItem>
  categories: SecurityLogEnumOption[]
  results: SecurityLogEnumOption[]
  riskLevels: SecurityLogEnumOption[]
  actorTypes: SecurityLogEnumOption[]
}

function toQuery(params: SecurityLogQuery) {
  const search = new URLSearchParams()
  if (params.from) search.set('from', params.from)
  if (params.to) search.set('to', params.to)
  if (params.category) search.set('category', params.category)
  if (params.eventType) search.set('eventType', params.eventType)
  if (params.riskLevel) search.set('riskLevel', params.riskLevel)
  if (params.result) search.set('result', params.result)
  if (params.actorType) search.set('actorType', params.actorType)
  if (params.q) search.set('q', params.q)
  if (params.page != null) search.set('page', String(params.page))
  if (params.size != null) search.set('size', String(params.size))
  const text = search.toString()
  return text ? `?${text}` : ''
}

export const securityLogsApi = {
  list: (params: SecurityLogQuery = {}) =>
    requestJson<SecurityLogPage>(`/api/admin/v1/security-logs${toQuery(params)}`),
  stats: () => requestJson<SecurityLogStats>('/api/admin/v1/security-logs/stats'),
  detail: (id: string) => requestJson<SecurityLogDetail>(`/api/admin/v1/security-logs/${id}`),
  eventTypes: () => requestJson<SecurityLogEventTypes>('/api/admin/v1/security-logs/event-types'),
}
