/**
 * 回归：创建保险箱依赖 Argon2id（crypto_pwhash），必须使用 libsodium-wrappers-sumo。
 * 标准包 crypto_pwhash_SALTBYTES 为 undefined，会抛出 "length cannot be null or undefined"。
 */
import sodium from 'libsodium-wrappers-sumo'

await sodium.ready

const required = {
  crypto_pwhash: typeof sodium.crypto_pwhash,
  SALTBYTES: sodium.crypto_pwhash_SALTBYTES,
  OPSLIMIT: sodium.crypto_pwhash_OPSLIMIT_INTERACTIVE,
  MEMLIMIT: sodium.crypto_pwhash_MEMLIMIT_INTERACTIVE,
  ALG: sodium.crypto_pwhash_ALG_ARGON2ID13,
  KEYBYTES: sodium.crypto_secretbox_KEYBYTES,
  NONCEBYTES: sodium.crypto_secretbox_NONCEBYTES,
}

for (const [name, value] of Object.entries(required)) {
  if (value == null || value === 'undefined') {
    throw new Error(`缺少 ${name}，请确认依赖为 libsodium-wrappers-sumo`)
  }
}

const salt = sodium.randombytes_buf(sodium.crypto_pwhash_SALTBYTES)
const kek = sodium.crypto_pwhash(
  sodium.crypto_secretbox_KEYBYTES,
  'verify-password-123',
  salt,
  sodium.crypto_pwhash_OPSLIMIT_INTERACTIVE,
  sodium.crypto_pwhash_MEMLIMIT_INTERACTIVE,
  sodium.crypto_pwhash_ALG_ARGON2ID13,
)
const dek = sodium.randombytes_buf(32)
const nonce = sodium.randombytes_buf(sodium.crypto_secretbox_NONCEBYTES)
const wrapped = sodium.crypto_secretbox_easy(dek, nonce, kek)
const opened = sodium.crypto_secretbox_open_easy(wrapped, nonce, kek)

if (opened.length !== 32) {
  throw new Error('DEK unwrap 长度异常')
}

console.log(
  JSON.stringify({
    ok: true,
    kdfSaltBytes: salt.length,
    kdfOpsLimit: sodium.crypto_pwhash_OPSLIMIT_INTERACTIVE,
    kdfMemLimit: sodium.crypto_pwhash_MEMLIMIT_INTERACTIVE,
    wrappedDekBytes: wrapped.length,
    wrappedNonceBytes: nonce.length,
  }),
)
