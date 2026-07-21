import { z } from 'zod'

export const fieldTypeSchema = z.enum([
  'TEXT',
  'PASSWORD',
  'EMAIL',
  'PHONE',
  'URL',
  'CODE',
  'DATETIME',
  'MULTILINE',
  'SELECT',
])

export type FieldType = z.infer<typeof fieldTypeSchema>

export const vaultFieldSchema = z.object({
  id: z.string().min(1),
  name: z.string().min(1),
  type: fieldTypeSchema,
  value: z.string(),
  required: z.boolean(),
  sensitive: z.boolean(),
  copyable: z.boolean(),
  hint: z.string(),
  order: z.number().int().nonnegative(),
  options: z.array(z.string()).optional(),
})

export type VaultField = z.infer<typeof vaultFieldSchema>

export const itemStatusSchema = z.enum(['NORMAL', 'PENDING', 'ABNORMAL', 'INVALID', 'ARCHIVED'])
export type ItemStatus = z.infer<typeof itemStatusSchema>

export const vaultItemPayloadSchema = z.object({
  name: z.string().min(1),
  platform: z.string().min(1),
  channel: z.string(),
  status: itemStatusSchema,
  tags: z.array(z.string()),
  fields: z.array(vaultFieldSchema),
  templateSnapshot: z.unknown().nullable(),
  notes: z.string(),
})

export type VaultItemPayload = z.infer<typeof vaultItemPayloadSchema>

export const privateTemplatePayloadSchema = z.object({
  name: z.string().min(1),
  platform: z.string(),
  channel: z.string(),
  fields: z.array(vaultFieldSchema.omit({ value: true }).extend({ value: z.string().optional() })),
})

export type PrivateTemplatePayload = z.infer<typeof privateTemplatePayloadSchema>

export const statusLabel: Record<ItemStatus, string> = {
  NORMAL: '正常',
  PENDING: '待验证',
  ABNORMAL: '异常',
  INVALID: '失效',
  ARCHIVED: '已归档',
}

export function emptyItemPayload(): VaultItemPayload {
  return {
    name: '',
    platform: '',
    channel: '',
    status: 'NORMAL',
    tags: [],
    fields: [
      {
        id: crypto.randomUUID(),
        name: '用户名',
        type: 'TEXT',
        value: '',
        required: false,
        sensitive: false,
        copyable: true,
        hint: '',
        order: 0,
      },
      {
        id: crypto.randomUUID(),
        name: '密码',
        type: 'PASSWORD',
        value: '',
        required: true,
        sensitive: true,
        copyable: true,
        hint: '',
        order: 1,
      },
    ],
    templateSnapshot: null,
    notes: '',
  }
}

export function fieldsFromTemplate(template: PrivateTemplatePayload): VaultField[] {
  return template.fields.map((field, index) => ({
    id: crypto.randomUUID(),
    name: field.name,
    type: field.type,
    value: '',
    required: field.required,
    sensitive: field.sensitive,
    copyable: field.copyable,
    hint: field.hint,
    order: index,
    options: field.options,
  }))
}
