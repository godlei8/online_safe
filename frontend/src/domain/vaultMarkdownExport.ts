import {
  effectiveStatus,
  statusLabel,
  type VaultItemPayload,
} from '@/domain/vaultPayload'

export type VaultExportItem = {
  payload: VaultItemPayload
  updatedAt?: string | null
}

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

/** 本地时间戳片段：YYYYMMDD-HHmmss */
export function exportTimestamp(now = new Date()) {
  return [
    now.getFullYear(),
    pad2(now.getMonth() + 1),
    pad2(now.getDate()),
    '-',
    pad2(now.getHours()),
    pad2(now.getMinutes()),
    pad2(now.getSeconds()),
  ].join('')
}

/** 文件名安全化：仅保留 ASCII，避免下载夹/资源管理器对中文名卡住 */
export function sanitizeExportName(raw: string, max = 40) {
  const cleaned = raw
    .trim()
    .replace(/[^A-Za-z0-9._-]+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^[-.]+|[-.]+$/g, '')
  const base = cleaned || 'record'
  return base.length > max ? base.slice(0, max).replace(/[-.]+$/g, '') || 'record' : base
}

function escapeCell(value: string) {
  return value
    .replace(/\|/g, '\\|')
    .replace(/\r\n/g, '\n')
    .replace(/\n/g, '<br>')
}

function formatExpires(expiresAt: string | null | undefined) {
  if (!expiresAt?.trim()) return '永久有效'
  return expiresAt.slice(0, 10)
}

function formatDisplayTime(now = new Date()) {
  return now.toLocaleString('zh-CN', { hour12: false })
}

function renderOneRecord(payload: VaultItemPayload): string {
  const status = statusLabel[effectiveStatus(payload)]
  const fields = [...payload.fields].sort((a, b) => a.order - b.order)
  const lines: string[] = [
    `## ${payload.name.trim() || '未命名记录'}`,
    '',
    `- 平台：${payload.platform.trim() || '—'}`,
    `- 渠道：${payload.channel.trim() || '—'}`,
    `- 渠道网址：${payload.channelUrl?.trim() || '—'}`,
    `- 状态：${status}`,
    `- 有效期：${formatExpires(payload.expiresAt)}`,
  ]
  if (payload.notes?.trim()) {
    lines.push(`- 备注：${payload.notes.trim()}`)
  }
  lines.push('', '### 字段', '', '| 字段 | 值 |', '| --- | --- |')
  if (fields.length === 0) {
    lines.push('| — | — |')
  } else {
    for (const field of fields) {
      lines.push(`| ${escapeCell(field.name)} | ${escapeCell(field.value)} |`)
    }
  }
  return lines.join('\n')
}

/** 将一条或多条账密渲染为 Markdown 全文（含明文） */
export function buildVaultMarkdown(items: VaultExportItem[], now = new Date()) {
  const body = items.map((item) => renderOneRecord(item.payload)).join('\n\n---\n\n')
  const header = [
    '# Online Safe 账密导出',
    '',
    `> 导出时间：${formatDisplayTime(now)}`,
    `> 条数：${items.length}`,
    '> 警告：含明文密码，请妥善保管本文件。',
    '',
  ].join('\n')
  return `${header}${body}\n`
}

export function batchExportFilename(now = new Date()) {
  return `online-safe-vault-${exportTimestamp(now)}.md`
}

export function singleExportFilename(recordName: string, now = new Date()) {
  return `online-safe-${sanitizeExportName(recordName)}-${exportTimestamp(now)}.md`
}

/** 触发浏览器下载文本文件 */
export function downloadTextFile(filename: string, content: string) {
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.rel = 'noopener'
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  URL.revokeObjectURL(url)
}

export function exportVaultItemsAsMarkdown(items: VaultExportItem[], filename: string) {
  if (items.length === 0) {
    throw new Error('没有可导出的记录')
  }
  downloadTextFile(filename, buildVaultMarkdown(items))
}
