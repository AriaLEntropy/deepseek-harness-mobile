# DSH App · DeepSeek Harness 移动端宿主

> 一套 Kotlin 代码，跑 Android / iOS / OpenHarmony（鸿蒙）三端，把电脑上的 DeepSeek Harness Agent 塞进你的口袋。

![Kotlin](https://img.shields.io/badge/Kotlin-Multiplatform-purple)
![Kuikly](https://img.shields.io/badge/Kuikly-Cross--Platform-blue)
![Android](https://img.shields.io/badge/Android-7.0%2B-green)
![iOS](https://img.shields.io/badge/iOS-14.1%2B-lightgrey)
![OpenHarmony](https://img.shields.io/badge/OpenHarmony-5.0-blue)
![License](https://img.shields.io/badge/License-MIT-green)

---

## 它解决了什么问题

DeepSeek Harness 是一个跑在电脑上的 Agent 运行时——它要吃 API Key、要跑工具、要起 HTTP 服务。**它本来不是为手机设计的。**

直接把整个 Harness 塞进 App 不可行：光运行时就约 115 MB，模型推理吃手机内存，工具链和文件系统也对不上。

DSH App 选择了另一条路：

```
手机 App（薄客户端）  ──扫码 Relay / SSH──▶  电脑 DSH Host（Agent + 模型 + 工具）
```

- App **不内嵌** Agent，不携带 `payload.zip`，APK 体积回到轻量；
- 模型推理和工具执行仍然在电脑上，能力不缩水；
- 手机只是一个远程的流式终端 + 工具调用面板 + 日志中心。

---

## 30 秒看懂架构

工程按 **页面 → 状态 → 数据 → 平台** 四层拆分，并且已经从单 `shared` 模块演进成 **5 个 core + 7 个 ui** 的多模块结构：

```text
┌──────────────────────────────────────────────────────────────┐
│  平台壳层                                                     │
│  androidApp · iosApp · ohosApp · h5App(预留) · miniApp(预留) │
├──────────────────────────────────────────────────────────────┤
│  聚合层：shared                                              │
│  DshHomePage 主页（会话 / 消息 / 输入框 / 模型配置）          │
├──────────────────────────────────────────────────────────────┤
│  UI 层（Kuikly DSL）                                         │
│  ui-base · ui-kit(渲染) · ui-settings · ui-export            │
│  ui-voice · ui-web · ui-dev                                  │
├──────────────────────────────────────────────────────────────┤
│  Core 层（commonMain，一套逻辑跑三端）                       │
│  core-platform · core-model · core-log · core-theme         │
│  core-data（host 协议 / 消息 / 连接 / 附件 / 导出 / 诊断）   │
└──────────────────────────────────────────────────────────────┘
        ▲ 扫码 Relay / SSH（两条通道）
┌────────┴─────────────────────────────────────────────────────┐
│  电脑 DSH Host :3080                                          │
│  ├─ host-plugin          ← 本仓库自研桥接（不改 DSH 源码）    │
│  └─ dsh-scan-remote       ← 外部扫码 Relay 插件               │
└───────────────────────────────────────────────────────────────┘
```

**三条设计原则**：

1. **业务逻辑全部下沉到 commonMain** —— 消息模型、Host 协议、日志、主题、附件校验、导出逻辑三端共用；平台只提供存储 / HTTP / WebSocket / 取图等能力。
2. **协议对齐官方** —— RPC 方法名与官方 `packages/host/apiproxy` 完全一致，App 不自定义 JSON-RPC 方法，能跟着官方版本升级。
3. **官方缺口用只读桥接补齐** —— 插件启停、取消归档、永久删除、通用文件上传都是官方能力缺口，由 `host-plugin` 基于公共 Cordis 服务补齐，**一行 DSH 核心源码都不改**。

---

## 我们做了什么（不只是照着题目点功能）

围绕"AI 对话体验增强"共交付 **8 项任务**（Task 1–6 + 附加题），但下面这些是题目之外、自己挖出来的硬骨头：

### 1. 流式 Markdown + LaTeX 双轨渲染

KuiklyMarkdown 解析器认识 `$...$` / `$$...$$` 节点，但渲染器不画公式。我们在送渲染前自己切分文本与公式段：

- 支持 WebView 的平台走 **内联 KaTeX**（CSS/JS 全量内联，无网络请求，深色模式单独配色）；
- 不支持 WebView 的平台自动降级为 **Unicode 近似公式文本**；
- 公式跨多个流式 chunk 到达时，闭合后才进渲染路径，未闭合不渲染、不崩、不丢消息。

### 2. 断线补偿与连接世代

远程连接会断。重连后我们做了三件事：

- mux 事件流重放；
- Host 还在跑的 turn 用 `adoptLiveStream` 挂回当前会话，**不重复发 prompt**；
- 按连接世代（generation）失效在途请求，过期结果不会写回当前会话。

### 3. 日志中心（不只是个 logcat 面板）

- 结构化日志落 **SQLite**（三端共用 kuiklySqlite），按类型 / 级别 / 会话过滤；
- **写后缓存**：单后台任务 + 合并唤醒信号 + 指数退避重试，不卡 UI；
- **容量硬上限**：5000 条 / 5 MiB 正文 / 50 MiB 磁盘，按真实文件字节核算，VACUUM 后 WAL TRUNCATE；
- **隐私脱敏**：API Key / Authorization / access-ticket / clientToken / hostToken / 附件 Base64 全部正则脱敏后才允许导出；
- 一键生成**问题反馈包**（脱敏日志 + 连接模式 + App 版本 + 设备型号），三端崩溃订阅，下次启动提示。

### 4. host-plugin：不改 DSH 源码的桥接层

官方 DSH 缺什么，我们补什么，但**不 fork、不 patch**：

| 端点 | 补的官方缺口 |
| --- | --- |
| `GET /api/mobile-plugin-inventory/v1/list` | 插件清单 / 失败原因 / 脱敏配置摘要 |
| `POST /api/mobile-plugin-inventory/v1/action` | 插件启停 / 重载（基于公共 `Entry.update()`） |
| `POST /api/session-manager/unarchive` | 官方根本没有公开的取消归档 RPC |
| `POST /api/session-manager/delete` / `deleteMany` | 永久删除（单条 / 项目内 / 全部） |
| `POST /api/mobile-attachment/v1/upload` | `v0.1.5-alpha.1` 前没有的通用文件上传 backport |

同时给电脑端 Web 加了两个**非侵入**入口：设置页「插件启停」标签页、侧边栏「已归档的聊天」管理页，样式跟随 DSH 明暗主题。桥接自己拒绝非回环请求、拒绝跨站来源、拒绝操作桥接自身，写操作串行执行。

### 5. 消息模型与流状态机

下行事件（`user/message`、`assistant/chunk`、`assistant/message`、`tool/call`、`tool/result`、`turn/end`）投影到内存 store，驱动气泡 / 工具卡片 / 流式 delta；`tool/call` + `tool/result` 折叠成一张卡片；历史按 `beforeSeq` 分页拉取，80 条是单页预算不是历史上限。

### 6. 工程质量

- 共享层业务逻辑全部有 `commonTest` 单测（协议解析、日志、附件校验、消息格式化、会话目录、搜索、导出选择、WebSocket URL……清理前 **205 项 0 失败**）；
- 用独立探针驱动修复闭环：序号覆盖、容量不达标、降级误清除、取消前刷盘、复制漏工具结果——全部修完并固化回归；
- SQLite 驱动从 `step()` 改 `execute()` 检查返回码，分页加结束哨兵，INSERT 拒绝冲突防覆盖。

---

## 任务完成度

| 任务 | 主题 | 状态 |
| --- | --- | --- |
| Task 1 | 夜间模式与系统主题适配 | ✅ 基础 + 独立代码主题 |
| 附加题 | Markdown 与 LaTeX 渲染 | ✅ KaTeX + Unicode 双轨 |
| Task 2 | 文本选择、复制与导出 | ✅ 含 PDF / HTML / 多选批量 |
| Task 3 | 图片与文件附件上传 | ✅ 预检 + Host 桥接 backport |
| Task 4 | 会话删除、重命名与归档 | ✅ 含取消归档 / 永久删除 |
| Task 5 | 插件菜单与插件状态展示 | ✅ 含详情 / 刷新 / 启停重载 |
| Task 6 | 日志中心与问题反馈 | ✅ 含反馈包 / 崩溃捕获 / 跳回会话 |

---

## 技术栈

| 层 | 选型 |
| --- | --- |
| 跨端 UI | 腾讯 Kuikly + Kotlin Multiplatform |
| 并发 / 序列化 | kotlinx-coroutines 1.10.1 / kotlinx-serialization 1.8.0 |
| 网络 | Ktor Client（Android OkHttp / iOS Darwin）+ 自研 WebSocket |
| 本地存储 | kuiklySqlite（三端共用） |
| Markdown / 公式 | KuiklyMarkdown + 自研流式增量渲染 + KaTeX 内联 |
| Android | compileSdk 34 / minSdk 24 |
| iOS | Xcode 15+ / iOS 14.1 / CocoaPods（NMSSH / PHPicker） |
| 鸿蒙 | ArkTS 原生桥接 |
| Host 桥接 | `host-plugin/`（Node.js，零额外依赖，装到 DSH `web` profile） |

---

## 怎么跑起来

需要两个插件：外部的 `dsh-scan-remote`（扫码 Relay）+ 本仓库的 `host-plugin`（能力桥接）。

```bash
# 1. 电脑起 Relay
git clone https://github.com/yukiykchen/dsh-scan-remote.git
cd dsh-scan-remote/relay && npm ci && npm run build
HOST=127.0.0.1 PORT=8787 npm start

# 2. 装外部扫码插件 + 本仓库桥接插件，起 DSH
npx @deepseek-ai/dsh plugin --profile web add "github:yukiykchen/dsh-scan-remote#v0.0.1"
npx @deepseek-ai/dsh plugin --profile web add "/absolute/path/deepseek-harness-mobile/host-plugin"
PUBLIC_RELAY_URL=http://<电脑 LAN IP>:8787 npx @deepseek-ai/dsh web

# 3. 手机装 App（或自己编）
./gradlew :androidApp:installDebug
```

App 里「扫码连接」扫电脑二维码即可；异地用 SSH 端口转发直连，不需要 Relay。

---

## 仓库结构

```text
deepseek-harness-mobile/
├── core-{model,platform,log,theme,data}/   # 5 个核心模块
├── ui-{base,kit,settings,export,voice,web,dev}/  # 7 个 UI 模块
├── shared/                                 # 聚合层 + DshHomePage 主页
├── androidApp/ iosApp/ ohosApp/            # 三端宿主壳
├── host-plugin/                            # ★ 本仓库自研 DSH 桥接插件
│   ├── index.mjs       # 插件清单/启停/会话管理端点
│   ├── attachment.mjs  # 通用文件附件 backport
│   └── client.js        # 电脑端 Web 两个非侵入标签页
├── tools/                                  # KaTeX 资源生成等
└── docs/                                   # 协议 / 任务 / 设计文档
```

---

## 它不是什么

- 不是离线模型——推理仍然走 DeepSeek 在线 API；
- 不是 fork DSH——Host 侧零改动，全部能力走桥接；
- 不是三端都验收完毕——iOS / 鸿蒙受 SDK 环境限制待真机回归；
- 不是生产版本——仍在开发验证阶段。

---

> DeepSeek Harness · Kuikly · Kotlin Multiplatform —— 把 Agent 装进口袋。
