import sodium from 'libsodium-wrappers-sumo'

let readyPromise: Promise<typeof sodium> | null = null

export async function getSodium() {
  if (!readyPromise) {
    readyPromise = sodium.ready.then(() => {
      // 标准包不含 Argon2；必须使用 sumo，否则 crypto_pwhash_* 常量为 undefined
      if (
        typeof sodium.crypto_pwhash !== 'function' ||
        sodium.crypto_pwhash_SALTBYTES == null ||
        sodium.crypto_pwhash_OPSLIMIT_INTERACTIVE == null ||
        sodium.crypto_pwhash_MEMLIMIT_INTERACTIVE == null
      ) {
        throw new Error('密码学库未正确加载（缺少 Argon2），请刷新页面后重试')
      }
      return sodium
    })
  }
  return readyPromise
}

export function toBase64(bytes: Uint8Array): string {
  return sodium.to_base64(bytes, sodium.base64_variants.ORIGINAL)
}

export function fromBase64(value: string): Uint8Array {
  return sodium.from_base64(value, sodium.base64_variants.ORIGINAL)
}
