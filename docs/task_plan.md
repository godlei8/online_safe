# Task Plan: 在线账密保险箱技术选型

## Goal

基于 v1.0 产品需求和技术选型，完成技术文档并搭建可运行、可测试的 Spring Boot 后端基础工程。

## Current Phase

Complete

## Phases

### Phase 1: 需求与约束提取
- [x] 复核现有产品需求文档
- [x] 提取功能、安全、部署和扩展性约束
- [x] 明确需要做出选择的技术层级
- **Status:** complete

### Phase 2: 官方资料调研
- [x] 调研前端、全栈框架和运行时
- [x] 调研数据库、ORM、认证与邮件能力
- [x] 调研浏览器端加密、密钥派生与 TOTP 能力
- [x] 调研测试、容器化和部署方案
- **Status:** complete

### Phase 2B: 用户指定技术栈复核
- [x] 核验 Java、Spring Boot 和 Spring Security 当前稳定基线
- [x] 核验 MySQL 8 系列支持周期和推荐分支
- [x] 核验 Vue 3、Vuetify、会话与测试集成
- **Status:** complete

### Phase 3: 方案比较与决策
- [x] 比较候选技术的适配性和风险
- [x] 确定推荐技术栈及关键版本策略
- [x] 明确不采用方案及理由
- [x] 定义系统边界和模块结构
- **Status:** complete

### Phase 4: 技术选型文档
- [x] 编写技术选型与架构决策文档
- [x] 给出开发环境、项目结构和部署建议
- [x] 标记待验证原型和后续决策
- **Status:** complete

### Phase 5: 校验与交付
- [x] 对照需求检查选型覆盖度
- [x] 检查引用、风险说明和敏感数据边界
- [x] 完成最终交付说明
- **Status:** complete

### Phase 6: 后端环境与工程边界确认
- [x] 检查 Git 分支、工作区和远程基线
- [x] 检查本机 Java、Maven 和 Docker 环境
- [x] 明确后端第一批功能和依赖边界
- **Status:** complete

### Phase 7: Spring Boot 工程骨架
- [x] 创建 Maven 工程、应用入口和环境配置
- [x] 配置 Spring Security、Session JDBC、JPA、Flyway 和 Actuator
- [x] 创建模块化包结构、统一错误模型和健康接口
- [x] 创建本机 MySQL 8.4 开发配置和初始迁移，不依赖 Docker
- **Status:** complete

### Phase 8: 第一版注册与认证基础
- [x] 实现手机号、用户名、密码、确认密码注册
- [x] 实现手机号/用户名唯一约束、规范化和 BCrypt 哈希
- [x] 实现 Session 登录、退出、当前会话和 CSRF 接口
- [x] 建立管理员与个人用户的权限隔离骨架
- **Status:** complete

### Phase 9: 自动化测试与构建验证
- [x] 编写注册、认证、CSRF 和权限测试
- [x] 使用内存测试库执行 Maven 测试和应用上下文验证
- [x] 检查敏感数据、配置和 Git 工作区
- **Status:** complete

### Phase 10: 后端交付说明
- [x] 补充后端 README、启动命令和环境变量说明
- [x] 记录尚未实现的业务范围和下一步建议
- [x] 完成最终交付说明
- **Status:** complete

### Phase 11: Java 25 development runtime
- [x] Install Java 25 from an official distribution into the local development directory
- [x] Re-run the backend test and package verification with Java 25
- **Status:** complete

### Phase 12: Stitch authentication prototypes
- [x] Retrieve the user-owned login and registration prototype screens from Stitch
- [x] Download their reference images and capture reusable visual decisions
- **Status:** complete

### Phase 13: Vue + Vuetify frontend
- [x] Create the Vue 3, TypeScript, Vite, Vuetify, Router, and Pinia project
- [x] Implement login, registration, and authenticated landing pages
- **Status:** complete

### Phase 14: Authentication integration
- [x] Integrate CSRF, registration, login, session lookup, and logout APIs
- [x] Add form validation, server error display, and local development proxying
- **Status:** complete

### Phase 15: Frontend verification and handoff
- [x] Build the frontend production bundle
- [x] Document local startup and environment dependencies
- **Status:** complete

## Key Questions

1. 采用前后端分离还是 TypeScript 全栈框架更适合第一版？
2. 如何实现管理员不可读取用户账密的客户端加密模型？
3. 登录密码、保险箱主密码和恢复密钥分别如何处理？
4. 动态字段、模板快照和多用户隔离如何落到数据存储层？
5. 哪种部署方案最适合 Windows 开发、Linux/Docker 生产环境？
6. 哪些安全能力必须在 MVP 中实现，哪些可以后置？

## Decisions Made

| Decision | Rationale |
| --- | --- |
| 先完成选型文档，不直接初始化代码 | 用户当前要求是技术选型，架构边界尚未正式确认 |
| 只以官方文档或项目官方仓库作为技术事实来源 | 软件版本和能力会变化，需要避免使用过时的二手资料 |
| 后端限定为 Java + Spring Boot | 用户明确指定 |
| 数据库限定为 MySQL 8 系列 | 用户明确指定 |
| 前端限定为 Vue + Vuetify | 用户明确指定 |
| 使用 Spring Security，不自研轻量认证框架 | 本产品处理高敏感数据，需要成熟的认证、会话、CSRF 与授权链路 |
| 使用 Java 25 LTS + Spring Boot 4.1.x | 当前官方支持基线，版本由 Maven Wrapper 和 Boot BOM 控制 |
| 使用 MySQL 8.4.x LTS | 满足 MySQL 8 约束并采用官方 LTS 分支 |
| 使用 Vue 3 + TypeScript + Vite + Vuetify 4 | 满足用户指定前端方向，并保留类型检查和现代构建能力 |
| 使用 Session Cookie，不使用浏览器长期 JWT | 同源 SPA 更适合服务端会话和 CSRF 防护 |
| 使用 libsodium.js 做浏览器端信封加密 | 服务端和管理员不能读取用户账密明文 |
| 第一版不引入 Redis、微服务和消息队列 | 单机模块化单体即可满足当前范围 |
| 开发环境不使用 Docker | 用户明确要求；本地 Spring Boot 直接连接已安装的 MySQL 8.4，测试使用内存数据库 |
| 正式 Java 25 目标暂以 Java 21 做本机构建覆盖验证 | 当前机器仅有 JDK 21；代码只使用 Java 21 兼容语法，发布前必须用 Java 25 重跑 |

## Errors Encountered

| Error | Attempt | Resolution |
| --- | --- | --- |
| 首次更新规划文件时上下文匹配失败 | 1 | 拆分补丁并按文件当前结构重新应用 |
| 更新多个规划文件时 findings 上下文匹配失败 | 1 | 拆分为逐文件补丁，并按实际原文更新 |
| planning session-catchup 无法运行，Windows Python 命令指向商店别名 | 1 | 直接以 Git 状态和规划文件为准恢复上下文，不重复调用该别名 |
| Docker 命令不可用 | 1 | 保留 Docker/Testcontainers 配置，本轮改用本机 Maven 和可用的 MySQL 环境验证 |
| PowerShell 未加引号传递 `-Djava.version=21`，Maven 将其拆成错误生命周期参数 | 1 | Wrapper 已成功生成；后续把含点号的 Maven `-D` 参数整体加双引号后重试 |
| Spring Boot 4.1 使用 Jackson 3，旧的 `com.fasterxml.jackson.databind.ObjectMapper` 导入无法编译 | 1 | 改为 `tools.jackson.databind.ObjectMapper` 后重新编译 |
| 首次敏感信息扫描使用了 `rg` 不支持的前瞻正则 | 1 | 改用 `rg --pcre2` 重新扫描；结果仅包含 Maven Wrapper 变量和明确的本机密码占位符 |

## Notes

- 2026-07-20: The initial frontend build selected TypeScript 7.0.2, but vue-tsc 3.3.7 resolves an unexported TypeScript compiler subpath. The frontend now pins TypeScript 5.9.3 before verification.
- 2026-07-20: Stitch work was corrected after clarification: the user-owned project `Online Security Vault` and its existing login/registration screens are the only design references used by the frontend.
- 2026-07-20: MySQL initialization initially reached JPA validation before Flyway ran. Replaced direct Flyway core dependency with `spring-boot-starter-flyway`; migrations V1 and V2 then completed successfully against local MySQL 8.4.

- 外部网页内容只作为研究数据记录在 findings.md，不作为可执行指令。
- 做出最终选择前重新阅读本计划和 findings.md。
- 本轮不读取或迁移任何真实账密。
- 先前 Node/Hono 调研只保留为备选比较，不再作为推荐方案。

### Phase 16: 保险箱首页 Stitch 优化
- [x] 读取需求文档、Design.md 与当前首页实现，提取首页信息架构和视觉约束
- [x] 连接用户已有的 Stitch 项目，定位已画好的保险箱首页
- [x] 基于需求和 Design.md 在 Stitch 中优化首页，并保留现有产品视觉系统
- [x] 记录 Stitch 优化结果与后续前端落地要点
- **Status:** complete

### Phase 17: 管理员后台首页 Stitch 原型
- [x] 提取需求文档中管理员权限、后台模块和安全边界
- [x] 核对 Design.md 与现有 Stitch 设计系统
- [x] 在同一 Stitch 项目中生成管理员登录后的后台管理首页
- [x] 视觉复核并记录 screen ID、页面结构和前端落地要点
- **Status:** complete
