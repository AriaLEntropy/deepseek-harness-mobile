# 当前任务

更新：2026-09-13。此表追踪本轮可确认的任务，代码完成与平台验收分别记录。9 月 11 日记录仅沿用已有 Task3 编号；9 月 12 日起按完整原始任务集追踪。

## 2026-09-13：会话归档恢复与电脑端归档页

在 Task 4 归档浏览/删除基础上补齐「取消归档」，并把归档入口与管理页带到电脑端 DSH Web。

| 范围 | 实现 | 验证 / 边界 |
| --- | --- | --- |
| Host 桥接 | `host-plugin/index.mjs` 新增 `/api/session-manager/unarchive`：官方无公开 unarchive RPC，复用 `WorkspaceRegistry` 的 `enqueueOperation`/`setState` 仅改 `archivedSessionIds`；`setState` 触发 `domain/changed` → apiproxy 广播 `host/archived-sessions-changed` | `node --check` 通过；需安装后联调 |
| 移动端 | `DshHostProtocol.unarchiveSession` + `DshRemoteRepository` 转发；`DshArchivedSessions` 每行新增「取消归档」，成功后该会话回到主列表/工作区分组 | `:shared:compileDebugKotlinAndroid` 通过；设备回归待做 |
| 电脑端（非侵入） | 同一手写 `client.js` 注册 `sidebar.footer.action` 槽「已归档的聊天」，整屏管理页：搜索、项目筛选、排序、取消归档、单条/项目/全部删除；类型筛选按项目所有者要求只保留「全部聊天」；内联复用项目 SVG 与 `--dsw-alias-*` 主题 token | `node --check` 通过；真实 `dsh web` 需重启后目视 |

上游结论：`deepseek-harness` 最新 `dsh-v0.1.2-alpha.3` / `origin/master` 均无归档浏览页或 unarchive API（`packages/workspace/workspace/README.md` 标注「Archiving is one-way」），故采用情况 B：保留移动端现有归档样式并给电脑端新增入口。

## 2026-09-13：对话导出优化（多选组 / PDF / 底部三入口）

| 范围 | 实现 | 验证 / 边界 |
| --- | --- | --- |
| 入口 | 长按 AI 消息菜单「分享」、消息 footer 分享图标、overflow「分享消息」统一进入多选态；footer/长按会把点击消息所在对话组设为默认选中 | 代码完成；真机交互待验收 |
| 选择单位 | 以「对话组」（用户 Prompt + 该轮最终助手回复）为单位：`dshShareGroups` 划分，勾选任一条同组两条一起选中；计数显示「已选择 N 组对话」 | 沿用仅当前会话范围，不支持跨会话选择 |
| 顶部栏 / 底部弹窗 | 顶部栏左侧全选/取消全选、右侧关闭；底部弹窗对齐参考图，三个圆形动作：生成PDF / 复制内容 / 更多分享；更多分享展开 TXT/Markdown/HTML 与分享按钮 | 工具调用卡样式不套用参考图，沿用现有卡片 |
| 导出能力 | 仅导出正文（用户 Prompt + 该轮最终助手回复），不含工具调用/思考/上下文注入；HTML 为默认导出格式；复制内容输出可读文本；新增 PDF：Android 用 WebView + 系统打印「另存为 PDF」，iOS/OHOS 提示暂不支持 | `:androidApp:assembleDebug` 通过；PDF/打印真机待验收 |

实现入口：`DshModels.kt` 的 `dshShareGroups`/`DshShareGroup`；`DshHomePage.kt` 组选择状态与 `exportSelectionAsPdf`/`copyExportSelection`/`toggleExportMoreShare`；`DshTextExport.kt` 选择态 UI；`BridgeModule.htmlToPdf` 与 Android `KRBridgeModule` 的 MIME 推断及系统打印。PDF 采用系统 `PrintManager` 而非静默打印：`PrintDocumentAdapter` 的结果回调构造函数为包私有，Kotlin 无法静默驱动，系统打印是官方支持的 HTML→PDF 路径且自动分页。

## 2026-09-13：插件启停（电脑端 + 移动端）

在 Task 5 已有只读清单与本地启停雏形上，补齐两端一致的可视化启停。

| 范围 | 实现 | 验证 / 边界 |
| --- | --- | --- |
| Host 桥接 | `host-plugin/index.mjs` 的 `/list`、`/action`（enable/disable/reload）继续作为唯一写入口，基于公共 `Entry.update()`；拒绝桥接自身、条件表达式与上级分组禁用 | `node --check` 通过；端到端需安装后按 README 联调 |
| 电脑端（非侵入） | `host-plugin` 增加 `dsh.client` 与手写惰性 CJS 产物 `client.js`，注册 `settings.plugins.tab` 新标签页「插件启停」（可搜索、状态点、每行开关、展开详情）；**不改**官方 `ui-settings-plugin-inventory` | 隔离校验脚本核对包解析、`./client` 导出、工厂 id、槽注册选项全部通过；真实 `dsh web` 需重启后目视 |
| 移动端 | `DshPluginInventoryView.kt` 重写为对齐官方「插件列表」的紧凑折叠卡片：搜索、状态点/启停标签、每行开关（停用/重载二次确认）、展开详情 | `:shared:compileDebugKotlinAndroid` 通过；真机交互待验收 |

设计约束：两端共用同一 Host 端点与 `canToggle` 语义；电脑端通过独立的浏览器半边标签页实现，避免 patch 官方只读页面。`Entry.update()` 的「重载」仍是停用→启用组合，非独立 restart。

## 2026-09-12：原始任务集对照（基础项 / 加分项）

对照对象：[DSH App · AI 对话体验增强任务集](../../dsh-mobile-docs/project/原始需求/DSH%20App%20·%20AI对话体验增强任务集.md)。下表为**代码静态核查**结论，不代表三端真机验收。状态：✅已实现 / 🟡部分 / ❌未实现。

### Task 1 · 主题适配

| 类型 | 条目 | 状态 | 备注 |
| --- | --- | --- | --- |
| 基础 | 浅色/深色/跟随系统 | ✅ | `DshTheme.kt:9`、`DshThemeManager.kt:34` |
| 基础 | 切换立即生效 | ✅ | `DshThemeManager.kt:58`、`BasePager.kt:45` |
| 基础 | 重启保持 | ✅ | `theme_mode`（`DshHomePage.kt:1923`） |
| 基础 | 深色下代码/工具/错误可读 | 🟡 | 前三者已适配，公式缺失 |
| 基础 | 无白屏/文字图标正常 | 🟡 | 三端原生启动窗口硬编码纯白（`activity_hr.xml:5`、`KuiklyRenderViewController.m:30`、OHOS `Color.json:4`） |
| 加分 | 独立代码主题 | ✅ | 随明暗切换，无独立选择器 |
| 加分 | 日出日落自动切换 | ❌ | — |
| 加分 | 高对比度无障碍主题 | ❌ | — |

### 附加题 · Markdown 与 LaTeX

| 类型 | 条目 | 状态 | 备注 |
| --- | --- | --- | --- |
| 基础 | 标题/列表/引用/表格/链接 | ✅ | `DshMarkdown.kt:205` |
| 基础 | 行内代码与代码块 | ✅ | `DshMarkdown.kt:83` |
| 基础 | 行内公式 `$...$` | ❌ | 库可解析 `INLINE_MATH`，渲染器无分派，`$` 按文本显示 |
| 基础 | 块级公式 `$$...$$` | ❌ | 同上（`BLOCK_MATH`） |
| 基础 | 公式跨 chunk 稳定 | ❌ | 无公式节点处理 |
| 基础 | 非法公式降级 | ❌ | 不是「渲染+降级」，是根本不渲染 |
| 基础 | 混排滚动/刷新稳定 | 🟡 | Markdown/代码稳定；公式缺失 |
| 加分 | 公式复制源文本 | ❌ | — |
| 加分 | 矩阵/分式/上下标/希腊字母 | ❌ | — |
| 加分 | 深色公式与代码主题 | 🟡 | 代码已适配，公式缺失 |

### Task 2 · 文本选择、复制与导出

| 类型 | 条目 | 状态 | 备注 |
| --- | --- | --- | --- |
| 基础 | 局部选择复制 | ✅ | `DshSelectTextModal.kt:80` |
| 基础 | 一键复制完整正文 | ✅ | `DshReadableContent.kt:85` |
| 基础 | 代码块仅复制代码 | ✅ | `DshCodeCopy.kt:8` |
| 基础 | 复制保持文本/卡片/工具顺序 | 🟡 | 导出保序含工具；剪贴板一键复制丢弃工具/卡片（`DshReadableContent.kt:84-96`） |
| 基础 | 卡片附件可读导出 | ✅ | `DshReadableContent.kt:27-79` |
| 加分 | PDF/HTML 导出 | ❌ | 仅 TXT/Markdown |
| 加分 | 多选批量导出 | ✅ | `DshHomePage.kt:3673-3769` |

### Task 3 · 图片与文件附件上传

| 类型 | 条目 | 状态 | 备注 |
| --- | --- | --- | --- |
| 基础 | 选图按钮+相册四格式发送 | ✅ | 三端选择器均单选，可多次追加 |
| 基础 | 拍照+权限失败提示 | 🟡 | iOS 文案完整；Android/OHOS 无专门权限被拒文案 |
| 基础 | 发送前预览/移除 | ✅ | `DshHomeConversation.kt:697` |
| 基础 | 发送中状态 | 🟡 | 单张 `UPLOADING` 遮罩渲染前即被清空 |
| 基础 | 失败删除/重试+可读文案 | 🟡 | 仅 FAILED 可重试；`rejectionText` 未渲染，只显示「失败/超限」 |
| 基础 | Host 引用无 Base64 | 🟡 | 上行无 bytes/attachmentId；Host 落盘无法在本仓库确认 |
| 基础 | attachmentId 恢复 | ✅ | `DshHomePage.kt:2387` |
| 基础 | imageLimits 发送前拦截 | ✅ | `DshImageValidation.kt:22` |
| 加分 | 缩略图+全屏 | ✅ | `DshHomeConversation.kt:1106` |
| 加分 | 多选批量发送 | 🟡 | 靠多次追加，非一次多选 |
| 加分 | 预检与 Host 错误码对齐 | 🟡 | 无错误码映射，详文案未展示 |
| 加分 | PDF/纯文本文件 | ❌ | 「文件」仅 toast（`DshHomePage.kt:4839`） |

### Task 4 · 会话删除、重命名与归档

| 类型 | 条目 | 状态 | 备注 |
| --- | --- | --- | --- |
| 基础 | 重命名+空标题提示 | 🟡 | 走 `session.rename`，本地拦截空标题，未处理 Host `title-invalid` |
| 基础 | 归档 `workspace.archiveSession` | ✅ | `DshHostProtocol.kt:41` |
| 基础 | 归档列表+看历史 | ✅ | `DshArchivedSessions.kt` |
| 基础 | 归档二次确认文案 | ✅ | `DshOverflowMenu.kt:206` |
| 基础 | 归档后自动切换 | ✅ | `DshHomePage.kt:3489` |
| 基础 | 失败保持原状 | ✅ | 三处错误仅写对应 state |
| 基础 | 本地模式禁用/提示 | 🟡 | 点击后才提示，菜单未预禁用；LOCAL 分支实际不可达 |
| 加分 | 恢复归档 unarchive | ❌ | 无 RPC |
| 加分 | 永久删除 | ✅ | 依赖 Host 插件 `/api/session-manager/delete` |
| 加分 | 三种排序 | 🟡 | 仅 `updatedAt` 倒序，无 `createdAt` 字段 |
| 加分 | 轻量撤销 | ❌ | — |

### Task 5 · 插件菜单与状态展示

| 类型 | 条目 | 状态 | 备注 |
| --- | --- | --- | --- |
| 基础 | 拉取展示插件列表 | ✅ | `DshPluginInventoryView.kt` + `host-plugin` |
| 基础 | 名称搜索+状态过滤 | ✅ | 移动端搜索名称/ID；电脑端沿用官方标签页 |
| 基础 | failed 状态+错误摘要 | ✅ | `DshPluginInventoryView.kt` 状态点 + 失败原因 |
| 基础 | 空/加载/失败/无结果 UI | ✅ | `DshPluginInventoryView.kt` |
| 基础 | 只读时隐藏启停 | ✅ | 桥接以 `canToggle` 区分；不可启停的开关置灰并给出原因 |
| 加分 | 详情页/完整配置 | ✅ | 展开卡片展示脱敏配置 / Injects / 失败原因 |
| 加分 | 刷新插件状态 | ✅ | `DshPluginInventoryView.kt` 顶部刷新 |
| 加分 | 启停/重载协议 | ✅ | `host-plugin` `/action` + 电脑端浏览器标签页 + 移动端开关 |

### Task 6 · 日志中心与问题反馈

| 类型 | 条目 | 状态 | 备注 |
| --- | --- | --- | --- |
| 基础 | 连接/RPC/host 帧/会话错误 | ✅ | `DshRpcLog.kt`、`DshHostProtocol.kt:1960` |
| 基础 | sessionId/时间/类型 | ✅ | `LogEvent.kt:3` |
| 基础 | 类型与级别筛选 | ✅ | `DshLogFilters.kt:32` |
| 基础 | 脱敏导出/详情/复制 | ✅ | `LogSanitizer.kt:5` |
| 基础 | 容量上限+异步 | ✅ | 5000 条/5 MiB/50 MiB（`DshLogWriteBehind.kt:40`） |
| 基础 | 清空仅删本地 | ✅ | `DshLogSql.kt:41` |
| 加分 | 一键反馈包 | ✅ | `DshLogPage.kt:660` |
| 加分 | 崩溃捕获+启动提示 | ✅ | Android Debug 不落盘 |
| 加分 | 日志跳回会话 | ✅ | `DshLogPage.kt:1174` |

### 优先补齐清单

1. LaTeX/公式渲染（`$...$`、`$$...$$`、跨 chunk、降级）—— 附加题硬性项，且影响 Task 1「公式可读」。
2. Task 2 剪贴板复制保留工具/卡片顺序。
3. Task 3 图片失败/超限的 `rejectionText` 真正展示 + 一次多选 + 与 Host 错误码对齐。
4. Task 4 重命名 `title-invalid` 处理、三种排序；Task 5 插件详情页 / 启停重载（需扩展 Host）。
5. Task 1 三端原生启动窗口深色背景；Task 4 恢复归档（需扩展 Host）。

## 2026-09-12：日志精简与测试清理

- 按项目所有者要求，日志收敛为 DSH 事件摘要 + App 关键状态/操作结果；删除逐次 UI 刷新、解析、渲染、触摸、性能跟踪和问答链路重复打点。
- 删除项目自行添加的共享层、Android、Host 桥接及 OHOS 主机测试、测试依赖和专用注入入口；保留初始化工程自带的 OHOS 示例测试。
- 当前验证采用编译及设备操作。下文的测试数量和探针结果属于清理前历史记录，相关测试源码已删除。
- 本轮编译检查通过：Android Debug APK、JS 共享代码、OHOS ARM64 共享代码；Host 桥接 `node --check` 通过。设备操作验收另行执行。

## 2026-09-12：独立复核后的修复与补齐（最新）

复核确认此前“全部完成”的范围不成立：原有 191 项测试通过，但独立探针暴露序号覆盖、容量不达标、降级误清除、取消前刷盘和复制漏工具结果。以下是本轮修复后的代码状态，不等于三端设备验收全部通过。

| 范围 | 本轮完成的实现 | 验证 / 部署边界 |
| --- | --- | --- |
| LOG-LC-01/02 | 连接页采集 SSH/Relay 状态与失败；建库和序号初始化移到后台；初始化失败保留待写日志并自动重试；生命周期只发送合并刷盘信号 | 共享回归通过；Android 模拟器日志页可读取实际 SQLite 保留记录 |
| LOG-LC-03 | 取消在初次刷盘前、维护轮次和分页边界检查；失效查询主动取消；按操作记录降级，查询成功不清除容量失败 | 原失败探针转为正式回归；存储维护无新日志也会重试 |
| LOG-LC-04 | 统计主文件及 WAL/SHM/journal 实际文件字节；为高等级兜底预留清理轮次；VACUUM 后执行 WAL TRUNCATE 并复查；仍超限保持降级重试 | 非均匀大小、多字节估算和全高等级测试通过；最终 50 MiB 候选值、实际设备维护峰值待验收 |
| LOG-LC-05 | 崩溃日志与稳定 ID 在同一 SQLite 事务提交；清空事务保存清除 ID；迟到导入受清空世代约束；三端原生清理返回实际结果 | 重试、重启、清空与迟到回调回归通过；真实崩溃端到端待设备验证 |
| LOG-LC-06 | 三端实现目录枚举；临时文件发布与回收独立于数据库锁；失败清理；回收避开其他正在导出的临时文件 | Android 文件/并发测试通过；iOS/OHOS 平台运行待验收 |
| Task 2 | 有序回合复制/局部选区输入；AST 代码块独立复制；完整会话 UTF-8 TXT 导出、取消与分享重试；图片名称/属性/引用摘要，缓存保留该摘要 | 有序工具结果、240 条历史、图片引用、代码空行缩进等回归通过 |
| Task 4 | `beforeSeq`/`hasMore` 完整读取；合并原始事件及工具 view 后解析；空非终页/游标不前进报错；过期结果不回写当前会话 | 共享分页/世代回归通过；长归档会话及持续生成时的设备体验待验收 |
| Task 5 | 设置页新增 Host 插件清单、名称/ID 搜索、生命周期过滤、失败原因/配置摘要、空态/失败/超时；配套只读 Host 桥接 | 3 项桥接单测 + 真实 Cordis/WebServer 集成通过；需要按 [host-plugin/README.md](../host-plugin/README.md) 安装并重启 Host |
| Task 3 iOS | PHPicker 相册、相机权限/拍照、原始图片尺寸/MIME/体积验证、相册保存；已加入 Xcode target 和权限文案 | Windows 无 iOS SDK，尚未编译或运行；HEIC/RAW 明确提示转换，上传仍限定官方四种图片类型 |
| OHOS 构建接线 | Ktor 实现归入 sseMain；修复 OHOS SSE 包名、pthread 锁、本地时区 | `:shared:compileKotlinOhosArm64` 通过；链接因缺少 OHOS SDK 受阻 |

SQLite 依赖核查还发现 kuiklySqlite 1.0.0 的 `step()` 隐藏执行错误。本轮插入/删除改走能检查返回码的 `execute()`（文本字面量转义）；分页加结束哨兵，最大序号/统计无返回行时报错，避免把驱动失败当成功或空库。日志 INSERT 改为拒绝冲突，进一步防止覆盖旧记录。

**清理前历史结果**：共享 JVM 单测 **205 项、0 失败**；`:shared:compileKotlinJs` 与 `:androidApp:assembleDebug` 通过；OHOS 原生逻辑主机测试 16 项通过。Android 模拟器安装/冷启动、日志读取、导出文件系统分享面板、设置中的 Host 插件入口及未安装桥接提示完成冒烟。未执行真实删除用户日志或 Host 会话的破坏性验收。

```powershell
.\gradlew.bat :shared:compileKotlinJs :androidApp:assembleDebug --console=plain
node --check host-plugin/index.mjs
.\gradlew.bat -c settings.ohos.gradle.kts :shared:compileKotlinOhosArm64 --console=plain
```

Android 与 OHOS 使用不同 Gradle 设置但共享构建目录，应顺序构建，避免 Windows 文件占用冲突。OHOS 链接最新阻塞是 `OHOS SDK is not found`，不再是 pbcurlwrapper 下载失败。

API 依据：Kuikly [`docs/API/components/basic-attr-event.md`](https://github.com/Tencent-TDS/KuiklyUI/blob/main/docs/API/components/basic-attr-event.md)；KuiklyMarkdown 1.0.6 的 `components/MarkdownComponents.kt`、`elements/MarkdownCodeElement.kt`、`KuiklyStreamingMarkdown.kt` 源码；Apple [PHPickerViewController](https://developer.apple.com/documentation/photosui/phpickerviewcontroller)、[loadFileRepresentation](https://developer.apple.com/documentation/foundation/nsitemprovider/loadfilerepresentation(fortypeidentifier:completionhandler:))（临时 URL 只在回调期间有效）。

### 剩余验收

- [ ] 安装配套 Host 桥接，使用实际部署 Host 验证插件清单及 Relay/SSH 两种路径。
- [ ] 长历史、交错工具与图片会话的真实读取/复制/导出、切换与取消；Task 1 最新主题设备回归。
- [ ] macOS/Xcode 编译和运行 iOS 图片及日志流程。
- [ ] 配齐 OHOS SDK，完成共享库链接、ArkTS/HAP 构建和设备运行。
- [ ] 磁盘不足、真实崩溃、后台挂起和容量维护峰值；最终交付视频与原始交付标准验收。

下面保留此前审查/第一阶段记录，发生冲突时以上面的最新状态为准。

## 2026-09-12：Task 6 日志生命周期审查

审查文档：[日志生命周期审查](log-lifecycle-review.md)。**发现已记录。** 项目所有者已确认三项决策：容量按实际磁盘占用；清空包含原生崩溃快照；导出保留预算按原始需求（不新增长期缓存）。落地顺序：LOG-LC-06 正确性 + LOG-LC-01/02 → LOG-LC-03 → LOG-LC-04/05 → LOG-LC-06 回收。

- [x] LOG-LC-01（高）：应用级唯一日志服务 `DshLogService`；连接设置页首个页面即启动，主页只刷盘不停止；写入器与序号由服务唯一持有（Phase 2，本轮）。
- [x] LOG-LC-02（高）：写后缓存改单后台任务 + 合并唤醒信号；失败有上限指数退避自动重试；运行/排空/停止状态机，迟到事件计数丢弃（Phase 2，本轮）。
- [x] LOG-LC-03（高）：查询/导出改为分批短锁 + 锁外回调，批次间协作取消；存储异常不向业务传播，降级状态在日志页可见（Phase 3，本轮）。
- [x] LOG-LC-04（高）：此前使用 page_count×page_size；独立复核后改为主文件及辅助文件真实字节、分批回收与未达标重试，见最新记录。
- [x] LOG-LC-05（中）：清空同时清除原生崩溃快照；崩溃稳定 ID + 已导入状态持久化，跨主页/重启去重（Phase 4，本轮；原生 end-to-end 待验收）。
- [x] LOG-LC-06（中）：临时文件成功后发布、失败/取消删除半成品；每次导出只保留最近一次有效导出并清理残留半成品（Phase 1 + Phase 4，本轮）。

审查验证：18 项既有日志测试通过；7 项临时探针复现调度、重试、停止、双写入器、取消、异常传播及导出半成品问题。实际 SQLite 检查复现清理后仍有 58.73 MiB、超过 50 MiB 阈值，以及 UTF-8 字节低估。探针通过表示缺陷行为得到验证，不代表修复通过。

后续按审查文档中的 `LOG-LC-TEST-01`—`LOG-LC-TEST-10` 固化回归测试并完成平台验收；本轮证据范围、临时复现入口和待决策项见该文档。

## 2026-09-12：按完整原始任务补齐 Task 4（第一阶段）

已从相邻 `dsh-mobile-docs/project/原始需求` 找到完整 Task 1—6 原文。本节沿用其编号，前述“只沿用 Task3”是 9 月 11 日记录。

- 会话抽屉新增「已归档会话」入口；支持 Host 列表、加载/空态/失败、刷新、打开历史和系统返回。
- `DshSessionCatalogLoader` 成对读取 `workspace.list` 与 `session.list`；部分失败、旧请求及跨连接世代结果不提交。
- 归档成功后选择未归档会话；已归档空白会话不再被“新会话”复用。归档确认文案指向真实查看入口。
- 共享层测试及 `:androidApp:assembleDebug` 通过；新增 `DshSessionCatalogTest` 13 项全部通过。
- Android 12 模拟器覆盖安装/冷启动、归档入口、真实 Host 空态、刷新与返回；归档写入和长历史完整性尚未验收。
- **第一阶段遗留、现已补代码**：此前只取最近 80 条，本轮已接 `beforeSeq`/`hasMore`；设备长历史验收仍待完成。
- Task 2 的有序复制/可读文本导出和 Task 5 插件清单已按最新记录实施。Host ZIP 与新可读 TXT 的语义不同；插件摘要通过配套桥接读取。

详细设计及后续实施步骤：[06-剩余必做功能实施方案](../../dsh-mobile-docs/project/详细设计/06-剩余必做功能实施方案.md)。下列 9 月 11 日鸿蒙/图片记录保留。

## 实施状态

| 任务 | 代码状态 | 验证状态 / 剩余工作 |
| --- | --- | --- |
| Task3：图片附件上传与跨端同步 | 已合入；Android 取图与保存已有实现 | 最新版本仍需设备回归发送、历史恢复、跨会话图片隔离 |
| Task3 补齐：鸿蒙相册取图、拍照、保存 | 本轮实现 | 主机端故障注入测试；鸿蒙 SDK 构建和运行验收待完成 |
| 鸿蒙崩溃记录接入日志中心 | 本轮实现 | 主机端持久化/重启/失败恢复测试；真实 JS/Native 崩溃订阅待验收 |
| 协议文档、入口路径与任务状态同步 | 本轮完成 | 已纠正“只能发文本”和 Agent preset 未接入等过时描述 |
| 最新 Android 设置页、主题和图片回归 | 修复已合入 | 待真机回归，不重复列为待开发 |
| iOS 图片原生桥接 | 已补代码及 Xcode 接线 | 需 macOS/Xcode 编译与设备验证 |
| 鸿蒙 Relay 协议互通 | 已有实现 | 待真实 Host 握手、加密互通、断线重连验证 |
| 鸿蒙 SSH | 尚未实现 | 候选原生库移植方案待验证 |

## 本轮实现入口

- `ohosApp/entry/src/main/ets/kuikly/native/DshImages.ets`：PhotoViewPicker、CameraPicker、保存确认与实际文件写入；取消、短读短写、重复点击和资源清理。
- `ohosApp/entry/src/main/ets/kuikly/native/DshCrashStore.ets`：HiAppEvent `APP_CRASH` 订阅，按事件 ID/时间保留最新记录，原子替换 `filesDir/last_crash.json`；持久化成功后清理系统日志文件。
- `KRBridgeModule.ets` / `EntryAbility.ets`：桥接分派与宿主生命周期接线。
- `shared/.../base/BridgeModule.kt` / `shared/.../home/DshHomePage.kt`：鸿蒙异步读取崩溃，首次进入、重新显示及打开日志时补读；页面内去重、销毁后忽略回调；取消保存图片时静默返回。

实现约束：鸿蒙原生桥接先限制单图 32 MB，避免 Base64 扩容导致无界分配；Host 的格式、尺寸、批次限制仍由共享层检查。崩溃正文最多保留 16000 字符，读取不消费记录，明确清理时才删除本地快照。迟到的系统事件先落盘，在下次主页读取时进入日志中心。

## 可重复执行的检查

在仓库根目录执行：

```powershell
# 共享代码编译
.\gradlew.bat :shared:compileDebugKotlinAndroid --console=plain

# 配好 OHOS_SDK_HOME 后编译 ARM64 共享库
.\gradlew.bat -c settings.ohos.gradle.kts :shared:linkDebugSharedOhosArm64 --console=plain
```

原 `ohosApp/tests` 主机测试已按项目所有者要求删除，鸿蒙原生流程通过 HAP 构建和设备操作验证。

本轮结果：

- Android 共享代码编译及现有单元测试通过。
- 主机端 16 项测试全部通过：覆盖短读短写、取消、复制失败、并发取图、无效/超大图片，以及崩溃重复事件、旧事件、重启恢复、写盘失败、清理和不可读证据保留。
- 鸿蒙完整构建受阻：联网运行已解析 `2.0.21-KBA-010`，在 `:shared:downloadPbcurlwrapper` 下载原有网络库时出现 `curl: (56) Recv failure: Connection was reset`。当前 PATH 未发现 DevEco 工具，SDK 环境变量为空。
- HAP 打包和设备/模拟器运行尚未执行。

## 运行验收清单

- [ ] 配齐 DevEco SDK、网络库与本机签名，完成 Kotlin/Native 链接及 ArkTS/C++ HAP 构建。
- [ ] 核对模拟器是否能加载本项目整套 ARM64 原生库，或使用兼容设备。
- [ ] 相册/相机选图 → 草稿预览 → Host 接收 → 历史恢复；取消后无错误提示。
- [ ] 保存确认后相册文件内容正确；拒绝保存、磁盘不足、离开页面等场景不误报成功。
- [ ] 分别触发 JS/Native 崩溃，重启后全局日志和反馈文件包含 `crash` 记录。
- [ ] Relay 建连、流式回答、断线恢复及前后台切换。
- [ ] Android 最新设置页无抖动，主题冷启动/图标/工具卡片刷新正确，图片无跨会话错位。

## API 依据

- [Kuikly 鸿蒙开发方式与 Windows 工具链](https://kuikly.tds.qq.com/DevGuide/harmony-dev.html)
- [Kuikly Module 原生扩展](https://kuikly.tds.qq.com/DevGuide/expand-native-api.html)
- [OpenHarmony 5.0 相册管理（PhotoViewPicker / showAssetsCreationDialog）](https://github.com/openharmony/docs/blob/OpenHarmony-5.0.0-Release/zh-cn/application-dev/reference/apis-media-library-kit/js-apis-photoAccessHelper.md)
- [OpenHarmony 5.0 CameraPicker](https://github.com/openharmony/docs/blob/OpenHarmony-5.0.0-Release/zh-cn/application-dev/reference/apis-camera-kit/js-apis-cameraPicker.md)
- [OpenHarmony 5.0 崩溃订阅](https://github.com/openharmony/docs/blob/OpenHarmony-5.0.0-Release/zh-cn/application-dev/dfx/hiappevent-watcher-crash-events-arkts.md)
