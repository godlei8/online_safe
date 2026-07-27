import { z } from 'zod'

export const BACKUP_FORMAT = 'online-safe-vault-backup'
export const BACKUP_FORMAT_VERSION = 1
export const DEFAULT_PBKDF2_ITERATIONS = 600_000
export const MAX_BACKUP_FILE_BYTES = 20 * 1024 * 1024
export const MAX_BACKUP_ITEMS = 5000
export const MAX_BACKUP_TEMPLATES = 500

export const outerEnvelopeSchema = z
  .object({
    format: z.literal(BACKUP_FORMAT),
    formatVersion: z.literal(BACKUP_FORMAT_VERSION),
    kdf: z
      .object({
        name: z.literal('PBKDF2-SHA-256'),
        iterations: z.number().int().min(100_000).max(5_000_000),
        salt: z.string().min(1),
      })
      .strict(),
    cipher: z
      .object({
        name: z.literal('AES-256-GCM'),
        iv: z.string().min(1),
      })
      .strict(),
    ciphertext: z.string().min(1),
  })
  .strict()

export type OuterEnvelope = z.infer<typeof outerEnvelopeSchema>

const assetSchema = z
  .object({
    id: z.string().uuid(),
    payload: z.record(z.string(), z.unknown()),
    revision: z.number().int().nonnegative(),
    createdAt: z.string(),
    updatedAt: z.string(),
    deletedAt: z.string().nullable(),
  })
  .strict()

export const innerManifestSchema = z
  .object({
    backupId: z.string().uuid(),
    schemaVersion: z.literal(1),
    createdAt: z.string(),
    source: z
      .object({
        application: z.literal('online-safe'),
        applicationVersion: z.string(),
        accountId: z.string().uuid(),
      })
      .strict(),
    scope: z
      .object({
        includesTrash: z.boolean(),
      })
      .strict(),
    items: z.array(assetSchema).max(MAX_BACKUP_ITEMS),
    privateTemplates: z.array(assetSchema).max(MAX_BACKUP_TEMPLATES),
  })
  .strict()

export type InnerManifest = z.infer<typeof innerManifestSchema>

export function assertSafeContext() {
  if (typeof window === 'undefined' || !window.isSecureContext) {
    throw new Error('请在 HTTPS 或 localhost 环境下使用加密备份')
  }
  if (!window.crypto?.subtle) {
    throw new Error('当前浏览器不支持 Web Crypto')
  }
}
