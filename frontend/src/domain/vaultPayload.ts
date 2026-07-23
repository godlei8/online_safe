import { z } from 'zod'

/** 动态字段可选类型（UI 开放：文本 / 密码 / 邮箱 / 网址 / 手机号） */
export const fieldTypeSchema = z.enum(['TEXT', 'PASSWORD', 'EMAIL', 'URL', 'PHONE'])
export type FieldType = z.infer<typeof fieldTypeSchema>

/** 兼容历史密文中可能出现的旧类型 */
const legacyFieldTypeSchema = z.enum([
  'TEXT',
  'PASSWORD',
  'EMAIL',
  'URL',
  'PHONE',
  'CODE',
  'DATETIME',
  'MULTILINE',
  'SELECT',
])

export const fieldTypeLabels: Record<FieldType, string> = {
  TEXT: '文本',
  PASSWORD: '密码',
  EMAIL: '邮箱',
  URL: '网址',
  PHONE: '手机号',
}

export const fieldTypeItems = (Object.keys(fieldTypeLabels) as FieldType[]).map((value) => ({
  title: fieldTypeLabels[value],
  value,
}))

export function fieldTypeLabel(type: string): string {
  if (type in fieldTypeLabels) return fieldTypeLabels[type as FieldType]
  const legacy: Record<string, string> = {
    CODE: '代码',
    DATETIME: '日期时间',
    MULTILINE: '多行文本',
    SELECT: '选项',
  }
  return legacy[type] ?? type
}

function normalizeFieldType(type: string): FieldType {
  if (type === 'PASSWORD') return 'PASSWORD'
  if (type === 'EMAIL') return 'EMAIL'
  if (type === 'URL') return 'URL'
  if (type === 'PHONE') return 'PHONE'
  return 'TEXT'
}

export const vaultFieldSchema = z.object({
  id: z.string().min(1),
  name: z.string().min(1),
  type: legacyFieldTypeSchema.transform(normalizeFieldType),
  value: z.string(),
  required: z.boolean(),
  sensitive: z.boolean(),
  copyable: z.boolean(),
  hint: z.string().nullish().transform((value) => value ?? ''),
  order: z.number().int().nonnegative(),
  options: z.array(z.string()).optional(),
  /** 系统固定字段：账号 / 密码 */
  systemKey: z.enum(['account', 'password']).optional(),
})

export type VaultField = z.infer<typeof vaultFieldSchema>

/** 仅保留：正常 / 异常 / 过期 */
export const itemStatusSchema = z.enum(['NORMAL', 'ABNORMAL', 'EXPIRED'])
export type ItemStatus = z.infer<typeof itemStatusSchema>

function normalizeStatus(raw: unknown): ItemStatus {
  const value = String(raw ?? 'NORMAL')
  if (value === 'ABNORMAL') return 'ABNORMAL'
  if (value === 'EXPIRED' || value === 'INVALID' || value === 'ARCHIVED') return 'EXPIRED'
  return 'NORMAL'
}

export const vaultItemPayloadSchema = z.object({
  name: z.string().min(1),
  platform: z.string().min(1),
  /** 渠道名 */
  channel: z.string(),
  /** 渠道网址；旧数据可能没有此字段 */
  channelUrl: z.string().optional().transform((value) => value ?? ''),
  status: z.preprocess(normalizeStatus, itemStatusSchema),
  /** YYYY-MM-DD；空表示不过期 */
  expiresAt: z.string().nullable().optional().transform((value) => value ?? null),
  tags: z.array(z.string()).optional().transform((value) => value ?? []),
  fields: z.array(vaultFieldSchema),
  templateSnapshot: z.unknown().nullable(),
  notes: z.string(),
})

export type VaultItemPayload = z.infer<typeof vaultItemPayloadSchema>

/** 录入草稿允许名称/平台暂空（使用模板创建时常见） */
const vaultItemDraftSchema = vaultItemPayloadSchema.extend({
  name: z.string(),
  platform: z.string(),
})

export const privateTemplatePayloadSchema = z.object({
  name: z.string().min(1),
  platform: z.string(),
  channel: z.string(),
  channelUrl: z.string().optional().transform((value) => value ?? ''),
  fields: z.array(vaultFieldSchema.omit({ value: true }).extend({ value: z.string().optional() })),
})

export type PrivateTemplatePayload = z.infer<typeof privateTemplatePayloadSchema>

export const statusLabel: Record<ItemStatus, string> = {
  NORMAL: '正常',
  ABNORMAL: '异常',
  EXPIRED: '过期',
}

export const statusFilterOptions = ['全部状态', '正常', '异常', '过期'] as const

export function isPastExpiry(expiresAt: string | null | undefined): boolean {
  if (!expiresAt) return false
  const day = expiresAt.slice(0, 10)
  if (!/^\d{4}-\d{2}-\d{2}$/.test(day)) {
    const ts = Date.parse(expiresAt)
    return Number.isFinite(ts) && ts < Date.now()
  }
  const end = new Date(`${day}T23:59:59.999`)
  return end.getTime() < Date.now()
}

/** 展示与筛选用的有效状态：异常优先；到期则过期 */
export function effectiveStatus(payload: Pick<VaultItemPayload, 'status' | 'expiresAt'>): ItemStatus {
  if (payload.status === 'ABNORMAL') return 'ABNORMAL'
  if (isPastExpiry(payload.expiresAt) || payload.status === 'EXPIRED') return 'EXPIRED'
  return 'NORMAL'
}

export function createAccountField(value = ''): VaultField {
  return {
    id: crypto.randomUUID(),
    name: '账号',
    type: 'TEXT',
    value,
    required: true,
    sensitive: false,
    copyable: true,
    hint: '',
    order: 0,
    systemKey: 'account',
  }
}

export function createPasswordField(value = ''): VaultField {
  return {
    id: crypto.randomUUID(),
    name: '密码',
    type: 'PASSWORD',
    value,
    required: true,
    sensitive: true,
    copyable: true,
    hint: '',
    order: 1,
    systemKey: 'password',
  }
}

/** 确保存在不可删除的账号/密码系统字段 */
export function ensureCredentialFields(payload: VaultItemPayload): VaultItemPayload {
  const source = [...payload.fields]
  const usedIds = new Set<string>()

  let account = source.find((field) => field.systemKey === 'account')
  if (!account) {
    account = source.find((field) => /^(账号|帐号|用户名)$/.test(field.name.trim()))
  }
  account = account
    ? {
        ...account,
        name: '账号',
        type: account.type === 'EMAIL' || account.type === 'PHONE' ? account.type : 'TEXT',
        required: true,
        copyable: true,
        systemKey: 'account',
      }
    : createAccountField()
  usedIds.add(account.id)

  let password = source.find((field) => field.systemKey === 'password' && !usedIds.has(field.id))
  if (!password) {
    password = source.find((field) => (
      !usedIds.has(field.id)
      && (field.name.includes('密码') || field.type === 'PASSWORD')
    ))
  }
  password = password
    ? {
        ...password,
        name: '密码',
        type: 'PASSWORD',
        required: true,
        sensitive: true,
        copyable: true,
        systemKey: 'password',
      }
    : createPasswordField()
  usedIds.add(password.id)

  const rest = source
    .filter((field) => !usedIds.has(field.id))
    .map(({ systemKey: _ignored, ...field }) => field)

  const normalized = [account, password, ...rest].map((field, order) => ({ ...field, order }))
  const nextStatus = payload.status === 'ABNORMAL'
    ? 'ABNORMAL'
    : (isPastExpiry(payload.expiresAt) ? 'EXPIRED' : 'NORMAL')

  return {
    ...payload,
    status: nextStatus,
    tags: [],
    channel: payload.channel ?? '',
    channelUrl: payload.channelUrl ?? '',
    expiresAt: payload.expiresAt ?? null,
    fields: normalized,
  }
}

export function emptyItemPayload(): VaultItemPayload {
  return ensureCredentialFields({
    name: '',
    platform: '',
    channel: '',
    channelUrl: '',
    status: 'NORMAL',
    expiresAt: null,
    tags: [],
    fields: [createAccountField(), createPasswordField()],
    templateSnapshot: null,
    notes: '',
  })
}

/** 深拷贝为纯对象，并补齐系统字段（草稿不强制名称/平台非空） */
export function cloneVaultItemPayload(payload: VaultItemPayload): VaultItemPayload {
  return ensureCredentialFields(
    vaultItemDraftSchema.parse(JSON.parse(JSON.stringify(payload))),
  )
}

export function fieldsFromTemplate(template: PrivateTemplatePayload): VaultField[] {
  const mapped = template.fields.map((field, index) => ({
    id: crypto.randomUUID(),
    name: field.name,
    type: normalizeFieldType(field.type),
    value: '',
    required: field.required,
    sensitive: field.sensitive,
    copyable: field.copyable,
    hint: field.hint,
    order: index,
    options: field.options,
    systemKey: field.systemKey,
  }))
  return ensureCredentialFields({
    ...emptyItemPayload(),
    fields: mapped,
  }).fields
}

function isPasswordField(field: VaultField): boolean {
  return field.systemKey === 'password' || field.type === 'PASSWORD' || field.name.includes('密码')
}

function isAccountField(field: VaultField): boolean {
  return field.systemKey === 'account' || /用户名|账号|帐号/.test(field.name)
}

/** 从已解密字段中识别列表展示用的账号与密码 */
export function pickListCredentials(fields: VaultField[]): {
  account: VaultField | null
  password: VaultField | null
} {
  const sorted = [...fields].sort((a, b) => a.order - b.order)
  const password = sorted.find((field) => field.systemKey === 'password')
    ?? sorted.find(isPasswordField)
    ?? null
  const account = sorted.find((field) => field.systemKey === 'account')
    ?? sorted.find((field) => !isPasswordField(field) && isAccountField(field))
    ?? sorted.find((field) => (
      !isPasswordField(field)
      && (field.type === 'EMAIL' || field.type === 'PHONE' || field.type === 'TEXT')
    ))
    ?? null
  return { account, password }
}

export function maskSecret(value: string, fixedLength = 8): string {
  const length = value ? Math.min(Math.max(value.length, 6), 12) : fixedLength
  return '•'.repeat(length)
}

export function truncatePlain(value: string, max = 22): string {
  const trimmed = value.trim()
  if (!trimmed) return ''
  if (trimmed.length <= max) return trimmed
  return `${trimmed.slice(0, max - 1)}…`
}

/** 将网址规范为可跳转 href */
export function toExternalHref(raw: string): string | null {
  const value = raw.trim()
  if (!value) return null
  try {
    const withProtocol = /^[a-zA-Z][a-zA-Z\d+\-.]*:/.test(value) ? value : `https://${value}`
    const url = new URL(withProtocol)
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return null
    return url.href
  } catch {
    return null
  }
}

/** 将手机号规范为可拨打 tel: 链接 */
export function toPhoneHref(raw: string): string | null {
  const value = raw.trim()
  if (!value) return null
  const compact = value.replace(/[\s()-]/g, '')
  if (!/^\+?\d{7,15}$/.test(compact)) return null
  return `tel:${compact}`
}

/** 渠道跳转：优先 channelUrl；兼容旧数据把网址写在 channel 里 */
export function channelExternalHref(
  payload: Pick<VaultItemPayload, 'channel' | 'channelUrl'>,
): string | null {
  return toExternalHref(payload.channelUrl || '')
    ?? toExternalHref(payload.channel || '')
}
