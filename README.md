# DeepSeek Harness Mobile

将 [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness) 接到 Android 手机的实验性移动端宿主项目。

App 启动后先选连接方式，再进入聊天：

- **手机本地**：把 Node.js Runtime 和编译后的 Harness 打进 APK，在手机上启动本机 Host。
- **扫码连接**：扫描电脑 DSH Settings 里的二维码，经 Relay 访问电脑上的 Harness。
- **SSH**：用本机端口转发连到电脑上的 DSH。

本项目的目标不是把 DeepSeek Harness 翻译成 Kotlin，而是用 Kotlin/Kuikly 做宿主界面，并按所选模式连到本地或远程 Host。

> 当前项目处于开发和验证阶段，包体较大、启动耗时和后台存活能力仍在优化中。请不要把它当作生产版本使用。

## 项目定位

DeepSeek Harness 本身是一个插件化 Agent 运行时，负责处理：

- Agent loop：模型请求、工具调用和多步骤执行；
- 会话事件：用户消息、模型流式输出、工具调用和执行结果；
- 模型适配、会话管理、任务和插件组合；
- Web Host、RPC 接口和事件流。

本仓库负责提供 Android 宿主环境：

- 用 Kotlin 管理 Android 生命周期和本地 Node.js 进程；
- 首次启动时将随 APK 携带的 Harness Runtime 解压到 App 私有目录；
- 在「手机本地」模式下于 `127.0.0.1:3080` 启动内嵌 Harness Web Host；
- 本地模式用 HTTP RPC + SSE；扫码 / SSH 远程模式用 HTTP RPC + WebSocket（`events.mux`）；
- 使用 Kuikly/Kotlin Multiplatform 实现主要 UI 和跨平台协议层；
- 按连接模式隔离 API Key、会话列表和消息缓存；
- 可选通过扫码 Relay 或 SSH 隧道连接电脑上的 DSH Host。

## 连接模式

启动后首页是「连接 DSH」，三个 Tab 含义如下。

| 模式 | App 文案 | Agent 跑在哪 | 传输 | API Key 配在哪 | 会话缓存 |
| --- | --- | --- | --- | --- | --- |
| 本地 | 手机本地 | 手机内嵌 Harness | 本机 HTTP + SSE | 手机 | `local` |
| 远程 Relay | 扫码连接 | 电脑 DSH | Relay sealed tunnel + 本机 loopback WebSocket | 电脑 | `relay:<hostId>` |
| 远程 SSH | SSH | 电脑 DSH | SSH 本地转发 + WebSocket | 电脑 | `ssh:default` |

三种模式互不影响。从本机切到扫码后，看不到本机会话，这是预期行为。

- 只想在手机上用、不连电脑：选 **手机本地**，需要 Shizuku 和首次解压 Runtime。
- 电脑已经在跑 DSH，手机和电脑同一 Wi-Fi / 热点：选 **扫码连接**。插件与 Relay 安装见 [dsh-scan-remote](https://github.com/yukiykchen/dsh-scan-remote/blob/master/README.zh-CN.md)。
- 已有 SSH 私钥，或不想在电脑上跑 Relay：选 **SSH**。

扫码连接目前仅支持 Android。

## 工作原理

### 手机本地

```text
Android App
  |
  | Kotlin / Kuikly UI、生命周期、桥接和本地缓存
  |
  +--> 启动内嵌 Node.js Runtime
          |
          +--> 执行编译后的 DeepSeek Harness JavaScript
                  |
                  +--> Harness Web Host
                          |
                          +--> 监听 127.0.0.1:3080

Android App -- HTTP RPC --> http://127.0.0.1:3080/api/*
Android App <-- SSE ------ http://127.0.0.1:3080/api/events.mux
```

### 扫码连接

```text
电脑 DSH :3080 (127.0.0.1)
        ^
        | 本机回环
dsh-scan-remote 插件 --WSS--> Relay :8787 <--WSS-- 手机 App
                                     ^
                                     |
                          二维码里的 publicRelayUrl
                          必须是手机能访问的电脑 IP
```

手机配对成功后，在本机拉起一个 loopback WebSocket 网关，聊天协议与远程 Host 对齐，不再走内嵌 Node 内核。

### SSH

```text
Android App -- HTTP/WS --> 127.0.0.1:<本地转发端口>
                              |
                         SSH tunnel
                              |
                         电脑 127.0.0.1:3080
```

### 运行时组成

`androidApp/src/main/assets/payload.zip` 包含随 App 分发的运行时资源，主要包括：

- Android 可执行的 Node.js Runtime；
- 编译后的 DeepSeek Harness JavaScript 和依赖；
- Harness 的 `web` profile、配置和运行目录模板；
- 本地运行所需的 Bash 和动态库资源。

`DshEngineManager` 会在首次启动时将这些文件解压到 App 私有目录，随后执行类似下面的命令：

```text
<app-files>/dsh-engine/runtime/bin/node \
  <app-files>/dsh-engine/dshroot/lib/node_modules/@deepseek-ai/dsh/lib/bin.js \
  web --host 127.0.0.1 --port 3080
```

这里的“本地运行”主要表示 Agent Runtime、Harness Host、会话和 App 与 Host 的通信都在手机上完成。当前项目使用 `deepseek-official` 模型提供方，因此模型推理和对 DeepSeek API 的请求仍然需要网络以及 DeepSeek API Key；它不是完全离线的本地大模型 App。

## 使用前置条件

### 1. 手机本地模式必须先启动 Shizuku 服务

**手机本地**模式依赖 [Shizuku](https://github.com/RikkaApps/Shizuku)。扫码连接和 SSH 连的是电脑上的 DSH，不走手机内嵌内核，不需要为远程模式启动 Shizuku。

使用本机模式前，请先在目标 Android 手机上安装并启动 Shizuku，确保状态显示为正在运行，然后再选「进入本地 Agent」。

Shizuku 的启动方式取决于手机系统版本和设备条件，通常可以选择：

- 无 Root：通过 Android 开发者选项中的无线调试启动；
- 有 Root：通过 Root 权限启动；
- 部分设备：通过 USB 调试和电脑上的 ADB 启动。

每次手机重启后，Shizuku 可能需要重新启动。请在 Shizuku App 中确认服务状态，不要只确认 Shizuku App 已安装。

> 注意：当前仓库的 Shizuku 服务是运行前置条件，App 不负责自动启动 Shizuku。若 Shizuku 未运行，依赖该服务的本地能力或实验功能可能无法正常工作。

### 2. DeepSeek API Key

**手机本地**模式：首次进入 App 后在界面中配置 DeepSeek API Key。Key 写入 App 本地存储，再交给本机 Host。

**扫码连接 / SSH**：Key 配在电脑端 DSH Host，不要指望手机本地那一套凭据生效。

请注意：

- 不要把真实 API Key 写进 Git、截图或公开 Issue；
- API Key 仍然用于访问 DeepSeek 在线模型，会产生网络流量和模型调用费用；
- 卸载 App 或清除 App 数据可能会删除本地保存的 Key 和会话缓存。

### 3. Android 开发环境

建议使用：

- Android Studio；
- Android SDK、Platform 34 和对应的 Build Tools；
- JDK 17 或 Android Studio 自带的 JBR；
- 一台 Android 7.0（API 24）或更高版本的设备；
- 已开启开发者选项和 USB 调试，或已配置无线调试。

### 4. Git LFS

`androidApp/src/main/assets/payload.zip` 约 115 MB，使用 Git LFS 存储。获取源码前请先安装并初始化 Git LFS：

```bash
brew install git-lfs        # macOS
git lfs install
```

Linux 和 Windows 用户请参考 [Git LFS 安装说明](https://git-lfs.com/)。克隆完成后可以检查运行时是否已下载：

```bash
git lfs ls-files
ls -lh androidApp/src/main/assets/payload.zip
```

如果 `payload.zip` 只有一百多字节，说明当前文件仍是 LFS 指针，可以执行：

```bash
git lfs pull
```

项目当前 Android 配置为：

```text
compileSdk = 34
minSdk     = 24
targetSdk  = 28
```

## 获取源码

建议先初始化 Git LFS，再克隆仓库：

```bash
git lfs install
```

```bash
git clone git@github.com:yukiykchen/deepseek-harness-mobile.git
cd deepseek-harness-mobile
```

如果使用 HTTPS：

```bash
git clone https://github.com/yukiykchen/deepseek-harness-mobile.git
cd deepseek-harness-mobile
```

## 使用 Android Studio 启动

1. 用 Android Studio 打开仓库根目录。
2. 等待 Gradle Sync 完成，并确认 Android SDK、JDK 和依赖下载没有错误。
3. 连接已经启动 Shizuku 的 Android 手机，确认 `adb devices` 能看到设备。
4. 在 Android Studio 的运行配置中选择 `androidApp` 模块和目标手机。
5. 点击 Run，等待 APK 安装并启动。
6. 启动后先出现「连接 DSH」。若选手机本地，等待内嵌 Harness 解压和启动；`payload.zip` 约 115 MB，首次可能较慢。
7. 本地模式在 App 中填写 DeepSeek API Key；扫码 / SSH 则使用电脑上已配置的 Key。
8. 创建会话并发送第一条消息。

## 通过扫码连接电脑上的 DSH Host

扫码模式走 [dsh-scan-remote](https://github.com/yukiykchen/dsh-scan-remote) 插件和本机 / 局域网 Relay，不把电脑的 `3080` 端口暴露到公网。完整电脑侧步骤见[插件中文说明](https://github.com/yukiykchen/dsh-scan-remote/blob/master/README.zh-CN.md)。

电脑侧最少要做：

1. 启动 Relay（默认 `127.0.0.1:8787`）。
2. 给 DSH web profile 安装 `dsh-scan-remote`。
3. 把 `publicRelayUrl` 设成**手机能访问的电脑 IP**，例如 `http://192.168.1.10:8787`。
4. 启动 `npx @deepseek-ai/dsh web`（或仓库里的 `pnpm dsh web`）。
5. 打开 **Settings > Remote Access**，确认二维码是当前网段的地址。

手机侧：

1. 打开 App，选 **扫码连接**。
2. 点 **扫描电脑二维码**。
3. 配对成功后点 **连接已配对电脑**。

二维码里的 origin 在生成时写死。电脑换 Wi-Fi、改连热点、或从公司网切到另一网段后，必须改 `~/.dsh-scan-remote/config.json` 的 `publicRelayUrl`、重启 DSH、再扫**新码**。只把手机和电脑连到同一个 Wi-Fi 还不够，如果码里仍是旧 IP，手机照样超时。

首版只保存一台电脑。换电脑先点「移除这台电脑」。

## 通过 SSH 连接电脑上的 DSH Host

App 支持手机本地 Agent，以及通过 SSH 连接电脑上的 DSH Host。SSH 模式只建立本地端口转发，不通过 SSH 执行远程命令，也不需要把 DSH 端口暴露到公网。

电脑端先启动 DSH，并保持回环监听：

```bash
dsh web --port 3080
```

推荐为手机使用的 SSH 用户配置 Ed25519 公钥：

```bash
ssh-keygen -t ed25519
ssh-copy-id your-user@your-computer
```

Windows OpenSSH 用户可以把 `.pub` 文件内容追加到 `%USERPROFILE%\\.ssh\\authorized_keys`。手机和电脑不在同一局域网时，可以让两台设备加入同一个 Tailscale 或 ZeroTier 网络，然后在 App 中填写虚拟地址，例如 `100.86.12.34`。

在 App 左侧菜单打开“设置”，选择“SSH 连接电脑”，填写 SSH 主机、SSH 端口、用户名和远程 DSH 端口，然后导入私钥。首次连接会展示 SSH 主机指纹；确认后会用于后续校验，指纹变化时需要重新确认。

SSH 模式下 API Key 应配置在电脑端 DSH Host 中。手机本地模式和 SSH 模式的会话缓存彼此隔离，远程 Host 是远程会话的最终数据来源。

App 支持用户主动开启 Android 前台服务来保持后台 SSH 隧道，但 Android 可能限制长时间后台服务。网络切换或系统回收后，App 会尝试重连，并通过会话历史恢复已被远程 Host 接受的任务。

## 使用 Gradle 命令行构建和安装

在仓库根目录执行：

```bash
./gradlew :androidApp:assembleDebug
```

生成的 APK 位于：

```text
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

连接手机后可以直接安装：

```bash
./gradlew :androidApp:installDebug
```

或者使用 ADB：

```bash
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

安装前建议确认 Shizuku 已经在手机上运行：

```bash
adb devices
```

然后在手机上打开 Shizuku App 检查状态，再打开 DeepSeek Harness Mobile。

## 首次启动流程

App 先打开「连接 DSH」，不会立刻启动内核。

**手机本地**进入首页后大致是：

1. 恢复该 mode 的会话列表和消息缓存；
2. 检查 App 私有目录中是否已有对应版本的 Harness Runtime；
3. 若没有，则从 `payload.zip` 解压 Node.js、Harness 和依赖；
4. 启动 Node.js 子进程，并运行 Harness `web` profile；
5. 等待 `127.0.0.1:3080` 健康检查成功；
6. 通过本地 API 查询会话、凭据和模型；
7. 发送消息时通过 RPC 提交 prompt，通过 SSE 接收流式响应。

如果不是首次启动，且运行时版本没有变化，App 会复用已解压的文件。运行时版本由 `androidApp/src/main/assets/dshroot_revision.txt` 和内部 revision 标记控制。

**扫码连接**进入首页后：恢复 Relay 配对、经 Relay 建隧道、在本机 loopback 上连远程 `events.mux`（WebSocket），不再解压或启动内嵌 Node。

**SSH** 进入首页后：建立本地端口转发，再按远程 Host 协议拉会话。

## 当前支持的能力

当前首页主要提供一个移动端 Harness Chat 界面，包括：

- 创建和切换本地会话；
- 恢复会话历史；
- DeepSeek API Key 配置；
- 查询可用模型并切换模型；
- Markdown 消息渲染；
- 流式回答；
- 取消当前请求；
- 展示工具调用事件；
- 本地 SQLite 会话和消息缓存；
- 本地 Harness 状态展示、启动失败和重启状态。

Harness 本身还具备工具、插件、文件系统、任务和 Agent loop 等扩展能力，但是否能在手机端使用，取决于当前随 `payload.zip` 打包的 profile、Android 权限和对应的宿主桥接实现。

## 目录结构

```text
androidApp/
  src/main/assets/payload.zip       # Node.js + DeepSeek Harness 运行时
  src/main/assets/dshroot_revision.txt
  src/main/java/.../engine/         # Android 侧运行时启动和 watchdog
  src/main/java/.../module/         # Android/Kuikly 原生模块

shared/
  src/commonMain/.../dsh/            # 跨平台 UI、协议、模型和本地存储接口
  src/androidMain/.../dsh/           # Android SQLite 存储实现
  src/iosMain/.../dsh/               # iOS 存储实现
  src/ohosMain/.../dsh/              # OpenHarmony 存储实现

iosApp/                              # iOS 宿主工程
ohosApp/                             # OpenHarmony 宿主工程
```

比较重要的文件：

- [`DshConnectionSetupPage.kt`](shared/src/commonMain/kotlin/com/example/dsh/dsh/DshConnectionSetupPage.kt)：启动时选择本地 / 扫码 / SSH；
- [`DshEngineManager.kt`](androidApp/src/main/java/com/example/dsh/engine/DshEngineManager.kt)：解压、启动、监控和停止 Node.js/Harness 进程；
- [`DshRelayManager.kt`](androidApp/src/main/java/com/example/dsh/relay/DshRelayManager.kt)：扫码配对、sealed tunnel 和本机 loopback 网关；
- [`DshHostProtocol.kt`](shared/src/commonMain/kotlin/com/example/dsh/dsh/DshHostProtocol.kt)：App 与 Host 的 RPC 和事件协议；
- [`DshHomePage.kt`](shared/src/commonMain/kotlin/com/example/dsh/dsh/DshHomePage.kt)：聊天、会话、输入框和模型配置；
- [`DshLocalStore.android.kt`](shared/src/androidMain/kotlin/com/example/dsh/dsh/DshLocalStore.android.kt)：按连接 scope 隔离的本地数据库。

## 网络和通信说明

三种模式最终都把 Host 看成「本机可访问的 DSH HTTP API」，但事件通道不同。

**手机本地**仍走本机回环：

```text
http://127.0.0.1:3080
HTTP RPC + SSE /api/events.mux
```

DeepSeek API 由手机上的 Harness 发起，所以本机模式也需要网络。`127.0.0.1` 只表示 App 与本机 Host 的通信，不代表模型离线。

**扫码连接**：手机经 Relay 与电脑插件建 sealed tunnel，再在手机 `127.0.0.1:<loopback>` 上露出 Host。事件流是 WebSocket，不是 SSE。二维码 origin 必须是手机能打开的电脑地址；插件连 Relay 仍用电脑本机 `127.0.0.1:8787`。

**SSH**：`127.0.0.1` 是手机上的 SSH 本地转发端点，实际流量到电脑 `127.0.0.1:3080`。远程模式使用 HTTP RPC + WebSocket，DSH API 路径不变。

如果事件流断开，客户端仍会尝试用会话历史恢复结果。

## 故障排查

### 扫码后连不上电脑

1. 电脑和手机是否在同一可互通网段，而不是只「看起来连了 Wi-Fi」；
2. 二维码 / `publicRelayUrl` 是否仍是上一张网的 IP；
3. 改完配置后是否重启了 `dsh web`，以及是否重新扫了新码；
4. 电脑上 Relay 是否还在 `8787` 监听；
5. 电脑防火墙是否拦截了来自手机的 `8787`。

### 页面一直显示“本地内核启动中”

这只出现在**手机本地**模式。可以依次检查：

1. 是否已经启动 Shizuku 服务；
2. 是否是支持的 Android ABI 和 Android 版本；
3. APK 是否完整安装，是否有资源解压失败；
4. App 私有目录中的 `dsh-engine.log`；
5. 是否有其他进程占用 `127.0.0.1:3080`；
6. 是否有足够的存储空间和可用内存。

清除 App 数据后重新启动，可以强制重新解压运行时：

```bash
adb shell pm clear com.example.dsh
```

这会删除本地 API Key、会话和消息缓存，请确认后再执行。

### API Key 配置成功但无法回答

请检查：

- API Key 是否有效；
- 手机是否可以访问 DeepSeek API；
- 当前选择的模型是否可用；
- App 日志和 `dsh-engine.log` 中是否有模型请求错误；
- 手机时间是否正确，避免 TLS 或鉴权异常。

### SSE 中断或回答停住

当前客户端会在事件流失败后尝试读取会话历史。如果长时间没有结果，可以停止并重新发送请求，或重启 App。开发调试时建议保留 Harness 日志，以便定位是 Node.js 进程、Host、网络请求还是模型服务的问题。

### Node.js 进程被系统杀掉

Android 对后台进程有严格限制。当前实现由 App 管理 Node.js 子进程，并提供健康检查和重启逻辑，但它不是一个完全独立的系统级常驻服务。省电策略、厂商后台限制、内存压力和 App 进程被回收，都可能导致 Harness 停止。

## 开发说明

修改 Kotlin 或 Kuikly 代码后，重新执行：

```bash
./gradlew :androidApp:assembleDebug
```

如果修改了 DeepSeek Harness 本身，需要重新构建对应的 dsh 产物、更新 `payload.zip`，并同步 `dshroot_revision.txt`。仅修改 Android UI 不会自动更新 `payload.zip` 里的 Harness 代码。

请注意，`payload.zip` 是约 115 MB 的二进制资源，更新它会显著增加 APK 体积和 Git 变更量。提交运行时更新时，应同时记录：

- Harness 来源版本或 commit；
- Node.js Runtime 版本和目标 ABI；
- 资源 revision；
- 是否修改了 profile 或权限策略；
- 在真实手机上的启动和对话验证结果。

## 当前限制

- 当前主要验证 Android 移动端宿主流程，其他平台目录不代表功能已经完全对齐；
- 扫码连接和 SSH 目前仅支持 Android；
- 模型推理仍然依赖 DeepSeek 在线 API，不是完全离线模型；
- 扫码二维码绑定电脑当前局域网 IP，换网络后必须改 `publicRelayUrl`、重启 DSH 并重新扫码；
- Shizuku 只约束手机本地模式，需要用户手动启动和维持运行；
- App 目前不负责自动启动 Shizuku；
- Node.js Runtime 和 Harness 资源会增大 APK 体积；
- 首次启动需要解压运行时，可能比较慢；
- Android 后台进程可能被系统或厂商策略回收；
- 工具是否可用取决于 Harness profile 和 Android 宿主桥接，不应默认所有桌面端工具都能直接在手机上运行。

## 许可证和上游项目

本项目是移动端集成和实验性宿主代码。DeepSeek Harness 的许可证、第三方依赖和使用限制请以其上游仓库为准：

- [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness)
- [DeepSeek Harness 中文说明](https://github.com/deepseek-ai/deepseek-harness/blob/main/README.zh.md)
- [Shizuku](https://github.com/RikkaApps/Shizuku)

在发布 APK 或重新分发 `payload.zip` 前，请确认上游项目、Node.js Runtime 及其第三方依赖的许可证要求。
