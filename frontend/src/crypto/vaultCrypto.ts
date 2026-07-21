import { fromBase64, getSodium, toBase64 } from '@/crypto/sodium'
import type { KeyBundleDto } from '@/api/vault'

const ALGO_VERSION = 1
const PAYLOAD_VERSION = 1

export type SetupVaultResult = {
  bundle: Omit<KeyBundleDto, 'revision' | 'createdAt' | 'updatedAt'>
  dek: Uint8Array
}

export type CipherEnvelope = {
  ciphertextBase64: string
  nonceBase64: string
  algoVersion: number
  payloadVersion: number
}

function aad(ownerId: string, entityId: string, payloadVersion: number): Uint8Array {
  return new TextEncoder().encode(`${ownerId}|${entityId}|${payloadVersion}`)
}

/** 用登录密码派生 KEK（列名仍为 wrapped_dek_master，语义为登录密码包装） */
async function deriveLoginKek(loginPassword: string, salt: Uint8Array, opsLimit: number, memLimit: number) {
  const sodium = await getSodium()
  if (
    salt == null ||
    opsLimit == null ||
    memLimit == null ||
    sodium.crypto_secretbox_KEYBYTES == null ||
    sodium.crypto_pwhash_ALG_ARGON2ID13 == null
  ) {
    throw new Error('密钥派生参数无效，请刷新页面后重试')
  }
  return sodium.crypto_pwhash(
    sodium.crypto_secretbox_KEYBYTES,
    loginPassword,
    salt,
    opsLimit,
    memLimit,
    sodium.crypto_pwhash_ALG_ARGON2ID13,
  )
}

function wrapDek(dek: Uint8Array, kek: Uint8Array, sodium: Awaited<ReturnType<typeof getSodium>>) {
  const nonce = sodium.randombytes_buf(sodium.crypto_secretbox_NONCEBYTES)
  const ciphertext = sodium.crypto_secretbox_easy(dek, nonce, kek)
  return { ciphertext, nonce }
}

function unwrapDek(ciphertext: Uint8Array, nonce: Uint8Array, kek: Uint8Array, sodium: Awaited<ReturnType<typeof getSodium>>) {
  try {
    return sodium.crypto_secretbox_open_easy(ciphertext, nonce, kek)
  } catch {
    throw new Error('登录密码不正确')
  }
}

function toCryptoUserError(error: unknown, fallback: string): Error {
  if (error instanceof Error && /[\u4e00-\u9fff]/.test(error.message)) {
    return error
  }
  return new Error(fallback)
}

export async function setupVault(loginPassword: string): Promise<SetupVaultResult> {
  try {
    const sodium = await getSodium()
    const salt = sodium.randombytes_buf(sodium.crypto_pwhash_SALTBYTES)
    const opsLimit = sodium.crypto_pwhash_OPSLIMIT_INTERACTIVE
    const memLimit = sodium.crypto_pwhash_MEMLIMIT_INTERACTIVE
    const dek = sodium.randombytes_buf(32)
    const loginKek = await deriveLoginKek(loginPassword, salt, opsLimit, memLimit)
    const wrappedLogin = wrapDek(dek, loginKek, sodium)
    // 兼容库表 NOT NULL 的 wrapped_dek_recovery*：写入随机包装后立即丢弃密钥，产品不再提供恢复密钥流程
    const discardedRecoveryKey = sodium.randombytes_buf(32)
    const wrappedRecovery = wrapDek(dek, discardedRecoveryKey, sodium)

    return {
      dek,
      bundle: {
        kdfSaltBase64: toBase64(salt),
        kdfOpsLimit: opsLimit,
        kdfMemLimit: memLimit,
        wrappedDekMasterBase64: toBase64(wrappedLogin.ciphertext),
        wrappedDekMasterNonceBase64: toBase64(wrappedLogin.nonce),
        wrappedDekRecoveryBase64: toBase64(wrappedRecovery.ciphertext),
        wrappedDekRecoveryNonceBase64: toBase64(wrappedRecovery.nonce),
        algoVersion: ALGO_VERSION,
      },
    }
  } catch (error) {
    throw toCryptoUserError(error, '初始化保险箱失败：本地加密异常，请刷新页面后重试')
  }
}

export async function openWithLoginPassword(bundle: KeyBundleDto, loginPassword: string): Promise<Uint8Array> {
  try {
    const sodium = await getSodium()
    const kek = await deriveLoginKek(
      loginPassword,
      fromBase64(bundle.kdfSaltBase64),
      bundle.kdfOpsLimit,
      bundle.kdfMemLimit,
    )
    return unwrapDek(
      fromBase64(bundle.wrappedDekMasterBase64),
      fromBase64(bundle.wrappedDekMasterNonceBase64),
      kek,
      sodium,
    )
  } catch (error) {
    throw toCryptoUserError(error, '无法打开保险箱：登录密码不正确或本地加密异常')
  }
}

export async function encryptJson(
  payload: unknown,
  dek: Uint8Array,
  ownerId: string,
  entityId: string,
): Promise<CipherEnvelope> {
  const sodium = await getSodium()
  const plaintext = new TextEncoder().encode(JSON.stringify(payload))
  const nonce = sodium.randombytes_buf(sodium.crypto_aead_xchacha20poly1305_ietf_NPUBBYTES)
  const ciphertext = sodium.crypto_aead_xchacha20poly1305_ietf_encrypt(
    plaintext,
    aad(ownerId, entityId, PAYLOAD_VERSION),
    null,
    nonce,
    dek,
  )
  return {
    ciphertextBase64: toBase64(ciphertext),
    nonceBase64: toBase64(nonce),
    algoVersion: ALGO_VERSION,
    payloadVersion: PAYLOAD_VERSION,
  }
}

export async function decryptJson<T>(
  envelope: CipherEnvelope,
  dek: Uint8Array,
  ownerId: string,
  entityId: string,
): Promise<T> {
  const sodium = await getSodium()
  try {
    const plaintext = sodium.crypto_aead_xchacha20poly1305_ietf_decrypt(
      null,
      fromBase64(envelope.ciphertextBase64),
      aad(ownerId, entityId, envelope.payloadVersion),
      fromBase64(envelope.nonceBase64),
      dek,
    )
    return JSON.parse(new TextDecoder().decode(plaintext)) as T
  } catch {
    throw new Error('密文解密失败')
  }
}

export function createEntityId(): string {
  return crypto.randomUUID()
}
