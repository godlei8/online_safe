import { requestJson } from './client'

export type AiConnectivityTestRequest = {
  baseUrl?: string
  model?: string
  apiKey?: string
}

export type AiConnectivityTestResponse = {
  ok: boolean
  latencyMs: number
  model: string
  replyPreview: string
  message: string
}

export const adminAiApi = {
  testConnectivity: (body: AiConnectivityTestRequest = {}) =>
    requestJson<AiConnectivityTestResponse>('/api/admin/v1/ai/connectivity-test', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
}
