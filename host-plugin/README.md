# DSH 移动端 Host 桥接（插件清单 / 详情 / 启停重载 + 归档会话管理）

为 App 提供两组能力：

1. 「设置 → Host 插件」的真实 Loader 清单、配置摘要、失败原因与**详情**，并支持**启停 / 重载**（见「插件端点」）。
2. 归档页的会话元数据读取与文件级删除（见「会话管理端点」）。

均基于公共 Cordis 服务，不改 DSH 核心源码。启停/重载只用 vendored loader 的公共 `Entry.update()`，不读写私有状态。

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

- 删除是**文件级**：`sessionPersistence.locate()` 取到会话 artifact 路径后删除其所在目录；不修改 workspace 归档集合、投影缓存或会话日志之外的数据。
- 运行中的会话拒绝删除；未知 id、当前后端无单会话 artifact 均返回可读错误。
- 本插件**不提供取消归档**（需要 Host 的 unarchive 能力，纯插件无法改归档集合）。

## 验证

```sh
node --check host-plugin/index.mjs
```

安装桥接后，通过实际 Host → Relay/SSH → App 核对插件清单显示、失败提示和刷新行为。
