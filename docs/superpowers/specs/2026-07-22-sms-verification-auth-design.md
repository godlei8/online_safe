# 手机短信验证码认证设计

> 日期：2026-07-22  
> 状态：已落地（替换密保重置）  
> 关联：`docs/Design.md` §5.2、Flyway V12、`app.sms.*`

## 目标

1. **开放注册**：不要求邀请码；管理端邀请码能力保留，当前不参与注册校验。
2. **注册**：手机号 + 短信验证码 + 用户名 + 密码；成功后 `phone_verified=true`。
3. **重置密码**：仅已注册手机号收验证码后设新密码；吊销会话、保留保险箱数据。
4. **下线密保**：注册/找回不再读写 `user_security_question`（表保留，便于回滚）。
5. **短信通道**：阿里云号码认证（PNVS / `SendSmsVerifyCode`）正式发送；赠送模板 CODE（如 100001）与赠送签名须配套；密钥走环境变量。

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/sms/send` | `{ phone, purpose: REGISTER\|RESET_PASSWORD }` |
| POST | `/api/auth/register` | 含 `smsCode`，无 `securityQuestions` |
| POST | `/api/auth/password-reset/confirm` | `{ phone, smsCode, newPassword, confirmPassword }` |

已移除：`GET /api/auth/security-questions/builtins`、`POST /api/auth/password-reset/lookup`。

## 限流

- 同一手机同一用途：发码间隔默认 60 秒、每日上限默认 10 次。
- 重置确认沿用按 phone+IP 的尝试次数限制。

## 配置（生产）

见 `deploy/.env.example`：`ALIYUN_*`、`SMS_*`。模板变量约定为 `code`。
