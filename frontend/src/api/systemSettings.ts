import { requestJson } from './client'

export type SystemSettingItem = {
  key: string
  labelZh: string
  type: string
  value: unknown
  defaultValue: unknown
  version: number
  min: number | null
  max: number | null
  allowedValues: string[]
  editable: boolean
  riskLevel: string
  secret?: boolean
  configured?: boolean
}

export type SystemSettingCapability = {
  label: string
  status: string
  statusLabel: string
}

export type SystemSettingsResponse = {
  groups: Record<string, SystemSettingItem[]>
  capabilities: Record<string, SystemSettingCapability>
}

export type SettingChange = { key: string; value: unknown; expectedVersion: number }

export type ValidateResponse = {
  valid: boolean
  highestRisk: string
  effects: string[]
  confirmationTitle: string
}

export const systemSettingsApi = {
  get: () => requestJson<SystemSettingsResponse>('/api/admin/v1/system-settings'),
  validate: (changes: SettingChange[]) =>
    requestJson<ValidateResponse>('/api/admin/v1/system-settings/validate', {
      method: 'POST',
      body: JSON.stringify({ changes }),
    }),
  update: (changes: SettingChange[]) =>
    requestJson<SystemSettingsResponse>('/api/admin/v1/system-settings', {
      method: 'PUT',
      body: JSON.stringify({ changes }),
    }),
}
