# Docker 生产部署实施记录 v1.0

## 目标

为在线账密保险箱新增生产 Profile、Docker 镜像和 Docker Compose 编排，并部署到已确认可用的 Linux 服务器。

## 阶段

- [x] 确认服务器 Docker、Compose、Git、磁盘和端口基线。
- [x] 新增生产 Profile、前后端镜像和 Compose 编排。
- [x] 完成本地后端测试、前端生产构建与密钥派生自检。
- [x] 使用服务器受保护环境文件生成密钥并构建前后端镜像。
- [x] 启动 MySQL、后端与前端，并完成后端健康检查。
- [x] 在未开放公网入口前替换开发种子管理员口令，初始口令仅保存于服务器受限文件。
- [ ] 启动 Caddy 公网 HTTPS 入口。
- [ ] 配置生产域名、DNS 和公网 HTTPS 验收。

## 当前约束

服务器当前只有 IP，没有可用于 ACME 证书签发的生产域名。Docker 服务可先在服务器完成构建与内网健康验证；Caddy 的公网 HTTPS 启动必须在 `DOMAIN` 已解析且 `80/443` 已开放后进行。

## 安全记录

- 本地开发配置、数据库口令与邀请码加密密钥不写入 Git。
- 生产 Profile 要求提供 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 和 `INVITATION_ENCRYPTION_KEY`。
- 生产环境关闭 Flyway 启动时自动 `repair`，并禁止 `clean`。
- 当前 MySQL、后端和前端均未映射宿主机端口；主机仍只监听 SSH，避免在 HTTPS 就绪前暴露账号保险箱。

## 验证记录

- 后端 `mvnw.cmd test`：20 项测试通过。
- 前端 `npm run build` 与 `npm run test:vault-kdf`：通过。
- 首次使用 PowerShell 的 `&&` 连接两个 npm 命令失败；已改为逐条执行并检查退出码，构建结果正常。
- 服务器首次 Docker 构建中的 Maven 全量离线依赖预下载耗时过长；已改为直接执行打包构建并复用已下载缓存。
- 服务器后端健康接口：`UP`；Flyway 已成功执行 V1–V9。
