# 项目协作约定

## 中文优先

项目全程遵循中文优先原则：凡是可以使用中文的地方，均使用中文。

- 业务文案、页面文案、接口错误提示、日志说明、注释、文档、提交说明和开发沟通使用中文。
- 代码中由框架、协议、第三方库或语言规范强制要求的标识符、配置键、命令和文件格式保留原样。
- 对外可见的字段说明和示例优先使用中文，必要的英文术语应附中文语义。

## 前端统一二次确认

删除、标记异常、禁用用户、使会话失效等危险/不可轻易撤销操作，必须使用 `OsConfirmDialog`（样式类 `os-confirm-dialog*`），并遵循 `docs/Design.md` §5.3.1 的色彩与动效。禁止使用浏览器 `window.confirm`。

## 按钮高度（−10px 规范）

工作区默认按钮 **30px**，认证页主按钮 **38px**，确认弹窗按钮 **28px**（相对旧 40/48/≈38 整体约 −10px）。新增 UI 不得恢复偏高按钮。详见 `docs/Design.md` §3.3。

## 下拉框（紧凑规范）

`v-select` / `v-autocomplete` / `v-combobox` 必须紧凑：字段约 **34px**，选项行高 **32px**（`os-select-menu`）。禁止恢复偏高默认列表项。详见 `docs/Design.md` §3.4。

## 表单校验提示

弹窗内校验失败、保存失败使用 `useOsToast().error(...)` 独立弹出，禁止再在表单顶部挂红色 `v-alert` 横幅。

## 手机 Web 布局（≤599px / `xs`）

新增或改版列表/工具栏时必须遵循 `docs/Design.md` §7.1，核心经验可全局复用：

1. **搜索优先**：第 1 行 = 搜索（`min-width ≥ 7.5rem`）+ 筛选/刷新工具钮（`admin-filter-actions--tools`）；禁止长文案「创建/新建」与搜索同处 nowrap 挤占行。
2. **主操作换行**：创建/新建放 `admin-filter-actions--primary`（整行全宽）；次级筛选进 `AdminFilterSheet`。
3. **标题同行次要动作**：如「运行能力」右侧放缩小刷新（约 26px），说明文案在标题下一行；标题容器勿用过大 `flex-basis` 造成空白高度。
4. **列表**：手机隐藏分页器，用 `useMobileInfiniteScroll`；底栏单行弱提示（`admin-page__infinite-status`，约 11px muted），禁止左右两行大字对峙。
5. **chrome 压缩**：藏顶栏副标题、`OsHintBar` 可收起、统计卡标签数值同行、摘要卡 `data-mobile-hide`；详情/长表单用全屏 `v-dialog`。

## 项目记忆

跨会话需查阅与维护：

- `docs/Design.md` — UI/交互唯一视觉依据（含 §7.1 手机 Web 布局通则）
- `docs/project_notes/decisions.md` — 架构与交互决策
- `docs/project_notes/key_facts.md` — 关键事实与约定
