---
type: issue-brief
status: draft
version: "0.2"
last_reviewed: "2026-09-12"
cynefin_domain: complicated
confidence: medium
behavior_mode: review-and-alignment
source_decision_brief: null
decision_required: false
---

# 日志生命周期审查

> 后续更新（2026-09-12）：项目所有者要求删除自行添加的全部测试。本文涉及的单测和探针结果保留为历史证据，相关测试源码和当前运行入口已移除；最新编译入口见 [tasks.md](tasks.md)。

> 记录日期：2026-09-12。审查基点：`deepseek-harness-mobile` 的 `40dc3ca` 加当时工作区修改，包括统一日志页、筛选和采集调整。
> 状态：**Phase 1—4 后经独立复核发现遗漏，本轮已补修复**。最新实现和验证以 [tasks.md 的最新记录](tasks.md#2026-09-12独立复核后的修复与补齐最新) 为准。三端设备验收未全部完成。
> 没有独立决策简报；元数据中的问题域、置信度和工作方式为文档整理时的轻量推断。代码事实、条件性风险和待验证范围在正文中分别说明。

## 0. 独立复核与后续修复（2026-09-12）

此前 191 项测试通过，但新增 5 项验收探针全部失败：最大序号读取失败后覆盖旧记录、16 轮后容量仍超限、查询成功误清维护失败、已取消工作仍先刷盘、回合复制遗漏工具结果。主机 SQLite 复现 16 轮清理后仍有 52.17 MiB；这是修复前证据。

本轮已将对应日志探针转为 `DshLogReliabilityTest`，并补充 `DshLogLifecycleTest`、`DshLogConcurrencyTest`；复制/历史由 `DshHistoryAndReadableTest` 覆盖。核心变化：后台重试初始化、不使用空库序号兜底；真实主文件及辅助文件大小；保留高等级兜底轮次并重试未达标维护；按操作恢复降级；取消检查前移；崩溃导入标记和日志事务一致；iOS/OHOS 实际目录枚举。

注意：下文第 3—5 节描述最初审查状态及当时复现，第 6.3 节保留项目所有者决策。本轮 205 项共享测试通过、Android Debug 构建及 OHOS 共享代码编译通过，不能替代实际磁盘不足、真实崩溃和三端生命周期验收。

## 1. 问题与结论（初次审查记录）

用户要求检查日志生命周期是否合理，重点涉及初始化、采集、落库、查询、导出、清空、退出和重启后的恢复。

**结论：本地持久化、批量落库、有限容量及与 Host 历史隔离的方向合理，但当前生命周期还不完整。正常路径可用，不足以证明高频采集、存储故障、页面重建和退出时也可靠。**

受影响对象包括查看日志和提交问题反馈的 App 用户，以及维护日志采集、存储、跨平台桥接和页面交互的开发者。当前没有证据表明发生了生产事故；以下优先级按可复现的缺陷及潜在影响划分。

| 编号 | 建议优先级 | 发现 | 证据状态 | 修复状态 |
| --- | --- | --- | --- | --- |
| LOG-LC-01 | 高 | 全局写入器由主页创建和销毁，生命周期归属不合理 | 代码确认；双写入器同序号覆盖已在探针中复现，多主页实际触发路径待设备验收 | 已修复（Phase 2，待设备验收） |
| LOG-LC-02 | 高 | 刷盘任务未充分合并，失败后无独立重试，停止后仍接收日志 | 三项探针复现 | 已修复（Phase 2，自动重试/停止态已有回归） |
| LOG-LC-03 | 高 | 整次查询/导出持锁，取消不打断扫描，退出等待；日志异常可向业务传播 | 两项探针复现及生命周期调用点确认 | 已修复（Phase 3：短锁分批 + 协作取消 + 异常隔离/降级可见） |
| LOG-LC-04 | 高 | 容量统计和淘汰无法保证满足预算 | 使用仓库实际 SQL 在 SQLite 中复现 | 已修复（Phase 4：实际磁盘字节 + 分批 VACUUM 至达标；阈值待设备验证） |
| LOG-LC-05 | 中 | 崩溃去重只在单个主页内有效，清空后旧崩溃可能再次入库 | 代码确认；跨平台重启端到端验证待完成 | 已修复（Phase 4：清空含快照 + 持久化去重；原生端待验收） |
| LOG-LC-06 | 中 | 导出失败残留半成品，应用内部导出文件缺少回收策略 | 半成品探针复现；导出目录及清理调用点检查 | 已修复（Phase 1+4：临时发布 + 失败清理 + 仅保留最近一次导出） |

## 2. 需求依据、范围与期望结果

### 2.1 原始需求和 DSH 边界

依据：[原始任务集 Task 6](../../dsh-mobile-docs/project/原始需求/DSH%20App%20·%20AI对话体验增强任务集.md)，原文第 197—232 行。

- 日志中心记录 App 观察到的连接、RPC、mux/host 关键帧和会话错误。
- 支持本地日志查看、筛选、详情、复制、脱敏导出和清空。
- 内存或磁盘有上限；建议环形缓冲 500～2000 条或按会话滚动淘汰。
- 持续生成及打开日志页时，对话页不得明显卡顿。
- 清空只删本地日志，不影响会话消息和 Host 历史。
- 崩溃保留和问题反馈包属于加分项。

当前 `5000 条 / 5 MiB / 50 MiB` 及优先淘汰低级别日志，是移动端实现选择，**没有依据表明这些数值和规则来自 DSH 官方**。DSH 会话事件日志是消息历史的事实源；App 日志中心是可淘汰的脱敏诊断记录，二者的保留责任不同。参考官方 [Session 类型与事件事实源定义](https://github.com/deepseek-ai/deepseek-harness/blob/master/packages/core/session/src/types.ts)。该链接随上游分支变化，不作为本次移动端测试的版本锁定依据。

### 2.2 审查范围与验证边界

- 共享层：`DshStreamLog`、`DshLogWriteBehind`、`DshLogQuery`、`DshLogWork`、日志页及主页生命周期接线。
- 平台层：Android/iOS/OHOS 日志存储、导出和崩溃记录桥接的静态检查。
- 动态验证：Android JVM 单元测试环境中的临时探针，以及主机 SQLite 执行仓库实际 SQL。
- 本轮没有进行真实 Host 流式压力测试，也没有完成 iOS/OHOS 原生生命周期、崩溃捕获和磁盘不足的设备验收。

期望结果：日志服务覆盖连接前后的采集，单一写入器管理序号和队列；采集、查询与退出互不长时间阻塞；存储故障可恢复；容量、崩溃和导出文件均有明确且可验证的保留及清理规则。

## 3. 当前生命周期事实

```text
App 启动 → 连接设置 / 首次连接探测
                  │ 主页尚未创建时，writeBehind 为空
                  ▼
主页 created → 创建 dsh_logs.db → 创建写入器 → onStart → 设置全局 writeBehind
                  │
日志采集 → 脱敏 → 待写队列 → 延迟 / 达到批量阈值后调度 flush → SQLite
                  │                                         │
                  │                                   检查容量并淘汰
                  ▼
日志页查询 / 导出 → 先 flush → 持有 storeLock 遍历分页

手动清空 → 等待 storeLock → 清待写队列和日志表
主页隐藏 → 同步 flush
主页销毁 → 停止连接 → onStop / 同步 flush → 全局 writeBehind 置空 → 取消所属作用域
下次主页创建 → 重新打开日志库，并重新读取原生最近一次崩溃记录
```

| 环节 | 当前实现 |
| --- | --- |
| 初始化 | 只在主页 `created()` 中创建写入器；使用主页的 `localReadScope` |
| 采集 | 全局采集级别默认 Debug；`writeBehind` 为空时跳过持久化 |
| 待写队列 | 默认最多 5000 条、`5 × 1024 × 1024` 的估算大小；超限优先淘汰旧 Debug，再 Info，必要时其他级别 |
| 刷盘 | 默认延迟 500 ms；待写队列达到 32 条时请求立即刷盘 |
| 存储淘汰 | 初始化和每次 `flush` 时检查；估算量超过 50 MiB，调用目标为 25 MiB 的 `dropOldest()` |
| 查询 / 导出 | 每批最多读取 256 条，但整次遍历共用一把存储锁；导出追加文件也在遍历回调中执行 |
| 手动清空 | 清待写队列和 `dsh_log_events`；不按当前筛选局部删除，也不删除 Host 历史 |
| 退出 | `onStop()` 取消保存的一个刷盘 Job，再同步 `flush()`；没有禁止继续入队的停止状态 |
| 崩溃导入 | 原生保留最近一次记录；主页用内存中的 `lastReportedCrash` 防止本页重复导入 |

源代码入口：

- [DshHomePage.kt](../shared/src/commonMain/kotlin/com/example/dsh/home/DshHomePage.kt)：`created`、`pageDidDisappear`、`pageWillDestroy`、`reportCrashRecord`。
- [DshStreamLog.kt](../shared/src/commonMain/kotlin/com/example/dsh/infrastructure/DshStreamLog.kt)：`writeBehind`、`persist`。
- [DshLogWriteBehind.kt](../shared/src/commonMain/kotlin/com/example/dsh/infrastructure/DshLogWriteBehind.kt)：队列、刷盘、容量、遍历和清空。

## 4. 详细发现与修正建议

### LOG-LC-01：写入器的生命周期归属于主页

**已确认事实**

- [主页第 334—401 行](../shared/src/commonMain/kotlin/com/example/dsh/home/DshHomePage.kt#L334)创建、替换并清空全局写入器引用。
- [连接页 `probeRemote`](../shared/src/commonMain/kotlin/com/example/dsh/connection/DshConnectionSetupPage.kt#L416)可以在首次进入主页之前创建远程 Repository 并探测 Host；此时 [日志持久化入口](../shared/src/commonMain/kotlin/com/example/dsh/infrastructure/DshStreamLog.kt#L132)因写入器为空而返回。
- 每个写入器分别从当前数据库最大序号初始化；[插入 SQL](../shared/src/commonMain/kotlin/com/example/dsh/infrastructure/DshLogSql.kt#L22)使用 `INSERT OR REPLACE`。

**影响与条件性风险**

- 首次连接失败阶段的日志可能只有控制台输出，无法在日志中心回看。
- 在多个主页实例并存或重建的条件下，全局引用可能被覆盖，页面销毁也没有所有者身份校验。
- 探针让两个写入器在任一写入发生之前完成初始化，双方都分配 `seq=1`；第二次落库覆盖了第一条记录。该探针验证的是双写入器条件下的风险，并不表示每次正常切页都会发生覆盖。

**建议**：使用应用级唯一日志服务，在连接页之前初始化；页面只持有查询能力。写入器创建、替换、关闭及序号分配由唯一所有者管理。旧页面销毁不得停止新实例的日志服务。

### LOG-LC-02：刷盘调度、失败恢复及停止状态不完整

依据：[DshLogWriteBehind.kt 第 89—154、241—246 行](../shared/src/commonMain/kotlin/com/example/dsh/infrastructure/DshLogWriteBehind.kt#L89)。

1. **刷盘任务重复排队。** 达到 32 条后，每次入队都会再次 `launch`，没有复用已存在的立即刷盘任务。暂不执行后台任务时，连续 100 次入队产生 70 个 Job；队列容量上限不等于后台任务数有上限。
2. **失败后没有独立的重试调度。** `appendBatch` 失败后把批次放回队列；如果没有新日志、查询或主动 `flush`，即使存储恢复，也不会自行重试。既有测试通过手动调用 `flush()` 验证恢复，不能证明存在自动重试。
3. **停止后仍接收事件。** `onStop()` 没有改变接收状态。探针在停止并取消所属作用域后再次入队，日志仍留在内存，但已没有可运行的刷盘任务。

**建议**：单一后台写入任务合并唤醒信号；失败按有上限的退避规则重试；明确“运行 → 排空 → 停止”的状态转换和迟到事件处理。关闭顺序应包括停止生产者、排空或明确报告未保存数量、释放资源。

### LOG-LC-03：长时间持锁、取消不生效及异常隔离不足

依据：

- [整次遍历持有存储锁](../shared/src/commonMain/kotlin/com/example/dsh/infrastructure/DshLogWriteBehind.kt#L165)。
- [导出在遍历回调内追加文件](../shared/src/commonMain/kotlin/com/example/dsh/diagnostics/DshLogQuery.kt#L44)。
- [DshLogWork](../shared/src/commonMain/kotlin/com/example/dsh/diagnostics/DshLogWork.kt)执行同步 `work`，扫描循环没有协作式取消检查。
- [主页隐藏/销毁同步刷盘](../shared/src/commonMain/kotlin/com/example/dsh/home/DshHomePage.kt#L384)。

**复现结果**

- 在首批读取处暂停扫描，再取消工作作用域并调用 `onStop()`：退出操作等待存储锁；放行后，被取消的扫描仍读完全部 3 批。
- 注入 `maxSeq()` 失败，异常可从 `onStart()` 和 `DshStreamLog.log()` 向调用方抛出。当前只对部分建库、写入路径做了异常捕获。

**影响**：大日志查询或慢速文件导出可能拖住 Kuikly 页面生命周期处理；取消并不等于停止 IO；日志存储错误可能影响页面创建或业务调用。测试确认的是阻塞机制，未测定目标设备上真实 ANR 的发生阈值。

**建议**：为查询定义有界快照/游标语义，缩短锁持有时间，在批次边界检查取消；导出文件 IO 不长期占用写入锁；页面线程不等待完整扫描。日志入口和存储维护应隔离可恢复异常，降级状态通过非递归方式反馈。

### LOG-LC-04：存储容量不是可验证的硬上限

依据：[DshLogSql.kt](../shared/src/commonMain/kotlin/com/example/dsh/infrastructure/DshLogSql.kt#L27)、[Android `dropOldest`](../shared/src/androidMain/kotlin/com/example/dsh/infrastructure/DshLogStore.android.kt#L74)。[iOS](../shared/src/iosMain/kotlin/com/example/dsh/infrastructure/DshLogStore.ios.kt#L68)和 [OHOS](../shared/src/ohosMain/kotlin/com/example/dsh/infrastructure/DshLogStore.ohos.kt#L68)使用相同的两阶段淘汰 SQL。

- 当前大小估算为 `SUM(length(message) + length(type) + 32)`，不是数据库文件实际字节数，也未完整计入索引和其他字段。
- 第一步删除最旧的 Debug/Info 的一半；仍高于目标则再删除全部记录中最旧的四分之一，随后返回，没有保证达到目标。
- 数据库 `DELETE` 后是否回收物理文件空间，也未纳入现有预算校验。

**实际 SQLite 复现**：插入 6000 条记录，其中最旧的 2000 条为 32 字符 Info，其余 4000 条为 16384 字符 Error；执行仓库原有大小统计与清理 SQL。

| 阶段 | SQL 估算量 |
| --- | --- |
| 清理前 | 62.77 MiB |
| 删除部分旧 Debug/Info 后 | 62.71 MiB |
| 再删除部分旧日志后 | 58.73 MiB |

最终仍超过 50 MiB 触发阈值及 25 MiB 清理目标。另插入 16384 个“中”字：正文 UTF-8 大小为 **49152 字节**，当前 SQL 总估算仅 **16421**。

**建议**：先确定预算约束的是逻辑记录大小还是实际磁盘占用；使用匹配该口径的统计方式，分批淘汰并复查，直到满足预算或明确进入降级状态。单次维护必须有工作量上限；严格磁盘预算还需考虑索引、空闲页、数据库辅助文件与维护成本。

### LOG-LC-05：崩溃证据保留和导入状态没有分开管理

依据：[主页 `reportLastCrashIfAny` / `reportCrashRecord`](../shared/src/commonMain/kotlin/com/example/dsh/home/DshHomePage.kt#L3220)、[日志页清空](../shared/src/commonMain/kotlin/com/example/dsh/home/DshLogPage.kt#L485)、[BridgeModule.clearLastCrash](../shared/src/commonMain/kotlin/com/example/dsh/base/BridgeModule.kt#L53)。

- `lastReportedCrash` 仅是单个主页实例的内存状态。
- 原生读取不消费最近一次崩溃文件，清空日志页只清待写队列和日志表，没有调用原生清理能力，也没有持久化“已导入/已清除”标记。
- 因此，在同一崩溃快照仍存在的前提下，新主页会再次写入该崩溃。清空后重新进入主页，也可能让旧崩溃再次出现在列表中。

**建议**：为崩溃记录使用稳定 ID，并持久化导入及用户清除状态；保留证据和重复生成日志分开处理。明确“清空本地日志”是否包括原生崩溃快照，并使文案、实现和测试一致。

**平台边界**：[Android 适配器](../androidApp/src/main/java/com/example/dsh/adapter/KRUncaughtExceptionHandlerAdapter.kt#L12)在 Debug 分支直接抛出，不写该崩溃文件；[iOS](../iosApp/iosApp/KuiklyExpand/Modules/HRBridgeModule.m#L24)使用 `NSUncaughtExceptionHandler`；[OHOS](../ohosApp/entry/src/main/ets/kuikly/native/DshCrashStore.ets)订阅系统 `APP_CRASH`。这些接入方式不等于已验证覆盖所有原生崩溃类型。

### LOG-LC-06：导出半成品和内部文件缺少回收规则

依据：[DshLogQuery.export](../shared/src/commonMain/kotlin/com/example/dsh/diagnostics/DshLogQuery.kt#L44)、[Android 导出文件写入](../shared/src/androidMain/kotlin/com/example/dsh/infrastructure/LogExporter.android.kt)、[Android 内部导出目录](../androidApp/src/main/java/com/example/dsh/KuiklyRenderActivity.kt#L162)。

- 先创建最终名称的文件并写入抬头，再读取日志、逐批追加。
- 探针使刷盘失败，导出报错，但目录中仍留下只有抬头的 `partial.txt`。
- 日志表容量管理和清空都不管理这些文件；检查到的应用内部导出路径没有数量、大小或过期回收规则。

**建议**：在临时文件中完成导出，成功后发布为最终文件；失败/取消时删除半成品；为应用管理的导出缓存设置独立预算和回收规则，并保留分享重试所需的有效期。用户主动保存到外部位置的文件，其生命周期应单独定义，不能被内部缓存回收误删。

## 5. 验证证据与复现说明

### 5.1 本轮结果

- 18 项既有日志测试通过：`DshLogWriteBehindTest` 15 项，`DshLogConcurrencyTest` 3 项。
- 7 项临时审查探针完成复现。**这些探针断言的是已观察到的缺陷行为，测试通过不代表缺陷已修复。**
- 主机 SQLite 检查执行从仓库 `DshLogSql.kt` 提取的实际 SQL，复现超限未清到目标及 UTF-8 字节低估。
- JUnit 探针结果中的原始时间戳为 `2026-09-11T17:34:28`；本文按本轮审查记录日期 2026-09-12 归档。

| 探针方法 | 条件 / 观察结果 | 关联发现 |
| --- | --- | --- |
| `twoWritersCanAllocateTheSamePersistentSequence` | 两个写入器先初始化后写入，均使用序号 1；按实际主键替换语义只剩第二条 | LOG-LC-01 |
| `burstQueuesOneFlushJobPerEventAfterThreshold` | 手动调度器暂不执行后台任务，100 次入队排队 70 个任务 | LOG-LC-02 |
| `failedAppendIsRetainedButHasNoScheduledRetry` | 一次写入失败后队列保留，无待执行重试；手动 flush 才恢复落库 | LOG-LC-02 |
| `stoppedWriterAcceptsEventsAfterItsScopeHasBeenCancelled` | 停止及取消作用域后仍接收迟到事件，数据留在内存 | LOG-LC-02 |
| `cancellationDoesNotInterruptAnActiveScanAndStopWaitsForItsLock` | 首批读取暂停期间取消工作，停止操作等待锁；放行后扫描仍读完 3 批 | LOG-LC-03 |
| `loggerInitializationFailureEscapesToCallingBusinessCode` | 注入最大序号读取失败，初始化和日志入口向调用方抛异常 | LOG-LC-03 |
| `failedExportLeavesAPartialFileAndClearDoesNotRemoveExports` | 导出失败留有仅包含抬头的文件；清日志表不清该文件 | LOG-LC-06 |

探针使用存储替身验证共享代码的调度、状态和异常传播；双写入器测试的替身按 `INSERT OR REPLACE` 更新主键，不等同于多页面真机测试。容量检查使用主机实际 SQLite，但不等同于测量 Android/iOS/OHOS 的物理数据库文件占用。

### 5.2 本机复现入口

本轮审查工具是临时产物，未纳入标准源码测试集；Gradle init 脚本仅在传入 `-I` 时把探针目录加入本次测试编译。

- 临时 Kotlin 探针：`shared/build/log-lifecycle-audit/DshLogLifecycleAuditTest.kt`。
- 本机 init 脚本：`C:/Users/94917/AppData/Local/Temp/opencode/dsh-log-lifecycle-audit/init.gradle`。
- 本机 SQLite 检查脚本：同目录下的 `check_sql.py`。
- JUnit 结果：`shared/build/test-results/testDebugUnitTest/TEST-com.example.dsh.infrastructure.DshLogLifecycleAuditTest.xml`。

本次实际执行命令（在仓库根目录运行）：

```powershell
.\gradlew.bat -I "C:/Users/94917/AppData/Local/Temp/opencode/dsh-log-lifecycle-audit/init.gradle" :shared:testDebugUnitTest --tests "com.example.dsh.infrastructure.DshLogLifecycleAuditTest" --tests "com.example.dsh.infrastructure.DshLogConcurrencyTest" --tests "com.example.dsh.infrastructure.DshLogWriteBehindTest" --console=plain

wsl -d Ubuntu-dev -- python3 /mnt/c/Users/94917/AppData/Local/Temp/opencode/dsh-log-lifecycle-audit/check_sql.py /mnt/f/projects/xiaoai/demo-dsh/deepseek-harness-mobile/shared/src/commonMain/kotlin/com/example/dsh/infrastructure/DshLogSql.kt
```

上述临时文件可能因清理 `build` 或系统临时目录而消失，不应把这些绝对路径当作新检出仓库的标准测试入口。后续修复应将对应探针转为仓库正式回归测试，断言修复后的预期行为；本节保存的条件和结果可用于重建测试。

## 6. 可以保留的设计与剩余未知项

### 6.1 可以保留的设计

- 本地持久化并在重启后回看，批量写入以减少逐条 IO。
- 采集和 UI 筛选分开，筛选不删除日志。
- 内存过载时优先淘汰较低级别记录。
- 本地清理与 Host 会话历史隔离。
- 清空和在途刷盘由同一把锁串行化，既有并发测试证明旧刷盘不会把已清空的旧记录重新写回。
- 按容量淘汰而不按天过期可以成立；原始 Task 6 没有要求固定保留天数，关键是预算有效、行为可说明。

清空后由仍在运行的采集器产生的新日志，与旧刷盘回写或旧崩溃再次导入是三种不同情况，后续验收应分别判断。

### 6.2 待决策 / 待验证

1. 应用级服务在各平台的初始化、后台运行及关闭接线；需要锁定真实生命周期行为。
2. 最终容量口径、数值、清理目标、单次工作量与停止排空时间预算。
3. 崩溃快照是否纳入“清空全部本地日志”，以及导入/清除状态如何持久化。
4. 应用内部导出文件的预算、有效期与分享重试规则。
5. 多主页实例、进程重建、磁盘不足、持续流式生成、后台挂起与真实崩溃的设备测试。

尚未指定实施负责人和期限；修正顺序是技术建议。

### 6.3 已确认决策（2026-09-12，项目所有者）

1. **容量口径 = 实际磁盘占用**（LOG-LC-04）。预算约束 `dsh_logs.db` 及其辅助文件的实际字节数，不再以 `length()` 逻辑估算为主口径；具体数值按原始 Task 6「容量必须有硬上限、具体值由设备验证确认」保留候选值待验收。
2. **清空包含原生崩溃快照**（LOG-LC-05）。「清空本地日志」同时清除原生最近一次崩溃记录，并持久化导入/清除状态，避免旧崩溃在主页重建或重启后再次入库。
3. **导出保留预算以原始需求为准**（LOG-LC-06）。原始 Task 6 只要求本地导出/分享，未定义应用内部导出缓存；因此不新增自动长期缓存：临时文件写成功后发布、失败/取消删除半成品，仅保留分享重试所需的最近一次有效导出，应用内部目录有界回收。用户主动保存到外部位置的文件不纳入回收。

实现顺序按第 7 节：先做不受决策阻塞的 LOG-LC-06 正确性与 LOG-LC-01/02，再做 LOG-LC-03，最后做容量与崩溃状态。

## 7. 修正顺序与验收追踪

建议顺序：**应用级唯一日志服务 → 单写入任务与失败恢复 → 查询取消及退出排空 → 可验证容量预算 → 崩溃去重与导出文件回收。**

| 验收编号 | 关联发现 | 修复后应满足的验证条件 |
| --- | --- | --- |
| LOG-LC-TEST-01 | LOG-LC-01 | 冷启动首次连接失败的日志可在日志中心回看；主页重建不重建或清空共享写入器 |
| LOG-LC-TEST-02 | LOG-LC-01 | 多页面及并发初始化只产生一个有效写入器；序号不冲突，旧页面销毁不影响新页面采集 |
| LOG-LC-TEST-03 | LOG-LC-02 | 高频突发采集时写入任务数量有界；不为每条事件排队一个刷盘任务 |
| LOG-LC-TEST-04 | LOG-LC-02 | 注入暂时写入失败，存储恢复后在没有新事件的条件下自动重试；停止后的迟到事件按明确策略处理 |
| LOG-LC-TEST-05 | LOG-LC-03 | 取消大范围查询/导出后在有限批次内停止；页面隐藏和退出不等待完整扫描 |
| LOG-LC-TEST-06 | LOG-LC-03 | 建库、序号读取、写入、容量查询和清理失败不向对话业务传播；降级状态可观察 |
| LOG-LC-TEST-07 | LOG-LC-04 | 非均匀大小、多字节文本、全高等级日志均满足已确认的容量预算；清理能推进且单次工作有界 |
| LOG-LC-TEST-08 | LOG-LC-05 | 同一崩溃跨主页和重启最多导入一次；清空后的表现符合已确认的快照保留规则 |
| LOG-LC-TEST-09 | LOG-LC-06 | 失败/取消不残留最终名称半成品；内部导出缓存有界；有效文件可以分享重试 |
| LOG-LC-TEST-10 | 全部 | 回归在途刷盘与清空的顺序，确认本地清理始终不修改 Host 消息、附件或历史 |

下一步：按上述编号实施和固化回归测试，再分别执行 Android/iOS/OHOS 设备验收。代码修复、测试通过和平台验收应独立更新状态。
