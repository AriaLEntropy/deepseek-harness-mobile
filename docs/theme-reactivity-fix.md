# 主题响应式修复计划

> 追踪文档：深色模式下仅设置页（sheet）变深、主界面不变的问题。
> 状态：✅ 三阶段全部完成，`:shared:compileDebugKotlinAndroid` BUILD SUCCESSFUL（2026-09-07）。
> 验证：✅ 真机全流程验收通过（2026-09-07，用户侧完成，三模式切换/连接跳变/重启恢复/系统跟随/浅色观感均确认）。

## 1. 现象

设置 → 外观 → 选择"深色"后，只有设置页（Modal）在重建时变为深色，会话区、顶栏、输入区等主界面保持浅色。

## 2. 根因（已定位，证据见下）

1. **读端未注册响应式依赖（主因）**：组件以值传递 `colors = this@DshHomePage.themeColors` 接收颜色。该读取发生在 vif creator / builder 执行期，位于 Kuikly 依赖收集窗口（`attr{}` 内）之外，因此视图从未注册对 `themeColors` 的依赖，主题切换后不重绘。
2. **写端通知不可靠（次因）**：项目使用废弃的顶层 `observable()`，其 get/set 依赖 `BridgeManager.currentPageId` 全局状态定位 observer（框架注释明确警告"错误使用可能导致 observable 不更新"）；且 `syncThemeColors()` 在 RPC 回调线程执行，未切主线程。
3. **硬编码色值（叠加因素）**：会话根容器、消息视口、输入区、命令面板等使用 `Color.WHITE` 等硬编码颜色，即使响应式链路修通也不会变化。

**"sheet 变深色"是重建取值的假象**：设置页关闭再打开时被 vif 销毁重建，重建时读到已变深的 `themeColors`，才显示深色。

### 证据索引

| 证据 | 位置 |
|---|---|
| 值传递调用点（顶栏/抽屉/选择器/设置页等） | `DshHomePage.kt` L393/757/782/834/858/878/899/929/1068/1097/1108/1117/1126 |
| 正确的 lambda 模式（attr 内 `this.colors = colors()`） | `DshHomeConversation.kt` L429/441/456/467/493/973/1050/1077/1131 |
| vif creator 不收集依赖 | Kuikly `ConditionView.kt` L108 `createSubViewIfNeed { creator() }` |
| attr 内 `bindValueChange` 收集依赖 | Kuikly `DeclarativeBaseView.kt` L64-80 |
| 废弃 observable 定位机制 | `ReactivePropertyHandler.kt` L42；`BridgeManager.kt` L50 注释 |
| 正确替代：PagerScope.observable 显式绑定 pagerId | `ReactivePropertyHandler.kt` L100；`UnsafePropertyAccessHandlerImpl` `ReactiveObserver.kt` L417-454 |
| Page 类本身是 PagerScope | `AbstractBaseView.kt` L87 `: IPagerId : PagerScope` |
| 硬编码白底 | `DshHomeConversation.kt` L250/276/796；`DshCommandSheet.kt` L56 |

## 3. 修复方案（三阶段）

### 阶段 1：写端（通知可靠化）

- `DshHomePage.kt`：删除废弃 `import com.tencent.kuikly.core.reactive.handler.observable` / `observableList`，`by observable(...)` 自动解析到 `PagerScope.observable`（页面类自身是 PagerScope），setValue 显式绑定 pagerId。
- `DshThemeController.kt`：`mode` / `systemDark` 改为普通 `var`（其值不参与 UI 依赖，仅被 `currentColors` getter 与 `syncThemeColors()` 代码路径读取，无需 observable）；删除废弃 import。
- `syncThemeColors()` 内部包 `setTimeout(pagerId, 0)` 切主线程，保证 setValue 在主线程触发通知。

### 阶段 2：读端（响应式连接，核心改造）✅

- 将值类型签名 `colors: DshColorTokens` 改为 `colors: () -> DshColorTokens`，attr 内 `colors.xxx` 改 `colors().xxx`，确保每次 attr 重放都读取最新主题色并注册依赖。
- 调用点同步改为传 lambda：`colors = { this@DshHomePage.themeColors }`；组件内部向子组件传递时直接传 lambda（`colors = colors`），不在 builder 中求值。
- 实际完成的 lambda 化签名（33 处）：
  - `DshHomeChrome.kt`：13 处原签名（DshConnectionSettingsModal、DshConnectionInput、DshCredentialSetupModal、DshSessionDrawer、DshSessionDrawerRow、DshModelPicker、DshPermissionPicker、DshAgentModePicker、DshTopBar、DshSettingsPage、DshSettingsGroupTitle、DshSettingsRow、DshSettingsChoicePicker）+ 本轮补 5 处（DshSessionRail、DshSessionButton、DshSessionDetailsPanel、DshDetailRow、DshWorkspaceBrowserModal）
  - `DshOverflowMenu.kt`：5 处（DshSessionLogModal 等）
  - `DshHomeConversation.kt`：6 处（DshTurnStatus、DshNewSessionHome、DshMessageFooter、DshFooterActionIcon + 已有 lambda 的 DshConversation/DshMessageRow 不动）
  - `DshCommandSheet.kt`：2 处（DshCommandSheet、DshCommandSheetTileRow）
  - `DshSelectTextModal.kt`、`DshMessageActionsMenu.kt`、`DshConnectionStatusCapsule.kt`：各 1 处
- `DshHomePage.kt`：15 处调用点 `colors = { this@DshHomePage.themeColors }`。
- ⚠️ trailing lambda 陷阱：组件签名若 `colors` 是最后一个参数，调用点不能用 trailing lambda 传其他函数参数（会绑定到 colors），需显式命名（如 `DshCommandSheetTileRow(tile, onClick = { ... }, colors = colors)`）。

### 阶段 3：硬编码替换 ✅

- `DshHomeConversation.kt`：会话根容器/消息视口白底 → `colors().bgBase`；命令补全面板 → `specificMenu`；角色标签/命令名/时间戳/附件卡片等 18 处 → labelPrimary/labelTertiary/labelSecondary/specificBubble/bgModulePlatform/borderL1/stateErrorPrimary/labelPrimaryBluish。
- `DshCommandSheet.kt`：sheet 白底 → `bgLayer1`，标题/命令名/描述/分隔线/附件方块 → 语义 token。
- `DshHomeChrome.kt`：会话侧边栏 `bgLayer2`、详情面板 `bgModulePlatform`、设置页 Scroller 背景/选择器/chevron、工作区浏览器弹窗、模型选择器、effort 列表等 30 处。
- `DshHomePage.kt`：重命名/删除工作区内联弹窗 9 处 → `this@DshHomePage.themeColors.xxx`（attr 内直读 observable 可注册依赖）。
- `DshSelectTextModal.kt` / `DshMessageActionsMenu.kt` / `DshConnectionStatusCapsule.kt` / `DshOverflowMenu.kt`：弹窗底色、菜单底色、连接胶囊、日志原文提示等。
- 有意保留的硬编码：遮罩（`0x66000000`/`0x55000000`）、透明（`0x00FFFFFF`）、彩色底上白字/白图标（按钮、日志级别色块）、阴影、`DshTheme.kt` 色板定义本身、RouterPage 品牌渐变页、DshConnectionSetupPage 连接页（遗留，见下）。

## 4. 验证方式

1. 每阶段后编译：`JAVA_HOME=C:\Users\26012\.jdks\jdk-17.0.20.1+1 .\gradlew.bat :shared:compileDebugKotlinAndroid`（默认 JDK 25 与项目不兼容）。✅ 阶段 1/2/3 均 BUILD SUCCESSFUL。
2. 最终验证：安装到设备，设置 → 外观 → 深色，确认主界面（顶栏/会话区/输入区/命令面板）实时变深、浅色可回切；系统暗色跟随模式下切换系统外观即时生效。✅ 真机已通过（2026-09-07）。
3. 静态复查（已完成）：值类型 `colors: DshColorTokens =` 签名残留 0；非 lambda `colors = themeColors` 调用残留 0；`this.colors = colors()`（attr 内）9 处保留为正确模式。

## 5. 遗留项（未在本轮处理）

- `DshWebViewPage.kt`（web 时间线全屏页）：独立 Page，无主题状态接入，需要仿照 DshHomePage 的 themeController/themeColors 机制接入后才能响应深色。
- `DshConnectionSetupPage.kt`（连接/扫码页）：独立路由页，硬编码较多（8 处），需要时再主题化。
- `RouterPage.kt`：品牌渐变启动页，浅色主题保持。
- 警示条（`0xFFFFF7E6`/`0xFF7A5B16`）与日志级别色块：语义色，两种主题下通用，保留。

## 6. 改动文件

`DshHomeChrome.kt`（+317）、`DshOverflowMenu.kt`、`DshHomeConversation.kt`、`DshHomePage.kt`、`DshCommandSheet.kt`、`DshSelectTextModal.kt`、`DshMessageActionsMenu.kt`、`DshConnectionStatusCapsule.kt`、`DshThemeController.kt`。

## 7. 侧边栏不变深（后续修复，2026-09-07）

**现象**：home page 侧边栏（会话抽屉）未应用深色模式。

**根因**：组件签名已 lambda 化且内部 token 化，但 3 处调用点未传 `colors`，落入默认浅色 `{ DshDefaultTheme.light }`：
- `DshSessionDrawer`（会话抽屉本体）@ `DshHomePage.kt` L713
- `DshSessionDrawerRow`（工作区文件夹分组下的会话行）@ `DshHomeChrome.kt` L461（L391 会话列表分支已传）
- `DshOverflowMenu`（抽屉内复用的溢出菜单）@ `DshHomeChrome.kt` L526

**修复**：补 `colors = { this@DshHomePage.themeColors }` / `colors = colors` 三处；`DshAgentModePicker` 经核实已传（脚本 40 行窗口误报）。暗色 token 值已核对无误（bgBase=nb950、bgLayer2=nb850、labelPrimary=nb50、specificSidebarNavItemActive=nb750）。

**验证**：`:shared:compileDebugKotlinAndroid` BUILD SUCCESSFUL；全量调用点复查（80 行窗口）9 处侧边栏相关调用全部 `colors=True`。真机已通过：抽屉/远程会话栏深色下变深、选中行高亮正确（2026-09-07）。

## 8. 主题偏好生命周期（后续修复，2026-09-07）

**现象**：进入设置页面后色彩模式才应用；App 启动/重启后持久化的深色偏好不生效（mode 停留在默认 SYSTEM，只有系统深色时才深）。

**根因**：持久化偏好（host 的 `ui-theme.preference`）只在 `openSettingsPage() → reloadSettings() → describeSettings` RPC 回调中恢复 `themeController.mode`。启动与连接建立路径（`created()` 只设置 `systemDark` + sync，不恢复 mode）从不读取持久化偏好。

**修复**（`DshHomePage.kt`）：
- 抽出 `applyThemeValue(themeValue)`：统一 mode 映射 + `syncThemeColors()`，`reloadSettings()` 与 `applySettingsChoice("theme")` 复用。
- 新增 `restoreThemePreference()`：连接就绪后 `describeSettings → applyThemeValue`，失败静默（保持 SYSTEM 兜底）。
- 调用时机：`loadRepository` 的 `loadSessions` 成功回调（初始连接、重连、保存 API Key 后都会走到），保证**启动即恢复**持久化偏好，不再依赖打开设置页。
- `syncThemeColors()` 加变化 guard（`themeColors !== next` 才 setValue），避免打开设置页时值未变也触发全量重绘。
- 系统暗色实时跟随保持不变（`themeDidChanged` → `systemDark` + sync）。

**验证**：BUILD SUCCESSFUL（18s）。真机已通过：冷启动后（持久化为深色、系统为浅色）主界面直接是深色，无需进设置页；重启后偏好保持（2026-09-07）。

## 9. 主题提升至 App 级（最终方案，2026-09-07）

**用户方向调整**：主题初始化提升生命周期至 App 级；连接页面同样应用主题；首次打开 App 默认跟随系统；设置页移除主题切换 UI；主题变量保持响应式。

**新架构**（替代 §8 方案）：
- **`DshThemeManager`（theme 包，全局 object，App 级）**：`theme` / `mode`（默认 `SYSTEM`，首次打开跟随系统）/ `systemDark` / `currentColors` getter / `isDark`；`addListener` / `removeListener` / `notifyChanged` 广播。删除 `DshThemeController.kt`（被全局单例替代）。
- **`BasePager` 统一接线**（所有页面继承，含 DshConnectionSetupPage / DshWebViewPage / RouterPage）：
  - `protected themeColors` observable（PagerScope.observable，页面级镜像）
  - `created()`：注入 `systemDark = isNightMode()` → 注册 listener → 初始 sync
  - `themeDidChanged()`：更新 `nightModel` → 更新 `systemDark` → `notifyChanged()`（全局广播，所有已注册页面重绘）
  - `pageWillDestroy()`：注销 listener
  - `syncThemeColors()`：`setTimeout(0)` + 变化 guard，把 `currentColors` 写入页面 observable
- **`DshHomePage` 清理**：删除 `themeController` / 页面级 `themeColors` / `syncThemeColors` / `applyThemeValue` / `restoreThemePreference`；40+ 处 `this@DshHomePage.themeColors` 引用自动解析为继承成员，不改调用点。
- **设置页外观切换（用户最终要求：正常显示并可调整）**：保留 `DshSettingsPage.onPickTheme` 参数、"外观"行（icon-followsystem16.svg + 模式文字）、`openSettingsChoice("theme")`（跟随系统/浅色/深色）、`applySettingsChoice("theme")`（`updateSetting("ui-theme", preference)` 持久化 + `DshThemeManager.applyPreference(choice.value)` 全局广播即时生效 + `reloadSettings()`）、选择器 selectedValue theme 分支。`DshThemeManager.applyPreference`：light→LIGHT / dark→DARK / 其他→SYSTEM，随后 `notifyChanged()` 全 App 响应式变色。
- **外观行文字响应式（后续修复，2026-09-07）**：原 `dshSettingsThemeLabel(snapshot())` 读 host 快照 `themeValue`，与本地内置 `DshThemeManager.mode` 脱节且不随广播刷新。改为 `dshThemeModeLabel()` 直接读取 `DshThemeManager.mode`（LIGHT→浅色 / DARK→深色 / SYSTEM→跟随系统）；`DshThemeManager` 新增 `preferenceValue`（light/dark/system）供选择器高亮使用；`BasePager` 新增 `themeMode` observable 镜像，`syncThemeColors()` 同步同步 `themeColors` 与 `themeMode`——即使颜色未变（如系统深色下 DARK→SYSTEM），mode 变化也触发 attr 重放刷新文字。
- **主题偏好本地化（后续修复，2026-09-07）**：用户报告"setup 页浅色，连接后变深色"。根因：连接就绪后 `restoreThemePreference()` 读取 **host 的 ui-theme preference**（此前设置过的 dark）覆盖移动端。修复：移动端主题偏好独立持久化于本地 SharedPreferences（`DshThemeManager.PREF_KEY_THEME_MODE` = "theme_mode"）；`BasePager.created()` 创建页面时恢复本地偏好；删除连接后恢复 host 偏好的逻辑。设置页切换：写本地 + `applyPreference` 即时生效（host 值不再反向影响移动端）。行为：setup 页浅色 → 连接后保持浅色；用户设置页切深色 → 重启后恢复深色。
- **图标/品牌资源深色适配（后续修复，2026-09-07）**：①空白会话首页 DeepSeek 鲸鱼 logo（fish.svg，fill `#000`）加 `tintColor(if (isDark) Color.WHITE else null)`，深色变白、浅色保持原样；②顶栏 menu.svg（stroke `#20252A`）/ more.svg（fill `#0F1115`）加 `tintColor(colors().labelPrimary)`；③`DshWordmark`（wordmark.svg，fill `#111111`，setup 页顶栏 + 会话抽屉顶部）新增 `colors` 参数 + `tintColor(if (isDark) Color.WHITE else null)`，两个调用点（DshConnectionSetupPage L125、DshSessionDrawer L350）传入主题色。
- **默认模式改为浅色 + 切换本地优先（后续修复，2026-09-07）**：用户报告设置页外观行仍显示"跟随系统"。修复：①`DshThemeManager.mode` 默认由 SYSTEM 改为 **LIGHT（浅色）**（`BasePager.themeMode` 同步），外观行默认显示"浅色"，与界面实际显示一致；跟随系统保留为可选模式（显式选择时显示"跟随系统"）。②主题切换改为**本地优先生效**：先写本地 + `applyPreference`（立即全界面变色、文字更新），再顺带同步 host（成功 `reloadSettings()`、失败仅 toast，不影响移动端）——此前生效逻辑挂在 host `updateSetting` 成功回调内，host RPC 失败会导致"点了没反应"。
- **`DshConnectionSetupPage` 主题化**：body 16 处硬编码色 → `ctx.themeColors.*`（显式 receiver，body lambda 内隐式 receiver 是 ViewContainer）；`DshSetupModeButton` / `DshSetupInput` 增加 `colors` lambda 参数并 token 化内部（labelSecondary / bgLayer1 / borderL1 / labelPrimary / labelTertiary / stateBusinessPrimary / stateErrorPrimary）。映射：背景→bgBase、顶栏→bgLayer1、分隔→borderL1、标题→labelPrimary、副文本→labelSecondary、模式容器→specificSelector、按钮→stateBusinessPrimary（busy→stateBusinessTertiary）、移除/错误→stateErrorPrimary、输入框→bgLayer1/borderL1、placeholder→labelTertiary。
- **`DshWebViewPage` 主题化**：页面背景→`ctx.themeColors.bgBase`、空态文字→labelSecondary；`DshLinkHeader`（ComposeView 类）经 `DshLinkHeaderAttr.colors`（observable）传递主题色：顶栏→bgLayer1、分隔→borderL1、返回/刷新/进度条→stateBusinessPrimary、标题→labelPrimary、状态→labelSecondary；扩展函数加 `colors` lambda 参数。

**浅色不变约束（用户要求）**：token 化不改浅色视觉。页面/弹窗大面积背景统一使用浅色值为纯白（nb00）的 token（bgBase / bgLayer1 / bgLayer2）；内容卡片保持原色映射（附件卡片 0xFFF6F8FA→bgModulePlatform、effort 行 0xFFF8F8F9→bgModulePlatform 等）。

**响应式链路**：系统外观变化 → 宿主 `themeDidChanged` → BasePager 更新 `DshThemeManager.systemDark` + `notifyChanged()` → 每页 `syncThemeColors()` 写页面 observable → attr 内 `colors()`/`ctx.themeColors` 读取触发依赖重放 → 全界面（home / 连接页 / 设置页 / 抽屉）同步变色。

**验证**：BUILD SUCCESSFUL（17s，修复 receiver 问题后）。真机已通过：①首次打开（系统浅色）浅色、系统切深后所有页面即时变深；②连接页深色下背景/输入框/按钮正确；③设置页"应用"分组显示"外观"行，可切换跟随系统/浅色/深色且全界面即时生效；④选择本地持久化，重启后恢复所选模式（2026-09-07）。

## 10. WebView 页 token 化 + 浅色白底约束（2026-09-07）

- `DshWebViewPage` 遗留项已完成 token 化（见 §9），`DshLinkHeader` 经 attr.colors 传递主题色，全部 4 个 Page 均已接入 App 级主题。
- 用户约束：**不要修改原来的颜色，浅色主题时背景保持白色**。因此页面/弹窗大面积背景（原 0xFFF7F9FA 类）统一改用浅色值为纯白（nb00=0xFFFFFFFF）的 token（连接页背景、WebView 页背景、工作区弹窗背景 → bgBase）；内容卡片（原 0xFFF6F8FA / 0xFFF8F8F9）保留 bgModulePlatform 映射，浅色观感与原设计一致。
- 验证：BUILD SUCCESSFUL（26s）。
