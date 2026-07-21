# 管理员后台壳与邀请码 Implementation Plan

> **For agentic workers:** 按任务顺序实现；每任务结束后跑相关测试。

**Goal:** 独立管理员登录、管理后台壳、邀请码 CRUD，注册校验数据库邀请码。

**Architecture:** 分离 `AdminUserPrincipal` 与用户认证；邀请码哈希入库；前端 `AdminLayout` + 邀请码页对齐原型。

**Tech Stack:** Spring Boot 4.1、Spring Security、JPA、Flyway、Vue 3、Vuetify 4

## Global Constraints

- 中文优先；管理员不可见用户账密明文；本轮无 TOTP。
- 种子管理员仅用于本地开发；初始口令不得写入计划、日志或版本库。

## Tasks

1. 后端管理员实体、认证接口、Security 配置
2. 邀请码迁移、领域与管理 API；注册改库内校验
3. 集成测试更新
4. 前端管理员会话、布局、路由、登录
5. 前端邀请码页（统计/筛选/表格/创建/停用）
6. README / CURSOR 边界更新
