# DSH 移动端 Host 桥接（插件清单 / 详情 / 启停重载 + 归档会话管理）

为移动 App 和电脑端 DSH Web 提供同一套插件启停能力：

1. 移动 App「设置 → Host 插件」的真实 Loader 清单、配置摘要、失败原因与**详情**，并支持**启停 / 重载**（见「插件端点」）。
2. 电脑端 DSH Web「设置 → 插件」分区新增的**插件启停**标签页：同样的清单、搜索、状态与开关（见「Web 端插件启停标签页」）。
3. 归档会话的元数据读取、取消归档与文件级删除（见「会话管理端点」）。
4. 电脑端 DSH Web 侧边栏底部的**已归档的聊天**入口与管理页：搜索、项目筛选、排序、取消归档、永久删除（见「电脑端已归档的聊天页」）。

均基于公共 Cordis 服务，不改 DSH 核心源码。启停/重载只用 vendored loader 的公共 `Entry.update()`，不读写私有状态。取消归档是唯一例外：官方没有公开的 unarchive RPC，见「会话管理端点」的说明。电脑端也不改官方只读的「插件列表」标签页，而是由本包自身的浏览器半边注册一个新标签页。

## Web 端插件启停标签页（非侵入）

本包声明 `dsh.client` 并提供手写的惰性 CJS 工厂产物 `client.js`（官方 `tsdown.client.ts` 预设未发布，外部包必须自行复现该格式）。Host 启动时扫描已挂载条目，把 `client.js` 作为 `/plugins/dsh-mobile-plugin-inventory/client.js` 下发；浏览器半边用 `settings.plugins.tab` 槽注册 `mobile-plugin-switch` 标签页（order 11）。

- 只读官方源码：`ui-settings-plugin-inventory` 与其 `all` 标签页原样保留；新能力以并列标签页形式提供，不 patch、不覆盖。
- 浏览器半边只 `require('react')`（平台模块表），通过同源 `fetch` 调用下面的 `/list` 与 `/action` 端点；文案按文档语言在中/英之间切换。
- 每行提供开关、状态点、启停标签与展开详情（Loader 条目、Cordis 状态、失败原因、脱敏配置、Injects、重载）。
- `canToggle` 为假的条目开关置灰并展示原因（桥接自身、条件表达式、上级分组禁用）。
- 改动 `client.js` 后重启 `dsh web` 即可生效；Host 会按内容哈希刷新 `rev`，开发期可用官方 HMR 通道热更新。

## 电脑端已归档的聊天页（非侵入）

官方 DSH（含 `dsh-v0.1.2-alpha.3` / `master`）只有「归档会话」动作，没有归档浏览或取消归档入口。本包在同一个手写 `client.js` 里再注册一个 `sidebar.footer.action` 槽条目：侧边栏底部的「已归档的聊天」，点击后以整屏浮层打开管理页，不替换官方任何页面。

- 入口由 `sidebar.footer.action`（list 槽）承载，展开态显示图标 + 文案，折叠成 56px 轨道时只显示图标。
- 数据直接读客户端标准 hook `useWorkspaces`（`items`、`archivedSessionIds`）与 `useSessions`（`byId`）：已归档会话按工作区 `sessionIds` 分组，未归属的进「无项目」，与官方树的分组口径一致；「创建时间」排序的 `createdAt` 由 `/api/session-manager/meta` 补充。
- 「取消归档」调用 `/api/session-manager/unarchive`，写入归档集合后由 `host/archived-sessions-changed` 驱动两端自动刷新。
- 永久删除复用 `/api/session-manager/deleteMany`（单条 / 项目内全部 / 全部）；已删除项在当前会话内本地过滤，重载后由 Host 列表自然消失。
- 类型筛选按项目所有者要求只保留「全部聊天」；「本地 / 云端」在当前 Host 协议里没有对应字段，因此不展示。
- 样式使用 `--dsw-alias-*` 语义 token，跟随 DSH 明暗主题；图标复用移动端 `shared/src/commonMain/assets` 中的 `archive/folder/delete/tool-search/chevron-down/check/more.svg`（内联为 SVG 路径）。
- 官方只读的「插件列表」标签页与官方会话树均不改动。

## 安装

在运行 DSH Host 的电脑上，从本工程的 `host-plugin` 目录安装到 **web profile**（将路径替换为该电脑的实际绝对路径）：

```sh
npx @deepseek-ai/dsh plugin --profile web add "/absolute/path/deepseek-harness-mobile/host-plugin"
```

然后重启 `dsh web`，保持 Host 绑定 `127.0.0.1`，通过现有 SSH 或已配对 Relay 连接。App 无需另配凭据；Relay 的本机网关继续检查其 Bearer token，SSH 继续使用已认证隧道。桥接拒绝非回环请求和跨站浏览器来源；不提供插件安装/卸载，也不写任意配置，只允许通过 `Entry.update()` 启用、停用、重载现有条目。

本插件没有额外 npm 运行依赖，入口是 `index.mjs`，安装清单位于 `cordis.patch.yml`。尚未安装时 App 显示接口不可用提示，不会显示成“暂无插件”。

## 契约

`GET /api/mobile-plugin-inventory/v1/list`：

```json
{
  "ok": true,
  "version": 1,
  "entries": [{
    "entryId": "plugin-entry-id",
    "moduleName": "example-plugin",
    "enabled": true,
    "fiberPhase": "failed",
    "configSummary": "{\"token\":\"***\"}",
    "failureSummary": "插件启动失败的真实原因",
    "configDetail": "{\n  \"token\": \"***\"\n}",
    "injectDetail": "[\"webServer\",\"loader\"]",
    "disabledExpr": null,
    "canToggle": true,
    "toggleHint": null
  }]
}
```

- 名称、启用状态和生命周期读取 `loader.entries()` / `entry.fiber.state`，与官方 inventory 的来源一致；跳过 group。
- 配置优先读取当前 `fiber.config`，无运行实例时读取 `entry.options.config`，递归脱敏并限制摘要深度和字段长度；`configSummary` 为压缩摘要，`configDetail` 为缩进后的完整脱敏配置。
- 仅对已失败的 Fiber 调用公共 `await()`，取得其真实启动/配置异常；遇到并发重启最多等待 500 ms，然后提示刷新。不访问私有 `_error`，不触发 restart。
- `enabled` 与 `fiberPhase` 独立，空 phase 表示无运行实例；未知 phase 原样保留。
- `canToggle` / `toggleHint` 告诉 App 该条目能否单独启停：桥接自身、条件表达式（`!!js`）控制的条目、以及被上级分组禁用的条目均不可启停并给出原因。
- 错误响应为 `{ok:false,error:{code,message}}`。清单仅支持 GET。

### 插件操作端点

`POST /api/mobile-plugin-inventory/v1/action`：

```json
请求：{ "entryId": "plugin-entry-id", "action": "enable" | "disable" | "reload" }
成功：{ "ok": true, "action": "disable", "entryId": "plugin-entry-id", "enabled": false, "fiberPhase": null }
失败：{ "ok": false, "error": { "code": "plugin-action-failed", "message": "可读原因" } }
```

- `enable` 清除该条目自身的 `disabled` 标记；`disable` 写入 `disabled:true` 并卸载；`reload` 通过一次「停用 → 启用」重新初始化（Loader 的 `Entry.update()` 没有独立 restart）。
- 只通过公共 `Entry.update()` 修改条目自身的启停标记；**不**写任意配置、**不**改变条目顺序、**不**访问私有状态。
- 拒绝操作桥接插件自身、条件表达式控制的条目，以及被上级分组禁用的条目，返回可读原因。
- 操作串行执行，避免 App 并发请求造成停用/启用交错。
- 仅支持 POST，回环 + Bearer；错误响应同 `{ok:false,error:{code,message}}`。

源码兼容性核对：DSH `b150a551b8d465e31e418e1b2eaf5e79bbb7d28e` 的 `vendor/loader/src/config/entry.ts`、`vendor/cordis/src/fiber.ts`、`packages/host/webserver/src/index.ts`。其他 Host 版本需安装后核对清单显示。

## 会话管理端点（归档页）

供 App「已归档的聊天」使用，全部回环 + Bearer，返回 JSON。插件因此 `inject` 增加 `sessionPersistence`。

`/api/session-manager/meta`（GET 或 POST）：

```json
{ "ok": true, "version": 1, "sessions": [{ "sessionId": "...", "createdAt": 1725000000000, "cwd": "/path" }] }
```

`createdAt`/`cwd` 来自 `ctx.sessionPersistence.list()` 的 `SessionHeader`，供归档页「创建时间」排序与项目分组。

`/api/session-manager/delete`（POST）：

```json
请求：{ "sessionId": "..." }
成功：{ "ok": true, "sessionId": "..." }
```

`/api/session-manager/deleteMany`（POST）：

```json
请求：{ "sessionIds": ["..."] }
响应：{ "ok": true, "deleted": ["..."], "failed": [{ "sessionId": "...", "error": "可读原因" }] }
```

`/api/session-manager/unarchive`（POST）：

```json
请求：{ "sessionId": "..." }
成功：{ "ok": true, "sessionId": "..." }
```

- 取消归档把会话从 registry 的归档集合移除，使其重新出现在工作区分组与「无项目」中。
- 官方 DSH 至今没有公开的 unarchive RPC，归档集合又是 `WorkspaceRegistry` 的私有状态；本端点复用 registry 自己的串行写链（`enqueueOperation`）与状态提交（`setState`），只改 `archivedSessionIds`。`setState` 写入 `workspace` domain global 会触发 `domain/changed`，Host apiproxy 随即向所有客户端广播 `host/archived-sessions-changed`，因此电脑端与手机端都无需手动刷新。
- 这是对 registry 运行期内部方法的最小依赖（TS `private` 在运行期可用）：实现前会探测 `state`/`setState`，缺失时返回可读错误而不是静默失败。
- 删除是**文件级**：`sessionPersistence.locate()` 取到会话 artifact 路径后删除其所在目录；不修改 workspace 归档集合、投影缓存或会话日志之外的数据。
- 运行中的会话拒绝删除；未知 id、当前后端无单会话 artifact 均返回可读错误。

## 验证

```sh
node --check host-plugin/index.mjs
node --check host-plugin/client.js
```

安装桥接后，通过实际 Host → Relay/SSH → App 核对插件清单显示、失败提示、刷新与启停；再打开电脑端 `dsh web` 的「设置 → 插件 → 插件启停」，核对同一插件在两端的启停状态一致（切换后另一端刷新可见）。

归档：先在电脑端 `dsh web` 归档一个会话，再从侧边栏底部打开「已归档的聊天」，核对搜索、项目筛选、排序、取消归档与删除；取消归档后该会话应回到工作区树，同一 Host 的移动 App 归档页刷新后也应同步。
