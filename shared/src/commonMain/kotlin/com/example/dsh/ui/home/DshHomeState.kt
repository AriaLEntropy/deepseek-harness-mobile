package com.example.dsh.ui.home

import com.tencent.kuikly.core.base.PagerScope
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.*
import com.example.dsh.base.setTimeout
import com.example.dsh.connection.DshConnectionCoordinator
import com.example.dsh.connection.DshEngineModule
import com.example.dsh.models.DshAgentPresetOption
import com.example.dsh.host.DshConnectionMode
import com.example.dsh.session.DshDirectoryEntry
import com.example.dsh.export.DshExportFormat
import com.example.dsh.session.DshGoalSnapshot
import com.example.dsh.attachment.DshImageLimits
import com.example.dsh.session.DshJobItem
import com.example.dsh.message.DshMessage
import com.example.dsh.models.DshModelOption
import com.example.dsh.interaction.DshPendingApproval
import com.example.dsh.attachment.DshPendingFile
import com.example.dsh.attachment.DshPendingImage
import com.example.dsh.interaction.DshPendingQuestion
import com.example.dsh.plugin.DshPluginConfigCard
import com.example.dsh.plugin.DshPluginEntry
import com.example.dsh.models.DshProviderConfig
import com.example.dsh.models.DshProviderModel
import com.example.dsh.interaction.DshQuestionDraft
import com.example.dsh.session.DshQueueItem
import com.example.dsh.host.DshRemoteRepository
import com.example.dsh.host.DshRepository
import com.example.dsh.session.DshSession
import com.example.dsh.session.DshSessionCacheState
import com.example.dsh.session.DshSessionScope
import com.example.dsh.models.DshSettingsChoice
import com.example.dsh.models.DshSettingsSnapshot
import com.example.dsh.session.DshSkill
import com.example.dsh.host.DshStreamHandle
import com.example.dsh.session.DshWorkspaceGroup
import com.example.dsh.diagnostics.DshLogWork
import com.example.dsh.log.DshLogService
import com.example.dsh.log.DshStreamLog
import com.example.dsh.ui.rendering.DSH_PREF_EXPAND_MODAL
import com.example.dsh.ui.rendering.DSH_PREF_PROCESS_DISPLAY
import com.example.dsh.ui.rendering.DSH_PREF_SHOW_CONNECTORS
import com.example.dsh.ui.rendering.DSH_PREF_SHOW_RESULT_CARDS
import com.example.dsh.ui.rendering.DshExpandedPayload
import com.example.dsh.ui.rendering.DshProcessDisplayMode
import com.example.dsh.ui.rendering.dshProcessDisplayFromValue
import com.example.dsh.storage.DshLocalStore
import com.example.dsh.storage.createDshLocalStore
import com.example.dsh.base.BasePager
import com.example.dsh.base.bridgeModule
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.ListView
import com.tencent.kuikly.core.views.TextAreaView
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.module.CallbackRef
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.base.BackPressCallback
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.time.TimeMark
import com.example.dsh.voice.DshVoiceWaveform
import com.tencent.kuikly.core.timer.setTimeout
import com.example.dsh.session.DSH_DRAWER_GROUP_WORKSPACE
import com.example.dsh.session.DSH_DRAWER_ORDER_UPDATED
import com.example.dsh.ui.session.DshArchiveConfirm
import com.example.dsh.ui.session.DshArchiveProjectOption
import com.example.dsh.ui.session.DshArchiveSort
import com.example.dsh.session.DshDrawerDrag
import com.example.dsh.ui.export.DshTextExportState
import com.example.dsh.ui.session.DshWorkspacePickerScreen
import com.example.dsh.ui.interaction.DshPermissionOption
import com.example.dsh.search.DshSessionSearchHit

/** Home 页面 UI 状态层：集中持有页面全部响应式状态字段，页面只做编排与渲染。 */
internal class DshHomeState(private val scope: PagerScope) {
    var readableExport by scope.observable(DshTextExportState())
    var readableExportDialogVisible by scope.observable(false)
    // ===== 分享多选态：消息列表勾选 + 底部格式弹窗 =====
    var exportSelectMode by scope.observable(false)
    // 以「对话组」为单位选择：key 由组内用户 Prompt（无则助手）消息 id 生成
    var exportSelectedGroups by scope.observable(emptySet<String>())
    var exportFormat by scope.observable(DshExportFormat.HTML)
    var exportMoreShareVisible by scope.observable(false)
    var exportPdfBusy by scope.observable(false)
    /** 吸顶选择器当前所属对话组 key（空=不显示）。 */
    var exportStickyGroupKey by scope.observable("")
    var pluginInventoryVisible by scope.observable(false)
    var pluginInventoryLoading by scope.observable(false)
    var pluginInventoryError by scope.observable("")
    var pluginKeyword by scope.observable("")
    /** 搜索框是否有内容，用于响应式显示清除按钮。 */
    var pluginSearchHasText by scope.observable(false)
    var pluginPhase by scope.observable("")
    var pluginTotal by scope.observable(0)
    val pluginRows by scope.observableList<DshPluginEntry>()
    var pluginExpandedId by scope.observable("")
    var pluginActionTarget by scope.observable<DshPluginEntry?>(null)
    var pluginConfirmAction by scope.observable("")
    var pluginBusyId by scope.observable("")
    var pluginActionError by scope.observable("")
    var pluginNotice by scope.observable("")
    var pluginActiveTab by scope.observable("config")
    var pluginConfigLoading by scope.observable(false)
    var pluginConfigError by scope.observable("")
    var pluginConfigWritable by scope.observable(true)
    val pluginConfigCards by scope.observableList<DshPluginConfigCard>()
    var pluginConfigDrafts by scope.observable<Map<String, String>>(emptyMap())
    var pluginConfigSecretDrafts by scope.observable<Map<String, String>>(emptyMap())
    var pluginConfigCollapsed by scope.observable<Set<String>>(emptySet())
    var pluginConfigBusyNamespace by scope.observable("")
    var pluginConfigCardError by scope.observable<Map<String, String>>(emptyMap())
    var pluginConfigCardNotice by scope.observable<Map<String, String>>(emptyMap())
    var connectionMode by scope.observable(DshConnectionMode.RELAY)
    var remoteProfileId by scope.observable(DshSessionScope.DEFAULT_REMOTE_PROFILE_ID)
    var sshHost by scope.observable("")
    var sshUser by scope.observable("")
    var sshPort by scope.observable("22")
    var sshDshPort by scope.observable("3080")
    var sshKeyId by scope.observable("")
    var sshFingerprint by scope.observable("")
    var sshKeyLabel by scope.observable("未导入私钥")
    var sshKeyPassphrase by scope.observable("")
    var sshSettingsVisible by scope.observable(false)
    var sshSettingsBusy by scope.observable(false)
    var sshSettingsError by scope.observable("")
    // Metadata is read by attr/vif outside vfor too. Publish immutable snapshots
    // so a same-ID title/status change invalidates those readers.
    var sessions by scope.observable(emptyList<DshSession>())
    val visibleSessions by scope.observableList<DshSession>()
    var messages by scope.observableList<DshMessage>()
    var conversationPanelIds by scope.observableList<String>()
    var activeSessionId by scope.observable("session-1")
    var draft by scope.observable("")
    var streaming by scope.observable(false)
    var stopButtonVisible by scope.observable(false)
    var streamingAssistantContent by scope.observable("")
    var copiedMessageId by scope.observable("")
    var keyboardHeight by scope.observable(0f)
    var keyboardAnimation by scope.observable(Animation.easeInOut(DshHomePage.ANIMATION_DURATION_S))
    var _connectionLabel by scope.observable("本地内核启动中")
    /** 连接状态胶囊可见性，从已连接变就绪时延迟 3s 后淡出隐藏 */
    var connectionCapsuleVisible by scope.observable(false)
    var connectionCapsuleFadeOut by scope.observable(false)
    var connectionCapsuleFadeOutAnimation by scope.observable(Animation.easeInOut(0.3f))
    var apiKeyDraft by scope.observable("")
    var credentialSetupVisible by scope.observable(false)
    var credentialSetupBusy by scope.observable(false)
    var credentialSetupError by scope.observable("")
    var credentialSetupTitle by scope.observable("添加一个 API Key 开始使用")
    var sessionDrawerVisible by scope.observable(false)
    var sessionDrawerAnimated by scope.observable(false)
    // 会话抽屉：工作区文件夹的展开/收起状态（内嵌菜单，会话内记忆）
    var workspaceExpandedIds by scope.observable(emptySet<String>())
    var pendingSessionIds by scope.observable(emptySet<String>())
    // 会话抽屉：从会话行 ⋯ 打开的 overflow menu 目标会话；为空时回退到当前会话
    var overflowTargetSessionId by scope.observable("")
    var modelPickerVisible by scope.observable(false)
    var modelEffortsVisible by scope.observable(false)
    var modelPickerBusy by scope.observable(false)
    var modelPickerError by scope.observable("")
    var selectedModelLabel by scope.observable("选择模型")
    var selectedEffortLabel by scope.observable("")
    var modelOptions by scope.observableList<DshModelOption>()
    var permissionPickerVisible by scope.observable(false)
    var riskConfirmVisible by scope.observable(false)
    var riskAcknowledged by scope.observable(false)
    var permissionValue by scope.observable("workspace-write")
    var permissionLabel by scope.observable("工作区写入")
    var agentModePickerVisible by scope.observable(false)
    var agentModePickerTitle by scope.observable("选择模式")
    var agentModeValue by scope.observable("standard")
    var agentModeLabel by scope.observable("标准模式")
    // 电脑端 agentPreset.list 拉取的预设（空则回退本地四项）
    val agentPresetOptions by scope.observableList<DshAgentPresetOption>()
    // 设置页（ds 风格）
    var settingsPageVisible by scope.observable(false)
    var settingsLoading by scope.observable(false)
    var settingsError by scope.observable("")
    var settingsSnapshot by scope.observable(DshSettingsSnapshot())
    var hostVersion by scope.observable("")
    var settingsChoiceTitle by scope.observable("")
    var settingsChoiceKind by scope.observable("")
    var settingsChoiceBusy by scope.observable(false)
    val settingsChoiceOptions by scope.observableList<DshSettingsChoice>()
    // 个性化「对话展示」：过程折叠方式（互斥）+ 弹窗查看开关（本地持久化）
    var personalizationPageVisible by scope.observable(false)
    var chatProcessMode by scope.observable(DshProcessDisplayMode.UNIFIED)
    var chatExpandInModal by scope.observable(false)
    var chatShowConnectors by scope.observable(true)
    var chatShowResultCards by scope.observable(true)
    var expandedPayload by scope.observable<DshExpandedPayload?>(null)
    // 设置页「模型」详情页（对齐电脑端 settings.models）
    var modelsPageVisible by scope.observable(false)
    var modelsLoading by scope.observable(false)
    var modelsError by scope.observable("")
    var modelsWritable by scope.observable(false)
    val modelsProviders by scope.observableList<DshProviderConfig>()
    var modelsEditingProvider by scope.observable("")
    var modelsDraftBaseUrl by scope.observable("")
    var modelsDraftApiKey by scope.observable("")
    val modelsDraftModels by scope.observableList<DshProviderModel>()
    var modelsSaving by scope.observable(false)
    var modelsSaveError by scope.observable("")
    var modelsDeleteTarget by scope.observable<DshProviderConfig?>(null)
    var modelsDeleting by scope.observable(false)
    var modelsProtocols by scope.observable<List<String>>(emptyList())
    var modelsCustomRevision by scope.observable(0)
    val modelsConfiguredProviders by scope.observableList<DshProviderConfig>()
    val modelsAddableProviders by scope.observableList<DshProviderConfig>()
    var modelsAdding by scope.observable(false)
    var modelsPickerVisible by scope.observable(false)
    var modelsCustomAdding by scope.observable(false)
    var modelsEditorAdvanced by scope.observable(false)
    var modelsSavedNotice by scope.observable("")
    var modelsCustomRoute by scope.observable("")
    var modelsCustomName by scope.observable("")
    var modelsCustomBaseUrl by scope.observable("")
    var modelsCustomProtocol by scope.observable("")
    var modelsCustomApiKey by scope.observable("")
    val modelsCustomModels by scope.observableList<DshProviderModel>()
    var modelsCustomBusy by scope.observable(false)
    var modelsCustomError by scope.observable("")
    var commandSheetVisible by scope.observable(false)
    // ===== 语音输入（按住说话 → 原生语音识别 → 转文字填入输入框）=====
    /** 语音输入 UI 状态；聚合原 voiceActive/Recording/CancelArmed/PartialText/WaveformRevision。 */
    var voiceUi by scope.observable(DshVoiceUiState())
    var conversationListEpoch by scope.observable(0)
    // 每轮流式结算 +1：驱动已上屏消息行就地重算过程分组与 footer，避免等下一次历史重挂。
    var messageRenderEpoch by scope.observable(0)
    var streamingAssistantId by scope.observable("")
    var webDisclosureRevision by scope.observable(0)
    var attachmentRevision by scope.observable(0)
    var previewImageUrl by scope.observable<String?>(null)
    // ===== Task 3 附件：输入区图片草稿（仅内存，不落盘）与 Host 下发限额 =====
    val pendingImages by scope.observableList<DshPendingImage>()
    // 通用文件草稿：旧 Host 无文件 content 类型，发送前经 host-plugin 落盘并写入 prompt handle。
    val pendingFiles by scope.observableList<DshPendingFile>()
    var attachmentEpoch by scope.observable(0)
    var imageLimits by scope.observable<DshImageLimits?>(null)
    var queueDockExpanded by scope.observable(false)
    var queueItems by scope.observableList<DshQueueItem>()
    var queueActionBusy by scope.observable(false)
    var jobItems by scope.observableList<DshJobItem>()
    var jobsPanelExpanded by scope.observable(false)
    var jobsNow by scope.observable(0L)
    var jobsClockScheduled by scope.observable(false)
    var liveJobItems by scope.observableList<DshJobItem>()
    var workspaceGroups by scope.observableList<DshWorkspaceGroup>()
    val skills by scope.observableList<DshSkill>()
    var goalSnapshot by scope.observable<DshGoalSnapshot?>(null)
    var goalActionBusy by scope.observable(false)
    var goalActionError by scope.observable("")
    var queueEditingId by scope.observable("")
    var queueEditingText by scope.observable("")
    var sessionRunning by scope.observable(false)
    var turnElapsedMs by scope.observable(0L)
    // ===== 新建会话-工作区选择（最近的文件夹 / 添加文件夹） =====
    var workspacePickerVisible by scope.observable(false)
    var workspacePickerScreen by scope.observable(DshWorkspacePickerScreen.RECENT)
    var workspacePickerBusy by scope.observable(false)
    var workspacePickerError by scope.observable("")
    var workspaceAddPath by scope.observable("")
    var workspaceAddHome by scope.observable("")
    var workspaceAddBusy by scope.observable(false)
    var workspaceAddNewName by scope.observable("")
    val workspaceAddEntries by scope.observableList<DshDirectoryEntry>()
    // 「添加文件夹」目录是否已加载（空目录缺省页/加载态判断）；键盘高度用于面板避让。
    var workspaceAddDirectoryLoaded by scope.observable(false)
    var workspaceAddKeyboardHeight by scope.observable(0f)
    // 「最近的文件夹」只列真实工作区（排除「未分组」占位），供 vfor 直接迭代。
    val workspacePickerFolders by scope.observableList<DshWorkspaceGroup>()
    var workspaceRenameTargetId by scope.observable("")
    var workspaceRenameDraft by scope.observable("")
    var workspaceDeleteTargetId by scope.observable("")
    var workspaceActionBusy by scope.observable(false)
    var workspaceActionError by scope.observable("")
    // ===== 会话 overflow menu 与会话管理动作 =====
    var overflowMenuVisible by scope.observable(false)
    // overflow menu 的锚点（点击会话行 ⋯ 的屏幕坐标）；-1 表示无锚点，回退到默认位置。
    var overflowAnchorX by scope.observable(-1f)
    var overflowAnchorY by scope.observable(-1f)
    var sessionRenameVisible by scope.observable(false)
    var sessionActionTargetId by scope.observable("")
    var sessionRenameDraft by scope.observable("")
    var sessionRenameBusy by scope.observable(false)
    var sessionRenameError by scope.observable("")
    var sessionArchiveVisible by scope.observable(false)
    var sessionArchiveBusy by scope.observable(false)
    var sessionArchiveError by scope.observable("")
    var archiveListVisible by scope.observable(false)
    var archiveListLoading by scope.observable(false)
    var archiveListError by scope.observable("")
    var archiveOpeningId by scope.observable("")
    val archivedSessions by scope.observableList<DshSession>()
    var archiveSearch by scope.observable("")
    var archiveProjectFilter by scope.observable("")
    var archiveSort by scope.observable(DshArchiveSort.UPDATED)
    var archiveMenu by scope.observable("")
    var archiveBusy by scope.observable(false)
    var archiveNotice by scope.observable("")
    var archiveConfirm by scope.observable<DshArchiveConfirm?>(null)
    // 归档页行内 ⋯ 溢出菜单（取消归档 / 删除）
    var archiveOverflowVisible by scope.observable(false)
    var archiveOverflowTargetId by scope.observable("")
    var archiveOverflowAnchorX by scope.observable(-1f)
    var archiveOverflowAnchorY by scope.observable(-1f)
    // 归档页筛选（所有项目 / 排序）上下文菜单锚点
    var archiveFilterX by scope.observable(-1f)
    var archiveFilterY by scope.observable(-1f)
    val archiveGroups by scope.observableList<DshWorkspaceGroup>()
    val archiveProjectOptions by scope.observableList<DshArchiveProjectOption>()
    var sessionCreatedAt by scope.observable<Map<String, Long>>(emptyMap())
    // ===== 全屏会话搜索 =====
    /** 搜索界面是否展示（由抽屉顶部搜索入口打开）。 */
    var sessionSearchVisible by scope.observable(false)
    /** 搜索框是否有内容，驱动结果列表与清除按钮。 */
    var sessionSearchActive by scope.observable(false)
    val sessionSearchHits by scope.observableList<DshSessionSearchHit>()
    // ===== 会话抽屉长按拖拽排序 =====
    var drawerDrag by scope.observable(DshDrawerDrag())
    var sessionManualOrder by scope.observable<Map<String, List<String>>>(emptyMap())
    var workspaceManualOrder by scope.observable<List<String>>(emptyList())
    // ===== 抽屉视图选项（对齐电脑端 WorkspaceBrowser）=====
    var drawerViewGroupBy by scope.observable(DSH_DRAWER_GROUP_WORKSPACE)
    var drawerViewOrderBy by scope.observable(DSH_DRAWER_ORDER_UPDATED)
    var drawerViewOptionsVisible by scope.observable(false)
    var drawerViewOptionsX by scope.observable(-1f)
    var drawerViewOptionsY by scope.observable(-1f)
    var sessionDeleteVisible by scope.observable(false)
    var sessionDeleteBusy by scope.observable(false)
    var sessionDeleteError by scope.observable("")
    var pendingApproval by scope.observable<DshPendingApproval?>(null)
    var pendingQuestion by scope.observable<DshPendingQuestion?>(null)
    var interactionBusy by scope.observable(false)
    val selectedQuestionOptions by scope.observableList<String>()
    var questionCustom by scope.observable("")
    var questionIndex by scope.observable(0)
    var questionError by scope.observable("")
    var questionHasSelection by scope.observable(false)
    var messageActionsMessage by scope.observable<DshMessage?>(null)
    var messageActionsX by scope.observable(0f)
    var messageActionsY by scope.observable(0f)
    var menuBlurUri by scope.observable("")
    // 「选择文本」弹窗：以单个可选中文本节点承载完整正文，供原生选区复制
    var selectTextModalVisible by scope.observable(false)
    var selectTextModalContent by scope.observable("")
}
