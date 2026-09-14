package com.example.dsh.ui.home

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
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.ListView
import com.tencent.kuikly.core.views.TextAreaView
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.module.CallbackRef
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.base.BackPressCallback
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.example.dsh.base.setTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.time.TimeMark
import com.example.dsh.voice.DshVoiceWaveform
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
    /** 吸顶选择器当前所属对话组 key（空=不显示）。 */
    internal var exportStickyGroupKey by observable("")
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
    // ===== 语音输入（按住说话 → 原生语音识别 → 转文字填入输入框）=====
    /** 语音输入 UI 状态；聚合原 voiceActive/Recording/CancelArmed/PartialText/WaveformRevision。 */
    internal var voiceUi by observable(DshVoiceUiState())
    /** 滚动音量窗口（0..1），驱动浮层蓝色方块高度。 */
    internal val voiceWaveformModel = DshVoiceWaveform()
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
    // 「添加文件夹」目录是否已加载（空目录缺省页/加载态判断）；键盘高度用于面板避让。
    internal var workspaceAddDirectoryLoaded by observable(false)
    internal var workspaceAddKeyboardHeight by observable(0f)
    internal var workspaceAddInputView: InputView? = null
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
    // 归档页行内 ⋯ 溢出菜单（取消归档 / 删除）
    internal var archiveOverflowVisible by observable(false)
    internal var archiveOverflowTargetId by observable("")
    internal var archiveOverflowAnchorX by observable(-1f)
    internal var archiveOverflowAnchorY by observable(-1f)
    // 归档页筛选（所有项目 / 排序）上下文菜单锚点
    internal var archiveFilterX by observable(-1f)
    internal var archiveFilterY by observable(-1f)
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
    /** 正在按需拉取历史的会话 id 集合：去重，避免同一次搜索重复请求。 */
    internal val sessionSearchHistoryLoading = mutableSetOf<String>()
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
        if (voiceUi.recording) {
            bridgeModule.cancelVoiceRecognition()
            voiceUi = voiceUi.copy(recording = false)
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
