# online_safe

## IDE 导入

请在 IntelliJ IDEA 中打开根目录的 `pom.xml`，或直接打开 `backend/pom.xml` 并选择 **Open as Project**。根目录 `pom.xml` 仅聚合后端 Maven 模块；Vue 前端仍在 `frontend/` 中以 npm 方式运行。

一个多人独立使用、浏览器端加密的在线账密保险箱。

## 项目资料

- [产品需求文档](./在线账密保险箱-产品需求文档-v1.0.md)
- [技术选型与架构设计](./在线账密保险箱-技术选型与架构设计-v1.0.md)
- [界面与交互设计规范](./Design.md)
- [后端开发说明](./backend/README.md)
- [前端开发说明](./frontend/README.md)

## 当前进度

- 后端：Spring Boot 基础工程及第一版注册、Session 登录和权限隔离骨架。
- 前端：Vue + Vuetify 登录、两步注册和管理员入口页面，已适配桌面端与移动端。
