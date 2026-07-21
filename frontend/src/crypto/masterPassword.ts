/** 登录密码最短长度（与注册/登录规则一致；亦用于信封包装） */
export const MASTER_PASSWORD_MIN_LENGTH = 8

/** 允许 ASCII 可打印字符，禁止空格与中文等非 ASCII */
const LOGIN_PASSWORD_CHARSET = /^[\x21-\x7E]+$/

export const MASTER_PASSWORD_CHARSET_MESSAGE =
  '登录密码请使用英文字母、数字或常见符号，不支持中文'

export const MASTER_PASSWORD_CHARSET_RULE_LABEL = '仅英文字母、数字或常见符号'

export function isMasterPasswordCharsetValid(value: string): boolean {
  return LOGIN_PASSWORD_CHARSET.test(value)
}

export function isMasterPasswordLengthValid(value: string): boolean {
  return value.length >= MASTER_PASSWORD_MIN_LENGTH
}
