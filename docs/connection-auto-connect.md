# 连接页自动连接与 SSH 隧道复用

## 背景

冷启动时，App 首个页面是「连接 DSH」。为了让常用电脑免手动点击，连接页现在会自动尝试一次连接：

- 连得上 → 直接进入主页 `home`；
- 连不上 → 停留在连接页并显示可读原因。

实现见 `shared/src/commonMain/kotlin/com/example/dsh/connection/DshConnectionSetupPage.kt`：

- `autoConnect()`：按上次保存的模式分派；
- `probeSsh()`：SSH 走真实探测（建隧道 + 拉会话）；
- `probeRelay()`：Relay 建隧道后拉一次会话，另加 8 秒超时兜底；
- 从主页返回连接页（改设置 / 断开）会带 `skipAutoConnect=true`，避免刚断开又被弹回主页。

## 观察到的问题：SSH 重复建连

自动连接上线后，从日志数据库 `dsh_logs.db` 观察到一个 SSH 会话出现了**两次**连接过程：

```text
connect.connecting  正在打开 DSH 事件流
connect.stopped     连接已停止
connect.connecting  正在打开 DSH 事件流
connect.handshake   事件流已连接
connect.syncing     正在同步远程会话
connect.ready       DSH 已就绪
```

即「连接页探测 → 断开 → 主页重连」。除了多一次 SSH 握手与本地转发，SSH 服务端也会看到两个会话。

## 根因

1. 连接页 `probeRemote()` 成功后会 `stopSsh()`，主动断开刚建好的隧道；
2. 主页 `startSshEngine()` 随后再次 `startSsh()`，只能重建；
3. 且 `SshTunnelManager.connect()` 没有复用判断，即使隧道仍是 `READY` 也会重新握手。

补充：修复过程中还发现复用路径如果再次 `publish(READY)`，会让主页 `connectRemoteEngine` 跑两次（`addListener` 已经回放过一次状态），表现为「连接 → 停止 → 再连接」。

## 修复

### 1. 连接页保留隧道（`DshConnectionSetupPage.probeRemote`）

探测成功后不再 `stopSsh()`，改为 `detachSsh()`：只解除本页监听，保留隧道交给主页。

### 2. 新增 `detachSsh`（跨端）

- 共享层 `DshEngineModule.detachSsh()`；
- Android `KRDshEngineModule`：移除本页 listener，不 `disconnect`；
- iOS `DshEngineModule.m` / OHOS `KRDshEngineModule.ets`：退化为断开，行为与 `stopSsh` 一致。

### 3. 隧道复用（Android `SshTunnelManager.connect`）

```kotlin
if (state.phase == SshPhase.READY && localPort > 0 && config == next) {
    Log.i(TAG, "reuse existing READY tunnel port=$localPort")
    return
}
```

配置相同且已就绪时直接复用；因为 `startSsh` 已先 `addListener` 回放 READY，这里不再 `publish`，避免重复建连。

## 各端差异

| 平台 | 隧道模型 | 复用 |
| --- | --- | --- |
| Android | `SshTunnelManager` 进程级单例 | 已支持，避免重复建连 |
| iOS | `DshSshTunnel` 为页面级实例，无法跨页持有 | 仍为「断开 + 重连」，无回归 |
| OHOS / H5 / 小程序 | 不支持 SSH | 不适用 |

iOS 若要同样复用，需要把隧道提升为单例，属于后续改造项。

## 验证

模拟器（Pixel 6 / Android 12，SSH 模式）冷启动：

- 日志库 `connect.*` 只出现**一次**连接过程（无 `connect.stopped` 夹在中间）；
- Logcat 出现 `DshSshTunnel: reuse existing READY tunnel port=35321`，确认命中复用；
- 会话同步完成（多个 `session/subscribed` 后 `connect.ready`）。

## 涉及文件

- `shared/src/commonMain/kotlin/com/example/dsh/connection/DshConnectionSetupPage.kt`
- `shared/src/commonMain/kotlin/com/example/dsh/connection/DshEngineModule.kt`
- `androidApp/src/main/java/com/example/dsh/ssh/SshTunnelManager.kt`
- `androidApp/src/main/java/com/example/dsh/module/KRDshEngineModule.kt`
- `iosApp/iosApp/KuiklyExpand/Modules/DshEngineModule.m`
- `ohosApp/entry/src/main/ets/kuikly/modules/KRDshEngineModule.ets`
