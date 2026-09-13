package com.example.dsh.ui.home

import com.example.dsh.base.setTimeout
import com.example.dsh.ui.chat.DshCommand
import com.example.dsh.ui.message.DshMessageRow
import com.example.dsh.ui.chat.TURN_STATUS_CLOCK_AFTER_MS
import com.example.dsh.message.contextCatalogEntries
import com.example.dsh.message.contextInstructions
import com.example.dsh.message.contextRecalls
import com.example.dsh.message.contextRelaySender
import com.example.dsh.message.contextSections
import com.example.dsh.connection.DshConnectionCoordinator
import com.example.dsh.connection.DshEngineModule
import com.example.dsh.models.DshAgentPresetOption
import com.example.dsh.host.DshConnectionMode
import com.example.dsh.session.DshDirectoryEntry
import com.example.dsh.export.DshExportFormat
import com.example.dsh.attachment.DshFileDraftState
import com.example.dsh.session.DshGoalSnapshot
import com.example.dsh.attachment.DshImageDraftState
import com.example.dsh.attachment.DshImageLimits
import com.example.dsh.session.DshJobItem
import com.example.dsh.message.DshMessage
import com.example.dsh.message.DshMessageRole
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
import com.example.dsh.host.DshRawSessionEvent
import com.example.dsh.export.DshReadableContent
import com.example.dsh.host.DshRemoteRepository
import com.example.dsh.tool.DshRemoteToolCallModels
import com.example.dsh.host.DshRepository
import com.example.dsh.session.DshSession
import com.example.dsh.session.DshSessionCacheState
import com.example.dsh.session.DshSessionScope
import com.example.dsh.models.DshSettingsChoice
import com.example.dsh.models.DshSettingsSnapshot
import com.example.dsh.session.DshSkill
import com.example.dsh.host.DshStreamHandle
import com.example.dsh.models.DshToolCardType
import com.example.dsh.message.DshWebTimelineItem
import com.example.dsh.session.DshWorkspaceGroup
import com.example.dsh.message.dshHistoryTailToResume
import com.example.dsh.message.dshIsLiveAssistantText
import com.example.dsh.host.dshIsTransportInterrupt
import com.example.dsh.message.dshMessagesVisuallyEqual
import com.example.dsh.message.dshSyncMessageForkAnchor
import com.example.dsh.tool.dshWireEvent
import com.example.dsh.message.isRuntimeContextSnapshot
import com.example.dsh.tool.toRemoteMessage
import com.example.dsh.diagnostics.DshCrashMarker
import com.example.dsh.diagnostics.DshLogWork
import com.example.dsh.diagnostics.DshLogPageContract
import com.example.dsh.log.DshLogService
import com.example.dsh.log.DshLogWriteBehind
import com.example.dsh.log.DshStreamLog
import com.example.dsh.log.LogEvent
import com.example.dsh.log.LogLevel
import com.example.dsh.log.LogSanitizer
import com.example.dsh.platform.currentTimeMillis
import com.example.dsh.ui.rendering.DSH_PREF_EXPAND_MODAL
import com.example.dsh.ui.rendering.DSH_PREF_PROCESS_DISPLAY
import com.example.dsh.ui.rendering.DSH_PREF_SHOW_CONNECTORS
import com.example.dsh.ui.rendering.DSH_PREF_SHOW_RESULT_CARDS
import com.example.dsh.ui.rendering.DshExpandedPayload
import com.example.dsh.ui.rendering.DshMarkdown
import com.example.dsh.ui.rendering.DshProcessDisplayMode
import com.example.dsh.ui.rendering.dshLiveJobs
import com.example.dsh.ui.rendering.dshProcessDisplayFromValue
import com.example.dsh.ui.rendering.dshProcessDisplayValue
import com.example.dsh.storage.DshLocalStore
import com.example.dsh.storage.createDshLocalStore
import com.example.dsh.theme.DshThemeManager
import com.example.dsh.base.BasePager
import com.example.dsh.base.bridgeModule
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.directives.scrollToPosition
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.ListView
import com.tencent.kuikly.core.views.TextAreaView
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.module.CallbackRef
import com.tencent.kuikly.core.module.NotifyModule
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.base.BackPressCallback
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.example.dsh.base.setTimeout
import com.tencent.kuikly.core.views.KeyboardParams
import com.tencent.kuikly.core.views.ListContentView
import com.tencent.kuikly.core.views.ScrollParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import com.example.dsh.voice.DshVoiceWaveform
import com.example.dsh.attachment.composePromptWithFiles
import com.example.dsh.message.messageRowKey
import com.example.dsh.models.selectedReasoningEffortName
import com.example.dsh.host.attachmentIdsFromBlocks
import com.example.dsh.host.contextSummary
import com.example.dsh.host.inlineImageDataUrl
import com.example.dsh.host.textFromBlocks
import com.example.dsh.host.toolCardType
import com.example.dsh.base.setTimeout
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
import com.example.dsh.ui.search.DshSessionSearchHit

internal const val SESSION_CACHE_WARM_LIMIT = 7
/** 会话搜索结果行上限，避免超长列表拖慢渲染。 */
internal const val SESSION_SEARCH_HIT_LIMIT = 80
internal const val SESSION_CACHE_WARM_INTERVAL_MS = 16
internal const val SESSION_CACHE_WARM_START_DELAY_MS = 600
internal const val CONVERSATION_PANEL_CACHE_LIMIT = 8
internal const val SCROLL_SETTLE_ATTEMPTS = 6
internal val SCROLL_SETTLE_DELAYS_MS = intArrayOf(0, 16, 32, 64, 120, 200)
internal const val FOLLOW_LIST_SLACK_PX = 72f

/** First usable DSH surface: local sessions, streaming Markdown, and a composer. */
@Page("home")
internal class DshHomePage : BasePager() {
    internal var repository: DshRepository? = null
    /** 仅远程模式可用的能力面；本地模式为 null。 */
    internal val remoteRepo: DshRemoteRepository? get() = repository as? DshRemoteRepository

    /** 下一帧执行：统一 Kuikly 的 setTimeout(pagerId, 0) 写法，明确“稍后执行”意图。 */
    internal inline fun postToUi(crossinline block: () -> Unit) {
        setTimeout(pagerId, 0) { block() }
    }
    internal var localStore: DshLocalStore? = null
    internal var logJumpNotifyRef: CallbackRef? = null

    internal var pageAlive = true
    internal var exportDir = ""
    internal var crashImportWork: DshLogWork<Boolean>? = null
    internal var readableExportWork: DshLogWork<String>? = null
    internal var readableExportVersion = 0
    internal var readableExport by observable(DshTextExportState())
    internal val readableExportBusy: Boolean get() = readableExport.busy
    internal var readableExportDialogVisible by observable(false)
    internal var readableExportSourceText: String? = null
    internal var readableExportExtension = "txt"
    // ===== 分享多选态：消息列表勾选 + 底部格式弹窗 =====
    internal var exportSelectMode by observable(false)
    internal var exportSelectSessionId = ""
    internal var pendingExportSelectionSessionId = ""
    internal var pendingExportSelectionPreselect = ""
    // 以「对话组」为单位选择：key 由组内用户 Prompt（无则助手）消息 id 生成
    internal var exportSelectedGroups by observable(emptySet<String>())
    internal var exportFormat by observable(DshExportFormat.HTML)
    internal var exportMoreShareVisible by observable(false)
    internal var exportPdfBusy by observable(false)
    internal var timelineReadVersion = 0
    internal var pluginInventoryVisible by observable(false)
    internal var pluginInventoryLoading by observable(false)
    internal var pluginInventoryError by observable("")
    internal var pluginKeyword by observable("")
    /** 搜索框原生文本：非响应式，只喂给 Input 的 text()，避免每次按键重设文本与原生输入互相覆盖。 */
    internal var pluginSearchInput = ""
    /** 搜索框是否有内容，用于响应式显示清除按钮。 */
    internal var pluginSearchHasText by observable(false)
    internal var pluginSearchInputView: InputView? = null
    internal var pluginPhase by observable("")
    internal var pluginTotal by observable(0)
    internal var pluginInventory = emptyList<DshPluginEntry>()
    internal val pluginRows by observableList<DshPluginEntry>()
    internal var pluginRequestVersion = 0
    internal var pluginExpandedId by observable("")
    internal var pluginActionTarget by observable<DshPluginEntry?>(null)
    internal var pluginConfirmAction by observable("")
    internal var pluginBusyId by observable("")
    internal var pluginActionError by observable("")
    internal var pluginNotice by observable("")
    internal var pluginActiveTab by observable("config")
    internal var pluginConfigLoading by observable(false)
    internal var pluginConfigError by observable("")
    internal var pluginConfigWritable by observable(true)
    internal val pluginConfigCards by observableList<DshPluginConfigCard>()
    internal var pluginConfigDrafts by observable<Map<String, String>>(emptyMap())
    internal var pluginConfigSecretDrafts by observable<Map<String, String>>(emptyMap())
    internal var pluginConfigCollapsed by observable<Set<String>>(emptySet())
    internal var pluginConfigBusyNamespace by observable("")
    internal var pluginConfigCardError by observable<Map<String, String>>(emptyMap())
    internal var pluginConfigCardNotice by observable<Map<String, String>>(emptyMap())
    internal var engineModule: DshEngineModule? = null
    internal var engineReady = false
    internal var relayEngineEndpoint = ""
    internal var pendingApiKey = ""
    internal var connectionMode by observable(DshConnectionMode.RELAY)
    internal val sshMode: Boolean
        get() = connectionMode == DshConnectionMode.SSH
    internal val isRemoteHost: Boolean
        get() = connectionMode == DshConnectionMode.RELAY || connectionMode == DshConnectionMode.SSH
    internal var remoteProfileId by observable(DshSessionScope.DEFAULT_REMOTE_PROFILE_ID)
    internal var sshHost by observable("")
    internal var sshUser by observable("")
    internal var sshPort by observable("22")
    internal var sshDshPort by observable("3080")
    internal var sshKeyId by observable("")
    internal var sshFingerprint by observable("")
    internal var sshKeyLabel by observable("未导入私钥")
    internal var sshKeyPassphrase by observable("")
    internal var sshSettingsVisible by observable(false)
    internal var sshSettingsBusy by observable(false)
    internal var sshSettingsError by observable("")
    internal val sessionScope: DshSessionScope
        get() = DshSessionScope(connectionMode, remoteProfileId)
    internal val activeConnectionId: String
        get() = sessionScope.storageKey

    // Metadata is read by attr/vif outside vfor too. Publish immutable snapshots
    // so a same-ID title/status change invalidates those readers.
    internal var sessions by observable(emptyList<DshSession>())
    internal val visibleSessions by observableList<DshSession>()
    internal var messages by observableList<DshMessage>()
    internal var conversationPanelIds by observableList<String>()
    internal var activeSessionId by observable("session-1")
    internal var preferBlankHomeOnNextLoad = true
    internal var draft by observable("")
    internal var streaming by observable(false)
    internal var stopButtonVisible by observable(false)
    internal var streamingAssistantContent by observable("")
    internal var copiedMessageId by observable("")
    internal var keyboardHeight by observable(0f)
    internal var keyboardAnimation by observable(Animation.easeInOut(ANIMATION_DURATION_S))
    internal var _connectionLabel by observable("本地内核启动中")
    internal var connectionLabel: String
        get() = _connectionLabel
        set(value) {
            if (_connectionLabel != value) {
                _connectionLabel = value
                onConnectionLabelChanged(value)
            }
        }
    /** 连接状态胶囊可见性，从已连接变就绪时延迟 3s 后淡出隐藏 */
    internal var connectionCapsuleVisible by observable(false)
    internal var connectionCapsuleFadeOut by observable(false)
    internal var connectionCapsuleFadeOutAnimation by observable(Animation.easeInOut(0.3f))
    internal var connectionCapsuleVersion = 0
    internal var apiKeyDraft by observable("")
    internal var credentialSetupVisible by observable(false)
    internal var credentialSetupBusy by observable(false)
    internal var credentialSetupError by observable("")
    internal var credentialSetupTitle by observable("添加一个 API Key 开始使用")
    internal var sessionDrawerVisible by observable(false)
    internal var sessionDrawerAnimated by observable(false)
    // 会话抽屉：工作区文件夹的展开/收起状态（内嵌菜单，会话内记忆）
    internal var workspaceExpandedIds by observable(emptySet<String>())
    internal var pendingSessionIds by observable(emptySet<String>())
    // 会话抽屉：从会话行 ⋯ 打开的 overflow menu 目标会话；为空时回退到当前会话
    internal var overflowTargetSessionId by observable("")
    internal var modelPickerVisible by observable(false)
    internal var modelEffortsVisible by observable(false)
    internal var modelPickerBusy by observable(false)
    internal var modelPickerError by observable("")
    internal var selectedModelLabel by observable("选择模型")
    internal var selectedEffortLabel by observable("")
    internal var modelOptions by observableList<DshModelOption>()
    internal var modelRequestVersion = 0
    internal var permissionPickerVisible by observable(false)
    internal var riskConfirmVisible by observable(false)
    internal var riskAcknowledged by observable(false)
    internal var pendingDangerPermission: DshPermissionOption? = null
    internal var permissionValue by observable("workspace-write")
    internal var permissionLabel by observable("工作区写入")
    internal var agentModePickerVisible by observable(false)
    internal var agentModePickerTitle by observable("选择模式")
    internal var agentModeValue by observable("standard")
    internal var agentModeLabel by observable("标准模式")
    // 电脑端 agentPreset.list 拉取的预设（空则回退本地四项）
    internal val agentPresetOptions by observableList<DshAgentPresetOption>()
    // 设置页（ds 风格）
    internal var settingsPageVisible by observable(false)
    internal var settingsLoading by observable(false)
    internal var settingsError by observable("")
    internal var settingsSnapshot by observable(DshSettingsSnapshot())
    internal var hostVersion by observable("")
    internal var settingsChoiceTitle by observable("")
    internal var settingsChoiceKind by observable("")
    internal var settingsChoiceBusy by observable(false)
    internal val settingsChoiceOptions by observableList<DshSettingsChoice>()
    // 个性化「对话展示」：过程折叠方式（互斥）+ 弹窗查看开关（本地持久化）
    internal var personalizationPageVisible by observable(false)
    internal var chatProcessMode by observable(DshProcessDisplayMode.UNIFIED)
    internal var chatExpandInModal by observable(false)
    internal var chatShowConnectors by observable(true)
    internal var chatShowResultCards by observable(true)
    internal var expandedPayload by observable<DshExpandedPayload?>(null)
    // 设置页「模型」详情页（对齐电脑端 settings.models）
    internal var modelsPageVisible by observable(false)
    internal var modelsLoading by observable(false)
    internal var modelsError by observable("")
    internal var modelsWritable by observable(false)
    internal val modelsProviders by observableList<DshProviderConfig>()
    internal var modelsEditingProvider by observable("")
    internal var modelsDraftBaseUrl by observable("")
    internal var modelsDraftApiKey by observable("")
    internal val modelsDraftModels by observableList<DshProviderModel>()
    internal var modelsSaving by observable(false)
    internal var modelsSaveError by observable("")
    internal var modelsDeleteTarget by observable<DshProviderConfig?>(null)
    internal var modelsDeleting by observable(false)
    internal var modelsProtocols by observable<List<String>>(emptyList())
    internal var modelsCustomRevision by observable(0)
    internal val modelsConfiguredProviders by observableList<DshProviderConfig>()
    internal val modelsAddableProviders by observableList<DshProviderConfig>()
    internal var modelsAdding by observable(false)
    internal var modelsPickerVisible by observable(false)
    internal var modelsCustomAdding by observable(false)
    internal var modelsEditorAdvanced by observable(false)
    internal var modelsSavedNotice by observable("")
    internal var modelsCustomRoute by observable("")
    internal var modelsCustomName by observable("")
    internal var modelsCustomBaseUrl by observable("")
    internal var modelsCustomProtocol by observable("")
    internal var modelsCustomApiKey by observable("")
    internal val modelsCustomModels by observableList<DshProviderModel>()
    internal var modelsCustomBusy by observable(false)
    internal var modelsCustomError by observable("")
    internal var commandSheetVisible by observable(false)
    internal var voiceActive by observable(false)
    // ===== 语音输入（按住说话 → 原生语音识别 → 转文字填入输入框）=====
    /** 录音浮层是否可见（按下「按住说话」到松手/取消之间为真）。 */
    internal var voiceRecording by observable(false)
    /** 手指上滑是否已进入取消区间（跟随移动实时更新）。 */
    internal var voiceCancelArmed by observable(false)
    /** 录音过程中的中间识别文本。 */
    internal var voicePartialText by observable("")
    /** 滚动音量窗口（0..1），驱动浮层蓝色方块高度。 */
    internal val voiceWaveformModel = DshVoiceWaveform()
    /** 每次音量采样 +1，驱动波形整体重绘。 */
    internal var voiceWaveformRevision by observable(0)
    /** 录音会话代次，防止衰减定时器跨会话重复运行。 */
    internal var voiceDecayGeneration = 0
    /** 按下点 pageY，用于计算上滑取消。 */
    internal var voiceHoldStartY = 0f
    /** 已松手（等待原生最终结果）。 */
    internal var voiceStopping = false
    /** 已请求取消，结果不入输入框。 */
    internal var voiceCancelRequested = false
    /** 已到达的最终识别文本。 */
    internal var voiceCommittedText = ""
    /** 松手兜底计时是否已排期。 */
    internal var voiceStopFallbackScheduled = false
    /** 音量事件节流时间戳。 */
    internal var voiceLastLevelAt = 0L
    /** 语音转文字结果：待输入框挂载后回填到原生 TextArea。 */
    internal var pendingVoiceDraft: String? = null
    internal var inputView: TextAreaView? = null
    internal var apiKeyInputView: InputView? = null
    internal var streamHandle: DshStreamHandle? = null
    internal val messageScrollerRefs = mutableMapOf<String, ViewRef<ListView<*, *>>>()
    internal val messageRowRefs = mutableMapOf<String, ViewRef<com.tencent.kuikly.core.views.DivView>>()
    internal var historyRequestGeneration = 0
    internal val sessionMessageStates = mutableMapOf<String, ObservableList<DshMessage>>()
    internal val conversationListEpochs = mutableMapOf<String, Int>()
    internal var conversationListEpoch by observable(0)
    internal val sessionMessageReady = mutableSetOf<String>()
    internal val pendingSessionSelections = mutableSetOf<String>()
    internal val localReadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    internal val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    internal val pendingLocalMessageReads = mutableSetOf<String>()
    internal val sessionCacheStates = mutableMapOf<String, DshSessionCacheState>()
    internal var inputFocused = false
    internal var streamingAssistantId by observable("")
    // The root id guards callbacks from an old request; the visible id points
    // at the current text segment between ordered tool cards.
    internal var streamingAssistantRootId = ""
    internal var streamingAssistantSegment = 0
    internal var streamingSourceSeq: Int? = null
    // Last completed assistant when the current prompt was sent. Resync must
    // not graft the new stream onto that bubble.
    internal var streamingTurnAnchorAssistantId = ""
    internal var streamingReasoningId = ""
    internal var streamingReasoningContent = ""
    internal val pendingAssistantDelta = StringBuilder()
    internal var assistantFlushScheduled = false
    internal var scrollSettleGeneration = 0
    internal var followListTail = true
    internal val connectionCoordinator = DshConnectionCoordinator()
    internal val webDisclosureStates = mutableMapOf<String, Boolean>()
    internal val webJsonNodeStates = mutableMapOf<String, Boolean>()
    internal var webDisclosureRevision by observable(0)
    internal var attachmentRevision by observable(0)
    internal var previewImageUrl by observable<String?>(null)
    internal val cachedAttachmentDataUrls = mutableMapOf<String, String>()
    internal val pendingAttachmentReads = mutableSetOf<String>()
    // ===== Task 3 附件：输入区图片草稿（仅内存，不落盘）与 Host 下发限额 =====
    internal val pendingImages by observableList<DshPendingImage>()
    // 通用文件草稿：旧 Host 无文件 content 类型，发送前经 host-plugin 落盘并写入 prompt handle。
    internal val pendingFiles by observableList<DshPendingFile>()
    internal var attachmentEpoch by observable(0)
    internal var imageLimits by observable<DshImageLimits?>(null)
    internal var queueDockExpanded by observable(false)
    internal var queueItems by observableList<DshQueueItem>()
    internal var queueActionBusy by observable(false)
    internal var jobItems by observableList<DshJobItem>()
    internal var jobsPanelExpanded by observable(false)
    internal var jobsNow by observable(0L)
    internal var jobsClockScheduled by observable(false)
    internal var liveJobItems by observableList<DshJobItem>()
    internal var workspaceGroups by observableList<DshWorkspaceGroup>()
    internal val skills by observableList<DshSkill>()
    internal var goalSnapshot by observable<DshGoalSnapshot?>(null)
    internal var goalActionBusy by observable(false)
    internal var goalActionError by observable("")
    internal var queueEditingId by observable("")
    internal var queueEditingText by observable("")
    internal var sessionRunning by observable(false)
    internal var turnElapsedMs by observable(0L)
    internal var turnStatusMark: TimeMark? = null
    internal var turnStatusTickerGeneration = 0
    internal var turnStatusClockBucket = -1L
    // ===== 新建会话-工作区选择（最近的文件夹 / 添加文件夹） =====
    internal var workspacePickerVisible by observable(false)
    internal var workspacePickerScreen by observable(DshWorkspacePickerScreen.RECENT)
    internal var workspacePickerBusy by observable(false)
    internal var workspacePickerError by observable("")
    internal var workspaceAddPath by observable("")
    internal var workspaceAddHome by observable("")
    internal var workspaceAddBusy by observable(false)
    internal var workspaceAddNewName by observable("")
    internal val workspaceAddEntries by observableList<DshDirectoryEntry>()
    // 「最近的文件夹」只列真实工作区（排除「未分组」占位），供 vfor 直接迭代。
    internal val workspacePickerFolders by observableList<DshWorkspaceGroup>()
    internal var workspacePickerGeneration = 0
    internal var workspaceRenameTargetId by observable("")
    internal var workspaceRenameDraft by observable("")
    internal var workspaceDeleteTargetId by observable("")
    internal var workspaceActionBusy by observable(false)
    internal var workspaceActionError by observable("")
    // ===== 会话 overflow menu 与会话管理动作 =====
    internal var overflowMenuVisible by observable(false)
    // overflow menu 的锚点（点击会话行 ⋯ 的屏幕坐标）；-1 表示无锚点，回退到默认位置。
    internal var overflowAnchorX by observable(-1f)
    internal var overflowAnchorY by observable(-1f)

    internal val overlayBackCallback = object : BackPressCallback() {
        override fun handleOnBackPressed() {
            when {

                expandedPayload != null -> closeExpandedModal()
                personalizationPageVisible -> closePersonalizationPage()
                exportSelectMode -> cancelExportSelection()
                settingsChoiceKind.isNotEmpty() -> { if (!settingsChoiceBusy) settingsChoiceKind = "" }
                pluginInventoryVisible -> handlePluginInventoryBack()
                readableExportDialogVisible -> closeReadableExportDialog()
                selectTextModalVisible -> closeSelectTextModal()
                sessionDeleteVisible -> { if (!sessionDeleteBusy) sessionDeleteVisible = false }
                sessionArchiveVisible -> { if (!sessionArchiveBusy) sessionArchiveVisible = false }
                sessionRenameVisible -> cancelSessionRename()
                archiveListVisible -> closeArchiveList()
                agentModePickerVisible -> agentModePickerVisible = false
                riskConfirmVisible -> riskConfirmVisible = false
                permissionPickerVisible -> permissionPickerVisible = false
                modelPickerVisible -> { if (modelEffortsVisible) modelEffortsVisible = false else modelPickerVisible = false }
                commandSheetVisible -> commandSheetVisible = false
                overflowMenuVisible -> closeOverflowMenu()
                workspacePickerVisible -> onWorkspacePickerBack()
                credentialSetupVisible -> closeCredentialSettings()
                sshSettingsVisible -> updateSshSettingsVisibility(false)
                modelsDeleteTarget != null -> { if (!modelsDeleting) modelsDeleteTarget = null }
                modelsPageVisible -> closeModelsPage()
                settingsPageVisible -> closeSettingsPage()

                sessionSearchVisible -> closeSessionSearch()
                sessionDrawerVisible -> closeSessionDrawer()
                else -> acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
            }
        }
    }

    internal var sessionRenameVisible by observable(false)
    internal var sessionActionTargetId by observable("")
    internal var sessionRenameDraft by observable("")
    internal var sessionRenameBusy by observable(false)
    internal var sessionRenameError by observable("")
    internal var sessionArchiveVisible by observable(false)
    internal var sessionArchiveBusy by observable(false)
    internal var sessionArchiveError by observable("")
    internal var archiveListVisible by observable(false)
    internal var archiveListLoading by observable(false)
    internal var archiveListError by observable("")
    internal var archiveOpeningId by observable("")
    internal val archivedSessions by observableList<DshSession>()
    internal var archiveRequestGeneration = 0L
    internal var archiveSearch by observable("")
    internal var archiveProjectFilter by observable("")
    internal var archiveSort by observable(DshArchiveSort.UPDATED)
    internal var archiveMenu by observable("")
    internal var archiveBusy by observable(false)
    internal var archiveNotice by observable("")
    internal var archiveConfirm by observable<DshArchiveConfirm?>(null)
    internal val archiveGroups by observableList<DshWorkspaceGroup>()
    internal val archiveProjectOptions by observableList<DshArchiveProjectOption>()
    internal var sessionCreatedAt by observable<Map<String, Long>>(emptyMap())
    // ===== 全屏会话搜索 =====
    /** 搜索界面是否展示（由抽屉顶部搜索入口打开）。 */
    internal var sessionSearchVisible by observable(false)
    /** 搜索框原生文本：非响应式，只喂给 Input 的 text()，避免每次按键重设文本与原生输入互相覆盖。 */
    internal var sessionSearchInput = ""
    /** 搜索框是否有内容，驱动结果列表与清除按钮。 */
    internal var sessionSearchActive by observable(false)
    internal val sessionSearchHits by observableList<DshSessionSearchHit>()
    internal var sessionSearchInputView: InputView? = null
    // ===== 会话抽屉长按拖拽排序 =====
    internal var drawerDrag by observable(DshDrawerDrag())
    internal var sessionManualOrder by observable<Map<String, List<String>>>(emptyMap())
    internal var workspaceManualOrder by observable<List<String>>(emptyList())
    internal var drawerOrderScope = ""
    // ===== 抽屉视图选项（对齐电脑端 WorkspaceBrowser）=====
    internal var drawerViewGroupBy by observable(DSH_DRAWER_GROUP_WORKSPACE)
    internal var drawerViewOrderBy by observable(DSH_DRAWER_ORDER_UPDATED)
    internal var drawerViewOptionsVisible by observable(false)
    internal var drawerViewOptionsX by observable(-1f)
    internal var drawerViewOptionsY by observable(-1f)
    internal var dragOriginPageY = 0f
    internal var prefsModule: SharedPreferencesModule? = null
    internal var catalogRequestGeneration = 0L
    internal var sessionDeleteVisible by observable(false)
    internal var sessionDeleteBusy by observable(false)
    internal var sessionDeleteError by observable("")
    internal var pendingApproval by observable<DshPendingApproval?>(null)
    internal var pendingQuestion by observable<DshPendingQuestion?>(null)
    internal var interactionBusy by observable(false)
    internal val selectedQuestionOptions by observableList<String>()
    internal var questionCustom by observable("")
    internal var questionIndex by observable(0)
    internal var questionError by observable("")
    internal var questionHasSelection by observable(false)
    internal var messageActionsMessage by observable<DshMessage?>(null)
    internal var messageForkBusy = false
    internal var messageForkVersion = 0
    /** 附件上传阶段的发送防重入：上传完成后用首次点击的快照继续发送。 */
    internal var sendInFlight = false
    internal var messageActionsX by observable(0f)
    internal var messageActionsY by observable(0f)
    internal var menuBlurUri by observable("")
    // 「选择文本」弹窗：以单个可选中文本节点承载完整正文，供原生选区复制
    internal var selectTextModalVisible by observable(false)
    internal var selectTextModalContent by observable("")
    internal val questionDrafts = mutableMapOf<Int, DshQuestionDraft>()

    /** connectionLabel 变化时更新胶囊可见性，就绪态延迟 3s 后淡出隐藏 */
    internal fun onConnectionLabelChanged(label: String) {
        if (label == "正在生成" || label == "正在聆听") {
            if (connectionCapsuleVisible) {
                connectionCapsuleVisible = false
                connectionCapsuleFadeOut = false
            }
            return
        }
        if (!isConnectionReadyLabel(label)) {
            connectionCapsuleVisible = true
            connectionCapsuleFadeOut = false
        } else if (connectionCapsuleVisible) {
            connectionCapsuleVersion++
            val version = connectionCapsuleVersion
            setTimeout(pagerId, CONNECTION_CAPSULE_HOLD_MS) {
                if (version == connectionCapsuleVersion && isConnectionReadyLabel(connectionLabel)) {
                    connectionCapsuleFadeOut = true
                    setTimeout(pagerId, CONNECTION_CAPSULE_FADE_MS) {
                        if (version == connectionCapsuleVersion) {
                            connectionCapsuleVisible = false
                            connectionCapsuleFadeOut = false
                        }
                    }
                }
            }
        }
    }

    override fun created() {
        super.created()
        val databaseDir = pageData.params.optString("databaseDir")
        exportDir = pageData.params.optString("exportDir").ifEmpty { databaseDir }
        if (databaseDir.isNotEmpty()) {
            localStore = runCatching {
                createDshLocalStore("$databaseDir/dsh.db")
            }.getOrNull()
            // 日志服务为应用级唯一实例：主页不创建/关闭写入器，重建时复用同一实例。
            DshLogService.ensureStarted(databaseDir)
        }
        registerLogPageNotifications()
        reportLastCrashIfAny()
        // 恢复本地「对话展示」偏好（个性化子页面）。
        runCatching {
            val prefs = acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
            prefsModule = prefs
            chatProcessMode = dshProcessDisplayFromValue(prefs.getItem(DSH_PREF_PROCESS_DISPLAY).orEmpty())
            chatExpandInModal = prefs.getItem(DSH_PREF_EXPAND_MODAL) == "1"
            chatShowConnectors = prefs.getItem(DSH_PREF_SHOW_CONNECTORS) != "0"
            chatShowResultCards = prefs.getItem(DSH_PREF_SHOW_RESULT_CARDS) != "0"
        }
        connectionMode = when (pageData.params.optString("connectionMode")) {
            "relay" -> DshConnectionMode.RELAY
            "ssh", "remote" -> DshConnectionMode.SSH
            else -> DshConnectionMode.RELAY
        }
        remoteProfileId = pageData.params.optString("profileId").ifEmpty { DshSessionScope.DEFAULT_REMOTE_PROFILE_ID }
        loadSshConfig()
        restoreCachedSessions()
        if (sessions.isEmpty()) {
            sessionMessageStates[activeSessionId] = messages
            ensureConversationPanel(activeSessionId)
        }
        ensureConversationPanel(activeSessionId)
        preloadAllSessionMessages()
        loadApiKeyAsync()
        setTimeout(pagerId, SESSION_CACHE_WARM_START_DELAY_MS) {
            warmRecentSessionCache(scrollToEndAfterLoad = false)
        }
        postToUi { startConnection() }
        getBackPressHandler().addCallback(overlayBackCallback)
        DshStreamLog.i("app.page.created page=home mode=$connectionMode")
    }

    override fun themeDidChanged(data: com.tencent.kuikly.core.nvi.serialization.json.JSONObject) {
        super.themeDidChanged(data)
    }

    override fun pageDidDisappear() {
        super.pageDidDisappear()
        DshStreamLog.i("app.page.hidden page=home")
        DshLogService.flush()
    }

    override fun pageDidAppear() {
        super.pageDidAppear()
        DshStreamLog.i("app.page.visible page=home")
        // HiAppEvent can deliver the previous crash after the initial page creation.
        reportLastCrashIfAny()
    }

    override fun pageWillDestroy() {
        pageAlive = false
        if (voiceRecording) {
            bridgeModule.cancelVoiceRecognition()
            voiceRecording = false
        } else {
            bridgeModule.releaseVoiceRecognition()
        }
        stopCurrentEngine()
        DshStreamLog.i("app.page.destroyed page=home")
        // 只刷盘，不停止/清空应用级日志服务：主页重建后仍复用同一写入器与序号。
        DshLogService.flush()
        unregisterLogPageNotifications()
        localReadScope.cancel()
        super.pageWillDestroy()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            // ===== 根容器 =====
            // 整页的根 View：纵向布局撑满剩余空间，背景色 BG，顶部留出系统状态栏高度。
            View {
                attr {
                    flex(1f)
                    flexDirectionColumn()
                    backgroundColor(this@DshHomePage.themeColors.bgBase)
                    paddingTop(pagerData.statusBarHeight)
                }

                this@DshHomePage.bodyTopBar().invoke(this)
                this@DshHomePage.bodyMainContent().invoke(this)
                this@DshHomePage.bodySessionDrawer().invoke(this)
                this@DshHomePage.bodySessionSearchAndArchive().invoke(this)
                this@DshHomePage.bodyPickers().invoke(this)
                this@DshHomePage.bodySettingsOverlays().invoke(this)
                this@DshHomePage.bodySettingsChoiceAndCredentials().invoke(this)
                this@DshHomePage.bodyWorkspaceDialogs().invoke(this)
                this@DshHomePage.bodyMessageOverlays().invoke(this)
            }
        }
    }


    override fun viewDidLoad() {
        super.viewDidLoad()
        addTaskWhenPagerUpdateLayoutFinish {
            refreshMountedSessionRenderTrees()
        }
    }







    internal fun refreshPendingSessionIds() {
        val remote = remoteRepo
        pendingSessionIds = sessions.filter { session ->
            val pending = remote?.pendingInteractions(session.id)
            pending?.first != null || pending?.second != null
        }.map { it.id }.toSet()
    }












    internal fun openCredentialSettings() {
        dismissKeyboard()
        commandSheetVisible = false
        //closeSessionDrawer()
        credentialSetupTitle = if (sshMode) "修改电脑端 DSH 的 API Key" else "设置 DeepSeek API Key"
        credentialSetupError = ""
        apiKeyDraft = pendingApiKey
        updateCredentialSetupVisibility(true)
    }

    internal fun openSettingsPage() {
        dismissKeyboard()
        commandSheetVisible = false
        closeSessionDrawerImmediately()
        reloadSettings()
        loadHostVersion()
        if (isRemoteHost) reloadModelsSettings(showLoading = false)
        settingsPageVisible = true
    }

    internal fun closeSettingsPage() {
        settingsPageVisible = false
        settingsChoiceKind = ""
        settingsChoiceBusy = false
    }

    // ===== 个性化「对话展示」 =====
    internal fun openPersonalizationPage() {
        dismissKeyboard()
        personalizationPageVisible = true
    }

    internal fun closePersonalizationPage() {
        personalizationPageVisible = false
    }

    internal fun applyChatProcessMode(mode: DshProcessDisplayMode) {
        chatProcessMode = mode
        runCatching {
            acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
                .setString(DSH_PREF_PROCESS_DISPLAY, dshProcessDisplayValue(mode))
        }
        remountConversationList(activeSessionId)
    }

    internal fun applyChatExpandInModal(enabled: Boolean) {
        chatExpandInModal = enabled
        runCatching {
            acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
                .setString(DSH_PREF_EXPAND_MODAL, if (enabled) "1" else "0")
        }
        remountConversationList(activeSessionId)
    }

    internal fun applyChatShowConnectors(enabled: Boolean) {
        chatShowConnectors = enabled
        runCatching {
            acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
                .setString(DSH_PREF_SHOW_CONNECTORS, if (enabled) "1" else "0")
        }
        remountConversationList(activeSessionId)
    }

    internal fun applyChatShowResultCards(enabled: Boolean) {
        chatShowResultCards = enabled
        runCatching {
            acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
                .setString(DSH_PREF_SHOW_RESULT_CARDS, if (enabled) "1" else "0")
        }
        remountConversationList(activeSessionId)
    }

    internal fun openExpandedModal(payload: DshExpandedPayload) {
        expandedPayload = payload
    }

    internal fun closeExpandedModal() {
        expandedPayload = null
    }

    /** 设置页「模型」摘要：已配置的提供方数量。 */









    internal fun removeCustomModel(index: Int) {
        if (index in 0 until modelsCustomModels.size) modelsCustomModels.removeAt(index)
    }








    internal fun reloadSettings(showLoading: Boolean = true) {
        // 保存后的回读只更新数据，避免加载提示插入列表导致滚动位置跳动。
        if (showLoading) {
            settingsLoading = true
            settingsError = ""
        }
        val repo = repository
        if (repo == null) {
            settingsLoading = false
            if (showLoading) settingsError = "未连接电脑端"
            return
        }
        repo.describeSettings({
            settingsSnapshot = it
            settingsError = ""
            settingsLoading = false
        }, {
            settingsLoading = false
            if (showLoading) settingsError = it
            else bridgeModule.toast("设置刷新失败：$it")
        })
    }

    internal fun loadHostVersion() {
        val repo = repository ?: return
        repo.loadHostVersion({ hostVersion = it }, { })
    }


    internal fun openAgentModePicker(title: String = "选择模式") {
        agentModePickerTitle = title
        if (agentPresetOptions.isEmpty()) {
            val repo = repository
            repo?.loadAgentPresets({
                agentPresetOptions.clear()
                agentPresetOptions.addAll(it)
                agentModePickerVisible = true
            }, {
                agentModePickerVisible = true
            })
        } else {
            agentModePickerVisible = true
        }
    }

    internal fun openSettingsChoice(kind: String, title: String) {
        if (settingsChoiceBusy) return
        settingsChoiceTitle = title
        settingsChoiceOptions.clear()
        when (kind) {
            "permission" -> settingsChoiceOptions.addAll(settingsSnapshot.permissionChoices)
            "locale" -> {
                settingsChoiceOptions.add(DshSettingsChoice("zh", "简体中文"))
                settingsChoiceOptions.add(DshSettingsChoice("en", "English"))
            }
            "theme" -> {
                settingsChoiceOptions.add(DshSettingsChoice("system", "跟随系统"))
                settingsChoiceOptions.add(DshSettingsChoice("sunrise-sunset", "日出日落"))
                settingsChoiceOptions.add(DshSettingsChoice("dark", "深色"))
                settingsChoiceOptions.add(DshSettingsChoice("light", "浅色"))
            }
        }
        settingsChoiceKind = kind
    }

    internal fun applySettingsChoice(choice: DshSettingsChoice) {
        val kind = settingsChoiceKind
        if (kind.isEmpty() || settingsChoiceBusy) return
        settingsChoiceKind = ""
        if (kind == "theme") {
            // 本地外观不依赖电脑连接；同步失败也保留移动端选择。
            runCatching {
                acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
                    .setString(DshThemeManager.PREF_KEY_THEME_MODE, choice.value)
            }
            DshThemeManager.applyPreference(choice.value)
        }
        settingsChoiceBusy = true
        val repo = repository
        if (repo == null) {
            settingsChoiceBusy = false
            if (kind != "theme") bridgeModule.toast("未连接电脑端")
            return
        }
        when (kind) {
            "permission" -> repo.updateSetting(
                "permission",
                JSONObject().apply { put("defaultPreset", choice.value) },
                settingsSnapshot.permissionRevision,
                {
                    settingsChoiceBusy = false
                    reloadSettings(showLoading = false)
                },
                {
                    settingsChoiceBusy = false
                    bridgeModule.toast("权限设置失败：$it")
                },
            )
            "locale" -> repo.updateSetting(
                "locale",
                JSONObject().apply { put("preference", choice.value) },
                settingsSnapshot.localeRevision,
                {
                    settingsChoiceBusy = false
                    reloadSettings(showLoading = false)
                },
                {
                    settingsChoiceBusy = false
                    bridgeModule.toast("语言设置失败：$it")
                },
            )
            "theme" -> {
                settingsChoiceBusy = false
                // 日出日落为移动端本地模式，电脑端不支持该偏好，不做同步。
                if (choice.value == "sunrise-sunset") return
                // 顺带同步电脑端外观（失败仅提示，不影响移动端）
                repo.updateSetting(
                    "ui-theme",
                    JSONObject().apply { put("preference", choice.value) },
                    settingsSnapshot.themeRevision,
                    { reloadSettings(showLoading = false) },
                    { bridgeModule.toast("外观同步电脑端失败：$it") },
                )
            }
            else -> settingsChoiceBusy = false
        }
    }

    // 首页权限弹窗的选择：与设置页「工作区权限」走同一个全局设置通道（settings.update），
    // 选项已优先取 host 动态枚举；未连接或 host 不支持设置时仅本地记住并提示。
    internal fun openPermissionPicker() {
        dismissKeyboard()
        commandSheetVisible = false
        permissionPickerVisible = true
        // 快照未加载（通常还没打开过设置页）时预热拉取，弹窗选项展示 host 真实预设。
        if (repository != null && settingsSnapshot.permissionChoices.isEmpty()) {
            reloadSettings(showLoading = false)
        }
    }

    internal fun applyPermissionPreset(option: DshPermissionOption) {
        val repo = repository ?: run {
            bridgeModule.toast("未连接电脑端，权限已本地记住，连接后可在设置页同步")
            return
        }
        if (settingsChoiceBusy) return
        if (settingsSnapshot.permissionChoices.isEmpty()) {
            // 设置快照尚未加载：先拉取一次 host settings（拿到 revision），成功后再写入。
            settingsChoiceBusy = true
            repo.describeSettings({
                settingsChoiceBusy = false
                settingsSnapshot = it
                pushPermissionPreset(repo, option, it.permissionRevision)
            }, {
                settingsChoiceBusy = false
                bridgeModule.toast("获取权限设置失败：$it")
            })
            return
        }
        pushPermissionPreset(repo, option, settingsSnapshot.permissionRevision)
    }

    internal fun pushPermissionPreset(repo: DshRepository, option: DshPermissionOption, revision: Int) {
        settingsChoiceBusy = true
        repo.updateSetting(
            "permission",
            JSONObject().apply { put("defaultPreset", option.value) },
            revision,
            {
                settingsChoiceBusy = false
                reloadSettings(showLoading = false)
            },
            {
                settingsChoiceBusy = false
                bridgeModule.toast("权限设置失败：$it")
            },
        )
    }













    internal fun closeCredentialSettings() {
        dismissKeyboard()
        updateCredentialSetupVisibility(false)
    }

    internal fun updateCredentialSetupVisibility(visible: Boolean) {
        credentialSetupVisible = visible
        if (pageData.isAndroid || pageData.isIOS) {
            bridgeModule.setSystemBarsDimmed(visible)
        }
    }

    internal fun composerFolderLabel(): String {
        val session = sessions.firstOrNull { it.id == activeSessionId }
        val cwd = session?.cwd
        if (cwd.isNullOrEmpty()) return "文件夹（可选）"
        // 只显示绝对路径最后一段（兼容 Windows 反斜杠与 Unix 斜杠，过滤空段），
        // 完整路径仍在右侧「会话详情面板」展示，不丢失信息。
        val leaf = cwd.split("\\", "/").lastOrNull { it.isNotBlank() } ?: return cwd
        return leaf
    }


    internal fun loadHistory(
        sessionId: String,
        scrollToEndAfterLoad: Boolean = true,
    ) {
        ++historyRequestGeneration

        // Show the selected session immediately. The Host history request is
        // remote and can take a moment, so keeping the previous list here
        // makes a session switch look stuck.
        messages = sessionMessageState(
            sessionId,
            scrollToEndAfterLoad = scrollToEndAfterLoad,
        )
        ensureConversationPanel(sessionId)
        fetchHostHistory(sessionId, scrollToEndAfterLoad)
    }

    internal fun fetchHostHistory(
        sessionId: String,
        scrollToEndAfterLoad: Boolean = true,
    ) {
        if (isRemoteHost) {
            loadSkills(sessionId)
            loadWebTimeline(sessionId, scrollToEndAfterLoad)
            return
        }

        val requestGeneration = historyRequestGeneration
        val hostRepository = repository ?: return
        hostRepository.loadHistory(sessionId, { loaded ->
            if (requestGeneration != historyRequestGeneration || activeSessionId != sessionId) return@loadHistory
            sessionMessageReady.add(sessionId)
            sessionCacheStates[sessionId] = DshSessionCacheState.SYNCED
            replaceMessagesIfChanged(loaded)
            runCatching { localStore?.replaceMessages(activeConnectionId, sessionId, loaded) }
            completePendingSessionSelection(sessionId)
            realizeSessionAfterData(sessionId, scrollToEndAfterLoad)
        }, { error ->
            if (requestGeneration != historyRequestGeneration || activeSessionId != sessionId) return@loadHistory
            if (messages.isNotEmpty()) {
                if (isRemoteHost) {
                    sessionCacheStates[sessionId] = DshSessionCacheState.SYNC_FAILED
                    connectionLabel = "远程历史同步失败 · 已显示缓存"
                } else {
                    connectionLabel = "内核连接失败 · 已显示缓存"
                }
            } else {
                messages.add(DshMessage("history-error", DshMessageRole.ERROR, error))
            }
        })
    }

    internal fun loadWebTimeline(
        sessionId: String,
        scrollToEndAfterLoad: Boolean = true,
        forceReplace: Boolean = false,
        afterApply: () -> Unit = {},
    ) {
        val hostRepository = remoteRepo ?: return
        val requestVersion = ++timelineReadVersion
        val scopeId = activeConnectionId
        fun current() = pageAlive && repository === hostRepository && activeConnectionId == scopeId &&
            activeSessionId == sessionId && requestVersion == timelineReadVersion
        hostRepository.loadWebTimeline(sessionId, { items ->
            if (!current()) return@loadWebTimeline
            // 先收集所有 attachmentIds，加载 dataUrl 后再创建消息（vforLazy 不响应列表变化，必须在消息入列时就有 imagePreviews）
            val allAttIds = items.flatMap { item ->
                when (item.kind) {
                    DshWebTimelineItem.Kind.USER -> item.attachmentIds
                    DshWebTimelineItem.Kind.IMAGE -> listOfNotNull(item.attachmentId)
                    else -> emptyList()
                }
            }.distinct()
            val pendingAttIds = allAttIds.filter { cachedAttachmentDataUrls[it] == null && pendingAttachmentReads.add(it) }
            val applyTimeline = apply@{
                if (!current()) return@apply
                val projected = items.map { item ->
                when (item.kind) {
                    DshWebTimelineItem.Kind.USER -> {
                        val loadedPreviews = item.attachmentIds.mapNotNull { cachedAttachmentDataUrls[it] }
                        DshMessage(item.key, DshMessageRole.USER, item.text, attachmentIds = item.attachmentIds, imagePreviews = loadedPreviews)
                    }
                    DshWebTimelineItem.Kind.ASSISTANT -> DshMessage(item.key, DshMessageRole.ASSISTANT, item.text)
                    DshWebTimelineItem.Kind.REASONING -> DshMessage(
                        item.key,
                        DshMessageRole.ASSISTANT,
                        item.text,
                        isReasoning = true,
                    )
                    DshWebTimelineItem.Kind.IMAGE -> DshMessage(
                        item.key,
                        DshMessageRole.ASSISTANT,
                        "",
                        attachmentId = item.attachmentId,
                        imagePreviews = item.attachmentId?.let { cachedAttachmentDataUrls[it] }?.let { listOf(it) } ?: item.imagePreviews,
                    )
                    DshWebTimelineItem.Kind.UNKNOWN_BLOCK -> DshMessage(
                        item.key,
                        DshMessageRole.TOOL,
                        item.text,
                        toolName = "未知内容块",
                        toolCardType = DshToolCardType.JSON,
                    )
                    DshWebTimelineItem.Kind.ERROR -> DshMessage(item.key, DshMessageRole.ERROR, item.text)
                    DshWebTimelineItem.Kind.CONTEXT -> DshMessage(
                        item.key,
                        DshMessageRole.TOOL,
                        item.text,
                        toolName = item.sourceLabel,
                        isContextInjection = true,
                        contextBody = item.text,
                        contextForm = item.source?.optString("form").orEmpty(),
                        contextCatalog = item.source?.let(::contextCatalogEntries).orEmpty(),
                        contextSections = item.source?.let(::contextSections).orEmpty(),
                        contextRecalls = item.source?.let(::contextRecalls).orEmpty(),
                        contextInstructions = item.source?.let(::contextInstructions).orEmpty(),
                        contextRelaySender = item.source?.let(::contextRelaySender).orEmpty(),
                    )
                    DshWebTimelineItem.Kind.TOOL -> item.remoteTool?.toRemoteMessage(item.key) ?: DshMessage(
                        item.key,
                        DshMessageRole.TOOL,
                        item.cardBody.ifEmpty { listOfNotNull(item.input, item.output).joinToString("\n\n") },
                        toolName = item.cardTitle.ifEmpty { item.toolName ?: "工具" },
                        toolCardType = item.cardType,
                        toolRunning = item.running,
                        toolError = item.error != null,
                        toolStopped = item.stopped,
                    )
                }.copy(readableContent = item.readableContent, sourceSeq = item.sourceSeq)
                }
                sessionMessageReady.add(sessionId)
                replaceMessagesIfChanged(projected, force = forceReplace && !isLocalPromptInFlight())
                if (projected.isNotEmpty()) {
                    persistMessages(sessionId)
                    sessionCacheStates[sessionId] = DshSessionCacheState.SYNCED
                }
                completePendingSessionSelection(sessionId)
                realizeSessionAfterData(sessionId, scrollToEndAfterLoad)
                afterApply()
            }
            if (pendingAttIds.isEmpty()) {
                applyTimeline()
            } else {
                var remaining = pendingAttIds.size
                var applied = false
                val tryApply = {
                    if (!applied && current()) {
                        applied = true
                        attachmentRevision += 1
                        applyTimeline()
                    }
                }
                pendingAttIds.forEach { attId ->
                    hostRepository.loadAttachment(sessionId, attId) { dataUrl, error ->
                        if (!current()) return@loadAttachment
                        if (error == null && dataUrl != null) {
                            cachedAttachmentDataUrls[attId] = dataUrl
                        }
                        pendingAttachmentReads.remove(attId)
                        remaining -= 1
                        if (remaining <= 0) tryApply()
                    }
                }
                // 兜底超时：15 秒后即使有回调丢失也强制 apply，避免会话一直空白
                setTimeout(pagerId, 15000) {
                    if (!applied && activeSessionId == sessionId) {
                        DshStreamLog.log(LogLevel.WARN, "ui.att-timeout", "attachment preload timeout, force apply session=$sessionId remaining=$remaining", sessionId, null)
                        tryApply()
                    }
                }
            }
        }, { error ->
            DshStreamLog.w("ui.history-fail session=$sessionId error='${DshStreamLog.preview(error)}'")
            if (!current()) return@loadWebTimeline
            sessionCacheStates[sessionId] = DshSessionCacheState.SYNC_FAILED
            bridgeModule.toast("历史读取失败：$error，可重新进入会话重试")
            if (forceReplace && !sessionRunning && (streaming || stopButtonVisible)) {
                finishStreamingFromHistory(sessionId)
            }
            afterApply()
        }, ::current)
    }

    internal fun resyncStreamingWithHost(sessionId: String, reason: String) {
        if (!isRemoteHost || sessionId != activeSessionId) return
        // A local prompt is already painting this turn. Reloading the web
        // timeline remounts every markdown bubble and delays the first token.
        if (reason == "host-session-running" && isLocalPromptInFlight()) {
            return
        }
        DshStreamLog.i("ui.resync.begin reason=$reason session=$sessionId running=$sessionRunning")
        if (sessionRunning) {
            loadWebTimeline(sessionId, scrollToEndAfterLoad = true, forceReplace = true) {
                resumeStreamingFromHistory(sessionId, reason)
            }
        } else {
            val forceReplace = streaming || stopButtonVisible
            loadWebTimeline(sessionId, scrollToEndAfterLoad = true, forceReplace = forceReplace) {
                finishStreamingFromHistory(sessionId)
                connectionLabel = "已连接"
                DshStreamLog.i("ui.resync.settled reason=$reason session=$sessionId messages=${messages.size}")
            }
        }
    }

    internal fun isLocalPromptInFlight(): Boolean =
        streaming && streamingAssistantRootId.isNotEmpty()

    internal fun rebindStreamingToHistoryTail(): Boolean {
        val live = dshHistoryTailToResume(messages.toList(), streamingTurnAnchorAssistantId)
            ?: return false
        streamingAssistantId = live.id
        streamingAssistantRootId = live.id
        streamingAssistantSegment = 0
        streamingSourceSeq = live.sourceSeq
        streamingAssistantContent = live.content
        return true
    }

    internal fun finishStreamingFromHistory(sessionId: String) {
        if (!(streaming || stopButtonVisible)) return
        flushAssistantDelta()
        if (rebindStreamingToHistoryTail()) {
            settleStreamingMessage(DshMessageRole.ASSISTANT, streamingAssistantContent)
        } else {
            releaseStreamingUi()
        }
        persistMessages(sessionId)
        (remoteRepo)?.detachLiveStreams(sessionId)
        streamHandle = null
    }

    internal fun resumeStreamingFromHistory(sessionId: String, reason: String) {
        if (sessionId != activeSessionId) return
        val rebound = rebindStreamingToHistoryTail()
        if (rebound) {
            streaming = true
            stopButtonVisible = true
            connectionLabel = "正在生成"
            val index = messages.indexOfFirst { it.id == streamingAssistantId }
            if (index >= 0) {
                messages[index] = messages[index].copy(streaming = true)
            }
        } else {
            if (streamingAssistantRootId.isEmpty()) {
                streamingAssistantRootId = "assistant-adopted-${messages.size}"
            }
            val liveStillPresent = streamingAssistantId.isNotEmpty() &&
                messages.any { it.id == streamingAssistantId }
            if (!liveStillPresent) {
                val kept = streamingAssistantContent + pendingAssistantDelta.toString()
                pendingAssistantDelta.setLength(0)
                streamingAssistantId = ""
                streamingAssistantSegment = 0
                streamingAssistantContent = ""
                if (kept.isNotEmpty()) {
                    ensureStreamingAssistantSegment()
                    streamingAssistantContent = kept
                    updateStreamingMessage(kept, streaming = true)
                }
            }
            streaming = true
            stopButtonVisible = true
            connectionLabel = "正在生成"
        }
        attachAdoptedLiveStream(sessionId)
        syncTurnStatusTicker()
        DshStreamLog.i(
            "ui.resync.resume reason=$reason rebound=$rebound id=${streamingAssistantId.ifEmpty { streamingAssistantRootId }} chars=${streamingAssistantContent.length}",
        )
    }

    internal fun attachAdoptedLiveStream(sessionId: String) {
        val hostRepository = remoteRepo ?: return
        streamHandle = hostRepository.adoptLiveStream(
            sessionId = sessionId,
            onDelta = { delta, isReasoning ->
                if (!connectionCoordinator.isActive(connectionMode) || activeSessionId != sessionId) return@adoptLiveStream
                if (isReasoning) {
                    val reasoningId = streamingReasoningId.ifEmpty { "$streamingAssistantRootId-reasoning" }
                    if (streamingReasoningId.isEmpty()) streamingReasoningId = reasoningId
                    queueReasoningDelta(reasoningId, delta)
                } else {
                    if (streamingAssistantRootId.isEmpty()) {
                        streamingAssistantRootId = "assistant-adopted-${messages.size}"
                    }
                    queueAssistantDelta(streamingAssistantRootId, delta)
                }
            },
            onComplete = { result ->
                if (!connectionCoordinator.isActive(connectionMode)) return@adoptLiveStream
                flushAssistantDelta()
                if (streamingAssistantId.isEmpty() && result.isNotEmpty()) {
                    ensureStreamingAssistantSegment()
                }
                val completedContent = streamingAssistantContent.ifEmpty { result }
                settleStreamingMessage(DshMessageRole.ASSISTANT, completedContent)
                persistMessages(sessionId)
                connectionLabel = "已连接"
                streamHandle = null
            },
            onError = { error ->
                if (!connectionCoordinator.isActive(connectionMode)) return@adoptLiveStream
                if (dshIsTransportInterrupt("", error)) {
                    DshStreamLog.i("ui.adopt-interrupt session=$sessionId message='${DshStreamLog.preview(error)}'")
                    return@adoptLiveStream
                }
                flushAssistantDelta()
                ensureStreamingAssistantSegment()
                DshStreamLog.log(LogLevel.ERROR, "ui.error", "ui.error session=$sessionId message='${DshStreamLog.preview(error)}'", sessionId, null)
                settleStreamingMessage(DshMessageRole.ERROR, error)
                persistMessages(sessionId)
                connectionLabel = "已连接"
                streamHandle = null
            },
        )
    }

    internal fun loadSkills(sessionId: String) {
        if (!isRemoteHost) {
            skills.clear()
            return
        }
        val remote = remoteRepo ?: return
        skills.clear()
        remote.loadSkills(sessionId, onSuccess = { loaded ->
            if (!isRemoteHost || activeSessionId != sessionId) return@loadSkills
            skills.clear()
            skills.addAll(loaded)
        })
    }

    internal fun loadAttachment(sessionId: String, attachmentId: String) {
        if (attachmentDataUrl(attachmentId) != null || !pendingAttachmentReads.add(attachmentId)) return
        val hostRepository = remoteRepo ?: return
        hostRepository.loadAttachment(sessionId, attachmentId) { dataUrl, error ->
            if (error != null || dataUrl == null) {
                pendingAttachmentReads.remove(attachmentId)
                return@loadAttachment
            }
            cachedAttachmentDataUrls[attachmentId] = dataUrl
            attachmentRevision += 1
        }
    }

    internal fun showRunningTool(event: DshRawSessionEvent) {
        val payload = runCatching { JSONObject(event.raw) }.getOrNull() ?: return
        val model = DshRemoteToolCallModels.fromLiveCall(payload) ?: return
        val id = "tool-${event.seq}"
        if (messages.any { it.id == id }) return
        // The Host emits tool/call after the assistant block that introduced
        // it. Seal that block before appending its card so the list follows the
        // actual event order instead of grouping all cards at the turn end.
        splitStreamingAssistantBeforeTool()
        messages.add(model.toRemoteMessage(id).copy(sourceSeq = event.seq))
        refreshSessionRenderTree(activeSessionId)
        scrollMessagesToEnd()
    }

    internal fun showContextInjection(event: DshRawSessionEvent) {
        val payload = runCatching { JSONObject(event.raw) }.getOrNull() ?: return
        val data = dshWireEvent(payload).optJSONObject("data") ?: return
        val source = data.optJSONObject("source") ?: return
        if (source.optString("kind") == "user") {
            val content = data.optJSONArray("content") ?: return
            val text = textFromBlocks(content)
            val index = messages.indexOfLast { it.role == DshMessageRole.USER && it.content == text }
            if (index >= 0) messages[index] = messages[index].copy(
                readableContent = DshReadableContent.blocks(content),
                attachmentIds = attachmentIdsFromBlocks(content),
                sourceSeq = event.seq,
            )
            return
        }
        val id = "context-${event.seq}"
        if (messages.any { it.id == id }) return
        val content = data.optJSONArray("content") ?: return
        val text = buildString {
            for (index in 0 until content.length()) {
                val block = content.optJSONObject(index) ?: continue
                if (block.optString("type") == "text") append(block.optString("text"))
            }
        }.trim()
        if (text.isEmpty()) return
        messages.add(DshMessage(
            id = id,
            role = DshMessageRole.TOOL,
            content = text,
            toolName = contextSummary(source),
            isContextInjection = true,
            contextBody = text,
            contextForm = source.optString("form"),
            contextCatalog = contextCatalogEntries(source),
            contextSections = contextSections(source),
            contextRecalls = contextRecalls(source),
            contextInstructions = contextInstructions(source),
            contextRelaySender = contextRelaySender(source),
            sourceSeq = event.seq,
        ))
        scrollMessagesToEnd()
    }

    internal fun showAssistantBlocks(event: DshRawSessionEvent) {
        val payload = runCatching { JSONObject(event.raw) }.getOrNull() ?: return
        val data = dshWireEvent(payload).optJSONObject("data") ?: return
        val blocks = (data.optJSONObject("message") ?: data).optJSONArray("content") ?: return
        if (streaming) {
            streamingSourceSeq = event.seq
            flushAssistantDelta()
            val textIndex = messages.indexOfFirst { it.id == streamingAssistantId }
            if (textIndex >= 0) messages[textIndex] = messages[textIndex].copy(sourceSeq = event.seq)
        }
        for (index in 0 until blocks.length()) {
            val block = blocks.optJSONObject(index) ?: continue
            when (block.optString("type")) {
                "image" -> {
                    val attachmentId = block.optJSONObject("attachment")?.optString("attachmentId").orEmpty()
                    val inlineUrl = inlineImageDataUrl(block)
                    if (attachmentId.isEmpty() && inlineUrl == null) continue
                    val id = "image-${event.seq}-$index"
                    if (messages.none { it.id == id }) {
                        messages.add(DshMessage(
                            id = id,
                            role = DshMessageRole.ASSISTANT,
                            content = "",
                            attachmentId = attachmentId.ifEmpty { null },
                            imagePreviews = listOfNotNull(inlineUrl),
                            readableContent = DshReadableContent.blocks(JSONArray().apply { put(block) }),
                            sourceSeq = event.seq,
                        ))
                    }
                    if (attachmentId.isNotEmpty()) loadAttachment(activeSessionId, attachmentId)
                }
                "text", "reasoning", "tool-call" -> Unit
                else -> {
                    val id = "block-${event.seq}-$index"
                    if (messages.none { it.id == id }) {
                        messages.add(DshMessage(
                            id = id,
                            role = DshMessageRole.TOOL,
                            content = block.toString(),
                            toolName = "未知内容块",
                            toolCardType = DshToolCardType.JSON,
                            sourceSeq = event.seq,
                        ))
                    }
                }
            }
        }
        scrollMessagesToEnd()
    }

    internal fun settleRunningTool(event: DshRawSessionEvent) {
        val payload = runCatching { JSONObject(event.raw) }.getOrNull() ?: return
        val eventData = dshWireEvent(payload).optJSONObject("data") ?: return
        val message = eventData.optJSONObject("message")
        val resultBlock = message?.optJSONArray("content")?.optJSONObject(0)
        val callId = resultBlock?.optString("toolCallId")
            ?: message?.optJSONObject("source")?.optString("callId")
            ?: eventData.optString("callId")
        if (callId.isEmpty()) return
        val index = messages.indexOfFirst { it.role == DshMessageRole.TOOL && it.toolCallId == callId }
        if (index < 0) return
        val previous = messages[index].remoteTool ?: return
        val model = DshRemoteToolCallModels.settleLiveResult(previous, payload) ?: return
        messages[index] = model.toRemoteMessage(messages[index].id).copy(sourceSeq = messages[index].sourceSeq)
    }

    internal fun attachmentDataUrl(attachmentId: String): String? {
        attachmentRevision // Read the reactive revision so image rows rerender after downloads.
        return cachedAttachmentDataUrls[attachmentId]
    }

    internal fun refreshQueueDock() {
        if (!isRemoteHost) {
            queueItems = ObservableList()
            return
        }
        val repository = remoteRepo ?: return
        val items = repository.queue(activeSessionId)
        queueItems = ObservableList(items.toMutableList())
        if (items.isEmpty()) {
            queueDockExpanded = false
            cancelQueueItemEdit()
        } else if (queueEditingId.isNotEmpty() && items.none { it.id == queueEditingId }) {
            cancelQueueItemEdit()
        }
    }

    internal fun refreshJobsPanel() {
        if (!isRemoteHost) {
            jobItems = ObservableList()
            liveJobItems = ObservableList()
            jobsPanelExpanded = false
            return
        }
        val repository = remoteRepo ?: return
        val items = repository.jobs(activeSessionId)
        jobItems = ObservableList(items.toMutableList())
        liveJobItems = ObservableList(dshLiveJobs(items).toMutableList())
        if (liveJobItems.isEmpty()) jobsPanelExpanded = false
        if (jobsPanelExpanded) {
            jobsNow = bridgeModule.currentTimeStamp()
            scheduleJobsClock()
        }
    }

    internal fun toggleJobsPanel() {
        jobsPanelExpanded = !jobsPanelExpanded
        if (jobsPanelExpanded) {
            jobsNow = bridgeModule.currentTimeStamp()
            scheduleJobsClock()
        }
    }

    internal fun scheduleJobsClock() {
        if (!jobsPanelExpanded || jobsClockScheduled || liveJobItems.isEmpty()) return
        jobsClockScheduled = true
        setTimeout(pagerId, 1_000) {
            jobsClockScheduled = false
            if (!jobsPanelExpanded) return@setTimeout
            jobsNow = bridgeModule.currentTimeStamp()
            scheduleJobsClock()
        }
    }
















    internal fun editQueueItem(itemId: String) {
        val item = queueItems.firstOrNull { it.id == itemId } ?: return
        val text = item.text ?: return
        queueDockExpanded = true
        queueEditingId = itemId
        queueEditingText = text
    }

    internal fun saveQueueItem(itemId: String) {
        val repository = remoteRepo ?: return
        val text = queueEditingText.trim()
        if (queueActionBusy || itemId != queueEditingId || text.isEmpty()) return
        queueActionBusy = true
        repository.updateQueue(
            sessionId = activeSessionId,
            itemId = itemId,
            action = JSONObject().apply {
                put("kind", "edit")
                put("content", JSONArray().apply { put(JSONObject().apply { put("type", "text"); put("text", text) }) })
            },
        ) { _, _ ->
            postToUi {
                queueActionBusy = false
                cancelQueueItemEdit()
                refreshQueueDock()
            }
        }
    }

    internal fun cancelQueueItemEdit() {
        queueEditingId = ""
        queueEditingText = ""
    }

    internal fun removeQueueItem(itemId: String) {
        updateQueueItem(itemId, JSONObject().apply { put("kind", "remove") })
    }

    internal fun steerQueueItem(itemId: String) {
        updateQueueItem(itemId, JSONObject().apply { put("kind", "steer") })
    }

    internal fun updateQueueItem(itemId: String, action: JSONObject) {
        val repository = remoteRepo ?: return
        if (queueActionBusy) return
        queueActionBusy = true
        repository.updateQueue(
            sessionId = activeSessionId,
            itemId = itemId,
            action = action,
        ) { _, _ ->
            postToUi {
                queueActionBusy = false
                refreshQueueDock()
            }
        }
    }


    internal fun archiveActiveSession() {
        val repository = remoteRepo ?: return
        repository.archiveSession(activeSessionId) { _, _ ->
            postToUi {
                loadRepository(preferredSessionId = null)
                refreshWorkspaceGroups()
            }
        }
    }

    /** 会话列表按消息时间（updatedAt）从新到旧重排，供加载完成与增量插入后统一调用。 */





    fun openSessionLogs() {
        closeSessionDrawer()
        openLogPage(overflowTargetId())
    }

    fun openDiagnosticLogs() {
        closeSessionDrawer()
        openLogPage("")
    }

    /** 打开统一日志页；logSessionId 仅作为可修改、可清除的初始会话筛选。 */
    internal fun openLogPage(logSessionId: String) {
        dismissKeyboard()
        if (pagerData.platform == "ohos") reportLastCrashIfAny()
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            "dsh_log",
            JSONObject().apply {
                put("pageName", "dsh_log")
                put("sessionId", logSessionId)
                put("exportDir", exportDir)
                put("connectionMode", connectionModeLabel())
                put(DshLogPageContract.KEY_OWNER, pagerId)
                put(DshLogPageContract.KEY_SESSION_TITLES, JSONArray().apply {
                    sessions.forEach { session ->
                        put(JSONObject().apply {
                            put(DshLogPageContract.KEY_SESSION_ID, session.id)
                            put(DshLogPageContract.KEY_SESSION_TITLE, session.title)
                        })
                    }
                })
            },
        )
    }

    internal fun registerLogPageNotifications() {
        val notify = acquireModule<NotifyModule>(NotifyModule.MODULE_NAME)
        logJumpNotifyRef = notify.addNotify(DshLogPageContract.EVENT_JUMP_TO_SESSION) { data ->
            if (data?.optString(DshLogPageContract.KEY_OWNER) != pagerId) return@addNotify
            val sessionId = data?.optString(DshLogPageContract.KEY_SESSION_ID).orEmpty()
            val requestId = data?.optString(DshLogPageContract.KEY_REQUEST).orEmpty()
            jumpToSession(sessionId) { ok, message ->
                notify.postNotify(DshLogPageContract.EVENT_JUMP_RESULT, JSONObject().apply {
                    put(DshLogPageContract.KEY_REQUEST, requestId)
                    put("ok", ok); put("message", message)
                })
            }
        }
    }

    internal fun unregisterLogPageNotifications() {
        val notify = acquireModule<NotifyModule>(NotifyModule.MODULE_NAME)
        logJumpNotifyRef?.let { notify.removeNotify(DshLogPageContract.EVENT_JUMP_TO_SESSION, it) }
        logJumpNotifyRef = null
    }

    internal fun jumpToSession(sessionId: String, isCurrent: () -> Boolean = { true }, onResult: (Boolean, String) -> Unit) {
        val remote = remoteRepo
        if (sessionId.isBlank() || remote == null) { onResult(false, "当前未连接到 Host"); return }
        val expectedConnection = activeConnectionId
        remote.loadSessions({ available ->
            if (!pageAlive || !isCurrent()) return@loadSessions
            if (repository !== remote || activeConnectionId != expectedConnection) { onResult(false, "连接已切换，请重新打开日志页"); return@loadSessions }
            val target = available.firstOrNull { it.id == sessionId }
            if (target == null) { onResult(false, "Host 中已找不到该会话，可能已删除"); return@loadSessions }
            // Includes archived sessions from session.list. Opening does not unarchive or alter the Host ledger.
            remote.loadHistory(sessionId, { loaded ->
                if (!pageAlive || !isCurrent()) return@loadHistory
                if (repository !== remote || activeConnectionId != expectedConnection) { onResult(false, "连接已切换"); return@loadHistory }
                if (sessions.none { it.id == sessionId }) sessions = sessions + target
                val targetState = sessionMessageState(sessionId, loadFromDisk = false)
                targetState.diffUpdate(loaded) { old, new -> old == new }
                sessionMessageReady.add(sessionId)
                closeSettingsPage()
                closeSessionDrawer()
                if (activeSessionId == sessionId) {
                    loadWebTimeline(sessionId, forceReplace = true)
                } else {
                    selectMountedSession(sessionId)
                }
                onResult(true, "")
            }, { message -> if (pageAlive && isCurrent()) onResult(false, "无法读取该会话：$message") })
        }, { message -> if (pageAlive && isCurrent()) onResult(false, "无法确认会话：$message") })
    }

    /** 读取上次崩溃；日志与稳定 ID 在同一 SQLite 事务提交，跨主页/重启幂等。 */
    internal fun reportLastCrashIfAny() {
        val source = DshLogService.current ?: return
        val epoch = source.clearVersion()
        if (pagerData.platform == "ohos") {
            bridgeModule.readLastCrashAsync { raw -> reportCrashRecord(raw, source, epoch) }
        } else {
            reportCrashRecord(runCatching { bridgeModule.readLastCrash() }.getOrDefault(""), source, epoch)
        }
    }

    internal fun reportCrashRecord(raw: String, source: DshLogWriteBehind, epoch: Long) {
        if (!pageAlive || raw.isEmpty() || crashImportWork != null) return
        val id = DshCrashMarker.idOf(raw)
        val text = "上次异常退出：${LogSanitizer.sanitize(raw).take(16000)}"
        val event = LogEvent(0, currentTimeMillis(), LogLevel.ERROR, "crash", null, null, text, text.length)
        val work = DshLogWork(localReadScope) { cancelled ->
            if (cancelled()) false else source.importCrash(id, event, epoch)
        }
        crashImportWork = work
        fun receive() {
            if (!pageAlive || crashImportWork !== work) return
            val result = work.take()
            if (result == null) { setTimeout(50) { receive() }; return }
            crashImportWork = null
            result.onSuccess { if (it) bridgeModule.toast("检测到上次异常退出，崩溃栈已保存到日志") }
                .onFailure { setTimeout(5000) { if (pageAlive && source.clearVersion() == epoch) reportLastCrashIfAny() } }
        }
        setTimeout(50) { receive() }
    }



    // ===== 重命名会话 =====



































































    internal fun restoreCachedSessions() {
        val store = localStore ?: return
        val cached = runCatching { store.loadSessions(activeConnectionId) }.getOrDefault(emptyList())
        if (cached.isEmpty()) return
        sessions = cached.toList()
        refreshVisibleSessions()
        val homeId = cached.firstOrNull { it.blank }?.id
        if (homeId != null) {
            activeSessionId = homeId
            val state = sessionMessageStates[homeId] ?: ObservableList()
            state.clear()
            sessionMessageStates[homeId] = state
            sessionMessageReady.add(homeId)
            messages = state
            ensureConversationPanel(homeId)
            return
        }
        val state = ObservableList<DshMessage>()
        messages = state
        sessionMessageStates[activeSessionId] = state
        sessionMessageReady.add(activeSessionId)
        ensureConversationPanel(activeSessionId)
    }

    internal fun loadApiKeyAsync() {
        if (isRemoteHost) return
        val store = localStore
        if (store == null) {
            showCredentialSetupIfNeeded("")
            return
        }
        localReadScope.launch {
            val apiKey = runCatching { store.loadApiKey() }.getOrDefault("")
            postToUi {
                pendingApiKey = apiKey
                if (apiKey.isEmpty()) {
                    showCredentialSetupIfNeeded(apiKey)
                } else if (engineReady && repository == null && connectionMode == DshConnectionMode.LOCAL) {
                    connectLocalEngine(apiKey)
                }
            }
        }
    }

    internal fun showCredentialSetupIfNeeded(apiKey: String) {
        if (isRemoteHost) return
        if (pendingApiKey.isNotEmpty() || apiKey.isNotEmpty()) return
        connectionLabel = "等待配置"
        updateCredentialSetupVisibility(true)
        if (messages.none { it.id == "api-key-required" }) {
            messages.add(
                DshMessage(
                    id = "api-key-required",
                    role = DshMessageRole.ASSISTANT,
                    content = "输入 DeepSeek API Key 后即可开始使用本地 Agent。",
                ),
            )
        }
    }



    internal fun isWebDisclosureExpanded(id: String): Boolean {
        webDisclosureRevision
        return webDisclosureStates[id] == true
    }

    internal fun toggleWebDisclosure(id: String) {
        val next = webDisclosureStates[id] != true
        webDisclosureStates[id] = next
        if (!next) {
            webJsonNodeStates.keys.filter { it.startsWith("$id:") }.toList().forEach(webJsonNodeStates::remove)
        }
        webDisclosureRevision += 1
        refreshSessionRenderTree(activeSessionId)
    }

    internal fun isWebJsonNodeExpanded(messageId: String, nodeId: String): Boolean {
        webDisclosureRevision
        return webJsonNodeStates["$messageId:$nodeId"] == true
    }

    internal fun toggleWebJsonNode(messageId: String, nodeId: String) {
        val key = "$messageId:$nodeId"
        webJsonNodeStates[key] = webJsonNodeStates[key] != true
        webDisclosureRevision += 1
        refreshSessionRenderTree(activeSessionId)
    }

    internal fun isBlankSession(sessionId: String = activeSessionId): Boolean =
        sessions.firstOrNull { it.id == sessionId }?.blank == true

    internal fun conversationListEpochFor(sessionId: String): Int {
        conversationListEpoch
        return conversationListEpochs[sessionId] ?: 0
    }

    internal fun remountConversationList(sessionId: String) {
        conversationListEpochs[sessionId] = (conversationListEpochs[sessionId] ?: 0) + 1
        conversationListEpoch += 1
    }

    internal fun applyActiveSessionChrome() {
        pendingApproval = null
        pendingQuestion = null
        selectedQuestionOptions.clear()
        questionCustom = ""
        questionIndex = 0
        questionError = ""
        questionDrafts.clear()
        goalSnapshot = null
        if (!isRemoteHost) {
            queueItems = ObservableList()
            jobItems = ObservableList()
            liveJobItems = ObservableList()
            return
        }
        refreshQueueDock()
        refreshJobsPanel()
        refreshPendingInteractions()
    }


    internal fun isTurnStatusActive(): Boolean =
        streaming || stopButtonVisible || sessionRunning

    internal fun syncTurnStatusTicker() {
        if (!isTurnStatusActive()) {
            turnStatusTickerGeneration += 1
            turnStatusMark = null
            turnElapsedMs = 0
            turnStatusClockBucket = -1L
            return
        }
        if (turnStatusMark == null) {
            turnStatusMark = TimeSource.Monotonic.markNow()
        }
        val token = ++turnStatusTickerGeneration
        fun tick() {
            if (token != turnStatusTickerGeneration) return
            if (!isTurnStatusActive()) {
                turnStatusMark = null
                turnElapsedMs = 0
                turnStatusClockBucket = -1L
                return
            }
            val elapsed = turnStatusMark?.elapsedNow()?.inWholeMilliseconds ?: 0L
            val showClock = elapsed >= TURN_STATUS_CLOCK_AFTER_MS
            val clockBucket = if (showClock) elapsed / 1_000L else 0L
            if (clockBucket != turnStatusClockBucket) {
                turnStatusClockBucket = clockBucket
                turnElapsedMs = elapsed
            }
            val wait = if (showClock) 1_000L else (TURN_STATUS_CLOCK_AFTER_MS - elapsed).coerceAtLeast(200L)
            setTimeout(pagerId, wait.toInt()) { tick() }
        }
        tick()
    }


    internal fun refreshMountedSessionRenderTrees() {
        conversationPanelIds.toList().forEach { refreshSessionRenderTree(it) }
    }

    internal fun refreshSessionRenderTree(sessionId: String) {
        val list = messageScrollerRefs[sessionId]?.view ?: return
        (list.contentView as? ListContentView)?.createRenderViewsOnVisibleRect()
    }

    internal fun realizeSessionAfterData(
        sessionId: String,
        scrollToEndAfterLoad: Boolean = true,
    ) {
        refreshSessionRenderTree(sessionId)
        addTaskWhenPagerUpdateLayoutFinish {
            refreshSessionRenderTree(sessionId)
            if (scrollToEndAfterLoad && activeSessionId == sessionId) scrollMessagesToEnd()
        }
        setTimeout(pagerId, 16) {
            refreshSessionRenderTree(sessionId)
            if (scrollToEndAfterLoad && activeSessionId == sessionId) scrollMessagesToEnd()
        }
    }

    internal fun loadCachedHistory(sessionId: String) {
        messages = sessionMessageState(sessionId, loadFromDisk = false)
        ensureConversationPanel(sessionId)
        loadMessagesFromDisk(sessionId)
    }

    internal fun sessionMessageState(
        sessionId: String,
        loadFromDisk: Boolean = true,
        scrollToEndAfterLoad: Boolean = true,
    ): ObservableList<DshMessage> {
        sessionMessageStates[sessionId]?.let { return it }
        val state = ObservableList<DshMessage>()
        sessionMessageStates[sessionId] = state
        if (loadFromDisk) loadMessagesFromDisk(sessionId, scrollToEndAfterLoad)
        return state
    }

    /**
     * Warm every known conversation after the session index is available.
     * Reads are serialized through one background coroutine because the local
     * SQLite driver is shared by the page and should not be queried concurrently.
     */
    internal fun preloadAllSessionMessages() {
        val sessionIds = sessions.toList().map { it.id }
        // Load data first. Do not mount empty ListViews: LazyLoop initializes
        // its visible range from the initial list and may not realize the
        // first items when the list is populated later.
        sessionIds.forEach { sessionMessageState(it, loadFromDisk = false) }
        val store = localStore ?: run {
            sessionIds.forEach {
                sessionMessageReady.add(it)
                completePendingSessionSelection(it)
            }
            return
        }
        val pending = sessionIds
            .filterNot { sessionMessageReady.contains(it) }
            .filter { pendingLocalMessageReads.add(it) }
        if (pending.isEmpty()) {
            return
        }
        localReadScope.launch {
            pending.forEach { sessionId ->
                val loaded = runCatching { store.loadMessages(activeConnectionId, sessionId) }
                    .getOrDefault(emptyList())
                    .filterNot { it.isRuntimeContextSnapshot() }
                postToUi {
                    pendingLocalMessageReads.remove(sessionId)
                    val state = sessionMessageStates[sessionId] ?: return@postToUi
                    sessionMessageReady.add(sessionId)
                    if (state.isEmpty() && loaded.isNotEmpty() &&
                        sessions.firstOrNull { it.id == sessionId }?.blank != true
                    ) {
                        state.addAll(loaded)
                        remountConversationList(sessionId)
                    }
                    if (conversationPanelIds.size < CONVERSATION_PANEL_CACHE_LIMIT) {
                        ensureConversationPanel(sessionId)
                    }
                    realizeSessionAfterData(sessionId, scrollToEndAfterLoad = false)
                    completePendingSessionSelection(sessionId)
                }
            }
        }
    }

    internal fun loadMessagesFromDisk(
        sessionId: String,
        scrollToEndAfterLoad: Boolean = true,
    ) {
        if (localStore == null || !pendingLocalMessageReads.add(sessionId)) return
        localReadScope.launch {
            val loaded = runCatching { localStore?.loadMessages(activeConnectionId, sessionId).orEmpty() }
                .getOrDefault(emptyList())
                .filterNot { it.isRuntimeContextSnapshot() }
            postToUi {
                pendingLocalMessageReads.remove(sessionId)
                val state = sessionMessageStates[sessionId] ?: return@postToUi
                sessionMessageReady.add(sessionId)
                // A remote history response or a new local prompt wins over
                // a disk snapshot that finishes later. The state is keyed by
                // session ID, so an inactive session can be updated safely.
                if (state.isEmpty() && loaded.isNotEmpty() &&
                    sessions.firstOrNull { it.id == sessionId }?.blank != true
                ) {
                    state.addAll(loaded)
                    remountConversationList(sessionId)
                }
                ensureConversationPanel(sessionId)
                realizeSessionAfterData(sessionId, scrollToEndAfterLoad)
                completePendingSessionSelection(sessionId)
            }
        }
    }

    internal fun completePendingSessionSelection(sessionId: String) {
        if (!pendingSessionSelections.remove(sessionId)) return
        postToUi {
            if (activeSessionId != sessionId) selectSession(sessionId)
        }
    }

    internal fun warmRecentSessionCache(
        sessionIds: kotlin.collections.List<String> = sessions.asSequence()
            .map { it.id }
            .filter { it != activeSessionId && !conversationPanelIds.contains(it) }
            .take(SESSION_CACHE_WARM_LIMIT)
            .toList(),
        index: Int = 0,
        scrollToEndAfterLoad: Boolean = true,
    ) {
        if (index >= sessionIds.size) return
        sessionMessageState(
            sessionIds[index],
            loadFromDisk = true,
            scrollToEndAfterLoad = scrollToEndAfterLoad,
        )
        if (sessionMessageReady.contains(sessionIds[index])) {
            ensureConversationPanel(sessionIds[index])
        }
        setTimeout(pagerId, SESSION_CACHE_WARM_INTERVAL_MS) {
            warmRecentSessionCache(sessionIds, index + 1, scrollToEndAfterLoad)
        }
    }

    internal fun ensureConversationPanel(sessionId: String) {
        if (conversationPanelIds.contains(sessionId)) return
        try {
            if (conversationPanelIds.size >= CONVERSATION_PANEL_CACHE_LIMIT) {
                val evictIndex = conversationPanelIds.indexOfFirst { it != activeSessionId }
                if (evictIndex >= 0) {
                    val evictedId = conversationPanelIds.removeAt(evictIndex)
                    messageScrollerRefs.remove(evictedId)
                }
            }
            conversationPanelIds.add(sessionId)
        } catch (e: ConcurrentModificationException) {
            // 渲染该列表期间新增 panel 会触发 Kuikly 对同一响应式列表的自注册，
            // 迭代中修改被绑定的 observers 集合导致 CME。此时元素通常已入列，
            // 下一消息幂等兜底收敛，避免拖垮整个页面。
            postToUi {
                runCatching {
                    if (!conversationPanelIds.contains(sessionId) &&
                        conversationPanelIds.size < CONVERSATION_PANEL_CACHE_LIMIT
                    ) {
                        conversationPanelIds.add(sessionId)
                    }
                }
            }
        }
    }

    internal fun sendDraft() {
        if (sendInFlight) return
        dismissKeyboard()
        val prompt = draft.trim()
        val sendableImages = pendingImages.filter { it.state != DshImageDraftState.INVALID && it.dataBase64.isNotEmpty() }
        val sendableFiles = pendingFiles.filter { it.state != DshFileDraftState.FAILED && it.dataBase64.isNotEmpty() }
        if ((prompt.isEmpty() && sendableImages.isEmpty() && sendableFiles.isEmpty()) || streaming) return
        val hostRepository = remoteRepo
        if (hostRepository == null) {
            connectionLabel = "本地内核尚未连接"
            messages.add(DshMessage(
                "send-engine-error-${messages.size}",
                DshMessageRole.ERROR,
                "本地 Harness 尚未连接，请稍候再试。",
            ))
            return
        }
        if (!hostRepository.isProductReady()) {
            connectionLabel = syncBusyLabel()
            return
        }
        if (sessions.isEmpty() || activeSessionId.isEmpty()) {
            connectionLabel = "正在创建会话"
            hostRepository.createSession(null, { sessionId ->
                sessions = sessions + DshSession(sessionId, "新会话", "Host", "", blank = true, permission = permissionValue, agentPreset = agentModeValue)

                touchSessionActivity(sessionId)
                activeSessionId = sessionId
                loadModels(sessionId)
                sendDraft()
            }, { error ->
                connectionLabel = "会话创建失败"
                messages.add(DshMessage(
                    "send-session-error-${messages.size}",
                    DshMessageRole.ERROR,
                    "无法创建会话：$error",
                ))
            }, permission = permissionValue, agentPreset = agentModeValue)
            return
        }
        val sessionId = activeSessionId
        // 旧 Host 无文件 content 类型：先把未落盘文件上传到会话工作目录，成功后再带上 handle 发送。
        val filesNeedingUpload = sendableFiles.filter { it.handle.isEmpty() }
        if (filesNeedingUpload.isNotEmpty()) {
            if (sendInFlight) return
            sendInFlight = true
            uploadPendingFiles(sessionId, filesNeedingUpload) { ok ->
                sendInFlight = false
                if (!ok) return@uploadPendingFiles
                // 用首次点击时的快照发送，避免读取被编辑后的草稿；文件带回上传后的 handle。
                val uploaded = sendableFiles.map { file ->
                    pendingFiles.firstOrNull { it.clientId == file.clientId } ?: file
                }
                submitDraft(sessionId, prompt, sendableImages, uploaded, hostRepository)
            }
            return
        }
        submitDraft(sessionId, prompt, sendableImages, sendableFiles, hostRepository)
    }

    internal fun submitDraft(
        sessionId: String,
        prompt: String,
        sendableImages: List<DshPendingImage>,
        sendableFiles: List<DshPendingFile>,
        hostRepository: DshRemoteRepository,
    ) {
        val wirePrompt = composePromptWithFiles(prompt, sendableFiles)
        val user = DshMessage(
            "user-${messages.size}",
            DshMessageRole.USER,
            wirePrompt,
        )
        val assistantId = "assistant-${messages.size}"
        val reasoningId = "$assistantId-reasoning"
        val wasEmpty = messages.isEmpty()
        messages.add(user)
        // DSH ChatView keeps the assistant node out of the flow until the
        // first token. The turn-status row ("Deep diving...") occupies that
        // gap so LazyLoop never has to realize an empty markdown bubble.
        sessionMessageStates[sessionId] = messages
        if (wasEmpty) remountConversationList(sessionId)
        // 第一条消息发出后会话即归属所选工作区，工作区配置入口（文件夹 chip）随即消失。
        if (wasEmpty && isBlankSession(sessionId)) {
            updateSessionMetadata(sessionId) { it.copy(blank = false) }
        }
        pinFollowListTail()
        scrollMessagesToMessage(user.id)
        streamingTurnAnchorAssistantId = messages.lastOrNull(::dshIsLiveAssistantText)?.id.orEmpty()
        streamingAssistantId = ""
        streamingAssistantRootId = assistantId
        streamingAssistantSegment = 0
        streamingReasoningId = reasoningId
        streamingSourceSeq = null
        streamingReasoningContent = ""
        streamingAssistantContent = ""
        pendingAssistantDelta.setLength(0)
        assistantFlushScheduled = false
        draft = ""
        inputView?.setText("")
        // 进入发送中：输入区草稿立即反映 UPLOADING 状态
        sendableImages.forEach { image ->
            val idx = pendingImages.indexOfFirst { it.clientId == image.clientId }
            if (idx >= 0) {
                pendingImages[idx] = image.copy(state = DshImageDraftState.UPLOADING)
                attachmentEpoch += 1
            }
        }
        sendableFiles.forEach { file ->
            val idx = pendingFiles.indexOfFirst { it.clientId == file.clientId }
            if (idx >= 0) {
                pendingFiles[idx] = file.copy(state = DshFileDraftState.UPLOADING)
                attachmentEpoch += 1
            }
        }
        streaming = true
        stopButtonVisible = true
        connectionLabel = "正在生成"
        syncTurnStatusTicker()
        touchSessionActivity(sessionId)
        streamHandle = hostRepository.streamReplyWithImages(
            pagerId = pagerId,
            sessionId = sessionId,
            prompt = wirePrompt,
            images = sendableImages,
            onDelta = { delta, isReasoning ->
                if (isReasoning) queueReasoningDelta(reasoningId, delta)
                else queueAssistantDelta(assistantId, delta)
            },
            onComplete = { result ->
                if (!connectionCoordinator.isActive(connectionMode)) return@streamReplyWithImages
                // Host 已接受该轮（含图片），输入区草稿收敛为空；图片由 Host timeline 以 attachmentId 呈现
                val completedIds = sendableImages.map { it.clientId }.toSet()
                pendingImages.removeAll { it.clientId in completedIds }
                pendingFiles.removeAll { it.clientId in sendableFiles.map { f -> f.clientId }.toSet() }
                attachmentEpoch += 1
                // 发送中的图片此时才移入用户气泡；整轮发送期间输入区保留“发送中”状态
                val previews = sendableImages.map { it.previewDataUrl }
                sessionMessageStates[sessionId]?.let { state ->
                    val index = state.indexOfFirst { it.id == user.id }
                    if (index >= 0 && previews.isNotEmpty()) {
                        state[index] = state[index].copy(imagePreviews = previews)
                    }
                }
                flushAssistantDelta()
                if (streamingAssistantId.isEmpty() && result.isNotEmpty()) {
                    ensureStreamingAssistantSegment()
                }
                // A turn may contain several assistant text blocks separated by
                // tool calls. The current segment already contains the final
                // block; using the turn-wide accumulator here would move all
                // earlier text back into this last row.
                val completedContent = streamingAssistantContent.ifEmpty { result }
                settleStreamingMessage(DshMessageRole.ASSISTANT, completedContent)
                persistMessages(sessionId)
                connectionLabel = "已连接"
                streamHandle = null
            },
            onError = { error ->
                if (!connectionCoordinator.isActive(connectionMode)) return@streamReplyWithImages
                if (dshIsTransportInterrupt("", error)) {
                    DshStreamLog.i("ui.prompt-interrupt session=$sessionId message='${DshStreamLog.preview(error)}'")
                    // 传输中断由重连接管，输入区不再挂“发送中”；图片由 Host timeline 以 attachmentId 回显
                    pendingImages.removeAll { it.clientId in sendableImages.map { it.clientId }.toSet() }
                    pendingFiles.removeAll { it.clientId in sendableFiles.map { f -> f.clientId }.toSet() }
                    attachmentEpoch += 1
                    return@streamReplyWithImages
                }
                // 发送失败：图片重新加入输入区并标记 FAILED，可重试或删除
                sendableImages.forEach { image ->
                    val idx = pendingImages.indexOfFirst { it.clientId == image.clientId }
                    val failed = image.copy(
                        state = DshImageDraftState.FAILED,
                        error = "发送失败：$error",
                    )
                    if (idx >= 0) {
                        pendingImages[idx] = failed
                    } else {
                        pendingImages.add(failed)
                    }
                }
                // 文件已落盘：仅回到已就绪，重发时复用同一 handle，不重复上传。
                sendableFiles.forEach { file ->
                    val idx = pendingFiles.indexOfFirst { it.clientId == file.clientId }
                    val reset = file.copy(state = DshFileDraftState.SELECTED)
                    if (idx >= 0) pendingFiles[idx] = reset else pendingFiles.add(reset)
                }
                attachmentEpoch += 1
                flushAssistantDelta()
                ensureStreamingAssistantSegment()
                DshStreamLog.log(LogLevel.ERROR, "ui.error", "ui.error session=$sessionId message='${DshStreamLog.preview(error)}'", sessionId, null)
                settleStreamingMessage(DshMessageRole.ERROR, error)
                persistMessages(sessionId)
                connectionLabel = "已连接"
                streamHandle = null
            },
        )
    }

    internal fun stopStream() {
        if (!stopButtonVisible) return
        dismissKeyboard()
        streamHandle?.cancel()
        streamHandle = null
        // 主动停止时，已随 prompt 发出的图片/文件不再留在输入区
        pendingImages.removeAll { it.state == DshImageDraftState.UPLOADING }
        pendingFiles.removeAll { it.state == DshFileDraftState.UPLOADING }
        attachmentEpoch += 1
        flushAssistantDelta()
        ensureStreamingAssistantSegment()
        val stoppedContent = streamingAssistantContent + "\n\n*已停止*"
        settleStreamingMessage(DshMessageRole.ASSISTANT, stoppedContent)
        persistMessages(activeSessionId)
        connectionLabel = "已连接"
    }

    internal fun cancelStreamingForSessionSwitch() {
        if (!streaming && !stopButtonVisible) return
        streamHandle?.cancel()
        streamHandle = null
        val partial = streamingAssistantContent + pendingAssistantDelta.toString()
        if (streamingAssistantId.isNotEmpty()) {
            updateStreamingMessage(partial, streaming = false)
        }
        finalizeStreamingReasoning()
        streamingAssistantId = ""
        streamingAssistantRootId = ""
        streamingAssistantSegment = 0
        streamingReasoningId = ""
        streamingReasoningContent = ""
        pendingAssistantDelta.setLength(0)
        streamingAssistantContent = ""
        assistantFlushScheduled = false
        streamingTurnAnchorAssistantId = ""
        streaming = false
        stopButtonVisible = false
        syncTurnStatusTicker()
    }

    internal fun dismissKeyboard() {
        if (!inputFocused && keyboardHeight <= 0f) return
        inputFocused = false
        inputView?.blur()
        bridgeModule.closeKeyboard()
        keyboardHeight = 0f
    }






    internal fun updateKeyboard(params: KeyboardParams) {
        // 仅当首页输入框自身聚焦时才让键盘顶起底页；重命名等弹窗里的输入框
        // 也会唤起软键盘，其高度不应驱动会话区布局。高度归零始终允许（收起态）。
        if (!inputFocused && params.height > 0f) return
        keyboardAnimation = Animation.easeInOut(ANIMATION_DURATION_S)
        keyboardHeight = effectiveKeyboardHeight(params.height)
        // Closing the keyboard after send must not undo the scroll to the
        // newly sent user message. Scroll to the end only when the composer
        // is opening while no response is being anchored.
        if (keyboardHeight > 0f && !streaming) scrollMessagesToEnd()
    }

    internal fun effectiveKeyboardHeight(rawHeight: Float): Float {
        if (rawHeight <= 0f) return 0f
        // Kuikly's Android watcher already reports IME height minus the
        // navigation bar. Subtracting the safe area here would lift the
        // composer a second time and leave a visible gap above the keyboard.
        return if (pagerData.isAndroid) {
            rawHeight
        } else {
            (rawHeight - pagerData.safeAreaInsets.bottom).coerceAtLeast(0f)
        }
    }

    internal fun loadModels(sessionId: String) {
        val hostRepository = repository ?: return
        val version = ++modelRequestVersion
        hostRepository.loadModels(sessionId, { loaded ->
            if (!pageAlive || repository !== hostRepository || activeSessionId != sessionId || version != modelRequestVersion) return@loadModels
            selectedModelLabel = loaded.current.name
            selectedEffortLabel = selectedReasoningEffortName(loaded.current)
            modelOptions = ObservableList(loaded.options.toMutableList())
            modelPickerBusy = false
            modelPickerError = if (loaded.routable) "" else "当前模型不可用，请选择其他模型。"
        }, { error ->
            if (!pageAlive || repository !== hostRepository || activeSessionId != sessionId || version != modelRequestVersion) return@loadModels
            modelPickerBusy = false
            modelPickerError = error
        })
    }

    internal fun openModelPicker() {
        if (sessions.isEmpty()) return
        dismissKeyboard()
        commandSheetVisible = false
        modelEffortsVisible = false
        modelPickerVisible = true
        modelPickerBusy = true
        modelPickerError = ""
        loadModels(activeSessionId)
    }

    internal fun selectModel(option: DshModelOption) {
        val hostRepository = repository ?: return
        if (modelPickerBusy) return
        val sessionId = activeSessionId
        val version = ++modelRequestVersion
        modelPickerBusy = true
        modelPickerError = ""
        hostRepository.selectModel(sessionId, option, { selected ->
            if (!pageAlive || repository !== hostRepository || activeSessionId != sessionId || version != modelRequestVersion) return@selectModel
            selectedModelLabel = selected.name
            selectedEffortLabel = selectedReasoningEffortName(selected)
            modelPickerBusy = false
            modelPickerVisible = false
            modelOptions = ObservableList(modelOptions.map {
                if (it.provider == selected.provider && it.model == selected.model) {
                    it.copy(selected = true, reasoningEffort = selected.reasoningEffort)
                } else {
                    it.copy(selected = false)
                }
            }.toMutableList())
        }, { error ->
            if (!pageAlive || repository !== hostRepository || activeSessionId != sessionId || version != modelRequestVersion) return@selectModel
            modelPickerBusy = false
            modelPickerError = error
        })
    }

    // 提交推理等级变更到 host：复用 selectModel 更新 effort。
    internal fun selectModelEffort(effortId: String) {
        val hostRepository = repository ?: return
        if (modelPickerBusy) return
        val sessionId = activeSessionId
        val version = ++modelRequestVersion
        val current = modelOptions.firstOrNull { it.selected } ?: return
        modelPickerBusy = true
        modelPickerError = ""
        hostRepository.selectModel(sessionId, current.copy(reasoningEffort = effortId), { selected ->
            if (!pageAlive || repository !== hostRepository || activeSessionId != sessionId || version != modelRequestVersion) return@selectModel
            selectedModelLabel = selected.name
            selectedEffortLabel = selectedReasoningEffortName(selected)
            modelPickerBusy = false
            modelOptions = ObservableList(modelOptions.map {
                if (it.provider == selected.provider && it.model == selected.model) {
                    it.copy(selected = true, reasoningEffort = selected.reasoningEffort)
                } else {
                    it.copy(selected = false)
                }
            }.toMutableList())
        }, { error ->
            if (!pageAlive || repository !== hostRepository || activeSessionId != sessionId || version != modelRequestVersion) return@selectModel
            modelPickerBusy = false
            modelPickerError = error
        })
    }

    /** 切换文字 / 语音输入模式（右下角语音↔键盘按钮）。 */





    internal fun toggleCommandSheet() {
        dismissKeyboard()
        commandSheetVisible = !commandSheetVisible
    }

    /** 点击命令：把 "/命令 " 写入输入框（补全草稿 + 原生输入框文本），并关闭面板 */
    internal fun insertCommand(command: DshCommand) {
        insertDraftText("/${command.name} ")
        commandSheetVisible = false
    }

    /** 把文本写入 draft 状态，并同步到原生输入框（draft observable 不会自动回流到 TextArea）。 */
    internal fun insertDraftText(text: String) {
        draft = text
        inputView?.setText(text)
    }

    internal fun queueAssistantDelta(id: String, delta: String) {
        if (delta.isEmpty()) return
        if (!streaming || streamingAssistantRootId != id) return
        ensureStreamingAssistantSegment()
        pendingAssistantDelta.append(delta)
        val firstPaint = streamingAssistantContent.isEmpty()
        if (assistantFlushScheduled && !firstPaint) return
        assistantFlushScheduled = true
        setTimeout(pagerId, if (firstPaint) 0 else STREAM_FLUSH_INTERVAL_MS) {
            assistantFlushScheduled = false
            flushAssistantDelta()
        }
    }

    internal fun queueReasoningDelta(id: String, delta: String) {
        if (delta.isEmpty() || streamingReasoningId != id) return
        streamingReasoningContent += delta
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) {
            messages[index] = messages[index].copy(
                content = streamingReasoningContent,
                streaming = true,
                isReasoning = true,
            )
        } else {
            messages.add(DshMessage(id, DshMessageRole.ASSISTANT, streamingReasoningContent, streaming = true, isReasoning = true, sourceSeq = streamingSourceSeq))
        }
        realizeVisibleMessages()
        if (followListTail) scrollMessagesToEnd()
    }

    internal fun flushAssistantDelta() {
        if (streamingAssistantId.isEmpty() || pendingAssistantDelta.isEmpty()) return
        streamingAssistantContent += pendingAssistantDelta.toString()
        pendingAssistantDelta.setLength(0)
        // Keep the ObservableList row stable while tokens arrive. `messages[i] =
        // copy()` is remove+add; LazyLoop treats an append at currentEnd as
        // "behind the visible range" and will not build the cell until scroll.
        // DshMarkdown already reads `streamingAssistantContent` via liveContent.
        insertLiveAssistantRow()
        ensureLiveMessageCell()
        refreshSessionRenderTree(activeSessionId)
        scrollMessagesToEnd()
    }

    /**
     * A live assistant response is an ordered sequence of text segments and
     * tool cards. Start a new row lazily after a tool card so the next delta is
     * placed after that card instead of being appended to the old row.
     */
    internal fun ensureStreamingAssistantSegment() {
        if (streamingAssistantId.isNotEmpty()) return
        if (streamingAssistantRootId.isEmpty()) return
        val id = if (streamingAssistantSegment == 0) {
            streamingAssistantRootId
        } else {
            "$streamingAssistantRootId-segment-${streamingAssistantSegment}"
        }
        streamingAssistantId = id
        if (streamingAssistantContent.isEmpty() && pendingAssistantDelta.isEmpty()) {
            // Inserting an empty assistant into a brand-new List (only the user
            // bubble) is "add behind currentEnd". LazyLoop will not build that
            // cell until a real scroll, and DshMessageRow also skips mounting
            // Markdown when the first paint is empty. Wait for the first flush.
            return
        }
        insertLiveAssistantRow()
    }

    internal fun insertLiveAssistantRow() {
        val id = streamingAssistantId
        if (id.isEmpty() || messages.any { it.id == id }) return
        // Keep content empty until settle. The first-flush snapshot must not
        // become the display source; DshMarkdown reads the live buffer.
        messages.add(DshMessage(id, DshMessageRole.ASSISTANT, "", streaming = true, sourceSeq = streamingSourceSeq))
        ensureLiveMessageCell()
    }

    /**
     * vforLazy only creates items inside `[currentStart, currentEnd)`. Appending
     * the first assistant after the list was mounted with a single user bubble
     * lands at `currentEnd`. `setContentOffset` is a no-op when content is
     * shorter than the viewport (new session, first turn), so the cell never
     * appears until the user drags. `scrollToPosition` is what actually builds it.
     */
    internal fun ensureLiveMessageCell() {
        if (!followListTail) return
        val id = streamingAssistantId
        if (id.isEmpty()) return
        if (messageRowRefs[messageRowKey(activeSessionId, id)]?.view != null) return
        val list = messageScrollerRefs[activeSessionId]?.view ?: return
        val index = messages.indexOfFirst { it.id == id }
        if (index < 0) return
        list.scrollToPosition(index, 0f, false)
    }

    /** Close the current text row immediately before the next tool card. */
    internal fun splitStreamingAssistantBeforeTool() {
        if (!streaming || streamingAssistantRootId.isEmpty()) return
        flushAssistantDelta()
        val id = streamingAssistantId
        if (id.isNotEmpty()) {
            val index = messages.indexOfFirst { it.id == id }
            if (index >= 0) {
                val current = messages[index]
                val text = current.content.ifEmpty { streamingAssistantContent }
                if (text.isEmpty()) {
                    messages.removeAt(index)
                } else {
                    messages[index] = current.copy(content = text, streaming = false)
                    realizeVisibleMessages()
                }
            }
        }
        streamingAssistantId = ""
        streamingAssistantContent = ""
        streamingAssistantSegment += 1
        pendingAssistantDelta.setLength(0)
        assistantFlushScheduled = false
    }

    internal fun updateStreamingMessage(content: String, streaming: Boolean, isReasoning: Boolean = false) {
        val index = messages.indexOfFirst { it.id == streamingAssistantId }
        if (index < 0) return
        messages[index] = messages[index].copy(
            content = content,
            streaming = streaming,
            isReasoning = isReasoning,
        )
        if (index >= messages.size - 1) realizeVisibleMessages()
    }

    internal fun finalizeStreamingReasoning() {
        if (streamingReasoningId.isEmpty()) return
        val index = messages.indexOfFirst { it.id == streamingReasoningId }
        if (index >= 0) {
            messages[index] = messages[index].copy(streaming = false, isReasoning = true)
        }
    }

    internal fun scrollMessagesToEnd() {
        if (!followListTail) return
        val generation = ++scrollSettleGeneration
        ensureLiveMessageCell()
        realizeVisibleMessages()
        addTaskWhenPagerUpdateLayoutFinish {
            settleScrollToEnd(generation, 0)
        }
    }

    internal fun scrollMessagesToMessage(messageId: String) {
        val generation = ++scrollSettleGeneration
        addTaskWhenPagerUpdateLayoutFinish {
            settleScrollToMessage(messageId, generation, 0)
        }
    }

    /**
     * Markdown and LazyLoop can add/layout children over several frames.
     * Re-apply the bottom offset while that burst settles, otherwise the first
     * offset is calculated from a shorter content height and the user sees the
     * list walk down a few screens after launch.
     */
    internal fun settleScrollToEnd(generation: Int, attempt: Int) {
        if (generation != scrollSettleGeneration || !followListTail) return
        ensureLiveMessageCell()
        realizeVisibleMessages()
        scrollMessagesToEndAfterLayout()
        if (attempt >= SCROLL_SETTLE_ATTEMPTS) return
        setTimeout(pagerId, SCROLL_SETTLE_DELAYS_MS[attempt]) {
            addTaskWhenPagerUpdateLayoutFinish {
                settleScrollToEnd(generation, attempt + 1)
            }
        }
    }

    internal fun realizeVisibleMessages() {
        val scroller = messageScrollerRefs[activeSessionId]?.view ?: return
        val content = scroller.contentView as? ListContentView ?: return
        content.flexNode.markDirty()
        content.createRenderViewsOnVisibleRect()
    }

    internal fun onConversationUserScroll(params: ScrollParams) {
        val maxOffset = (params.contentHeight - params.viewHeight).coerceAtLeast(0f)
        val nearBottom = params.offsetY >= maxOffset - FOLLOW_LIST_SLACK_PX
        if (nearBottom) {
            followListTail = true
            return
        }
        if (params.isDragging) cancelFollowListTail()
    }

    internal fun cancelFollowListTail() {
        followListTail = false
        scrollSettleGeneration += 1
    }

    internal fun pinFollowListTail() {
        followListTail = true
    }

    internal fun scrollMessagesToEndAfterLayout() {
        if (!followListTail) return
        val scroller = messageScrollerRefs[activeSessionId]?.view ?: return
        val contentHeight = scroller.contentView?.flexNode?.layoutFrame?.height ?: return
        val viewportHeight = scroller.flexNode?.layoutFrame?.height ?: return
        scroller.setContentOffset(0f, (contentHeight - viewportHeight).coerceAtLeast(0f), animated = false)
    }

    internal fun settleScrollToMessage(messageId: String, generation: Int, attempt: Int) {
        if (generation != scrollSettleGeneration) return
        val row = messageRowRefs[messageRowKey(activeSessionId, messageId)]?.view
        val rowY = row?.flexNode?.layoutFrame?.y
        if (rowY != null) {
            messageScrollerRefs[activeSessionId]?.view?.setContentOffset(
                0f,
                rowY.coerceAtLeast(0f),
                animated = false,
            )
        }
        if (attempt >= SCROLL_SETTLE_ATTEMPTS) return
        setTimeout(pagerId, SCROLL_SETTLE_DELAYS_MS[attempt]) {
            addTaskWhenPagerUpdateLayoutFinish {
                settleScrollToMessage(messageId, generation, attempt + 1)
            }
        }
    }

    internal fun settleStreamingMessage(role: DshMessageRole, content: String) {
        val id = streamingAssistantId
        if (id.isNotEmpty()) {
            val sessionId = activeSessionId
            val finalContent = content.ifEmpty { streamingAssistantContent }
            finalizeStreamingReasoning()
            val index = messages.indexOfFirst { it.id == id }
            if (index >= 0) {
                messages[index] = messages[index].copy(
                    role = role,
                    content = finalContent,
                    streaming = false,
                    sourceSeq = streamingSourceSeq ?: messages[index].sourceSeq,
                )
            } else {
                messages.add(DshMessage(id, role, finalContent, streaming = false, sourceSeq = streamingSourceSeq))
            }
            realizeVisibleMessages()
            streamingReasoningId = ""
            streamingReasoningContent = ""
            pendingAssistantDelta.setLength(0)
            stopButtonVisible = false
            streaming = false
            streamingAssistantContent = finalContent
            syncTurnStatusTicker()
            addTaskWhenPagerUpdateLayoutFinish {
                if (activeSessionId != sessionId) return@addTaskWhenPagerUpdateLayoutFinish
                if (!streaming && streamingAssistantId == id) {
                    val stored = messages.firstOrNull { it.id == id }?.content.orEmpty()
                    if (stored.length >= finalContent.length) {
                        streamingAssistantId = ""
                        streamingAssistantRootId = ""
                        streamingAssistantSegment = 0
                        streamingTurnAnchorAssistantId = ""
                        if (streamingAssistantContent == finalContent) {
                            streamingAssistantContent = ""
                        }
                    }
                }
                refreshSessionRenderTree(sessionId)
                setTimeout(pagerId, 16) {
                    if (activeSessionId != sessionId) return@setTimeout
                    addTaskWhenPagerUpdateLayoutFinish {
                        if (activeSessionId != sessionId) return@addTaskWhenPagerUpdateLayoutFinish
                        refreshSessionRenderTree(sessionId)
                    }
                }
            }
            return
        }
        releaseStreamingUi()
    }

    internal fun releaseStreamingUi() {
        streamingSourceSeq = null
        streamingAssistantId = ""
        streamingAssistantRootId = ""
        streamingAssistantSegment = 0
        streamingTurnAnchorAssistantId = ""
        streamingReasoningId = ""
        streamingReasoningContent = ""
        pendingAssistantDelta.setLength(0)
        streaming = false
        stopButtonVisible = false
        streamingAssistantContent = ""
        syncTurnStatusTicker()
    }

    internal fun persistMessages(sessionId: String) {
        val snapshot = messages.toList()
        sessionMessageStates[sessionId] = messages
        runCatching { localStore?.replaceMessages(activeConnectionId, sessionId, snapshot) }
    }

    internal fun replaceMessagesIfChanged(next: List<DshMessage>, force: Boolean = false) {
        val filtered = next.filterNot { it.isRuntimeContextSnapshot() }
        if (streaming && isRemoteHost && !force) {
            // History is a snapshot that can arrive while the current turn is
            // still being projected. Replacing the observable list here drops
            // optimistic text segments and their in-order tool cards.
            return
        }
        val current = messages.toList()
        if (current == filtered) return
        if (dshMessagesVisuallyEqual(current, filtered)) {
            // Preserve stable UI ids, but still hydrate anchors in pre-upgrade/live caches.
            for (index in current.indices) {
                val synced = dshSyncMessageForkAnchor(current[index], filtered[index])
                if (synced != current[index]) messages[index] = synced
            }
            return
        }
        val remount = force || current.isEmpty() && filtered.isNotEmpty()
        applyMessagesInPlace(filtered)
        sessionMessageStates[activeSessionId] = messages
        if (remount) remountConversationList(activeSessionId)
    }

    internal fun applyMessagesInPlace(next: List<DshMessage>) {
        val shared = minOf(messages.size, next.size)
        for (index in 0 until shared) {
            if (messages[index] != next[index]) messages[index] = next[index]
        }
        when {
            next.size < messages.size -> {
                for (index in messages.lastIndex downTo next.size) {
                    messages.removeAt(index)
                }
            }
            next.size > messages.size -> {
                messages.addAll(next.subList(messages.size, next.size))
            }
        }
    }

    companion object {
        /** 无选中会话时返回的哨兵，确保未分组组不会被误判为激活。 */
        internal const val NO_ACTIVE_WORKSPACE = "__none__"
        internal const val BG = 0xFFF7F9FA
        internal const val LOCAL_ENGINE_URL = "http://127.0.0.1:3080"
        internal const val ENGINE_CONNECT_RETRIES = 60
        internal const val ENGINE_RETRY_DELAY_MS = 1_000
        internal const val ANIMATION_DURATION_MS = 240
        internal const val ANIMATION_DURATION_S = 0.24f
        internal const val STREAM_FLUSH_INTERVAL_MS = 16
        internal const val CONNECTION_CAPSULE_HOLD_MS = 1_500
        internal const val CONNECTION_CAPSULE_FADE_MS = 300
    }
}
