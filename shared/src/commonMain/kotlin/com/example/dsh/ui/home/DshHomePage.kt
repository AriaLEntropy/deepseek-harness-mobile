package com.example.dsh.ui.home

import com.example.dsh.base.setTimeout
import com.example.dsh.voice.DSH_VOICE_DECAY_GAP_MS
import com.example.dsh.voice.DSH_VOICE_DECAY_TICK_MS
import com.example.dsh.voice.DSH_VOICE_LEVEL_INTERVAL_MS
import com.example.dsh.voice.DSH_VOICE_STOP_TIMEOUT_MS
import com.example.dsh.ui.chat.DshCommand
import com.example.dsh.ui.chat.DshCommandSheetTile
import com.example.dsh.ui.chat.DshConversation
import com.example.dsh.message.DshMessageActionItem
import com.example.dsh.message.DshMessageActionsMenu
import com.example.dsh.ui.chat.DshMessageFooterAction
import com.example.dsh.message.DshMessageRow
import com.example.dsh.ui.chat.DshSelectTextModal
import com.example.dsh.ui.chat.TURN_STATUS_CLOCK_AFTER_MS
import com.example.dsh.message.buildQuestionAnswer
import com.example.dsh.message.contextCatalogEntries
import com.example.dsh.message.contextInstructions
import com.example.dsh.message.contextRecalls
import com.example.dsh.message.contextRelaySender
import com.example.dsh.message.contextSections
import com.example.dsh.voice.dshVoiceErrorMessage
import com.example.dsh.voice.dshVoiceStrings
import com.example.dsh.message.isRemoteCatalogInvalidationEvent
import com.example.dsh.message.parseGoalProjection
import com.example.dsh.connection.DshConnectionCoordinator
import com.example.dsh.connection.DshEngineModule
import com.example.dsh.connection.DshRelayModule
import com.example.dsh.connection.DshRelayPhase
import com.example.dsh.connection.DshSseModule
import com.example.dsh.connection.DshSshConfig
import com.example.dsh.connection.DshSshPhase
import com.example.dsh.connection.supportsRelayBridge
import com.example.dsh.models.DshAgentPresetOption
import com.example.dsh.host.DshConnectionMode
import com.example.dsh.session.DshDirectoryEntry
import com.example.dsh.export.DshExportFormat
import com.example.dsh.attachment.DshFileDraftState
import com.example.dsh.session.DshGoalSnapshot
import com.example.dsh.host.DshHostRepository
import com.example.dsh.host.DshRemoteHostRepository
import com.example.dsh.host.DshHostRuntimePhase
import com.example.dsh.host.DshHostRuntimeState
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
import com.example.dsh.plugin.DshPluginConfigSave
import com.example.dsh.plugin.DshPluginEntry
import com.example.dsh.plugin.DshPluginFieldKind
import com.example.dsh.models.DshProviderConfig
import com.example.dsh.models.DshProviderModel
import com.example.dsh.interaction.DshQuestionDraft
import com.example.dsh.session.DshQueueItem
import com.example.dsh.host.DshRawSessionEvent
import com.example.dsh.export.DshReadableContent
import com.example.dsh.session.DshRemoteProfile
import com.example.dsh.host.DshRemoteRepository
import com.example.dsh.tool.DshRemoteToolCallModels
import com.example.dsh.host.DshRepository
import com.example.dsh.host.DshRpcError
import com.example.dsh.session.DshSession
import com.example.dsh.session.DshSessionCacheState
import com.example.dsh.session.DshSessionCatalog
import com.example.dsh.session.DshSessionCatalogLoader
import com.example.dsh.session.DshSessionScope
import com.example.dsh.models.DshSettingsChoice
import com.example.dsh.models.DshSettingsSnapshot
import com.example.dsh.message.DshShareGroup
import com.example.dsh.session.DshSkill
import com.example.dsh.host.DshStreamHandle
import com.example.dsh.models.DshToolCardType
import com.example.dsh.message.DshWebTimelineItem
import com.example.dsh.session.DshWorkspaceGroup
import com.example.dsh.models.dshCreateCustomProvider
import com.example.dsh.message.dshHistoryTailToResume
import com.example.dsh.message.dshIsLiveAssistantText
import com.example.dsh.host.dshIsTransportInterrupt
import com.example.dsh.message.dshMessagesVisuallyEqual
import com.example.dsh.models.dshRemoveProviderProfile
import com.example.dsh.models.dshSaveProviderProfile
import com.example.dsh.message.dshShareGroupForMessage
import com.example.dsh.message.dshSyncMessageForkAnchor
import com.example.dsh.tool.dshWireEvent
import com.example.dsh.plugin.filterDshPlugins
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
import com.example.dsh.log.publishReadableExport
import com.example.dsh.log.shareExportFile
import com.example.dsh.ui.rendering.DSH_PREF_EXPAND_MODAL
import com.example.dsh.ui.rendering.DSH_PREF_PROCESS_DISPLAY
import com.example.dsh.ui.rendering.DSH_PREF_SHOW_CONNECTORS
import com.example.dsh.ui.rendering.DSH_PREF_SHOW_RESULT_CARDS
import com.example.dsh.ui.rendering.DshExpandedContentModal
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
import com.example.dsh.base.blurModule
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.directives.scrollToPosition
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.ListView
import com.tencent.kuikly.core.views.TextAreaView
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.CallbackRef
import com.tencent.kuikly.core.module.NotifyModule
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.base.BackPressCallback
import com.tencent.kuikly.core.datetime.DateTime
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
import com.example.dsh.attachment.DshAttachmentIntake
import com.example.dsh.export.DshExportSelection
import com.example.dsh.attachment.DshFileIntake
import com.example.dsh.attachment.DshImageIntake
import com.example.dsh.search.DshSessionSearch
import com.example.dsh.voice.DshVoiceWaveform
import com.example.dsh.attachment.composePromptWithFiles
import com.example.dsh.connection.dshConnectionModeLabel
import com.example.dsh.connection.dshReconnectLabel
import com.example.dsh.connection.dshSyncBusyLabel
import com.example.dsh.models.dshValidateCustomProvider
import com.example.dsh.models.dshValidateProviderDraft
import com.example.dsh.voice.dshVoiceCancelArmed
import com.example.dsh.voice.dshVoiceResolvedText
import com.example.dsh.voice.dshVoiceShouldCommit
import com.example.dsh.interaction.interactionFailureLabel
import com.example.dsh.message.messageRowKey
import com.example.dsh.attachment.parsePickedFiles
import com.example.dsh.attachment.parsePickedImages
import com.example.dsh.models.selectedReasoningEffortName
import com.example.dsh.host.attachmentIdsFromBlocks
import com.example.dsh.host.contextSummary
import com.example.dsh.host.DshHostConnection
import com.example.dsh.host.DshWebSocketModule
import com.example.dsh.host.inlineImageDataUrl
import com.example.dsh.host.textFromBlocks
import com.example.dsh.host.toolCardType
import com.example.dsh.base.setTimeout
import com.example.dsh.connection.DshSshSettingsValidation
import com.example.dsh.connection.dshValidateSshSettings
import com.tencent.kuikly.core.timer.setTimeout
import com.example.dsh.session.DSH_DRAWER_GROUP_FLAT
import com.example.dsh.session.DSH_DRAWER_GROUP_WORKSPACE
import com.example.dsh.session.DSH_DRAWER_ORDER_MANUAL
import com.example.dsh.session.DSH_DRAWER_ORDER_UPDATED
import com.example.dsh.session.DSH_DRAWER_ROW_HEIGHT
import com.example.dsh.session.DSH_WORKSPACE_DRAG_SCOPE
import com.example.dsh.session.dshApplyOrder
import com.example.dsh.session.DshArchiveConfirm
import com.example.dsh.session.DshArchiveConfirmKind
import com.example.dsh.session.DshArchivedSessions
import com.example.dsh.session.DshArchiveProjectOption
import com.example.dsh.session.DshArchiveSort
import com.example.dsh.session.dshDecodeOrder
import com.example.dsh.session.dshDecodeSessionOrder
import com.example.dsh.session.DshDrawerDrag
import com.example.dsh.session.DshDrawerDragKind
import com.example.dsh.session.dshDrawerGroupByKey
import com.example.dsh.session.dshDrawerOrderByKey
import com.example.dsh.session.dshDropIndex
import com.example.dsh.session.dshEncodeOrder
import com.example.dsh.session.dshEncodeSessionOrder
import com.example.dsh.export.DshExportSelectionTopBar
import com.example.dsh.log.DshLogPage
import com.example.dsh.models.DshModelsPage
import com.example.dsh.session.dshMoveItem
import com.example.dsh.session.dshSessionOrderKey
import com.example.dsh.export.DshTextExportDialog
import com.example.dsh.export.DshTextExportPhase
import com.example.dsh.export.DshTextExportState
import com.example.dsh.session.dshWorkspaceOrderKey
import com.example.dsh.session.DshWorkspacePickerModal
import com.example.dsh.session.DshWorkspacePickerScreen
import com.example.dsh.interaction.DshAgentModeOption
import com.example.dsh.interaction.DshAgentModePicker
import com.example.dsh.connection.DshConnectionSettingsModal
import com.example.dsh.connection.DshCredentialSetupModal
import com.example.dsh.models.DshModelPicker
import com.example.dsh.session.DshOverflowAction
import com.example.dsh.interaction.DshPermissionOption
import com.example.dsh.interaction.DshPermissionPicker
import com.example.dsh.settings.DshPersonalizationPage
import com.example.dsh.plugin.DshPluginSettingsView
import com.example.dsh.interaction.DshRiskConfirmationModal
import com.example.dsh.session.DshSessionArchiveDialog
import com.example.dsh.session.DshSessionDeleteDialog
import com.example.dsh.search.DshSessionDrawer
import com.example.dsh.session.DshSessionRenameDialog
import com.example.dsh.search.DshSessionSearchHit
import com.example.dsh.search.DshSessionSearchOverlay
import com.example.dsh.settings.DshSettingsChoicePicker
import com.example.dsh.settings.DshSettingsPage
import com.example.dsh.plugin.pluginActionLabel

private const val SESSION_CACHE_WARM_LIMIT = 7
/** 会话搜索结果行上限，避免超长列表拖慢渲染。 */
private const val SESSION_SEARCH_HIT_LIMIT = 80
private const val SESSION_CACHE_WARM_INTERVAL_MS = 16
private const val SESSION_CACHE_WARM_START_DELAY_MS = 600
private const val CONVERSATION_PANEL_CACHE_LIMIT = 8
private const val SCROLL_SETTLE_ATTEMPTS = 6
private val SCROLL_SETTLE_DELAYS_MS = intArrayOf(0, 16, 32, 64, 120, 200)
private const val FOLLOW_LIST_SLACK_PX = 72f

/** First usable DSH surface: local sessions, streaming Markdown, and a composer. */
@Page("home")
internal class DshHomePage : BasePager() {
    private var repository: DshRepository? = null
    /** 仅远程模式可用的能力面；本地模式为 null。 */
    private val remoteRepo: DshRemoteRepository? get() = repository as? DshRemoteRepository

    /** 下一帧执行：统一 Kuikly 的 setTimeout(pagerId, 0) 写法，明确“稍后执行”意图。 */
    private inline fun postToUi(crossinline block: () -> Unit) {
        setTimeout(pagerId, 0) { block() }
    }
    private var localStore: DshLocalStore? = null
    private var logJumpNotifyRef: CallbackRef? = null

    private var pageAlive = true
    private var exportDir = ""
    private var crashImportWork: DshLogWork<Boolean>? = null
    private var readableExportWork: DshLogWork<String>? = null
    private var readableExportVersion = 0
    private var readableExport by observable(DshTextExportState())
    private val readableExportBusy: Boolean get() = readableExport.busy
    private var readableExportDialogVisible by observable(false)
    private var readableExportSourceText: String? = null
    private var readableExportExtension = "txt"
    // ===== 分享多选态：消息列表勾选 + 底部格式弹窗 =====
    private var exportSelectMode by observable(false)
    private var exportSelectSessionId = ""
    private var pendingExportSelectionSessionId = ""
    private var pendingExportSelectionPreselect = ""
    // 以「对话组」为单位选择：key 由组内用户 Prompt（无则助手）消息 id 生成
    private var exportSelectedGroups by observable(emptySet<String>())
    private var exportFormat by observable(DshExportFormat.HTML)
    private var exportMoreShareVisible by observable(false)
    private var exportPdfBusy by observable(false)
    private var timelineReadVersion = 0
    private var pluginInventoryVisible by observable(false)
    private var pluginInventoryLoading by observable(false)
    private var pluginInventoryError by observable("")
    private var pluginKeyword by observable("")
    /** 搜索框原生文本：非响应式，只喂给 Input 的 text()，避免每次按键重设文本与原生输入互相覆盖。 */
    private var pluginSearchInput = ""
    /** 搜索框是否有内容，用于响应式显示清除按钮。 */
    private var pluginSearchHasText by observable(false)
    private var pluginSearchInputView: InputView? = null
    private var pluginPhase by observable("")
    private var pluginTotal by observable(0)
    private var pluginInventory = emptyList<DshPluginEntry>()
    private val pluginRows by observableList<DshPluginEntry>()
    private var pluginRequestVersion = 0
    private var pluginExpandedId by observable("")
    private var pluginActionTarget by observable<DshPluginEntry?>(null)
    private var pluginConfirmAction by observable("")
    private var pluginBusyId by observable("")
    private var pluginActionError by observable("")
    private var pluginNotice by observable("")
    private var pluginActiveTab by observable("config")
    private var pluginConfigLoading by observable(false)
    private var pluginConfigError by observable("")
    private var pluginConfigWritable by observable(true)
    private val pluginConfigCards by observableList<DshPluginConfigCard>()
    private var pluginConfigDrafts by observable<Map<String, String>>(emptyMap())
    private var pluginConfigSecretDrafts by observable<Map<String, String>>(emptyMap())
    private var pluginConfigCollapsed by observable<Set<String>>(emptySet())
    private var pluginConfigBusyNamespace by observable("")
    private var pluginConfigCardError by observable<Map<String, String>>(emptyMap())
    private var pluginConfigCardNotice by observable<Map<String, String>>(emptyMap())
    private var engineModule: DshEngineModule? = null
    private var engineReady = false
    private var relayEngineEndpoint = ""
    private var pendingApiKey = ""
    private var connectionMode by observable(DshConnectionMode.RELAY)
    private val sshMode: Boolean
        get() = connectionMode == DshConnectionMode.SSH
    private val isRemoteHost: Boolean
        get() = connectionMode == DshConnectionMode.RELAY || connectionMode == DshConnectionMode.SSH
    private var remoteProfileId by observable(DshSessionScope.DEFAULT_REMOTE_PROFILE_ID)
    private var sshHost by observable("")
    private var sshUser by observable("")
    private var sshPort by observable("22")
    private var sshDshPort by observable("3080")
    private var sshKeyId by observable("")
    private var sshFingerprint by observable("")
    private var sshKeyLabel by observable("未导入私钥")
    private var sshKeyPassphrase by observable("")
    private var sshSettingsVisible by observable(false)
    private var sshSettingsBusy by observable(false)
    private var sshSettingsError by observable("")
    private val sessionScope: DshSessionScope
        get() = DshSessionScope(connectionMode, remoteProfileId)
    private val activeConnectionId: String
        get() = sessionScope.storageKey

    // Metadata is read by attr/vif outside vfor too. Publish immutable snapshots
    // so a same-ID title/status change invalidates those readers.
    private var sessions by observable(emptyList<DshSession>())
    private val visibleSessions by observableList<DshSession>()
    private var messages by observableList<DshMessage>()
    private var conversationPanelIds by observableList<String>()
    private var activeSessionId by observable("session-1")
    private var preferBlankHomeOnNextLoad = true
    private var draft by observable("")
    private var streaming by observable(false)
    private var stopButtonVisible by observable(false)
    private var streamingAssistantContent by observable("")
    private var copiedMessageId by observable("")
    private var keyboardHeight by observable(0f)
    private var keyboardAnimation by observable(Animation.easeInOut(ANIMATION_DURATION_S))
    private var _connectionLabel by observable("本地内核启动中")
    private var connectionLabel: String
        get() = _connectionLabel
        set(value) {
            if (_connectionLabel != value) {
                _connectionLabel = value
                onConnectionLabelChanged(value)
            }
        }
    /** 连接状态胶囊可见性，从已连接变就绪时延迟 3s 后淡出隐藏 */
    private var connectionCapsuleVisible by observable(false)
    private var connectionCapsuleFadeOut by observable(false)
    private var connectionCapsuleFadeOutAnimation by observable(Animation.easeInOut(0.3f))
    private var connectionCapsuleVersion = 0
    private var apiKeyDraft by observable("")
    private var credentialSetupVisible by observable(false)
    private var credentialSetupBusy by observable(false)
    private var credentialSetupError by observable("")
    private var credentialSetupTitle by observable("添加一个 API Key 开始使用")
    private var sessionDrawerVisible by observable(false)
    private var sessionDrawerAnimated by observable(false)
    // 会话抽屉：工作区文件夹的展开/收起状态（内嵌菜单，会话内记忆）
    private var workspaceExpandedIds by observable(emptySet<String>())
    private var pendingSessionIds by observable(emptySet<String>())
    // 会话抽屉：从会话行 ⋯ 打开的 overflow menu 目标会话；为空时回退到当前会话
    private var overflowTargetSessionId by observable("")
    private var modelPickerVisible by observable(false)
    private var modelEffortsVisible by observable(false)
    private var modelPickerBusy by observable(false)
    private var modelPickerError by observable("")
    private var selectedModelLabel by observable("选择模型")
    private var selectedEffortLabel by observable("")
    private var modelOptions by observableList<DshModelOption>()
    private var modelRequestVersion = 0
    private var permissionPickerVisible by observable(false)
    private var riskConfirmVisible by observable(false)
    private var riskAcknowledged by observable(false)
    private var pendingDangerPermission: DshPermissionOption? = null
    private var permissionValue by observable("workspace-write")
    private var permissionLabel by observable("工作区写入")
    private var agentModePickerVisible by observable(false)
    private var agentModePickerTitle by observable("选择模式")
    private var agentModeValue by observable("standard")
    private var agentModeLabel by observable("标准模式")
    // 电脑端 agentPreset.list 拉取的预设（空则回退本地四项）
    private val agentPresetOptions by observableList<DshAgentPresetOption>()
    // 设置页（ds 风格）
    private var settingsPageVisible by observable(false)
    private var settingsLoading by observable(false)
    private var settingsError by observable("")
    private var settingsSnapshot by observable(DshSettingsSnapshot())
    private var hostVersion by observable("")
    private var settingsChoiceTitle by observable("")
    private var settingsChoiceKind by observable("")
    private var settingsChoiceBusy by observable(false)
    private val settingsChoiceOptions by observableList<DshSettingsChoice>()
    // 个性化「对话展示」：过程折叠方式（互斥）+ 弹窗查看开关（本地持久化）
    private var personalizationPageVisible by observable(false)
    private var chatProcessMode by observable(DshProcessDisplayMode.UNIFIED)
    private var chatExpandInModal by observable(false)
    private var chatShowConnectors by observable(true)
    private var chatShowResultCards by observable(true)
    private var expandedPayload by observable<DshExpandedPayload?>(null)
    // 设置页「模型」详情页（对齐电脑端 settings.models）
    private var modelsPageVisible by observable(false)
    private var modelsLoading by observable(false)
    private var modelsError by observable("")
    private var modelsWritable by observable(false)
    private val modelsProviders by observableList<DshProviderConfig>()
    private var modelsEditingProvider by observable("")
    private var modelsDraftBaseUrl by observable("")
    private var modelsDraftApiKey by observable("")
    private val modelsDraftModels by observableList<DshProviderModel>()
    private var modelsSaving by observable(false)
    private var modelsSaveError by observable("")
    private var modelsDeleteTarget by observable<DshProviderConfig?>(null)
    private var modelsDeleting by observable(false)
    private var modelsProtocols by observable<List<String>>(emptyList())
    private var modelsCustomRevision by observable(0)
    private val modelsConfiguredProviders by observableList<DshProviderConfig>()
    private val modelsAddableProviders by observableList<DshProviderConfig>()
    private var modelsAdding by observable(false)
    private var modelsPickerVisible by observable(false)
    private var modelsCustomAdding by observable(false)
    private var modelsEditorAdvanced by observable(false)
    private var modelsSavedNotice by observable("")
    private var modelsCustomRoute by observable("")
    private var modelsCustomName by observable("")
    private var modelsCustomBaseUrl by observable("")
    private var modelsCustomProtocol by observable("")
    private var modelsCustomApiKey by observable("")
    private val modelsCustomModels by observableList<DshProviderModel>()
    private var modelsCustomBusy by observable(false)
    private var modelsCustomError by observable("")
    private var commandSheetVisible by observable(false)
    private var voiceActive by observable(false)
    // ===== 语音输入（按住说话 → 原生语音识别 → 转文字填入输入框）=====
    /** 录音浮层是否可见（按下「按住说话」到松手/取消之间为真）。 */
    private var voiceRecording by observable(false)
    /** 手指上滑是否已进入取消区间（跟随移动实时更新）。 */
    private var voiceCancelArmed by observable(false)
    /** 录音过程中的中间识别文本。 */
    private var voicePartialText by observable("")
    /** 滚动音量窗口（0..1），驱动浮层蓝色方块高度。 */
    private val voiceWaveformModel = DshVoiceWaveform()
    /** 每次音量采样 +1，驱动波形整体重绘。 */
    private var voiceWaveformRevision by observable(0)
    /** 录音会话代次，防止衰减定时器跨会话重复运行。 */
    private var voiceDecayGeneration = 0
    /** 按下点 pageY，用于计算上滑取消。 */
    private var voiceHoldStartY = 0f
    /** 已松手（等待原生最终结果）。 */
    private var voiceStopping = false
    /** 已请求取消，结果不入输入框。 */
    private var voiceCancelRequested = false
    /** 已到达的最终识别文本。 */
    private var voiceCommittedText = ""
    /** 松手兜底计时是否已排期。 */
    private var voiceStopFallbackScheduled = false
    /** 音量事件节流时间戳。 */
    private var voiceLastLevelAt = 0L
    /** 语音转文字结果：待输入框挂载后回填到原生 TextArea。 */
    private var pendingVoiceDraft: String? = null
    private var inputView: TextAreaView? = null
    private var apiKeyInputView: InputView? = null
    private var streamHandle: DshStreamHandle? = null
    private val messageScrollerRefs = mutableMapOf<String, ViewRef<ListView<*, *>>>()
    private val messageRowRefs = mutableMapOf<String, ViewRef<com.tencent.kuikly.core.views.DivView>>()
    private var historyRequestGeneration = 0
    private val sessionMessageStates = mutableMapOf<String, ObservableList<DshMessage>>()
    private val conversationListEpochs = mutableMapOf<String, Int>()
    private var conversationListEpoch by observable(0)
    private val sessionMessageReady = mutableSetOf<String>()
    private val pendingSessionSelections = mutableSetOf<String>()
    private val localReadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val pendingLocalMessageReads = mutableSetOf<String>()
    private val sessionCacheStates = mutableMapOf<String, DshSessionCacheState>()
    private var inputFocused = false
    private var streamingAssistantId by observable("")
    // The root id guards callbacks from an old request; the visible id points
    // at the current text segment between ordered tool cards.
    private var streamingAssistantRootId = ""
    private var streamingAssistantSegment = 0
    private var streamingSourceSeq: Int? = null
    // Last completed assistant when the current prompt was sent. Resync must
    // not graft the new stream onto that bubble.
    private var streamingTurnAnchorAssistantId = ""
    private var streamingReasoningId = ""
    private var streamingReasoningContent = ""
    private val pendingAssistantDelta = StringBuilder()
    private var assistantFlushScheduled = false
    private var scrollSettleGeneration = 0
    private var followListTail = true
    private val connectionCoordinator = DshConnectionCoordinator()
    private val webDisclosureStates = mutableMapOf<String, Boolean>()
    private val webJsonNodeStates = mutableMapOf<String, Boolean>()
    private var webDisclosureRevision by observable(0)
    private var attachmentRevision by observable(0)
    private var previewImageUrl by observable<String?>(null)
    private val cachedAttachmentDataUrls = mutableMapOf<String, String>()
    private val pendingAttachmentReads = mutableSetOf<String>()
    // ===== Task 3 附件：输入区图片草稿（仅内存，不落盘）与 Host 下发限额 =====
    private val pendingImages by observableList<DshPendingImage>()
    // 通用文件草稿：旧 Host 无文件 content 类型，发送前经 host-plugin 落盘并写入 prompt handle。
    private val pendingFiles by observableList<DshPendingFile>()
    private var attachmentEpoch by observable(0)
    private var imageLimits by observable<DshImageLimits?>(null)
    private var queueDockExpanded by observable(false)
    private var queueItems by observableList<DshQueueItem>()
    private var queueActionBusy by observable(false)
    private var jobItems by observableList<DshJobItem>()
    private var jobsPanelExpanded by observable(false)
    private var jobsNow by observable(0L)
    private var jobsClockScheduled by observable(false)
    private var liveJobItems by observableList<DshJobItem>()
    private var workspaceGroups by observableList<DshWorkspaceGroup>()
    private val skills by observableList<DshSkill>()
    private var goalSnapshot by observable<DshGoalSnapshot?>(null)
    private var goalActionBusy by observable(false)
    private var goalActionError by observable("")
    private var queueEditingId by observable("")
    private var queueEditingText by observable("")
    private var sessionRunning by observable(false)
    private var turnElapsedMs by observable(0L)
    private var turnStatusMark: TimeMark? = null
    private var turnStatusTickerGeneration = 0
    private var turnStatusClockBucket = -1L
    // ===== 新建会话-工作区选择（最近的文件夹 / 添加文件夹） =====
    private var workspacePickerVisible by observable(false)
    private var workspacePickerScreen by observable(DshWorkspacePickerScreen.RECENT)
    private var workspacePickerBusy by observable(false)
    private var workspacePickerError by observable("")
    private var workspaceAddPath by observable("")
    private var workspaceAddHome by observable("")
    private var workspaceAddBusy by observable(false)
    private var workspaceAddNewName by observable("")
    private val workspaceAddEntries by observableList<DshDirectoryEntry>()
    // 「最近的文件夹」只列真实工作区（排除「未分组」占位），供 vfor 直接迭代。
    private val workspacePickerFolders by observableList<DshWorkspaceGroup>()
    private var workspacePickerGeneration = 0
    private var workspaceRenameTargetId by observable("")
    private var workspaceRenameDraft by observable("")
    private var workspaceDeleteTargetId by observable("")
    private var workspaceActionBusy by observable(false)
    private var workspaceActionError by observable("")
    // ===== 会话 overflow menu 与会话管理动作 =====
    private var overflowMenuVisible by observable(false)
    // overflow menu 的锚点（点击会话行 ⋯ 的屏幕坐标）；-1 表示无锚点，回退到默认位置。
    private var overflowAnchorX by observable(-1f)
    private var overflowAnchorY by observable(-1f)

    private val overlayBackCallback = object : BackPressCallback() {
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

    private var sessionRenameVisible by observable(false)
    private var sessionActionTargetId by observable("")
    private var sessionRenameDraft by observable("")
    private var sessionRenameBusy by observable(false)
    private var sessionRenameError by observable("")
    private var sessionArchiveVisible by observable(false)
    private var sessionArchiveBusy by observable(false)
    private var sessionArchiveError by observable("")
    private var archiveListVisible by observable(false)
    private var archiveListLoading by observable(false)
    private var archiveListError by observable("")
    private var archiveOpeningId by observable("")
    private val archivedSessions by observableList<DshSession>()
    private var archiveRequestGeneration = 0L
    private var archiveSearch by observable("")
    private var archiveProjectFilter by observable("")
    private var archiveSort by observable(DshArchiveSort.UPDATED)
    private var archiveMenu by observable("")
    private var archiveBusy by observable(false)
    private var archiveNotice by observable("")
    private var archiveConfirm by observable<DshArchiveConfirm?>(null)
    private val archiveGroups by observableList<DshWorkspaceGroup>()
    private val archiveProjectOptions by observableList<DshArchiveProjectOption>()
    private var sessionCreatedAt by observable<Map<String, Long>>(emptyMap())
    // ===== 全屏会话搜索 =====
    /** 搜索界面是否展示（由抽屉顶部搜索入口打开）。 */
    private var sessionSearchVisible by observable(false)
    /** 搜索框原生文本：非响应式，只喂给 Input 的 text()，避免每次按键重设文本与原生输入互相覆盖。 */
    private var sessionSearchInput = ""
    /** 搜索框是否有内容，驱动结果列表与清除按钮。 */
    private var sessionSearchActive by observable(false)
    private val sessionSearchHits by observableList<DshSessionSearchHit>()
    private var sessionSearchInputView: InputView? = null
    // ===== 会话抽屉长按拖拽排序 =====
    private var drawerDrag by observable(DshDrawerDrag())
    private var sessionManualOrder by observable<Map<String, List<String>>>(emptyMap())
    private var workspaceManualOrder by observable<List<String>>(emptyList())
    private var drawerOrderScope = ""
    // ===== 抽屉视图选项（对齐电脑端 WorkspaceBrowser）=====
    private var drawerViewGroupBy by observable(DSH_DRAWER_GROUP_WORKSPACE)
    private var drawerViewOrderBy by observable(DSH_DRAWER_ORDER_UPDATED)
    private var drawerViewOptionsVisible by observable(false)
    private var drawerViewOptionsX by observable(-1f)
    private var drawerViewOptionsY by observable(-1f)
    private var dragOriginPageY = 0f
    private var prefsModule: SharedPreferencesModule? = null
    private var catalogRequestGeneration = 0L
    private var sessionDeleteVisible by observable(false)
    private var sessionDeleteBusy by observable(false)
    private var sessionDeleteError by observable("")
    private var pendingApproval by observable<DshPendingApproval?>(null)
    private var pendingQuestion by observable<DshPendingQuestion?>(null)
    private var interactionBusy by observable(false)
    private val selectedQuestionOptions by observableList<String>()
    private var questionCustom by observable("")
    private var questionIndex by observable(0)
    private var questionError by observable("")
    private var questionHasSelection by observable(false)
    private var messageActionsMessage by observable<DshMessage?>(null)
    private var messageForkBusy = false
    private var messageForkVersion = 0
    /** 附件上传阶段的发送防重入：上传完成后用首次点击的快照继续发送。 */
    private var sendInFlight = false
    private var messageActionsX by observable(0f)
    private var messageActionsY by observable(0f)
    private var menuBlurUri by observable("")
    // 「选择文本」弹窗：以单个可选中文本节点承载完整正文，供原生选区复制
    private var selectTextModalVisible by observable(false)
    private var selectTextModalContent by observable("")
    private val questionDrafts = mutableMapOf<Int, DshQuestionDraft>()

    /** connectionLabel 变化时更新胶囊可见性，就绪态延迟 3s 后淡出隐藏 */
    private fun onConnectionLabelChanged(label: String) {
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


    private fun bodyTopBar(): ViewBuilder {
        val ctx = this
        return {
            // ===== 顶部栏 =====
            // 58dp 高的标题栏容器（zIndex 置顶），内部是 DshTopBar：
            // 左侧菜单/标题点击打开会话抽屉，右上角 overflow menu 打开会话管理菜单。
            View {
                attr {
                    height(58f)
                    zIndex(3)
                }
                vif({ ctx.exportSelectMode }) {
                    DshExportSelectionTopBar(
                        totalCount = { ctx.exportTotalCount() },
                        allSelected = { ctx.exportAllSelected() },
                        onToggleAll = { ctx.toggleExportSelectAll() },
                        onClose = { ctx.cancelExportSelection() },
                        colors = { this@DshHomePage.themeColors },
                    )
                }
                velse {
                    DshTopBar(
                        title = { ctx.sessions.firstOrNull { it.id == ctx.activeSessionId }?.title ?: "DeepSeek Harness" },
                        onOpenDrawer = {
                            ctx.dismissKeyboard()
                            ctx.openSessionDrawer()
                        },
                        onNewSession = { ctx.createSession() },
                        colors = { this@DshHomePage.themeColors },
                    )
                }
            }

        }
    }

    private fun bodyMainContent(): ViewBuilder {
        val ctx = this
        val wide = pagerData.pageViewWidth >= 720f
        return {
            // ===== 主内容容器 =====
            // 撑满剩余空间，容纳下方的会话栏/对话区/详情面板与抽屉遮罩。
            // 会话抽屉打开时整体右移（translate），露出右侧被半透明遮罩覆盖的内容边缘，带位移动画。
            View {
                attr {
                    flex(1f)
                    flexDirectionColumn()
                    // Push the conversation with the drawer, leaving the
                    // dimmed right edge visible like the reference UI.
                    transform(Translate(
                        0f,
                        offsetX = if (ctx.sessionDrawerAnimated) {
                            (pagerData.pageViewWidth - 44f).coerceAtMost(340f)
                        } else {
                            0f
                        },
                    ))
                    animation(Animation.easeOut(ANIMATION_DURATION_S), ctx.sessionDrawerAnimated)
                }
                if (wide) {
                    // ==== 宽屏（平板/桌面）三栏布局容器 ====
                    View {
                        attr {
                            flex(1f)
                            flexDirectionRow()
                            backgroundColor(this@DshHomePage.themeColors.bgBase)
                        }
                        // -- 左侧「会话栏」--：仅远程（扫码/SSH）模式显示，列出所有会话，点击切换。
                        vif({ ctx.isRemoteHost }) {
                            DshSessionRail(
                                sessions = { ctx.visibleSessions },
                                activeId = { ctx.activeSessionId },
                                compact = false,
                                onSelect = { id ->
                                    ctx.closeSessionDrawer()
                                    setTimeout(ctx.pagerId, 0) { ctx.selectSession(id) }
                                },
                                colors = { this@DshHomePage.themeColors },
                            )
                        }
                        // centerWidth：中间对话区可用宽度 = 总宽 - 左会话栏(236) - 右详情面板(280)，最小 360。
                        val centerWidth = if (ctx.isRemoteHost) {
                            (ctx.pagerData.pageViewWidth - 236f - 280f).coerceAtLeast(360f)
                        } else {
                            ctx.pagerData.pageViewWidth
                        }
                        // -- 中间「对话区」--：核心聊天界面 = 消息列表 + 底部输入区。
                        //    输入区内含技能、模型选择、附件、语音、停止按钮，
                        //    以及远程模式下的任务队列/作业面板/目标/审批与提问等。
                        DshConversation(
                            conversationIds = { ctx.conversationPanelIds },
                            activeConversationId = { ctx.activeSessionId },
                            messagesForSession = { ctx.sessionMessageState(it) },
                            streaming = { ctx.streaming },
                            streamingMessageId = { ctx.streamingAssistantId },
                            streamingContent = { ctx.streamingAssistantContent },
                            scrollerRef = { id, ref -> ctx.messageScrollerRefs[id] = ref },
                            messageRef = { sessionId, messageId, ref ->
                                ctx.messageRowRefs[messageRowKey(sessionId, messageId)] = ref
                            },
                            draft = { ctx.draft },
                            skills = { ctx.skills },
                            onPickSkill = { ctx.insertDraftText("/$it ") },
                            keyboardHeight = { ctx.keyboardHeight },
                            stopButtonVisible = { ctx.stopButtonVisible },
                            inputRef = { ctx.onInputViewCreated(it.view) },
                            onInputFocusChange = { ctx.inputFocused = it },
                            onDraftChange = { ctx.draft = it },
                            keyboardAnimation = { ctx.keyboardAnimation },
                            onKeyboardHeightChange = { ctx.updateKeyboard(it) },
                            onSend = { ctx.sendDraft() },
                            onStop = { ctx.stopStream() },
                            onDismissKeyboard = { ctx.dismissKeyboard() },
                            onUserListScroll = { ctx.onConversationUserScroll(it) },
                            modelLabel = { if (ctx.selectedEffortLabel.isEmpty()) ctx.selectedModelLabel else "${ctx.selectedModelLabel} · ${ctx.selectedEffortLabel}" },
                            commandSheetVisible = { ctx.commandSheetVisible },
                            voiceActive = { ctx.voiceActive },
                            onOpenModels = { ctx.openModelPicker() },
                            onToggleCommandSheet = { ctx.toggleCommandSheet() },
                            onPickCommand = { ctx.insertCommand(it) },
                            onAttachmentTile = { ctx.onAttachmentTile(it) },
                        attachmentEpoch = { ctx.attachmentEpoch },
                        attachmentRevision = { ctx.attachmentRevision },
                        pendingImages = { ctx.pendingImages },
                        onRemovePendingImage = { ctx.removePendingImage(it) },
                        onRetryPendingImage = { ctx.retryPendingImage(it) },
                        pendingFiles = { ctx.pendingFiles },
                        onRemovePendingFile = { ctx.removePendingFile(it) },
                        onRetryPendingFile = { ctx.retryPendingFile(it) },
                            onToggleVoice = { ctx.toggleVoice() },
                            voiceRecording = { ctx.voiceRecording },
                            voiceCancelArmed = { ctx.voiceCancelArmed },
                            voicePartialText = { ctx.voicePartialText },
                            voiceWaveform = { ctx.voiceWaveformModel.levels },
                            voiceWaveformRevision = { ctx.voiceWaveformRevision },
                            voiceStrings = { dshVoiceStrings(ctx.settingsSnapshot.localeValue) },
                            onVoiceHoldStart = { ctx.beginVoiceRecord(it) },
                            onVoiceHoldMove = { ctx.updateVoiceHold(it) },
                            onVoiceHoldEnd = { ctx.endVoiceRecord() },
                            folderLabel = { ctx.composerFolderLabel() },
                            onOpenWorkspacePicker = { ctx.openWorkspacePicker() },
                            permissionValue = { ctx.permissionValue },
                            permissionLabel = { ctx.permissionLabel },
                            onOpenPermissions = { ctx.openPermissionPicker() },
                            agentModeLabel = { ctx.agentModeLabel },
                            onOpenAgentModes = { ctx.openAgentModePicker() },
                            isWebTimeline = { ctx.isRemoteHost },
                            processDisplayMode = { ctx.chatProcessMode },
                            showConnectors = { ctx.chatShowConnectors },
                            showResultCards = { ctx.chatShowResultCards },
                            expandInModal = { ctx.chatExpandInModal },
                            onOpenExpandedModal = { ctx.openExpandedModal(it) },
                            isDisclosureExpanded = { ctx.isWebDisclosureExpanded(it) },
                            onToggleDisclosure = { ctx.toggleWebDisclosure(it) },
                            isJsonNodeExpanded = { messageId, nodeId ->
                                ctx.isWebJsonNodeExpanded(messageId, nodeId)
                            },
                            onToggleJsonNode = { messageId, nodeId ->
                                ctx.toggleWebJsonNode(messageId, nodeId)
                            },
                            onCopyToolContent = {
                                ctx.bridgeModule.copyToPasteboard(it)
                                ctx.bridgeModule.toast("已复制")
                            },
                            onCopyMessageContent = { msg -> ctx.copyMessageBody(msg) },
                            copiedMessageId = { ctx.copiedMessageId },
                            colors = { this@DshHomePage.themeColors },
                            onMessageLongPress = { msg, content, px, py ->
                                ctx.openMessageActions(msg, content, px, py)
                            },
                            onFooterAction = { msg, action -> ctx.onMessageFooterAction(msg, action) },
                             attachmentDataUrl = { ctx.attachmentDataUrl(it) },
                             queueItems = { ctx.queueItems },
                             jobItems = { ctx.jobItems },
                             liveJobItems = { ctx.liveJobItems },
                             goal = { ctx.goalSnapshot },
                            goalActionBusy = { ctx.goalActionBusy },
                            goalActionError = { ctx.goalActionError },
                            onPauseGoal = { ctx.pauseGoal() },
                            onResumeGoal = { ctx.resumeGoal() },
                            onEditGoal = { text, done -> ctx.editGoal(text, done) },
                            onClearGoal = { ctx.clearGoal() },
                            jobsPanelExpanded = { ctx.jobsPanelExpanded },
                            jobsNow = { ctx.jobsNow },
                            onToggleJobsPanel = { ctx.toggleJobsPanel() },
                            queueExpanded = { ctx.queueDockExpanded },
                            queueEditingId = { ctx.queueEditingId },
                            queueActionBusy = { ctx.queueActionBusy },
                            queueEditingText = { ctx.queueEditingText },
                            sessionRunning = { ctx.sessionRunning },
                            isBlankConversation = { ctx.isBlankSession() },
                            conversationListEpoch = { ctx.conversationListEpochFor(it) },
                            turnReconnecting = { isReconnectLabel(ctx.connectionLabel) },
                            turnElapsedMs = { ctx.turnElapsedMs },
                            onToggleQueue = { ctx.queueDockExpanded = !ctx.queueDockExpanded },
                            onEditQueueItem = { ctx.editQueueItem(it) },
                            onQueueEditingTextChange = { ctx.queueEditingText = it },
                            onSaveQueueItem = { ctx.saveQueueItem(it) },
                            onCancelQueueItemEdit = { ctx.cancelQueueItemEdit() },
                            onRemoveQueueItem = { ctx.removeQueueItem(it) },
                            onSteerQueueItem = { ctx.steerQueueItem(it) },
                            pendingApproval = { ctx.pendingApproval },
                            pendingQuestion = { ctx.pendingQuestion },
                            interactionBusy = { ctx.interactionBusy },
                            selectedQuestionOptions = { ctx.selectedQuestionOptions },
                            questionCustom = { ctx.questionCustom },
                            questionIndex = { ctx.questionIndex },
                            questionError = { ctx.questionError },
                            questionHasSelection = { ctx.questionHasSelection },
                            onAnswerApproval = { ctx.answerApproval(it) },
                            onToggleQuestionOption = { ctx.toggleQuestionOption(it) },
                            onQuestionCustomChange = { ctx.updateQuestionCustom(it) },
                            onQuestionNavigate = { ctx.navigateQuestion(it) },
                            onQuestionSkip = { ctx.skipQuestion() },
                            onSubmitQuestion = { ctx.submitQuestion() },
                            onDismissQuestion = { ctx.cancelQuestion() },
                            availableWidth = centerWidth,
                            connectionLabel = { ctx.connectionLabel },
                            connectionCapsuleVisible = { ctx.connectionCapsuleVisible },
                            connectionCapsuleFadeOut = { ctx.connectionCapsuleFadeOut },
                            connectionCapsuleFadeOutAnimation = { ctx.connectionCapsuleFadeOutAnimation },
                            onPreviewImage = { ctx.previewImageUrl = it },
                            previewImageUrl = { ctx.previewImageUrl },
                            onDismissPreview = { ctx.previewImageUrl = null },
                            onSaveImage = { ctx.saveImageToGallery(it) },
                            exportSelectMode = { ctx.exportSelectMode },
                            exportSelectedIds = { ctx.exportSelectedMessageIds() },
                            exportFormat = { ctx.exportFormat },
                            exportSelectedCount = { ctx.exportSelectedCount() },
                            exportMoreShareExpanded = { ctx.exportMoreShareVisible },
                            exportPdfBusy = { ctx.exportPdfBusy },
                            onToggleExportMessage = { ctx.toggleExportMessage(it) },
                            onExportFormatChange = { ctx.exportFormat = it },
                            onExportConfirm = { ctx.confirmExportSelection() },
                            onExportPdf = { ctx.exportSelectionAsPdf() },
                            onExportCopy = { ctx.copyExportSelection() },
                            onExportMore = { ctx.toggleExportMoreShare() },
                            onExportClose = { ctx.cancelExportSelection() },
                        )
                        // -- 右侧「会话详情面板」--：仅远程模式显示，展示当前会话的标题、
                        //    工作目录、模型、运行状态、队列/作业数量。
                        vif({ ctx.isRemoteHost }) {
                            DshSessionDetailsPanel(
                                title = { ctx.sessions.firstOrNull { it.id == ctx.activeSessionId }?.title ?: "尚无标题" },
                                cwd = { ctx.sessions.firstOrNull { it.id == ctx.activeSessionId }?.cwd ?: "" },
                                modelLabel = { if (ctx.selectedEffortLabel.isEmpty()) ctx.selectedModelLabel else "${ctx.selectedModelLabel} · ${ctx.selectedEffortLabel}" },
                                agentPreset = { ctx.sessions.firstOrNull { it.id == ctx.activeSessionId }?.agentPreset.orEmpty() },
                                running = { ctx.sessionRunning },
                                queueCount = { ctx.queueItems.size },
                                jobCount = { ctx.jobItems.size },
                                colors = { this@DshHomePage.themeColors },
                            )
                        }
                    }
                } else {
                    // ==== 窄屏（手机）单栏布局 ====
                    // 不显示会话栏/详情面板，对话区直接铺满整宽。
                    DshConversation(
                        conversationIds = { ctx.conversationPanelIds },
                        activeConversationId = { ctx.activeSessionId },
                        messagesForSession = { ctx.sessionMessageState(it) },
                        streaming = { ctx.streaming },
                        streamingMessageId = { ctx.streamingAssistantId },
                        streamingContent = { ctx.streamingAssistantContent },
                        scrollerRef = { id, ref -> ctx.messageScrollerRefs[id] = ref },
                        messageRef = { sessionId, messageId, ref ->
                            ctx.messageRowRefs[messageRowKey(sessionId, messageId)] = ref
                        },
                        draft = { ctx.draft },
                        skills = { ctx.skills },
                        onPickSkill = { ctx.insertDraftText("/$it ") },
                        keyboardHeight = { ctx.keyboardHeight },
                        stopButtonVisible = { ctx.stopButtonVisible },
                        inputRef = { ctx.onInputViewCreated(it.view) },
                        onInputFocusChange = { ctx.inputFocused = it },
                        onDraftChange = { ctx.draft = it },
                        keyboardAnimation = { ctx.keyboardAnimation },
                        onKeyboardHeightChange = { ctx.updateKeyboard(it) },
                        onSend = { ctx.sendDraft() },
                        onStop = { ctx.stopStream() },
                        onDismissKeyboard = { ctx.dismissKeyboard() },
                        onUserListScroll = { ctx.onConversationUserScroll(it) },
                        modelLabel = { if (ctx.selectedEffortLabel.isEmpty()) ctx.selectedModelLabel else "${ctx.selectedModelLabel} · ${ctx.selectedEffortLabel}" },
                        commandSheetVisible = { ctx.commandSheetVisible },
                        voiceActive = { ctx.voiceActive },
                        onOpenModels = { ctx.openModelPicker() },
                        permissionValue = { ctx.permissionValue },
                        permissionLabel = { ctx.permissionLabel },
                        onOpenPermissions = { ctx.openPermissionPicker() },
                        agentModeLabel = { ctx.agentModeLabel },
                        onOpenAgentModes = { ctx.openAgentModePicker() },
                        onToggleCommandSheet = { ctx.toggleCommandSheet() },
                        onPickCommand = { ctx.insertCommand(it) },
                        onAttachmentTile = { ctx.onAttachmentTile(it) },
                        attachmentEpoch = { ctx.attachmentEpoch },
                        attachmentRevision = { ctx.attachmentRevision },
                        pendingImages = { ctx.pendingImages },
                        onRemovePendingImage = { ctx.removePendingImage(it) },
                        onRetryPendingImage = { ctx.retryPendingImage(it) },
                        pendingFiles = { ctx.pendingFiles },
                        onRemovePendingFile = { ctx.removePendingFile(it) },
                        onRetryPendingFile = { ctx.retryPendingFile(it) },
                        onToggleVoice = { ctx.toggleVoice() },
                            voiceRecording = { ctx.voiceRecording },
                            voiceCancelArmed = { ctx.voiceCancelArmed },
                            voicePartialText = { ctx.voicePartialText },
                            voiceWaveform = { ctx.voiceWaveformModel.levels },
                            voiceWaveformRevision = { ctx.voiceWaveformRevision },
                            voiceStrings = { dshVoiceStrings(ctx.settingsSnapshot.localeValue) },
                            onVoiceHoldStart = { ctx.beginVoiceRecord(it) },
                            onVoiceHoldMove = { ctx.updateVoiceHold(it) },
                            onVoiceHoldEnd = { ctx.endVoiceRecord() },
                        folderLabel = { ctx.composerFolderLabel() },
                        onOpenWorkspacePicker = { ctx.openWorkspacePicker() },
                        isWebTimeline = { ctx.isRemoteHost },
                        processDisplayMode = { ctx.chatProcessMode },
                        showConnectors = { ctx.chatShowConnectors },
                        showResultCards = { ctx.chatShowResultCards },
                        expandInModal = { ctx.chatExpandInModal },
                        onOpenExpandedModal = { ctx.openExpandedModal(it) },
                        isDisclosureExpanded = { ctx.isWebDisclosureExpanded(it) },
                        onToggleDisclosure = { ctx.toggleWebDisclosure(it) },
                        isJsonNodeExpanded = { messageId, nodeId ->
                            ctx.isWebJsonNodeExpanded(messageId, nodeId)
                        },
                        onToggleJsonNode = { messageId, nodeId ->
                            ctx.toggleWebJsonNode(messageId, nodeId)
                        },
                        onCopyToolContent = {
                            ctx.bridgeModule.copyToPasteboard(it)
                            ctx.bridgeModule.toast("已复制")
                        },
                        onCopyMessageContent = { msg -> ctx.copyMessageBody(msg) },
                        copiedMessageId = { ctx.copiedMessageId },
                        colors = { this@DshHomePage.themeColors },
                        onMessageLongPress = { msg, content, px, py ->
                            ctx.openMessageActions(msg, content, px, py)
                        },
                            onFooterAction = { msg, action -> ctx.onMessageFooterAction(msg, action) },
                         attachmentDataUrl = { ctx.attachmentDataUrl(it) },
                         queueItems = { ctx.queueItems },
                         jobItems = { ctx.jobItems },
                         liveJobItems = { ctx.liveJobItems },
                         goal = { ctx.goalSnapshot },
                        goalActionBusy = { ctx.goalActionBusy },
                        goalActionError = { ctx.goalActionError },
                        onPauseGoal = { ctx.pauseGoal() },
                        onResumeGoal = { ctx.resumeGoal() },
                        onEditGoal = { text, done -> ctx.editGoal(text, done) },
                        onClearGoal = { ctx.clearGoal() },
                        jobsPanelExpanded = { ctx.jobsPanelExpanded },
                        jobsNow = { ctx.jobsNow },
                        onToggleJobsPanel = { ctx.toggleJobsPanel() },
                        queueExpanded = { ctx.queueDockExpanded },
                        queueEditingId = { ctx.queueEditingId },
                        queueActionBusy = { ctx.queueActionBusy },
                        queueEditingText = { ctx.queueEditingText },
                        sessionRunning = { ctx.sessionRunning },
                        isBlankConversation = { ctx.isBlankSession() },
                        conversationListEpoch = { ctx.conversationListEpochFor(it) },
                        turnReconnecting = { isReconnectLabel(ctx.connectionLabel) },
                        turnElapsedMs = { ctx.turnElapsedMs },
                        onToggleQueue = { ctx.queueDockExpanded = !ctx.queueDockExpanded },
                        onEditQueueItem = { ctx.editQueueItem(it) },
                        onQueueEditingTextChange = { ctx.queueEditingText = it },
                        onSaveQueueItem = { ctx.saveQueueItem(it) },
                        onCancelQueueItemEdit = { ctx.cancelQueueItemEdit() },
                        onRemoveQueueItem = { ctx.removeQueueItem(it) },
                        onSteerQueueItem = { ctx.steerQueueItem(it) },
                        pendingApproval = { ctx.pendingApproval },
                        pendingQuestion = { ctx.pendingQuestion },
                        interactionBusy = { ctx.interactionBusy },
                        selectedQuestionOptions = { ctx.selectedQuestionOptions },
                        questionCustom = { ctx.questionCustom },
                        questionIndex = { ctx.questionIndex },
                        questionError = { ctx.questionError },
                        questionHasSelection = { ctx.questionHasSelection },
                        onAnswerApproval = { ctx.answerApproval(it) },
                        onToggleQuestionOption = { ctx.toggleQuestionOption(it) },
                        onQuestionCustomChange = { ctx.updateQuestionCustom(it) },
                        onQuestionNavigate = { ctx.navigateQuestion(it) },
                        onQuestionSkip = { ctx.skipQuestion() },
                        onSubmitQuestion = { ctx.submitQuestion() },
                        onDismissQuestion = { ctx.cancelQuestion() },
                        availableWidth = ctx.pagerData.pageViewWidth,
                        connectionLabel = { ctx.connectionLabel },
                        connectionCapsuleVisible = { ctx.connectionCapsuleVisible },
                        connectionCapsuleFadeOut = { ctx.connectionCapsuleFadeOut },
                        connectionCapsuleFadeOutAnimation = { ctx.connectionCapsuleFadeOutAnimation },
                        onPreviewImage = { ctx.previewImageUrl = it },
                        previewImageUrl = { ctx.previewImageUrl },
                        onDismissPreview = { ctx.previewImageUrl = null },
                        onSaveImage = { ctx.saveImageToGallery(it) },
                        exportSelectMode = { ctx.exportSelectMode },
                        exportSelectedIds = { ctx.exportSelectedMessageIds() },
                        exportFormat = { ctx.exportFormat },
                        exportSelectedCount = { ctx.exportSelectedCount() },
                        exportMoreShareExpanded = { ctx.exportMoreShareVisible },
                        exportPdfBusy = { ctx.exportPdfBusy },
                        onToggleExportMessage = { ctx.toggleExportMessage(it) },
                        onExportFormatChange = { ctx.exportFormat = it },
                        onExportConfirm = { ctx.confirmExportSelection() },
                        onExportPdf = { ctx.exportSelectionAsPdf() },
                        onExportCopy = { ctx.copyExportSelection() },
                        onExportMore = { ctx.toggleExportMoreShare() },
                        onExportClose = { ctx.cancelExportSelection() },
                    )
                }
            }

        }
    }

    private fun bodySessionDrawer(): ViewBuilder {
        val ctx = this
        return {
            // ===== 会话抽屉 =====
            // 从左侧滑出的侧栏（覆盖在主内容之上）：顶部搜索框、会话列表/工作区分组、
            // 已归档会话与设置入口；点击遮罩或选择会话后关闭。
            vif({ ctx.sessionDrawerVisible }) {
                DshSessionDrawer(
                    sessions = { ctx.visibleSessions },
                    workspaceGroups = { ctx.workspaceGroups },
                    isWebTimeline = { ctx.isRemoteHost },
                    activeId = { ctx.activeSessionId },
                    animated = { ctx.sessionDrawerAnimated },
                    expandedGroupIds = { ctx.workspaceExpandedIds },
                    onToggleGroup = { ctx.toggleWorkspaceExpanded(it) },
                    sessionPending = { ctx.sessionPending(it) },
                    activeWorkspaceId = { ctx.activeWorkspaceId() },
                    overflowVisible = { ctx.overflowMenuVisible },
                    overflowActions = { ctx.overflowActions() },
                    onOverflowSelect = { ctx.onOverflowAction(it) },
                    onDismissOverflow = { ctx.closeOverflowMenu() },
                    onOpenOverflowFor = { id, x, y -> ctx.openOverflowMenuFor(id, x, y) },
                    overflowAnchorX = { ctx.overflowAnchorX },
                    overflowAnchorY = { ctx.overflowAnchorY },
                    dragState = { ctx.drawerDrag },
                    onSessionDragStart = { groupKey, sessionId, index, pageY ->
                        ctx.beginSessionDrag(groupKey, sessionId, index, pageY)
                    },
                    onWorkspaceDragStart = { workspaceId, index, itemHeight, pageY ->
                        ctx.beginWorkspaceDrag(workspaceId, index, itemHeight, pageY)
                    },
                    onDragMove = { pageY, heights -> ctx.updateDrawerDrag(pageY, heights) },
                    onDragEnd = { ctx.endDrawerDrag() },
                    onDragCancel = { ctx.cancelDrawerDrag() },
                    viewGroupBy = { ctx.drawerViewGroupBy },
                    viewOrderBy = { ctx.drawerViewOrderBy },
                    viewOptionsVisible = { ctx.drawerViewOptionsVisible },
                    viewOptionsAnchorX = { ctx.drawerViewOptionsX },
                    viewOptionsAnchorY = { ctx.drawerViewOptionsY },
                    onViewOptionsOpen = { x, y -> ctx.openDrawerViewOptions(x, y) },
                    onViewOptionsSelect = { ctx.selectDrawerViewOption(it) },
                    onViewOptionsDismiss = { ctx.closeDrawerViewOptions() },
                    statusBarHeight = ctx.pagerData.statusBarHeight,
                    pageViewWidth = ctx.pagerData.pageViewWidth,
                    pageViewHeight = ctx.pagerData.pageViewHeight,
                    onClose = { ctx.closeSessionDrawer() },
                    onOpenSettings = { ctx.openSettingsPage() },
                    onOpenArchive = { ctx.openArchiveList() },
                    onSelect = { id ->
                        ctx.closeSessionDrawer()
                        setTimeout(ctx.pagerId, 0) {
                            ctx.selectSession(id)
                        }
                    },
                    onOpenSearch = { ctx.openSessionSearch() },
                    colors = { this@DshHomePage.themeColors },
                )
            }

        }
    }

    private fun bodySessionSearchAndArchive(): ViewBuilder {
        val ctx = this
        return {
            // ===== 独立全屏搜索界面 =====
            // 从抽屉搜索入口打开，顶部返回箭头 + 输入框，下方为命中结果。
            vif({ ctx.sessionSearchVisible }) {
                DshSessionSearchOverlay(
                    input = { ctx.sessionSearchInput },
                    hasInput = { ctx.sessionSearchActive },
                    results = { ctx.sessionSearchHits },
                    activeId = { ctx.activeSessionId },
                    onInput = { ctx.onSessionSearch(it) },
                    onClear = { ctx.clearSessionSearch() },
                    onBack = { ctx.closeSessionSearch() },
                    onSelect = { id ->
                        ctx.closeSessionSearch()
                        ctx.closeSessionDrawerImmediately()
                        setTimeout(ctx.pagerId, 0) {
                            ctx.selectSession(id)
                        }
                    },
                    onInputRef = { ctx.sessionSearchInputView = it.view },
                    colors = { this@DshHomePage.themeColors },
                )
            }

            vif({ ctx.archiveListVisible }) {
                DshArchivedSessions(
                    groups = { ctx.archiveGroups },
                    projectOptions = { ctx.archiveProjectOptions },
                    selectedProject = { ctx.archiveProjectFilter },
                    sort = { ctx.archiveSort },
                    openMenu = { ctx.archiveMenu },
                    search = { ctx.archiveSearch },
                    loading = { ctx.archiveListLoading },
                    error = { ctx.archiveListError },
                    notice = { ctx.archiveNotice },
                    busy = { ctx.archiveBusy },
                    openingId = { ctx.archiveOpeningId },
                    confirm = { ctx.archiveConfirm },
                    onSearch = { ctx.onArchiveSearch(it) },
                    onToggleMenu = { ctx.onArchiveToggleMenu(it) },
                    onPickProject = { ctx.onArchivePickProject(it) },
                    onPickSort = { ctx.onArchivePickSort(it) },
                    onClose = { ctx.closeArchiveList() },
                    onOpen = { ctx.openArchivedSession(it) },
                    onUnarchive = { ctx.unarchiveArchivedSession(it) },
                    onRequestDelete = { ctx.requestArchiveDeleteSession(it) },
                    onRequestDeleteProject = { ctx.requestArchiveDeleteProject(it) },
                    onRequestDeleteAll = { ctx.requestArchiveDeleteAll() },
                    onConfirmDelete = { ctx.confirmArchiveDelete() },
                    onCancelConfirm = { ctx.cancelArchiveConfirm() },
                    formatDate = { ctx.archiveDateLabel(it) },
                    colors = { ctx.themeColors },
                )
            }

        }
    }

    private fun bodyPickers(): ViewBuilder {
        val ctx = this
        return {
            // ===== 模型选择弹窗 =====
            // 选择当前会话使用的模型。
            vif({ ctx.modelPickerVisible }) {
                DshModelPicker(
                    options = { ctx.modelOptions },
                    busy = { ctx.modelPickerBusy },
                    error = { ctx.modelPickerError },
                    showEfforts = { ctx.modelEffortsVisible },
                    onShowEfforts = { ctx.modelEffortsVisible = it },
                    onClose = { ctx.modelPickerVisible = false },
                    onSelect = { option ->
                        ctx.selectModel(option)
                    },
                    onSelectEffort = { ctx.selectModelEffort(it) },
                    colors = { this@DshHomePage.themeColors },
                )
            }

            // ===== 权限选择弹窗 =====
            // 选择「默认权限预设」：选项优先取 host settings 动态枚举（与设置页一致），
            // 未连接/不支持时回退本地三项。选择动作走 settings.update 全局设置通道，
            // 与设置页「工作区权限」同一入口，影响之后新建的会话。
            vif({ ctx.permissionPickerVisible }) {
                DshPermissionPicker(
                    options = {
                        ObservableList<DshPermissionOption>().apply {
                            val choices = ctx.settingsSnapshot.permissionChoices
                            if (choices.isNotEmpty()) {
                                addAll(choices.map {
                                    DshPermissionOption(
                                        it.value,
                                        it.label,
                                        it.value == ctx.settingsSnapshot.permissionPreset,
                                    )
                                })
                            } else {
                                addAll(listOf(
                                    DshPermissionOption("read-only", "只读", ctx.permissionValue == "read-only"),
                                    DshPermissionOption("workspace-write", "工作区写入", ctx.permissionValue == "workspace-write"),
                                    DshPermissionOption("danger-full-access", "完全访问", ctx.permissionValue == "danger-full-access"),
                                ))
                            }
                        }
                    },
                    onClose = { ctx.permissionPickerVisible = false },
                    onSelect = { option ->
                        if (option.value == "danger-full-access") {
                            ctx.pendingDangerPermission = option
                            ctx.riskAcknowledged = false
                            ctx.riskConfirmVisible = true
                        } else {
                            ctx.permissionValue = option.value
                            ctx.permissionLabel = option.label
                            ctx.permissionPickerVisible = false
                            ctx.applyPermissionPreset(option)
                        }
                    },
                    colors = { this@DshHomePage.themeColors },
                )
            }

            // ===== Full access 风险确认弹窗（对齐 dsh 原版 RiskConfirmation）=====
            vif({ ctx.riskConfirmVisible }) {
                DshRiskConfirmationModal(
                    title = "确认启用 Full access？",
                    description = "启用 Full access 后，agent 将减少确认步骤，并且可以直接执行更多操作，包括敏感操作、文件修改或外部命令。仅建议在你信任当前任务时使用。",
                    acknowledgeLabel = "我已了解风险，并愿意继续",
                    cancelLabel = "取消",
                    confirmLabel = "启用 Full access",
                    acknowledged = { ctx.riskAcknowledged },
                    busy = { ctx.settingsChoiceBusy },
                    onAcknowledgedChange = { ctx.riskAcknowledged = it },
                    onCancel = { ctx.riskConfirmVisible = false },
                    onConfirm = {
                        val option = ctx.pendingDangerPermission
                        if (option != null) {
                            ctx.riskConfirmVisible = false
                            ctx.pendingDangerPermission = null
                            ctx.permissionPickerVisible = false
                            ctx.permissionValue = option.value
                            ctx.permissionLabel = option.label
                            ctx.applyPermissionPreset(option)
                        }
                    },
                    colors = { this@DshHomePage.themeColors },
                )
            }

            // ===== 模式选择弹窗 =====
            // 会话开始前选择 Agent 模式（agentPreset）。host 有 agentPreset.list，
            // 当前以本地预设为兜底；选中值存本地，未来在创建会话时随参数下发。
            vif({ ctx.agentModePickerVisible }) {
                DshAgentModePicker(
                    title = { ctx.agentModePickerTitle },
                    options = {
                        ObservableList<DshAgentModeOption>().apply {
                            if (ctx.agentPresetOptions.isEmpty()) {
                                addAll(listOf(
                                    DshAgentModeOption(
                                        "standard", "标准模式",
                                        "功能完整的编码 Agent，支持文件编辑、Shell、文件与网页检索、Skills、计划、目标、子代理和工作流",
                                        ctx.agentModeValue == "standard",
                                    ),
                                    DshAgentModeOption(
                                        "ptc", "PTC 模式",
                                        "具备标准模式的全部能力，并通过 Code Mode SDK 呈现工具，让模型用一个 TypeScript 程序组合多步操作",
                                        ctx.agentModeValue == "ptc",
                                    ),
                                    DshAgentModeOption(
                                        "minimal", "极简模式",
                                        "仅提供持久 bash 与 str_replace_editor 的双工具编码 Agent",
                                        ctx.agentModeValue == "minimal",
                                    ),
                                    DshAgentModeOption(
                                        "creator", "创造模式",
                                        "用于创建自定义 Agent preset：具备标准模式的全部能力，并提供运行时检查、插件实验和 preset 创作指导",
                                        ctx.agentModeValue == "creator",
                                    ),
                                ))
                            } else {
                                for (preset in ctx.agentPresetOptions) {
                                    add(DshAgentModeOption(
                                        preset.id,
                                        preset.name,
                                        preset.description,
                                        ctx.agentModeValue == preset.id,
                                    ))
                                }
                            }
                        }
                    },
                    onClose = { ctx.agentModePickerVisible = false },
                    onSelect = { option ->
                        ctx.agentModeValue = option.value
                        ctx.agentModeLabel = option.label
                        ctx.agentModePickerVisible = false
                    },
                    colors = { this@DshHomePage.themeColors },
                )
            }

        }
    }

    private fun bodySettingsOverlays(): ViewBuilder {
        val ctx = this
        return {
            // ===== 设置页（ds 风格：顶部居中标题，账户/权限/应用/关于分组） =====
            vif({ ctx.settingsPageVisible }) {
                DshSettingsPage(
                    loading = { ctx.settingsLoading },
                    error = { ctx.settingsError },
                    snapshot = { ctx.settingsSnapshot },
                    isRemoteHost = { ctx.isRemoteHost },
                    connectionModeLabel = { ctx.connectionModeLabel() },
                    modelsSummary = { ctx.modelsSummary() },
                    hostVersion = { ctx.hostVersion },
                    themeMode = { this@DshHomePage.themeMode },
                    processDisplayMode = { ctx.chatProcessMode },
                    agentPresetLabel = { ctx.agentModeLabel },
                    onClose = { ctx.closeSettingsPage() },
                    onRetry = { ctx.reloadSettings() },
                    onOpenConnection = { ctx.openConnectionSettings() },
                    onOpenModels = { ctx.openModelsPage() },
                    onOpenPersonalization = { ctx.openPersonalizationPage() },
                    onPickPermission = { ctx.openSettingsChoice("permission", "工作区权限") },
                    onPickLocale = { ctx.openSettingsChoice("locale", "语言") },
                    onPickTheme = { ctx.openSettingsChoice("theme", "外观") },
                    onOpenAgentPresets = { ctx.openAgentModePicker("Agent 预设") },
                    onOpenDiagnosticLogs = { ctx.openDiagnosticLogs() },
                    onOpenPlugins = { ctx.openPluginInventory() },
                    onDisconnect = { ctx.disconnectFromHost() },
                    colors = { this@DshHomePage.themeColors },
                )
            }

            // ===== 设置页「个性化」子页面：过程展示方式（互斥）+ 弹窗查看开关 =====
            vif({ ctx.personalizationPageVisible }) {
                DshPersonalizationPage(
                    mode = { ctx.chatProcessMode },
                    expandInModal = { ctx.chatExpandInModal },
                    showConnectors = { ctx.chatShowConnectors },
                    showResultCards = { ctx.chatShowResultCards },
                    onPickMode = { ctx.applyChatProcessMode(it) },
                    onToggleExpandInModal = { ctx.applyChatExpandInModal(it) },
                    onToggleConnectors = { ctx.applyChatShowConnectors(it) },
                    onToggleResultCards = { ctx.applyChatShowResultCards(it) },
                    onClose = { ctx.closePersonalizationPage() },
                    colors = { this@DshHomePage.themeColors },
                )
            }

            // ===== 展开内容底部大弹层（「弹窗查看」开启时） =====
            vif({ ctx.expandedPayload != null }) {
                DshExpandedContentModal(
                    payload = { ctx.expandedPayload },
                    onClose = { ctx.closeExpandedModal() },
                    colors = { this@DshHomePage.themeColors },
                )
            }

            vif({ ctx.pluginInventoryVisible }) {
                DshPluginSettingsView(
                    activeTab = { ctx.pluginActiveTab }, onSelectTab = { ctx.selectPluginTab(it) },
                    loading = { ctx.pluginInventoryLoading }, error = { ctx.pluginInventoryError },
                    keyword = { ctx.pluginSearchInput }, onKeyword = { ctx.onPluginKeyword(it) },
                    hasKeyword = { ctx.pluginSearchHasText }, onClearKeyword = { ctx.clearPluginKeyword() },
                    onSearchInputRef = { ctx.pluginSearchInputView = it.view },
                    onRefresh = { ctx.refreshPluginInventory() }, onClose = { ctx.closePluginInventory() },
                    rows = { ctx.pluginRows }, total = { ctx.pluginTotal },
                    expandedId = { ctx.pluginExpandedId }, busyId = { ctx.pluginBusyId },
                    onToggleExpand = { ctx.togglePluginExpanded(it) },
                    actionError = { ctx.pluginActionError }, actionNotice = { ctx.pluginNotice },
                    onToggleEnabled = { entry, enable -> ctx.requestPluginToggle(entry, enable) },
                    onReload = { ctx.requestPluginReload(it) },
                    confirmEntry = { ctx.pluginActionTarget }, confirmAction = { ctx.pluginConfirmAction },
                    onConfirm = { ctx.confirmPluginAction() }, onCancelConfirm = { ctx.cancelPluginAction() },
                    configCards = { ctx.pluginConfigCards }, configLoading = { ctx.pluginConfigLoading },
                    configError = { ctx.pluginConfigError }, configWritable = { ctx.pluginConfigWritable },
                    configDraft = { ns, key -> ctx.pluginConfigDraft(ns, key) },
                    configSecretDraft = { ns -> ctx.pluginConfigSecretDraft(ns) },
                    configCollapsed = { ns -> ctx.isPluginConfigCollapsed(ns) },
                    configBusyNamespace = { ctx.pluginConfigBusyNamespace },
                    configCardError = { ns -> ctx.pluginConfigCardError[ns] ?: "" },
                    configCardNotice = { ns -> ctx.pluginConfigCardNotice[ns] ?: "" },
                    configHasChanges = { ns -> ctx.hasPluginConfigChanges(ns) },
                    onConfigDraft = { ns, key, value -> ctx.onPluginConfigDraft(ns, key, value) },
                    onConfigSecretDraft = { ns, value -> ctx.onPluginConfigSecretDraft(ns, value) },
                    onConfigToggleCollapse = { ctx.togglePluginConfigCollapsed(it) },
                    onConfigSave = { ctx.savePluginConfigCard(it) },
                    onConfigDiscard = { ctx.discardPluginConfigCard(it) },
                    colors = { ctx.themeColors },
                )
            }

            // ===== 设置页「模型」详情页（对齐电脑端 settings.models） =====
            vif({ ctx.modelsPageVisible }) {
                DshModelsPage(
                    loading = { ctx.modelsLoading },
                    error = { ctx.modelsError },
                    writable = { ctx.modelsWritable },
                    configuredProviders = { ctx.modelsConfiguredProviders },
                    addableProviders = { ctx.modelsAddableProviders },
                    editingProvider = { ctx.modelsEditingProvider },
                    pickerVisible = { ctx.modelsPickerVisible },
                    customAdding = { ctx.modelsCustomAdding },
                    savedNotice = { ctx.modelsSavedNotice },
                    editorAdvanced = { ctx.modelsEditorAdvanced },
                    onToggleEditorAdvanced = { ctx.modelsEditorAdvanced = !ctx.modelsEditorAdvanced },
                    draftBaseUrl = { ctx.modelsDraftBaseUrl },
                    draftApiKey = { ctx.modelsDraftApiKey },
                    draftModels = { ctx.modelsDraftModels },
                    saving = { ctx.modelsSaving },
                    saveError = { ctx.modelsSaveError },
                    customProtocols = { ctx.modelsProtocols },
                    customRoute = { ctx.modelsCustomRoute },
                    customName = { ctx.modelsCustomName },
                    customBaseUrl = { ctx.modelsCustomBaseUrl },
                    customProtocol = { ctx.modelsCustomProtocol },
                    customApiKey = { ctx.modelsCustomApiKey },
                    customModels = { ctx.modelsCustomModels },
                    customBusy = { ctx.modelsCustomBusy },
                    customError = { ctx.modelsCustomError },
                    deleteTarget = { ctx.modelsDeleteTarget },
                    deleting = { ctx.modelsDeleting },
                    onClose = { ctx.closeModelsPage() },
                    onRetry = { ctx.reloadModelsSettings() },
                    onEdit = { ctx.openProviderEditor(it) },
                    onCancelAdd = { ctx.cancelAddProvider() },
                    onOpenPicker = { ctx.modelsPickerVisible = true },
                    onClosePicker = { ctx.modelsPickerVisible = false },
                    onSelectAddable = { ctx.selectAddableProvider(it) },
                    onOpenCustom = { ctx.openCustomProvider() },
                    onCancelCustom = { ctx.cancelCustomProvider() },
                    onBaseUrlChange = { ctx.modelsDraftBaseUrl = it; ctx.modelsSaveError = "" },
                    onApiKeyChange = { ctx.modelsDraftApiKey = it; ctx.modelsSaveError = "" },
                    onModelChange = { index, field, value ->
                        ctx.updateDraftModel(index, field, value)
                        ctx.modelsSaveError = ""
                    },
                    onAddModel = { ctx.addDraftModel() },
                    onRemoveModel = { ctx.removeDraftModel(it) },
                    onApply = { ctx.applyProviderEditor(it) },
                    onCustomRoute = { ctx.modelsCustomRoute = it; ctx.modelsCustomError = "" },
                    onCustomName = { ctx.modelsCustomName = it },
                    onCustomBaseUrl = { ctx.modelsCustomBaseUrl = it; ctx.modelsCustomError = "" },
                    onCustomProtocol = { ctx.modelsCustomProtocol = it },
                    onCustomApiKey = { ctx.modelsCustomApiKey = it },
                    onCustomModelChange = { index, field, value -> ctx.updateCustomModel(index, field, value) },
                    onCustomAddModel = { ctx.modelsCustomModels.add(DshProviderModel()) },
                    onCustomRemoveModel = { ctx.removeCustomModel(it) },
                    onApplyCustom = { ctx.applyCustomProvider() },
                    onRequestDelete = { ctx.requestRemoveProvider(it) },
                    onConfirmDelete = { ctx.confirmRemoveProvider() },
                    onCancelDelete = { ctx.modelsDeleteTarget = null },
                    colors = { this@DshHomePage.themeColors },
                )
            }

        }
    }

    private fun bodySettingsChoiceAndCredentials(): ViewBuilder {
        val ctx = this
        return {
            // ===== 设置项选择器（权限预设 / 语言 / 外观） =====
            vif({ ctx.settingsChoiceKind.isNotEmpty() }) {
                DshSettingsChoicePicker(
                    title = ctx.settingsChoiceTitle,
                    options = { ctx.settingsChoiceOptions },
                    selectedValue = {
                        when (ctx.settingsChoiceKind) {
                            "permission" -> ctx.settingsSnapshot.permissionPreset
                            "locale" -> ctx.settingsSnapshot.localeValue
                            "theme" -> DshThemeManager.preferenceValue
                            else -> ""
                        }
                    },
                    busy = { ctx.settingsChoiceBusy },
                    onClose = { if (!ctx.settingsChoiceBusy) ctx.settingsChoiceKind = "" },
                    onSelect = { ctx.applySettingsChoice(it) },
                    colors = { this@DshHomePage.themeColors },
                )
            }

            // ===== API Key 设置弹窗 =====
            // 输入并保存 DeepSeek API Key（也可用于修改远程 DSH 的 Key）。
            vif({ ctx.credentialSetupVisible }) {
                DshCredentialSetupModal(
                    title = { ctx.credentialSetupTitle },
                    busy = { ctx.credentialSetupBusy },
                    error = { ctx.credentialSetupError },
                    inputRef = {
                        ctx.apiKeyInputView = it.view
                        ctx.apiKeyInputView?.setText(ctx.apiKeyDraft)
                    },
                    onApiKeyChange = {
                        ctx.apiKeyDraft = it
                        ctx.credentialSetupError = ""
                    },
                    onSave = { ctx.saveDeepSeekApiKey() },
                    onClose = { ctx.closeCredentialSettings() },
                    colors = { this@DshHomePage.themeColors },
                )
            }
            // ===== 连接设置弹窗 =====
            // 配置连接方式（扫码 RELAY / SSH）：主机、端口、用户名、私钥导入、指纹确认、DSH 端口。
            vif({ ctx.sshSettingsVisible }) {
                DshConnectionSettingsModal(
                    sshMode = { ctx.sshMode },
                    host = { ctx.sshHost },
                    user = { ctx.sshUser },
                    port = { ctx.sshPort },
                    dshPort = { ctx.sshDshPort },
                    keyLabel = { ctx.sshKeyLabel },
                    keyPassphrase = { ctx.sshKeyPassphrase },
                    busy = { ctx.sshSettingsBusy },
                    error = { ctx.sshSettingsError },
                    onModeChange = { ctx.setConnectionMode(it) },
                    onHostChange = { ctx.sshHost = it; ctx.sshSettingsError = "" },
                    onUserChange = { ctx.sshUser = it; ctx.sshSettingsError = "" },
                    onPortChange = { ctx.sshPort = it; ctx.sshSettingsError = "" },
                    onDshPortChange = { ctx.sshDshPort = it; ctx.sshSettingsError = "" },
                    onPickKey = { ctx.pickSshKey() },
                    onPassphraseChange = { ctx.sshKeyPassphrase = it },
                    onTrustFingerprint = { ctx.trustSshFingerprint() },
                    onSave = { ctx.saveConnectionSettings() },
                    onClose = { ctx.updateSshSettingsVisibility(false) },
                    onOpenApiKey = {
                        ctx.updateSshSettingsVisibility(false)
                        ctx.openCredentialSettings()
                    },
                    colors = { this@DshHomePage.themeColors },
                )
            }
        }
    }

    private fun bodyWorkspaceDialogs(): ViewBuilder {
        val ctx = this
        return {
            // ===== 新建会话-工作区选择弹窗（最近的文件夹 / 添加文件夹） =====
            // 仅远程模式、且当前会话尚未发送第一条消息（blank）时可用。
            vif({ ctx.workspacePickerVisible && ctx.isRemoteHost }) {
                DshWorkspacePickerModal(
                    screen = { ctx.workspacePickerScreen },
                    folders = { ctx.workspacePickerFolders },
                    activeWorkspaceId = { ctx.activeWorkspaceId() },
                    busy = { ctx.workspacePickerBusy || ctx.workspaceAddBusy },
                    error = { ctx.workspacePickerError },
                    onSelectFolder = { ctx.switchWorkspaceTo(it) },
                    onAddFolder = { ctx.openWorkspaceAddFolder() },
                    onBack = { ctx.onWorkspacePickerBack() },
                    onClose = { ctx.closeWorkspacePicker() },
                    path = { ctx.workspaceAddPath },
                    home = { ctx.workspaceAddHome },
                    entries = { ctx.workspaceAddEntries },
                    newName = { ctx.workspaceAddNewName },
                    onDirectorySelect = { ctx.loadWorkspaceAddDirectory(it) },
                    onNewNameChange = { ctx.workspaceAddNewName = it },
                    onCreateDirectory = { ctx.createWorkspaceAddDirectory() },
                    onAdopt = { ctx.adoptWorkspaceAddDirectory() },
                    colors = { this@DshHomePage.themeColors },
                )
            }
            // ===== 重命名工作区 弹窗 =====
            // 内嵌 Modal：输入新名称 → 保存/取消；错误信息红字显示。
            vif({ ctx.workspaceRenameTargetId.isNotEmpty() && ctx.isRemoteHost }) {
                Modal(inWindow = true) {
                    attr {
                        absolutePositionAllZero()
                        allCenter()
                        paddingLeft(20f)
                        paddingRight(20f)
                        backgroundColor(Color(0x66000000))
                    }
                    View {
                        attr {
                            width(pagerData.pageViewWidth - 40f)
                            maxWidth(420f)
                            padding(20f)
                            borderRadius(16f)
                            backgroundColor(this@DshHomePage.themeColors.bgLayer3)
                        }
                        Text { attr { text("重命名工作区"); fontSize(18f); fontWeightBold(); color(this@DshHomePage.themeColors.labelPrimary) } }
                        Input {
                            attr {
                                height(38f)
                                marginTop(14f)
                                fontSize(14f)
                                placeholder("工作区名称")
                                placeholderColor(this@DshHomePage.themeColors.labelTertiary)
                                text(ctx.workspaceRenameDraft)
                            }
                            event { textDidChange { ctx.workspaceRenameDraft = it.text } }
                        }
                        vif({ ctx.workspaceActionError.isNotEmpty() }) {
                            Text { attr { text(ctx.workspaceActionError); marginTop(8f); fontSize(12f); color(this@DshHomePage.themeColors.stateErrorPrimary) } }
                        }
                        View {
                            attr { height(40f); marginTop(18f); flexDirectionRow(); justifyContentFlexEnd() }
                            Text {
                                attr { text("取消"); width(78f); height(38f); textAlignCenter(); fontSize(14f); color(this@DshHomePage.themeColors.labelTertiary) }
                                event { click { ctx.workspaceRenameTargetId = ""; ctx.workspaceActionError = "" } }
                            }
                            Text {
                                attr { text(if (ctx.workspaceActionBusy) "保存中..." else "保存"); width(78f); height(38f); marginLeft(8f); textAlignCenter(); fontSize(14f); color(this@DshHomePage.themeColors.stateBusinessPrimary) }
                                event { click { if (!ctx.workspaceActionBusy) ctx.saveWorkspaceRename() } }
                            }
                        }
                    }
                }
            }
            // ===== 删除工作区注册 确认弹窗 =====
            // 仅从列表移除注册，不删除实际目录/会话/日志；红色「删除注册」按钮。
            vif({ ctx.workspaceDeleteTargetId.isNotEmpty() && ctx.isRemoteHost }) {
                Modal(inWindow = true) {
                    attr {
                        absolutePositionAllZero()
                        allCenter()
                        paddingLeft(20f)
                        paddingRight(20f)
                        backgroundColor(Color(0x66000000))
                    }
                    View {
                        attr {
                            width(pagerData.pageViewWidth - 40f)
                            maxWidth(420f)
                            padding(20f)
                            borderRadius(16f)
                            backgroundColor(this@DshHomePage.themeColors.bgLayer3)
                        }
                        Text { attr { text("删除工作区注册?"); fontSize(18f); fontWeightBold(); color(this@DshHomePage.themeColors.labelPrimary) } }
                        Text {
                            attr {
                                text("只会从列表移除注册，不会删除目录、会话或日志。")
                                marginTop(8f)
                                fontSize(13f)
                                lineHeight(20f)
                                color(this@DshHomePage.themeColors.labelSecondary)
                            }
                        }
                        vif({ ctx.workspaceActionError.isNotEmpty() }) {
                            Text { attr { text(ctx.workspaceActionError); marginTop(8f); fontSize(12f); color(this@DshHomePage.themeColors.stateErrorPrimary) } }
                        }
                        View {
                            attr { height(40f); marginTop(18f); flexDirectionRow(); justifyContentFlexEnd() }
                            Text {
                                attr { text("取消"); width(78f); height(38f); textAlignCenter(); fontSize(14f); color(this@DshHomePage.themeColors.labelTertiary) }
                                event { click { ctx.workspaceDeleteTargetId = ""; ctx.workspaceActionError = "" } }
                            }
                            Text {
                                attr { text(if (ctx.workspaceActionBusy) "删除中..." else "删除注册"); width(112f); height(38f); marginLeft(8f); textAlignCenter(); fontSize(14f); color(this@DshHomePage.themeColors.stateErrorPrimary) }
                                event { click { if (!ctx.workspaceActionBusy) ctx.confirmWorkspaceDelete() } }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun bodyMessageOverlays(): ViewBuilder {
        val ctx = this
        return {
            // ===== 消息长按操作菜单 =====
            // 长按 AI 输出消息时弹出的底部操作菜单（复制/选择文本/反馈/分支/分享）。
            DshMessageActionsMenu(
                visible = { ctx.messageActionsMessage != null },
                items = { ctx.messageActionsItems() },
                blurUri = { ctx.menuBlurUri },
                x = { ctx.messageActionsX },
                y = { ctx.messageActionsY },
                onDismiss = { ctx.closeMessageActions() },
                colors = { this@DshHomePage.themeColors },
            )
            // ===== 选择文本弹窗 =====
            // 「选择文本」以单个可选中文本节点承载完整正文，供原生选区复制。
            DshSelectTextModal(
                visible = { ctx.selectTextModalVisible },
                content = { ctx.selectTextModalContent },
                onClose = { ctx.closeSelectTextModal() },
                colors = { this@DshHomePage.themeColors },
            )
            // ===== 会话管理动作 =====
            // overflow menu 由会话抽屉（DshSessionDrawer）内实例渲染，锚定到点击的会话行 ⋯，
            // 这里不再重复渲染，避免同时弹出两个菜单。
            DshSessionRenameDialog(
                visible = { ctx.sessionRenameVisible },
                draft = { ctx.sessionRenameDraft },
                busy = { ctx.sessionRenameBusy },
                error = { ctx.sessionRenameError },
                onDraftChange = { ctx.sessionRenameDraft = it },
                onCancel = { ctx.cancelSessionRename() },
                onSave = { ctx.saveSessionRename() },
                pageViewWidth = ctx.pagerData.pageViewWidth,
                colors = { this@DshHomePage.themeColors },
            )
            vif({ ctx.readableExportDialogVisible }) {
                DshTextExportDialog(
                    state = { ctx.readableExport },
                    onClose = { ctx.closeReadableExportDialog() },
                    onRetry = { ctx.retryReadableExport() },
                    onShare = { ctx.shareReadableExport() },
                    colors = { ctx.themeColors },
                )
            }
            DshSessionArchiveDialog(
                visible = { ctx.sessionArchiveVisible },
                busy = { ctx.sessionArchiveBusy },
                error = { ctx.sessionArchiveError },
                onCancel = { if (!ctx.sessionArchiveBusy) { ctx.sessionArchiveVisible = false; ctx.sessionArchiveError = "" } },
                onConfirm = { ctx.confirmSessionArchive() },
                pageViewWidth = ctx.pagerData.pageViewWidth,
                colors = { this@DshHomePage.themeColors },
            )
            DshSessionDeleteDialog(
                visible = { ctx.sessionDeleteVisible },
                busy = { ctx.sessionDeleteBusy },
                error = { ctx.sessionDeleteError },
                onCancel = { if (!ctx.sessionDeleteBusy) { ctx.sessionDeleteVisible = false; ctx.sessionDeleteError = "" } },
                onConfirm = { ctx.confirmSessionDelete() },
                pageViewWidth = ctx.pagerData.pageViewWidth,
                colors = { this@DshHomePage.themeColors },
            )
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        addTaskWhenPagerUpdateLayoutFinish {
            refreshMountedSessionRenderTrees()
        }
    }

    private fun openSessionDrawer() {
        if (sessionDrawerVisible) return
        dismissKeyboard()
        if (isConnectionReadyLabel(connectionLabel)) {
            connectionCapsuleVisible = false
            connectionCapsuleFadeOut = false
        }
        // 抽屉是独立 Modal 窗口，菜单的透明捕获层够不着它；打开抽屉前先关闭长按菜单，
        // 否则切换会话后菜单仍会残留。
        closeMessageActions()
        closeSelectTextModal()
        // 重置上次可能残留的拖拽状态。
        drawerDrag = DshDrawerDrag()
        drawerViewOptionsVisible = false
        // Mount transparent first, then start drawer on the same frame.
        sessionDrawerAnimated = false
        sessionDrawerVisible = true
        setTimeout(pagerId, 16) {
            sessionDrawerAnimated = true
        }
        // 首次打开且未手动展开过任何文件夹时，自动展开当前会话所在工作区（与原版一致）
        if (isRemoteHost && workspaceExpandedIds.isEmpty()) {
            val group = workspaceGroups.firstOrNull { it.sessions.any { s -> s.id == activeSessionId } }
                ?: workspaceGroups.firstOrNull()
            group?.let { workspaceExpandedIds = workspaceExpandedIds + it.workspaceId }
        }
        setTimeout(pagerId, ANIMATION_DURATION_MS) {
            warmRecentSessionCache(scrollToEndAfterLoad = false)
        }
    }

    private fun closeSessionDrawer() {
        if (!sessionDrawerVisible) return
        // 关闭抽屉时清掉会话行溢出菜单的目标会话，避免残留指向已关闭的会话
        overflowTargetSessionId = ""
        drawerViewOptionsVisible = false
        // Reverse the opening transition: drawer slides back out.
        sessionDrawerAnimated = false
        setTimeout(pagerId, ANIMATION_DURATION_MS) {
            sessionDrawerVisible = false
        }
    }

    private fun closeSessionDrawerImmediately() {
        if (!sessionDrawerVisible) return
        overflowTargetSessionId = ""
        sessionDrawerAnimated = false
        sessionDrawerVisible = false
    }

    /** 从抽屉搜索入口进入独立搜索界面，挂载后聚焦输入框。 */
    private fun openSessionSearch() {
        dismissKeyboard()
        sessionSearchInput = ""
        if (sessionSearchHits.isNotEmpty()) sessionSearchHits.clear()
        sessionSearchActive = false
        sessionSearchVisible = true
        postToUi {
            if (pageAlive && sessionSearchVisible) sessionSearchInputView?.focus()
        }
    }

    /** 退出搜索界面：清空输入与结果并收起键盘。 */
    private fun closeSessionSearch() {
        sessionSearchInput = ""
        sessionSearchInputView?.setText("")
        sessionSearchInputView?.blur()
        if (sessionSearchHits.isNotEmpty()) sessionSearchHits.clear()
        sessionSearchActive = false
        sessionSearchVisible = false
    }

    /**
     * 搜索输入变化：仅用可观察的命中列表驱动 UI，原生文本单独保存，
     * 避免每次按键重设 Input 文本导致光标跳动。
     */
    private fun onSessionSearch(value: String) {
        sessionSearchInput = value
        rebuildSessionSearch(value)
    }

    /** 清空搜索框：保留搜索界面，仅重置输入与结果。 */
    private fun clearSessionSearch() {
        sessionSearchInput = ""
        sessionSearchInputView?.setText("")
        if (sessionSearchHits.isNotEmpty()) sessionSearchHits.clear()
        sessionSearchActive = false
    }

    private fun sessionSearchDateLabel(timestamp: Long): String =
        if (timestamp <= 0L) "" else bridgeModule.dateFormatter(timestamp, "yyyy年M月d日")

    /**
     * 本地搜索：命中计算委托给纯函数 [DshSessionSearch.build]，
     * 这里只负责读取内存缓存并把结果灌进 observable 列表（先收集再一次 diffUpdate）。
     */
    private fun rebuildSessionSearch(rawQuery: String) {
        if (rawQuery.isBlank()) {
            if (sessionSearchHits.isNotEmpty()) sessionSearchHits.clear()
            sessionSearchActive = false
            return
        }
        val hits = DshSessionSearch.build(
            sessions = visibleSessions,
            messagesFor = { sessionMessageStates[it] },
            rawQuery = rawQuery,
            hitLimit = SESSION_SEARCH_HIT_LIMIT,
            dateLabel = { sessionSearchDateLabel(it) },
        )
        sessionSearchHits.diffUpdate(hits)
        sessionSearchActive = true
    }

    private fun toggleWorkspaceExpanded(workspaceId: String) {
        workspaceExpandedIds = if (workspaceId in workspaceExpandedIds) workspaceExpandedIds - workspaceId
        else workspaceExpandedIds + workspaceId
    }

    /** 会话是否有待用户决策（审批/提问），供抽屉行状态点显示；本地模式恒 false。 */
    private fun sessionPending(sessionId: String): Boolean {
        return sessionId in pendingSessionIds
    }

    /** 当前会话所在工作区；不在任何真实工作区时返回未分组键。 */
    private fun activeWorkspaceId(): String {
        val id = activeSessionId
        if (id.isEmpty()) return NO_ACTIVE_WORKSPACE
        return workspaceGroups
            .firstOrNull { it.sessions.any { s -> s.id == id } }
            ?.workspaceId
            ?: ""
    }

    private fun refreshVisibleSessions() {
        ensureDrawerOrdersLoaded()
        val archived = (remoteRepo)?.archivedSessionIds.orEmpty()
        val visible = sessions.filterNot { it.blank || it.id in archived }
        val order = if (drawerViewOrderBy == DSH_DRAWER_ORDER_MANUAL) sessionManualOrder[""].orEmpty() else emptyList()
        val ordered = when {
            order.isNotEmpty() -> dshApplyOrder(visible, order) { it.id }
            drawerViewOrderBy == DSH_DRAWER_ORDER_UPDATED ->
                visible.sortedWith(compareByDescending<DshSession> { it.updatedAt }.thenBy { it.id })
            else -> visible
        }
        syncVisibleSessions(ordered, visibleSessions, archived)
        refreshPendingSessionIds()
    }

    private fun refreshPendingSessionIds() {
        val remote = remoteRepo
        pendingSessionIds = sessions.filter { session ->
            val pending = remote?.pendingInteractions(session.id)
            pending?.first != null || pending?.second != null
        }.map { it.id }.toSet()
    }

    private fun updateSessionMetadata(sessionId: String, update: (DshSession) -> DshSession) {
        val next = sessions.map { if (it.id == sessionId) update(it) else it }
        if (next == sessions) return
        sessions = next
        refreshVisibleSessions()
        refreshWorkspaceGroups()
        archivedSessions.diffUpdate(archivedSessions.map { if (it.id == sessionId) update(it) else it })
        runCatching { localStore?.replaceSessions(activeConnectionId, sessions) }
    }

    private fun loadRepository(preferredSessionId: String? = null, restoreOnError: Boolean = true) {
        val hostRepository = repository ?: return
        val expectedConnection = activeConnectionId
        val requestGeneration = ++catalogRequestGeneration
        val requestedActiveId = activeSessionId
        fun current() = pageAlive && repository === hostRepository && activeConnectionId == expectedConnection &&
            requestGeneration == catalogRequestGeneration && connectionCoordinator.isActive(connectionMode)
        val onLoaded: (DshSessionCatalog) -> Unit = loaded@{ catalog ->
            if (!current()) return@loaded
            val loaded = catalog.sessions
            val loadedIds = loaded.map { it.id }.toSet()
            sessions.map { it.id }
                .filterNot { loadedIds.contains(it) }
                .forEach {
                    sessionMessageStates.remove(it)
                    sessionCacheStates.remove(it)
                    sessionMessageReady.remove(it)
                    conversationPanelIds.remove(it)
                }
            if (isRemoteHost) {
                loaded.forEach { sessionCacheStates[it.id] = DshSessionCacheState.STALE }
            }
            // 会话列表按消息时间（updatedAt = 最新消息时间）从新到旧排序，不按创建时间。
            sessions = loaded.toList()
            reorderSessionsByUpdatedAt()
            refreshVisibleSessions()
            runCatching { localStore?.replaceSessions(activeConnectionId, sessions.toList()) }
            preloadAllSessionMessages()
            connectionLabel = if (loaded.isEmpty()) "已连接 · 无会话" else "已连接 · 正在同步远程历史"
            refreshWorkspaceGroups()
            val selectionChanged = activeSessionId != requestedActiveId
            val preferred = if (selectionChanged) activeSessionId else preferredSessionId
            val nextId = catalog.forReload(preferred, preferBlankHomeOnNextLoad && !selectionChanged)?.id
            preferBlankHomeOnNextLoad = false
            if (nextId != null) {
                if (activeSessionId != nextId) cancelStreamingForSessionSwitch()
                activeSessionId = nextId
                sessionRunning = loaded.firstOrNull { it.id == activeSessionId }?.running == true
                refreshQueueDock()
                refreshJobsPanel()
                refreshPendingInteractions()
                loadModels(activeSessionId)
                loadHistory(activeSessionId, scrollToEndAfterLoad = false)
                if (streaming || stopButtonVisible || sessionRunning) {
                    resyncStreamingWithHost(activeSessionId, "session-list")
                }
            } else {
                preferBlankHomeOnNextLoad = false
                cancelStreamingForSessionSwitch()
                activeSessionId = ""
                messages = ObservableList()
                createSession()
            }
        }
        val onError: (String) -> Unit = failed@{ error ->
            if (!current()) return@failed
            if (!restoreOnError) {
                bridgeModule.toast("会话操作已完成，列表刷新失败：$error")
                return@failed
            }
            connectionLabel = "内核连接失败"
            restoreCachedSessions()
            if (sessions.isEmpty()) {
                messages.clear()
                messages.add(DshMessage("load-error", DshMessageRole.ERROR, error))
            } else {
                connectionLabel = "连接失败 · 已显示缓存"
            }
        }
        if (hostRepository is DshRemoteRepository) {
            hostRepository.loadSessionCatalog(onLoaded) { error ->
                if (error.code != DshSessionCatalogLoader.SUPERSEDED) onError(error.message)
            }
        } else {
            hostRepository.loadSessions({ onLoaded(DshSessionCatalog(it, emptySet(), "[]")) }, onError)
        }
    }

    private fun startConnection() {
        val generation = connectionCoordinator.begin(connectionMode)
        when (connectionMode) {
            DshConnectionMode.SSH -> {
                startSshEngine(generation)
                return
            }
            DshConnectionMode.RELAY -> {
                startRelayEngine(generation)
                return
            }
            DshConnectionMode.LOCAL -> {
                connectionLabel = "本地模式已独立为 DSH Local App"
                return
            }
        }
    }

    private fun loadSshConfig() {
        val profile = runCatching { localStore?.loadRemoteProfile() }.getOrNull()
        sshHost = profile?.host.orEmpty()
        sshUser = profile?.username.orEmpty()
        sshPort = profile?.sshPort?.toString() ?: "22"
        sshDshPort = profile?.remoteDshPort?.toString() ?: "3080"
        sshKeyId = profile?.keyId.orEmpty()
        sshFingerprint = profile?.hostFingerprint.orEmpty()
        sshKeyLabel = if (sshKeyId.isEmpty()) "未导入私钥" else "已导入私钥"
    }

    private fun startRelayEngine(generation: Long) {
        if (!pageData.supportsRelayBridge) {
            connectionLabel = "扫码连接目前仅支持 Android、iOS 和 HarmonyOS"
            return
        }
        connectionLabel = "正在连接扫码电脑"
        acquireModule<DshRelayModule>(DshRelayModule.MODULE_NAME).connect { state ->
            if (!isCurrent(generation, DshConnectionMode.RELAY)) return@connect
            when (state.phase) {
                DshRelayPhase.READY -> {
                    if (state.localPort <= 0 || state.localToken.isEmpty()) return@connect
                    val endpoint = "http://127.0.0.1:${state.localPort}"
                    engineReady = true
                    connectionLabel = state.message.ifEmpty { "扫码隧道已连接" }
                    if (state.hostId.isNotEmpty()) remoteProfileId = state.hostId
                    if (relayEngineEndpoint == endpoint && repository != null) return@connect
                    relayEngineEndpoint = endpoint
                    connectRemoteEngine(endpoint, state.localToken)
                }
                DshRelayPhase.ERROR -> {
                    engineReady = false
                    relayEngineEndpoint = ""
                    connectionLabel = state.message.ifEmpty { "扫码连接失败" }
                }
                DshRelayPhase.RECONNECTING -> {
                    relayEngineEndpoint = ""
                    (remoteRepo)?.stop()
                    repository = null
                    connectionLabel = "扫码连接重试中"
                    syncTurnStatusTicker()
                }
                DshRelayPhase.STOPPED -> {
                    engineReady = false
                    relayEngineEndpoint = ""
                    (remoteRepo)?.stop()
                    repository = null
                    connectionLabel = "扫码连接已断开"
                }
                else -> {
                    if (state.localPort <= 0) relayEngineEndpoint = ""
                    connectionLabel = state.message.ifEmpty { "正在建立扫码隧道" }
                }
            }
        }
    }

    private fun startSshEngine(generation: Long) {
        if (sshHost.isBlank() || sshUser.isBlank() || sshKeyId.isBlank()) {
            connectionLabel = "请配置 SSH 连接"
            openConnectionSettings()
            return
        }
        val module = acquireModule<DshEngineModule>(DshEngineModule.MODULE_NAME)
        engineModule = module
        connectionLabel = "正在连接 SSH"
        module.startSsh(DshSshConfig(
            host = sshHost,
            port = sshPort.toIntOrNull() ?: 22,
            username = sshUser,
            remoteDshPort = sshDshPort.toIntOrNull() ?: 3080,
            keyId = sshKeyId,
            hostFingerprint = sshFingerprint,
            keyPassphrase = sshKeyPassphrase,
        )) { state ->
            if (!isCurrent(generation, DshConnectionMode.SSH)) return@startSsh
            when (state.phase) {
                DshSshPhase.FINGERPRINT_REQUIRED -> {
                    sshFingerprint = state.message
                    sshSettingsError = "首次连接需要确认主机指纹：${state.message}"
                    openConnectionSetup()
                }
                DshSshPhase.READY -> {
                    engineReady = true
                    connectionLabel = "正在检查远程 DSH"
                    connectRemoteEngine("http://127.0.0.1:${state.localPort}")
                }
                DshSshPhase.RECONNECTING -> connectionLabel = "SSH 重连中"
                DshSshPhase.ERROR -> {
                    engineReady = false
                    connectionLabel = "SSH 连接失败"
                    sshSettingsError = state.message
                    openConnectionSetup()
                }
                DshSshPhase.STOPPED -> {
                    engineReady = false
                    repository = null
                    connectionLabel = "SSH 已断开"
                }
                else -> connectionLabel = state.message.ifEmpty { "正在连接 SSH" }
            }
        }
    }

    private fun connectRemoteEngine(baseUrl: String, token: String = "") {
        resetSessionActions()
        messageForkVersion++
        messageForkBusy = false
        closeArchiveList()
        sessionArchiveVisible = false
        sessionArchiveBusy = false
        sessionArchiveError = ""
        (remoteRepo)?.stop()
        repository = DshRemoteHostRepository(
            network = acquireModule<NetworkModule>(NetworkModule.MODULE_NAME),
            webSocket = acquireModule<DshWebSocketModule>(DshWebSocketModule.MODULE_NAME),
            connection = DshHostConnection(baseUrl, token),
            pagerId = pagerId,
            onState = { state -> handleHostRuntimeState(state) },
            onQueueSnapshot = { sessionId ->
                if (sessionId == activeSessionId) {
                    refreshQueueDock()
                    refreshPendingInteractions()
                }
            },
            onJobsSnapshot = { sessionId ->
                if (sessionId == activeSessionId) refreshJobsPanel()
            },
            onSessionStatus = { sessionId, running ->
                updateSessionMetadata(sessionId) { it.copy(running = running) }
                if (sessionId == activeSessionId) {
                    val wasRunning = sessionRunning
                    sessionRunning = running
                    if (wasRunning != running) {
                        resyncStreamingWithHost(
                            sessionId,
                            if (running) "host-session-running" else "host-session-idle",
                        )
                    }
                    syncTurnStatusTicker()
                }
            },
            onProjection = { sessionId, key, value, seq ->
                if (key == "title") {
                    val title = value.trim().removeSurrounding("\"")
                    if (title.isNotEmpty()) updateSessionMetadata(sessionId) { it.copy(title = title) }
                }
                if (sessionId == activeSessionId) {
                    when (key) {
                        "goal" -> goalSnapshot = parseGoalProjection(value)
                        "imageLimits" -> {
                            val limits = runCatching {
                                DshImageLimits.fromJson(
                                    com.tencent.kuikly.core.nvi.serialization.json.JSONObject(value),
                                )
                            }.getOrNull()
                            if (limits != null) imageLimits = limits
                        }
                    }
                }
            },
            onSessionEvent = { sessionId, event ->
                // HostProtocol records each event with its original type and metadata.
                // UI projection must not duplicate it or mislabel assistant/message as a chunk.
                if (sessionId == activeSessionId) {
                    when (event.type) {
                        "turn/start" -> streamingSourceSeq = null
                        "assistant/chunk" -> if (streaming) streamingSourceSeq = event.seq
                        "tool/call" -> showRunningTool(event)
                        "tool/result" -> settleRunningTool(event)
                        "user/message" -> showContextInjection(event)
                        "assistant/message" -> showAssistantBlocks(event)
                    }
                }
            },
            onRemoteEvent = { event ->
                if (activeSessionId.isNotEmpty() && isRemoteCatalogInvalidationEvent(event)) {
                    loadSkills(activeSessionId)
                    loadModels(activeSessionId)
                }
            },
            onPendingInteraction = { sessionId ->
                refreshPendingSessionIds()
                if (sessionId == activeSessionId) {
                    refreshPendingInteractions()
                    loadWebTimeline(sessionId, scrollToEndAfterLoad = true)
                }
            },
        )
        loadRepository(preferredSessionId = activeSessionId)
    }

    private fun handleHostRuntimeState(state: DshHostRuntimeState) {
        if (!connectionCoordinator.isActive(connectionMode)) return
        val wasReconnecting = isReconnectLabel(connectionLabel)
        connectionLabel = when (state.phase) {
            DshHostRuntimePhase.CONNECTING -> "正在打开远程事件流"
            DshHostRuntimePhase.HOST_HANDSHAKE -> "正在检查远程 DSH"
            DshHostRuntimePhase.SYNCING -> "正在同步远程会话"
            DshHostRuntimePhase.READY -> "远程 DSH 已就绪"
            DshHostRuntimePhase.RECONNECTING -> reconnectLabel()
            DshHostRuntimePhase.ERROR -> "远程 DSH 连接失败"
            DshHostRuntimePhase.STOPPED -> "远程 DSH 已停止"
            DshHostRuntimePhase.DISCONNECTED -> "等待远程连接"
        }
        val connLogType = when (state.phase) {
            DshHostRuntimePhase.CONNECTING -> "connect.connecting"
            DshHostRuntimePhase.HOST_HANDSHAKE -> "connect.handshake"
            DshHostRuntimePhase.SYNCING -> "connect.syncing"
            DshHostRuntimePhase.READY -> "connect.ready"
            DshHostRuntimePhase.RECONNECTING -> "connect.reconnecting"
            DshHostRuntimePhase.ERROR -> "connect.error"
            DshHostRuntimePhase.STOPPED -> "connect.stopped"
            DshHostRuntimePhase.DISCONNECTED -> "connect.disconnected"
        }
        val connLogLevel = if (state.phase == DshHostRuntimePhase.ERROR) LogLevel.ERROR else LogLevel.INFO
        val connErr = state.message.let { if (it.isNotEmpty()) " error='${DshStreamLog.preview(it)}'" else "" }
        DshStreamLog.log(connLogLevel, connLogType, "$connLogType mode=$connectionMode$connErr", null, null)
        if (archiveListVisible && state.phase in listOf(DshHostRuntimePhase.RECONNECTING, DshHostRuntimePhase.ERROR, DshHostRuntimePhase.STOPPED)) {
            archiveRequestGeneration++
            archiveListLoading = false
            archiveOpeningId = ""
            archiveListError = "连接已中断，请连接后刷新归档列表"
            archivedSessions.clear()
            archiveGroups.clear()
            archiveProjectOptions.clear()
            archiveConfirm = null
            archiveBusy = false
        }
        if (state.phase == DshHostRuntimePhase.READY && wasReconnecting) {
            loadRepository(preferredSessionId = activeSessionId)
        }
        syncTurnStatusTicker()
    }

    private fun connectLocalEngine(apiKey: String) {
        connectionLabel = "本地内核启动中"
        repository = DshHostRepository(
            network = acquireModule<NetworkModule>(NetworkModule.MODULE_NAME),
            sse = acquireModule<DshSseModule>(DshSseModule.MODULE_NAME),
            connection = DshHostConnection(LOCAL_ENGINE_URL),
            pagerId = pagerId,
        )
        syncLocalCredential(apiKey, 0)
    }

    private fun syncLocalCredential(apiKey: String, attempt: Int) {
        val hostRepository = repository ?: return
        hostRepository.saveDeepSeekApiKey(apiKey, {
            connectionLabel = "已连接"
            loadRepository()
        }, { error ->
            if (attempt < ENGINE_CONNECT_RETRIES) {
                connectionLabel = "本地内核启动中"
                setTimeout(pagerId, ENGINE_RETRY_DELAY_MS) {
                    syncLocalCredential(apiKey, attempt + 1)
                }
            } else {
                connectionLabel = "内核启动失败"
                messages.clear()
                messages.add(DshMessage(
                    "engine-start-error",
                    DshMessageRole.ERROR,
                    "本地 DeepSeek Harness 内核暂未就绪：$error",
                ))
            }
        })
    }

    private fun saveDeepSeekApiKey() {
        val key = apiKeyDraft.trim()
        when {
            key.isEmpty() -> {
                credentialSetupError = "请输入 API Key 后继续。"
                return
            }
            key.any { it.code !in 0x21..0x7E } -> {
                credentialSetupError = "API Key 格式错误，请检查后重试。"
                return
            }
        }
        credentialSetupBusy = true
        credentialSetupError = ""
        if (sshMode) {
            val hostRepository = repository
            if (hostRepository == null) {
                credentialSetupBusy = false
                credentialSetupError = "远程 DSH 尚未就绪"
                return
            }
            hostRepository.saveDeepSeekApiKey(key, {
                postToUi {
                    apiKeyDraft = ""
                    apiKeyInputView?.setText("")
                    credentialSetupBusy = false
                    updateCredentialSetupVisibility(false)
                    dismissKeyboard()
                    connectionLabel = "远程 DSH 已更新"
                    loadRepository()
                }
            }, { error ->
                postToUi {
                    credentialSetupBusy = false
                    credentialSetupError = "无法修改电脑端 DSH：$error"
                }
            })
            return
        }
        val saved = runCatching { localStore?.saveApiKey(key) }
        if (saved.isFailure || localStore == null) {
            credentialSetupBusy = false
            credentialSetupError = saved.exceptionOrNull()?.message ?: "本地数据库不可用"
            return
        }
        apiKeyDraft = ""
        apiKeyInputView?.setText("")
        credentialSetupBusy = false
        credentialSetupError = ""
        updateCredentialSetupVisibility(false)
        dismissKeyboard()
        pendingApiKey = key
        if (engineReady) {
            connectLocalEngine(key)
        } else {
            connectionLabel = "等待本地内核启动"
        }
    }

    private fun openCredentialSettings() {
        dismissKeyboard()
        commandSheetVisible = false
        //closeSessionDrawer()
        credentialSetupTitle = if (sshMode) "修改电脑端 DSH 的 API Key" else "设置 DeepSeek API Key"
        credentialSetupError = ""
        apiKeyDraft = pendingApiKey
        updateCredentialSetupVisibility(true)
    }

    private fun openSettingsPage() {
        dismissKeyboard()
        commandSheetVisible = false
        closeSessionDrawerImmediately()
        reloadSettings()
        loadHostVersion()
        if (isRemoteHost) reloadModelsSettings(showLoading = false)
        settingsPageVisible = true
    }

    private fun closeSettingsPage() {
        settingsPageVisible = false
        settingsChoiceKind = ""
        settingsChoiceBusy = false
    }

    // ===== 个性化「对话展示」 =====
    private fun openPersonalizationPage() {
        dismissKeyboard()
        personalizationPageVisible = true
    }

    private fun closePersonalizationPage() {
        personalizationPageVisible = false
    }

    private fun applyChatProcessMode(mode: DshProcessDisplayMode) {
        chatProcessMode = mode
        runCatching {
            acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
                .setString(DSH_PREF_PROCESS_DISPLAY, dshProcessDisplayValue(mode))
        }
        remountConversationList(activeSessionId)
    }

    private fun applyChatExpandInModal(enabled: Boolean) {
        chatExpandInModal = enabled
        runCatching {
            acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
                .setString(DSH_PREF_EXPAND_MODAL, if (enabled) "1" else "0")
        }
        remountConversationList(activeSessionId)
    }

    private fun applyChatShowConnectors(enabled: Boolean) {
        chatShowConnectors = enabled
        runCatching {
            acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
                .setString(DSH_PREF_SHOW_CONNECTORS, if (enabled) "1" else "0")
        }
        remountConversationList(activeSessionId)
    }

    private fun applyChatShowResultCards(enabled: Boolean) {
        chatShowResultCards = enabled
        runCatching {
            acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
                .setString(DSH_PREF_SHOW_RESULT_CARDS, if (enabled) "1" else "0")
        }
        remountConversationList(activeSessionId)
    }

    private fun openExpandedModal(payload: DshExpandedPayload) {
        expandedPayload = payload
    }

    private fun closeExpandedModal() {
        expandedPayload = null
    }

    /** 设置页「模型」摘要：已配置的提供方数量。 */
    private fun modelsSummary(): String {
        val configured = modelsProviders.count { it.configured }
        return if (configured > 0) "已配置 $configured 个" else ""
    }

    private fun openModelsPage() {
        dismissKeyboard()
        modelsEditingProvider = ""
        modelsSaveError = ""
        modelsDeleteTarget = null
        modelsAdding = false
        modelsPickerVisible = false
        modelsCustomAdding = false
        modelsEditorAdvanced = false
        modelsSavedNotice = ""
        modelsPageVisible = true
        reloadModelsSettings()
    }

    private fun closeModelsPage() {
        modelsPageVisible = false
        modelsEditingProvider = ""
        modelsDraftApiKey = ""
        modelsSaveError = ""
        modelsDeleteTarget = null
        modelsAdding = false
        modelsPickerVisible = false
        modelsCustomAdding = false
        modelsEditorAdvanced = false
        modelsSavedNotice = ""
    }

    private fun reloadModelsSettings(showLoading: Boolean = true) {
        if (showLoading) {
            modelsLoading = true
            modelsError = ""
        }
        val repo = repository
        if (repo == null) {
            modelsLoading = false
            if (showLoading) modelsError = "未连接电脑端"
            return
        }
        repo.loadModelsSettings({
            modelsWritable = it.writable
            modelsProviders.clear()
            modelsProviders.addAll(it.providers)
            modelsConfiguredProviders.clear()
            modelsConfiguredProviders.addAll(it.providers.filter { p -> p.configured })
            modelsAddableProviders.clear()
            modelsAddableProviders.addAll(it.providers.filter { p -> !p.configured && p.settingsNs.isNotEmpty() })
            modelsProtocols = it.protocols
            modelsCustomRevision = it.customRevision
            if (modelsCustomProtocol.isEmpty()) modelsCustomProtocol = it.protocols.firstOrNull() ?: ""
            modelsError = ""
            modelsLoading = false
        }, {
            modelsLoading = false
            if (showLoading) modelsError = it else bridgeModule.toast("模型设置刷新失败：$it")
        })
    }

    private fun openProviderEditor(provider: DshProviderConfig) {
        if (modelsEditingProvider == provider.provider && !modelsAdding) {
            modelsEditingProvider = ""
            return
        }
        modelsAdding = false
        modelsCustomAdding = false
        modelsEditingProvider = provider.provider
        modelsEditorAdvanced = false
        modelsDraftBaseUrl = provider.baseUrl
        modelsDraftApiKey = ""
        modelsSaveError = ""
        modelsDraftModels.clear()
        // 保留 raw，保存时才能带出未在编辑器展示的字段（如容量）。
        modelsDraftModels.addAll(provider.models.map { it.copy() })
    }

    private fun cancelAddProvider() {
        modelsAdding = false
        modelsEditingProvider = ""
        modelsSaveError = ""
    }

    /** 选择要添加的已有提供方：以空草稿打开其编辑器。 */
    private fun selectAddableProvider(provider: DshProviderConfig) {
        modelsPickerVisible = false
        modelsCustomAdding = false
        modelsAdding = true
        modelsEditingProvider = provider.provider
        modelsEditorAdvanced = false
        modelsDraftBaseUrl = provider.baseUrl
        modelsDraftApiKey = ""
        modelsSaveError = ""
        modelsSavedNotice = ""
        modelsDraftModels.clear()
        modelsDraftModels.addAll(provider.models.map { it.copy() })
    }

    private fun openCustomProvider() {
        modelsAdding = false
        modelsEditingProvider = ""
        modelsCustomAdding = true
        modelsSavedNotice = ""
        modelsCustomError = ""
        modelsCustomRoute = ""
        modelsCustomName = ""
        modelsCustomBaseUrl = ""
        modelsCustomApiKey = ""
        modelsCustomProtocol = modelsProtocols.firstOrNull() ?: ""
        modelsCustomModels.clear()
        modelsCustomModels.add(DshProviderModel())
    }

    private fun cancelCustomProvider() {
        modelsCustomAdding = false
        modelsCustomError = ""
    }

    private fun updateCustomModel(index: Int, field: String, value: String) {
        if (index !in 0 until modelsCustomModels.size) return
        val current = modelsCustomModels[index]
        modelsCustomModels[index] = when (field) {
            "id" -> current.copy(id = value)
            "name" -> current.copy(name = value)
            else -> current
        }
    }

    private fun removeCustomModel(index: Int) {
        if (index in 0 until modelsCustomModels.size) modelsCustomModels.removeAt(index)
    }

    private fun applyCustomProvider() {
        if (modelsCustomBusy) return
        val route = modelsCustomRoute.trim()
        val taken = modelsConfiguredProviders.map { it.provider } + modelsAddableProviders.map { it.provider }
        val baseUrl = modelsCustomBaseUrl.trim()
        val validationError = dshValidateCustomProvider(
            route = route,
            baseUrl = baseUrl,
            protocol = modelsCustomProtocol,
            models = modelsCustomModels,
            takenRoutes = taken,
        )
        if (validationError != null) {
            modelsCustomError = validationError
            return
        }
        val repo = repository ?: run { modelsCustomError = "未连接电脑端"; return }
        modelsCustomBusy = true
        modelsCustomError = ""
        dshCreateCustomProvider(
            repo = repo,
            route = route,
            displayName = modelsCustomName,
            baseUrl = baseUrl,
            protocol = modelsCustomProtocol,
            apiKey = modelsCustomApiKey.trim(),
            models = modelsCustomModels.toList(),
            revision = modelsCustomRevision,
            onSuccess = {
                modelsCustomBusy = false
                modelsCustomAdding = false
                modelsSavedNotice = "已保存 ${modelsCustomName.trim().ifEmpty { route }}。"
                reloadModelsSettings(showLoading = false)
                reloadSettings(showLoading = false)
            },
            onError = {
                modelsCustomBusy = false
                modelsCustomError = it
            },
        )
    }

    private fun updateDraftModel(index: Int, field: String, value: String) {
        if (index !in 0 until modelsDraftModels.size) return
        val current = modelsDraftModels[index]
        modelsDraftModels[index] = when (field) {
            "id" -> current.copy(id = value)
            "name" -> current.copy(name = value)
            else -> current
        }
    }

    private fun addDraftModel() {
        modelsDraftModels.add(DshProviderModel())
    }

    private fun removeDraftModel(index: Int) {
        if (index in 0 until modelsDraftModels.size) modelsDraftModels.removeAt(index)
    }

    private fun applyProviderEditor(provider: DshProviderConfig) {
        if (modelsSaving) return
        val draftError = dshValidateProviderDraft(modelsDraftModels)
        if (draftError != null) {
            modelsSaveError = draftError
            return
        }
        val repo = repository
        if (repo == null) {
            modelsSaveError = "未连接电脑端"
            return
        }
        modelsSaving = true
        modelsSaveError = ""
        dshSaveProviderProfile(
            repo = repo,
            provider = provider,
            apiKey = modelsDraftApiKey.trim(),
            baseUrl = modelsDraftBaseUrl,
            models = modelsDraftModels.toList(),
            onSuccess = {
                modelsSaving = false
                modelsEditingProvider = ""
                modelsAdding = false
                modelsDraftApiKey = ""
                modelsSavedNotice = "已保存 ${provider.displayName}。"
                reloadModelsSettings(showLoading = false)
                reloadSettings(showLoading = false)
            },
            onError = {
                modelsSaving = false
                modelsSaveError = it
            },
        )
    }

    private fun requestRemoveProvider(provider: DshProviderConfig) {
        modelsSaveError = ""
        modelsDeleteTarget = provider
    }

    private fun confirmRemoveProvider() {
        val provider = modelsDeleteTarget ?: return
        if (modelsDeleting) return
        val repo = repository
        if (repo == null) {
            bridgeModule.toast("未连接电脑端")
            return
        }
        modelsDeleting = true
        dshRemoveProviderProfile(repo, provider, {
            modelsDeleting = false
            modelsDeleteTarget = null
            if (modelsEditingProvider == provider.provider) {
                modelsEditingProvider = ""
                modelsAdding = false
            }
            reloadModelsSettings(showLoading = false)
        }, {
            modelsDeleting = false
            bridgeModule.toast("删除提供方失败：$it")
        })
    }

    private fun reloadSettings(showLoading: Boolean = true) {
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

    private fun loadHostVersion() {
        val repo = repository ?: return
        repo.loadHostVersion({ hostVersion = it }, { })
    }

    private fun connectionModeLabel(): String = dshConnectionModeLabel(connectionMode)

    private fun openAgentModePicker(title: String = "选择模式") {
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

    private fun openSettingsChoice(kind: String, title: String) {
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

    private fun applySettingsChoice(choice: DshSettingsChoice) {
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
    private fun openPermissionPicker() {
        dismissKeyboard()
        commandSheetVisible = false
        permissionPickerVisible = true
        // 快照未加载（通常还没打开过设置页）时预热拉取，弹窗选项展示 host 真实预设。
        if (repository != null && settingsSnapshot.permissionChoices.isEmpty()) {
            reloadSettings(showLoading = false)
        }
    }

    private fun applyPermissionPreset(option: DshPermissionOption) {
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

    private fun pushPermissionPreset(repo: DshRepository, option: DshPermissionOption, revision: Int) {
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

    private fun disconnectFromHost() {
        DshStreamLog.log(LogLevel.INFO, "connect.disconnect", "connect.disconnect by user", null, null)
        closeSettingsPage()
        stopCurrentEngine()
        bridgeModule.toast("已断开连接")
    }

    private fun openConnectionSettings(preserveError: Boolean = false) {
        dismissKeyboard()
        commandSheetVisible = false
        if (!preserveError) sshSettingsError = ""
        updateSshSettingsVisibility(true)
    }

    private fun updateSshSettingsVisibility(visible: Boolean) {
        sshSettingsVisible = visible
        if (pageData.isAndroid || pageData.isIOS) {
            bridgeModule.setSystemBarsDimmed(visible)
        }
    }

    private fun setConnectionMode(useSsh: Boolean) {
        connectionMode = if (useSsh) DshConnectionMode.SSH else DshConnectionMode.RELAY
        sshSettingsError = ""
    }

    private fun pickSshKey() {
        bridgeModule.pickSshKey { uri ->
            if (uri.isEmpty()) return@pickSshKey
            sshSettingsBusy = true
            bridgeModule.importSshKey(uri) { keyId ->
                postToUi {
                    sshSettingsBusy = false
                    if (keyId.isEmpty()) {
                        sshSettingsError = "无法导入 SSH 私钥"
                    } else {
                        sshKeyId = keyId
                        sshKeyLabel = "已导入私钥"
                        sshSettingsError = ""
                    }
                }
            }
        }
    }

    private fun trustSshFingerprint() {
        if (sshFingerprint.isBlank()) return
        acquireModule<DshEngineModule>(DshEngineModule.MODULE_NAME).trustSshFingerprint(sshFingerprint)
        runCatching {
            localStore?.saveRemoteProfile(DshRemoteProfile(
                host = sshHost.trim(),
                sshPort = sshPort.toIntOrNull() ?: 22,
                username = sshUser.trim(),
                remoteDshPort = sshDshPort.toIntOrNull() ?: 3080,
                keyId = sshKeyId,
                hostFingerprint = sshFingerprint,
            ))
        }
        sshSettingsError = "正在使用已确认的主机指纹连接"
    }

    private fun saveConnectionSettings() {
        if (sshMode) {
            when (val validation = dshValidateSshSettings(sshHost, sshUser, sshPort, sshDshPort, sshKeyId)) {
                is DshSshSettingsValidation.Invalid -> sshSettingsError = validation.message
                is DshSshSettingsValidation.Valid -> {
                    runCatching { localStore?.saveRemoteProfile(DshRemoteProfile(
                        host = sshHost.trim(),
                        sshPort = validation.sshPort,
                        username = sshUser.trim(),
                        remoteDshPort = validation.dshPort,
                        keyId = sshKeyId,
                        hostFingerprint = sshFingerprint,
                    )) }
                    runCatching { localStore?.saveLastConnectionMode(DshConnectionMode.SSH) }
                    updateSshSettingsVisibility(false)
                    stopCurrentEngine()
                    openConnectionSetup()
                }
            }
        } else {
            runCatching { localStore?.saveLastConnectionMode(DshConnectionMode.RELAY) }
            updateSshSettingsVisibility(false)
            stopCurrentEngine()
            openConnectionSetup()
        }
    }

    private fun stopCurrentEngine() {
        resetSessionActions()
        cancelReadableExport()
        pluginRequestVersion++
        pluginInventoryLoading = false
        pluginInventory = emptyList(); pluginRows.clear(); pluginTotal = 0
        pluginSearchInput = ""; pluginSearchHasText = false; pluginKeyword = ""
        pluginExpandedId = ""; pluginActionTarget = null; pluginConfirmAction = ""; pluginBusyId = ""
        pluginActionError = ""; pluginNotice = ""
        pluginConfigLoading = false; pluginConfigCards.clear(); pluginConfigDrafts = emptyMap()
        pluginConfigSecretDrafts = emptyMap(); pluginConfigCollapsed = emptySet()
        pluginConfigBusyNamespace = ""; pluginConfigCardError = emptyMap(); pluginConfigCardNotice = emptyMap()
        if (pluginInventoryVisible) pluginInventoryError = "连接已断开，请连接 Host 后刷新"
        timelineReadVersion++
        val mode = connectionCoordinator.activeModeOr(connectionMode)
        connectionCoordinator.stop()
        (remoteRepo)?.stop()
        repository = null
        goalSnapshot = null
        goalActionBusy = false
        goalActionError = ""
        streamHandle?.cancel()
        streamHandle = null
        when (mode) {
            DshConnectionMode.RELAY -> acquireModule<DshRelayModule>(DshRelayModule.MODULE_NAME).disconnect()
            DshConnectionMode.SSH -> engineModule?.stopSsh()
            DshConnectionMode.LOCAL -> engineModule?.stop()
        }
        engineReady = false
    }

    private fun goalMutation(
        action: (DshRemoteRepository, DshGoalSnapshot, (DshRpcError?) -> Unit) -> Unit,
        onDone: (Boolean) -> Unit = {},
    ) {
        val goal = goalSnapshot ?: return
        val remote = remoteRepo ?: return
        if (goalActionBusy) return
        goalActionBusy = true
        goalActionError = ""
        action(remote, goal) { error ->
            postToUi {
                goalActionBusy = false
                if (error != null) goalActionError = "${error.message} (${error.code})"
                else goalActionError = ""
                onDone(error == null)
            }
        }
    }

    private fun pauseGoal() = goalMutation(action = { remote, goal, callback -> remote.goalPause(activeSessionId, goal, callback) })
    private fun resumeGoal() = goalMutation(action = { remote, goal, callback -> remote.goalResume(activeSessionId, goal, callback) })
    private fun editGoal(objective: String, onDone: (Boolean) -> Unit) = goalMutation(
        action = { remote, goal, callback -> remote.goalEdit(activeSessionId, goal, objective, callback) },
        onDone = onDone,
    )
    private fun clearGoal() = goalMutation(action = { remote, goal, callback ->
        remote.goalClear(activeSessionId, goal) { error ->
            if (error == null) goalSnapshot = null
            callback(error)
        }
    })

    private fun isCurrent(generation: Long, mode: DshConnectionMode): Boolean =
        connectionCoordinator.accepts(generation, mode)

    private fun openConnectionSetup() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            "connection_setup",
            JSONObject().apply {
                put("pageName", "connection_setup")
                // 从主页返回连接页是主动改设置/断开，跳过自动连接，避免立刻又被弹回主页。
                put("skipAutoConnect", true)
            },
        )
    }

    private fun closeCredentialSettings() {
        dismissKeyboard()
        updateCredentialSetupVisibility(false)
    }

    private fun updateCredentialSetupVisibility(visible: Boolean) {
        credentialSetupVisible = visible
        if (pageData.isAndroid || pageData.isIOS) {
            bridgeModule.setSystemBarsDimmed(visible)
        }
    }

    private fun composerFolderLabel(): String {
        val session = sessions.firstOrNull { it.id == activeSessionId }
        val cwd = session?.cwd
        if (cwd.isNullOrEmpty()) return "文件夹（可选）"
        // 只显示绝对路径最后一段（兼容 Windows 反斜杠与 Unix 斜杠，过滤空段），
        // 完整路径仍在右侧「会话详情面板」展示，不丢失信息。
        val leaf = cwd.split("\\", "/").lastOrNull { it.isNotBlank() } ?: return cwd
        return leaf
    }

    private fun createSession() {
        val hostRepository = repository ?: run {
            if (isRemoteHost) {
                closeSessionDrawer()
                bridgeModule.toast("未连接到远程 DSH")
            } else if (pendingApiKey.isEmpty()) {
                connectionLabel = "请先配置 API Key"
                openCredentialSettings()
            } else {
                closeSessionDrawer()
                connectionLabel = "本地 DSH 尚未就绪"
            }
            return
        }
        dismissKeyboard()
        closeSessionDrawer()
        val remoteRepository = hostRepository as? DshRemoteRepository
        // 当前工作区：优先当前会话所在的工作区分组，其次用会话 cwd 反查工作区路径，
        // 最后回退 Host 工作区基线。保证「新会话」留在当前工作区；
        // 只有工作区选择器里的「添加文件夹」才会创建新工作区。
        val activeSession = sessions.firstOrNull { it.id == activeSessionId }
        val currentWorkspaceId = if (isRemoteHost) {
            activeWorkspaceId().takeIf { it.isNotEmpty() }
                ?: activeSession?.cwd?.takeIf { it.isNotEmpty() }
                    ?.let { cwd -> workspaceGroups.firstOrNull { it.path == cwd }?.workspaceId }
                ?: remoteRepository?.workspaceIdForSession(activeSessionId)
        } else {
            null
        }
        val blankSession = if (isRemoteHost) {
            remoteRepository?.blankSessionInWorkspace(currentWorkspaceId)
        } else {
            sessions.firstOrNull { it.blank }
        }
        if (blankSession != null) {
            if (blankSession.id != activeSessionId) {
                selectSession(blankSession.id)
            } else {
                applyActiveSessionChrome()
            }
            loadSkills(blankSession.id)
            postToUi { loadModels(blankSession.id) }
            return
        }
        hostRepository.createSession(currentWorkspaceId, { sessionId ->
            val created = DshSession(
                id = sessionId,
                title = "新会话",
                workspace = "Host",
                updatedLabel = "",
                blank = true,
                permission = permissionValue,
                agentPreset = agentModeValue,
            )
            // Keep the existing sessions when creating a new one. Clearing
            // this list also rewrites SQLite with only the newly created row.
            if (sessions.none { it.id == created.id }) {
                sessions = sessions + created
                reorderSessionsByUpdatedAt()
                refreshVisibleSessions()
            }
            runCatching { localStore?.replaceSessions(activeConnectionId, sessions.toList()) }
            activeSessionId = sessionId
            messages = ObservableList()
            sessionMessageStates[sessionId] = messages
            sessionMessageReady.add(sessionId)
            ensureConversationPanel(sessionId)
            draft = ""
            inputView?.setText("")
            applyActiveSessionChrome()
            // 远程模式：同步 Host 会话目录，让新会话归入当前工作区分组并带上 cwd，
            // 避免新会话落入「未分组」导致工作区看似被清除。
            if (isRemoteHost) {
                remoteRepository?.loadSessionCatalog({ catalog ->
                    sessions = catalog.sessions
                    reorderSessionsByUpdatedAt()
                    refreshVisibleSessions()
                    refreshWorkspaceGroups()
                    applyActiveSessionChrome()
                }, { /* 忽略：会话已创建，本地已保留 */ })
            }
            postToUi {
                if (activeSessionId == sessionId) {
                    loadSkills(sessionId)
                    loadModels(sessionId)
                }
            }
        }, { error ->
            connectionLabel = "新会话创建失败"
            messages.add(DshMessage("session-create-error-${messages.size}", DshMessageRole.ERROR, error))
        }, permission = permissionValue, agentPreset = agentModeValue)
    }

    private fun loadHistory(
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

    private fun fetchHostHistory(
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

    private fun loadWebTimeline(
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

    private fun resyncStreamingWithHost(sessionId: String, reason: String) {
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

    private fun isLocalPromptInFlight(): Boolean =
        streaming && streamingAssistantRootId.isNotEmpty()

    private fun rebindStreamingToHistoryTail(): Boolean {
        val live = dshHistoryTailToResume(messages.toList(), streamingTurnAnchorAssistantId)
            ?: return false
        streamingAssistantId = live.id
        streamingAssistantRootId = live.id
        streamingAssistantSegment = 0
        streamingSourceSeq = live.sourceSeq
        streamingAssistantContent = live.content
        return true
    }

    private fun finishStreamingFromHistory(sessionId: String) {
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

    private fun resumeStreamingFromHistory(sessionId: String, reason: String) {
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

    private fun attachAdoptedLiveStream(sessionId: String) {
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

    private fun loadSkills(sessionId: String) {
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

    private fun loadAttachment(sessionId: String, attachmentId: String) {
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

    private fun showRunningTool(event: DshRawSessionEvent) {
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

    private fun showContextInjection(event: DshRawSessionEvent) {
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

    private fun showAssistantBlocks(event: DshRawSessionEvent) {
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

    private fun settleRunningTool(event: DshRawSessionEvent) {
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

    private fun attachmentDataUrl(attachmentId: String): String? {
        attachmentRevision // Read the reactive revision so image rows rerender after downloads.
        return cachedAttachmentDataUrls[attachmentId]
    }

    private fun refreshQueueDock() {
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

    private fun refreshJobsPanel() {
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

    private fun toggleJobsPanel() {
        jobsPanelExpanded = !jobsPanelExpanded
        if (jobsPanelExpanded) {
            jobsNow = bridgeModule.currentTimeStamp()
            scheduleJobsClock()
        }
    }

    private fun scheduleJobsClock() {
        if (!jobsPanelExpanded || jobsClockScheduled || liveJobItems.isEmpty()) return
        jobsClockScheduled = true
        setTimeout(pagerId, 1_000) {
            jobsClockScheduled = false
            if (!jobsPanelExpanded) return@setTimeout
            jobsNow = bridgeModule.currentTimeStamp()
            scheduleJobsClock()
        }
    }

    private fun refreshWorkspaceGroups() {
        ensureDrawerOrdersLoaded()
        if (!isRemoteHost) {
            workspaceGroups = ObservableList()
            workspacePickerFolders.clear()
            return
        }
        val repository = remoteRepo ?: return
        val byId = sessions.associateBy { it.id }
        val groups = repository.workspaceGroups().map { group ->
            val resolved = group.sessions.map { byId[it.id] ?: it }
            val order = if (drawerViewOrderBy == DSH_DRAWER_ORDER_MANUAL) {
                sessionManualOrder[group.workspaceId].orEmpty()
            } else {
                emptyList()
            }
            val orderedSessions = when {
                order.isNotEmpty() -> dshApplyOrder(resolved, order) { it.id }
                drawerViewOrderBy == DSH_DRAWER_ORDER_UPDATED ->
                    resolved.sortedWith(compareByDescending<DshSession> { it.updatedAt }.thenBy { it.id })
                else -> resolved
            }
            group.copy(sessions = orderedSessions)
        }
        val orderedGroups = if (workspaceManualOrder.isEmpty()) {
            groups
        } else {
            dshApplyOrder(groups, workspaceManualOrder) { it.workspaceId }
        }
        if (orderedGroups != workspaceGroups.toList()) workspaceGroups = ObservableList(orderedGroups.toMutableList())
        val folders = orderedGroups.filter { it.workspaceId.isNotEmpty() }
        workspacePickerFolders.clear()
        workspacePickerFolders.addAll(folders)
    }

    // ===== 会话抽屉拖拽排序 =====

    /** 首次使用时按连接范围加载本地保存的自定义顺序。 */
    private fun ensureDrawerOrdersLoaded() {
        val prefs = prefsModule ?: return
        val scope = activeConnectionId
        if (scope == drawerOrderScope) return
        drawerOrderScope = scope
        sessionManualOrder = dshDecodeSessionOrder(runCatching { prefs.getItem(dshSessionOrderKey(scope)) }.getOrNull())
        workspaceManualOrder = dshDecodeOrder(runCatching { prefs.getItem(dshWorkspaceOrderKey(scope)) }.getOrNull())
        drawerViewGroupBy = runCatching { prefs.getItem(dshDrawerGroupByKey(scope)) }.getOrNull()
            ?.takeIf { it == DSH_DRAWER_GROUP_WORKSPACE || it == DSH_DRAWER_GROUP_FLAT }
            ?: DSH_DRAWER_GROUP_WORKSPACE
        drawerViewOrderBy = runCatching { prefs.getItem(dshDrawerOrderByKey(scope)) }.getOrNull()
            ?.takeIf { it == DSH_DRAWER_ORDER_MANUAL || it == DSH_DRAWER_ORDER_UPDATED }
            ?: DSH_DRAWER_ORDER_UPDATED
    }

    private fun persistSessionManualOrder() {
        val prefs = prefsModule ?: return
        runCatching { prefs.setItem(dshSessionOrderKey(activeConnectionId), dshEncodeSessionOrder(sessionManualOrder)) }
    }

    private fun persistWorkspaceManualOrder() {
        val prefs = prefsModule ?: return
        runCatching { prefs.setItem(dshWorkspaceOrderKey(activeConnectionId), dshEncodeOrder(workspaceManualOrder)) }
    }

    private fun persistDrawerViewOptions() {
        val prefs = prefsModule ?: return
        runCatching { prefs.setItem(dshDrawerGroupByKey(activeConnectionId), drawerViewGroupBy) }
        runCatching { prefs.setItem(dshDrawerOrderByKey(activeConnectionId), drawerViewOrderBy) }
    }

    /** 打开抽屉「视图选项」菜单，锚定到点击的按钮坐标（对齐电脑端 ViewOptionsMenu）。 */
    fun openDrawerViewOptions(pageX: Float, pageY: Float) {
        drawerViewOptionsX = pageX
        drawerViewOptionsY = pageY
        drawerViewOptionsVisible = true
    }

    fun closeDrawerViewOptions() {
        drawerViewOptionsVisible = false
    }

    /** 视图选项选择：workspace/flat 切换分组，manual/updated 切换排序。 */
    fun selectDrawerViewOption(id: String) {
        when (id) {
            DSH_DRAWER_GROUP_WORKSPACE, DSH_DRAWER_GROUP_FLAT -> drawerViewGroupBy = id
            DSH_DRAWER_ORDER_MANUAL, DSH_DRAWER_ORDER_UPDATED -> drawerViewOrderBy = id
            else -> return
        }
        drawerViewOptionsVisible = false
        persistDrawerViewOptions()
        refreshVisibleSessions()
        refreshWorkspaceGroups()
    }

    /** 长按会话行开始拖拽；groupKey 为空表示本地（非工作区分组）的扁平列表。 */
    fun beginSessionDrag(groupKey: String, sessionId: String, index: Int, pageY: Float) {
        dragOriginPageY = pageY
        drawerDrag = DshDrawerDrag(
            kind = DshDrawerDragKind.SESSION,
            key = sessionId,
            groupKey = groupKey,
            startIndex = index,
            targetIndex = index,
            offsetY = 0f,
            itemHeight = DSH_DRAWER_ROW_HEIGHT,
        )
    }

    /** 工作区文件夹行拖拽开始。 */
    fun beginWorkspaceDrag(workspaceId: String, index: Int, itemHeight: Float, pageY: Float) {
        dragOriginPageY = pageY
        drawerDrag = DshDrawerDrag(
            kind = DshDrawerDragKind.WORKSPACE,
            key = workspaceId,
            groupKey = DSH_WORKSPACE_DRAG_SCOPE,
            startIndex = index,
            targetIndex = index,
            offsetY = 0f,
            itemHeight = itemHeight,
        )
    }

    /** 拖拽过程中更新位移与落点；heights 为当前列表各项高度。 */
    fun updateDrawerDrag(pageY: Float, heights: List<Float>) {
        val state = drawerDrag
        if (state.kind == DshDrawerDragKind.NONE) return
        val offset = pageY - dragOriginPageY
        val target = if (heights.isEmpty()) state.startIndex else dshDropIndex(offset, state.startIndex, heights)
        drawerDrag = state.copy(offsetY = offset, targetIndex = target)
    }

    /** 拖拽结束：按落点提交顺序并持久化。 */
    fun endDrawerDrag() {
        val state = drawerDrag
        drawerDrag = DshDrawerDrag()
        if (state.kind == DshDrawerDragKind.NONE) return
        if (state.startIndex < 0 || state.targetIndex < 0 || state.startIndex == state.targetIndex) return
        when (state.kind) {
            DshDrawerDragKind.SESSION -> commitSessionOrder(state.groupKey, state.startIndex, state.targetIndex)
            DshDrawerDragKind.WORKSPACE -> commitWorkspaceOrder(state.startIndex, state.targetIndex)
            DshDrawerDragKind.NONE -> Unit
        }
    }

    /** 拖拽被系统取消：丢弃本次排序，不提交。 */
    fun cancelDrawerDrag() {
        drawerDrag = DshDrawerDrag()
    }

    private fun commitSessionOrder(groupKey: String, from: Int, to: Int) {
        if (groupKey.isEmpty()) {
            val ids = visibleSessions.map { it.id }
            val moved = dshMoveItem(ids, from, to)
            if (moved == ids) return
            sessionManualOrder = sessionManualOrder + ("" to moved)
            persistSessionManualOrder()
            markDrawerOrderManual()
            refreshVisibleSessions()
            return
        }
        val group = workspaceGroups.firstOrNull { it.workspaceId == groupKey } ?: return
        val ids = group.sessions.map { it.id }
        val moved = dshMoveItem(ids, from, to)
        if (moved == ids) return
        sessionManualOrder = sessionManualOrder + (groupKey to moved)
        persistSessionManualOrder()
        markDrawerOrderManual()
        refreshWorkspaceGroups()
    }

    /** 手动拖拽后固定为「手动排序」，否则最近更新排序会立即覆盖用户编排。 */
    private fun markDrawerOrderManual() {
        if (drawerViewOrderBy == DSH_DRAWER_ORDER_MANUAL) return
        drawerViewOrderBy = DSH_DRAWER_ORDER_MANUAL
        persistDrawerViewOptions()
    }

    private fun commitWorkspaceOrder(from: Int, to: Int) {
        val ids = workspaceGroups.filter { it.workspaceId.isNotEmpty() }.map { it.workspaceId }
        val moved = dshMoveItem(ids, from, to)
        if (moved == ids) return
        workspaceManualOrder = moved
        persistWorkspaceManualOrder()
        refreshWorkspaceGroups()
    }

    private fun refreshPendingInteractions() {
        refreshPendingSessionIds()
        if (!isRemoteHost) {
            pendingApproval = null
            pendingQuestion = null
            selectedQuestionOptions.clear()
            questionCustom = ""
            questionIndex = 0
            questionError = ""
            questionDrafts.clear()
            return
        }
        val repository = remoteRepo ?: return
        val (approval, question) = repository.pendingInteractions(activeSessionId)
        val hadInteraction = pendingApproval != null || pendingQuestion != null
        pendingApproval = approval
        pendingQuestion = question
        // 授权/提问交互刚出现时收起键盘，让底部提问卡片覆盖输入框，而非浮在键盘上方
        if (!hadInteraction && (approval != null || question != null)) dismissKeyboard()
        questionIndex = questionIndex.coerceIn(0, (question?.questions?.size ?: 1) - 1)
        loadQuestionDraft(questionIndex)
    }

    private fun answerApproval(outcome: String) {
        val repository = remoteRepo ?: return
        val approval = pendingApproval ?: return
        interactionBusy = true
        repository.respondApproval(
            rpcId = approval.rpcId,
            sessionId = approval.sessionId,
            approvalId = approval.approvalId,
            outcome = outcome,
        ) { accepted, reason ->
            postToUi {
                interactionBusy = false
                if (!accepted) {
                    connectionLabel = interactionFailureLabel(reason)
                    return@postToUi
                }
                refreshPendingInteractions()
            }
        }
    }

    private fun toggleQuestionOption(label: String) {
        val item = pendingQuestion?.questions?.getOrNull(questionIndex) ?: return
        if (!item.multiSelect) {
            selectedQuestionOptions.clear()
            questionCustom = ""
        }
        if (selectedQuestionOptions.contains(label)) selectedQuestionOptions.remove(label)
        else selectedQuestionOptions.add(label)
        questionError = ""
        questionDrafts[questionIndex] = DshQuestionDraft(selectedQuestionOptions.toList(), questionCustom)
        questionHasSelection = selectedQuestionOptions.isNotEmpty() || questionCustom.isNotBlank()
    }

    private fun updateQuestionCustom(value: String) {
        val item = pendingQuestion?.questions?.getOrNull(questionIndex) ?: return
        if (!item.multiSelect) selectedQuestionOptions.clear()
        questionCustom = value
        questionError = ""
        questionDrafts[questionIndex] = DshQuestionDraft(selectedQuestionOptions.toList(), questionCustom)
        questionHasSelection = selectedQuestionOptions.isNotEmpty() || questionCustom.isNotBlank()
    }

    private fun skipQuestion() {
        val count = pendingQuestion?.questions?.size ?: return
        questionDrafts[questionIndex] = DshQuestionDraft(skipped = true)
        selectedQuestionOptions.clear()
        questionCustom = ""
        questionError = ""
        questionHasSelection = false
        if (questionIndex < count - 1) {
            questionIndex += 1
            loadQuestionDraft(questionIndex)
        } else {
            submitQuestion()
        }
    }

    /**
     * 取消提问（关闭按钮）：以 ok=false + code=cancelled 拒绝整个等待
     * （与原版 apiproxy respond 的 cancelled 分支一致，wire 上表现为 ASK_CANCELLED），
     * 同时清除本地 UI 状态。不再把全部题目标记 skipped 后当作正常答案提交。
     */
    private fun cancelQuestion() {
        val question = pendingQuestion ?: return
        if (question.rpcId.isEmpty()) {
            questionError = "这个问题已失效，请等 Agent 重新提问"
            return
        }
        val repository = remoteRepo
        if (repository == null) {
            return
        }
        questionError = ""
        interactionBusy = true
        repository.respondQuestionCancel(question.rpcId, question.sessionId) { accepted, reason ->
            postToUi {
                interactionBusy = false
                if (!accepted) {
                    questionError = interactionFailureLabel(reason)
                    return@postToUi
                }
                repository.clearPending(question.rpcId)
                if (pendingQuestion?.rpcId == question.rpcId) {
                    pendingQuestion = null
                    selectedQuestionOptions.clear()
                    questionCustom = ""
                    questionError = ""
                    questionDrafts.clear()
                }
                refreshPendingInteractions()
                if (activeSessionId == question.sessionId) {
                    loadWebTimeline(question.sessionId, scrollToEndAfterLoad = true)
                }
            }
        }
    }

    private fun navigateQuestion(delta: Int) {
        val count = pendingQuestion?.questions?.size ?: return
        val next = (questionIndex + delta).coerceIn(0, count - 1)
        if (next == questionIndex) return
        questionDrafts[questionIndex] = DshQuestionDraft(selectedQuestionOptions.toList(), questionCustom)
        questionIndex = next
        questionError = ""
        loadQuestionDraft(next)
    }

    private fun loadQuestionDraft(index: Int) {
        val draft = questionDrafts[index] ?: DshQuestionDraft()
        selectedQuestionOptions.clear()
        selectedQuestionOptions.addAll(draft.selected)
        questionCustom = draft.custom
        questionHasSelection = selectedQuestionOptions.isNotEmpty() || questionCustom.isNotBlank()
    }

    private fun submitQuestion() {
        val repository = remoteRepo
        if (repository == null) {
            return
        }
        val question = pendingQuestion
        if (question == null) {
            return
        }
        // 保留已跳过的草稿：skipQuestion 已把当前题标记为 skipped=true，不能再被未作答草稿覆盖，
        // 否则“跳过最后一题/单题”时会被下方的未作答校验拦下。其余路径的草稿在交互时已写入。
        val currentDraft = questionDrafts[questionIndex]
        if (currentDraft?.skipped != true) {
            questionDrafts[questionIndex] = DshQuestionDraft(selectedQuestionOptions.toList(), questionCustom)
        }
        val missing = question.questions.indexOfFirst { item ->
            val draft = questionDrafts[question.questions.indexOf(item)] ?: DshQuestionDraft()
            draft.selected.isEmpty() && draft.custom.isBlank() && !draft.skipped
        }
        if (missing >= 0) {
            questionIndex = missing
            loadQuestionDraft(missing)
            questionError = "请先选择一项，或自己写答案"
            return
        }
        if (question.rpcId.isEmpty()) {
            questionError = "这个问题已失效，请等 Agent 重新提问"
            return
        }
        questionError = ""
        interactionBusy = true
        val answer = buildQuestionAnswer(question, questionDrafts)
        repository.respondQuestion(
            rpcId = question.rpcId,
            sessionId = question.sessionId,
            answer = answer,
        ) { accepted, reason ->
            postToUi {
                interactionBusy = false
                if (!accepted) {
                    questionError = interactionFailureLabel(reason)
                    return@postToUi
                }
                repository.clearPending(question.rpcId)
                if (pendingQuestion?.rpcId == question.rpcId) {
                    pendingQuestion = null
                    selectedQuestionOptions.clear()
                    questionCustom = ""
                    questionError = ""
                    questionDrafts.clear()
                }
                refreshPendingInteractions()
                if (activeSessionId == question.sessionId) {
                    loadWebTimeline(question.sessionId, scrollToEndAfterLoad = true)
                }
            }
        }
    }

    private fun editQueueItem(itemId: String) {
        val item = queueItems.firstOrNull { it.id == itemId } ?: return
        val text = item.text ?: return
        queueDockExpanded = true
        queueEditingId = itemId
        queueEditingText = text
    }

    private fun saveQueueItem(itemId: String) {
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

    private fun cancelQueueItemEdit() {
        queueEditingId = ""
        queueEditingText = ""
    }

    private fun removeQueueItem(itemId: String) {
        updateQueueItem(itemId, JSONObject().apply { put("kind", "remove") })
    }

    private fun steerQueueItem(itemId: String) {
        updateQueueItem(itemId, JSONObject().apply { put("kind", "steer") })
    }

    private fun updateQueueItem(itemId: String, action: JSONObject) {
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

    private fun renameActiveSession() {
        val repository = remoteRepo ?: return
        val current = sessions.firstOrNull { it.id == activeSessionId } ?: return
        val title = current.title.takeIf { it != "尚无标题" && it != "新会话" } ?: ""
        if (title.isBlank()) return
        repository.renameSession(activeSessionId, title) { _, _ ->
            postToUi { loadRepository(preferredSessionId = activeSessionId) }
        }
    }

    private fun archiveActiveSession() {
        val repository = remoteRepo ?: return
        repository.archiveSession(activeSessionId) { _, _ ->
            postToUi {
                loadRepository(preferredSessionId = null)
                refreshWorkspaceGroups()
            }
        }
    }

    /** 会话列表按消息时间（updatedAt）从新到旧重排，供加载完成与增量插入后统一调用。 */
    private fun reorderSessionsByUpdatedAt() {
        sessions = sessions.sortedByDescending { it.updatedAt }
    }

    /** 会话产生新消息时刷新 updatedAt（消息时间 = 当前时刻），并按新到旧重排主列表与抽屉分组。 */
    private fun touchSessionActivity(sessionId: String) {
        val idx = sessions.indexOfFirst { it.id == sessionId }
        if (idx < 0) return
        val now = currentTimeMillis()
        if (sessions[idx].updatedAt >= now) return
        sessions = sessions.map { if (it.id == sessionId) it.copy(updatedAt = now) else it }
        (remoteRepo)?.touchSessionActivity(sessionId, now)
        reorderSessionsByUpdatedAt()
        refreshWorkspaceGroups()
        refreshVisibleSessions()
        runCatching { localStore?.replaceSessions(activeConnectionId, sessions.toList()) }
    }

    // ===== 会话 overflow menu：日志 / 重命名 / 归档 / 删除 =====

    /** 从会话抽屉某行的 ⋯ 打开 overflow menu：锁定目标会话并锚定到点击位置，抽屉保持开启。 */
    fun openOverflowMenuFor(sessionId: String, anchorX: Float = -1f, anchorY: Float = -1f) {
        if (overflowMenuVisible) return
        closeMessageActions()
        closeSelectTextModal()
        overflowTargetSessionId = sessionId
        overflowAnchorX = anchorX
        overflowAnchorY = anchorY
        overflowMenuVisible = true
    }

    fun closeOverflowMenu() {
        overflowMenuVisible = false
    }

    /** overflow menu 的目标会话：抽屉行打开时指向该行会话，否则回退到当前会话。 */
    private fun overflowTargetId(): String = overflowTargetSessionId.ifEmpty { activeSessionId }


    fun overflowActions(): ObservableList<DshOverflowAction> {
        val result = ObservableList<DshOverflowAction>()
        result.add(DshOverflowAction("log", "日志", "log.svg"))
        val session = sessions.firstOrNull { it.id == overflowTargetId() }
        if (readableExportBusy) result.add(DshOverflowAction("export-status", "查看分享进度", "share.svg"))
        else if (session != null && !session.blank) result.add(DshOverflowAction("export-text", "分享消息", "share.svg"))
        if (readableExport.canShare(overflowTargetId(), activeConnectionId)) {
            result.add(DshOverflowAction("share-text", "重新分享上次内容", "share.svg"))
        }
        if (session != null && !session.blank) {
            result.add(DshOverflowAction("rename", "重命名", "rename.svg"))
            result.add(DshOverflowAction("archive", "归档", "archive.svg"))
            result.add(DshOverflowAction("delete", "删除", "delete.svg", danger = true))
        }
        return result
    }

    fun onOverflowAction(id: String) {
        sessionActionTargetId = overflowTargetId()
        closeOverflowMenu()
        when (id) {
            "log" -> openSessionLogs()
            "export-text" -> beginExportSelection(sessionActionTargetId)
            "export-status" -> { closeSessionDrawer(); readableExportDialogVisible = true }
            "share-text" -> shareReadableExport()
            "rename" -> openSessionRenameDialog()
            "archive" -> { sessionArchiveError = ""; sessionArchiveVisible = true }
            "delete" -> { sessionDeleteError = ""; sessionDeleteVisible = true }
        }
    }

    // ===== 会话日志（独立路由页 DshLogPage） =====

    fun openSessionLogs() {
        closeSessionDrawer()
        openLogPage(overflowTargetId())
    }

    fun openDiagnosticLogs() {
        closeSessionDrawer()
        openLogPage("")
    }

    /** 打开统一日志页；logSessionId 仅作为可修改、可清除的初始会话筛选。 */
    private fun openLogPage(logSessionId: String) {
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

    private fun registerLogPageNotifications() {
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

    private fun unregisterLogPageNotifications() {
        val notify = acquireModule<NotifyModule>(NotifyModule.MODULE_NAME)
        logJumpNotifyRef?.let { notify.removeNotify(DshLogPageContract.EVENT_JUMP_TO_SESSION, it) }
        logJumpNotifyRef = null
    }

    private fun jumpToSession(sessionId: String, isCurrent: () -> Boolean = { true }, onResult: (Boolean, String) -> Unit) {
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
    private fun reportLastCrashIfAny() {
        val source = DshLogService.current ?: return
        val epoch = source.clearVersion()
        if (pagerData.platform == "ohos") {
            bridgeModule.readLastCrashAsync { raw -> reportCrashRecord(raw, source, epoch) }
        } else {
            reportCrashRecord(runCatching { bridgeModule.readLastCrash() }.getOrDefault(""), source, epoch)
        }
    }

    private fun reportCrashRecord(raw: String, source: DshLogWriteBehind, epoch: Long) {
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

    fun openSessionRenameDialog() {
        val session = sessions.firstOrNull { it.id == sessionActionTargetId } ?: return
        dismissKeyboard()
        sessionRenameDraft = session.title.takeIf { it != "尚无标题" && it != "新会话" } ?: ""
        sessionRenameError = ""
        sessionRenameVisible = true
    }

    fun cancelSessionRename() {
        if (sessionRenameBusy) return
        bridgeModule.closeKeyboard()
        sessionRenameVisible = false
        sessionRenameError = ""
    }

    fun saveSessionRename() {
        if (sessionRenameBusy) return
        val targetId = sessionActionTargetId
        if (targetId.isEmpty()) return
        val repository = remoteRepo ?: run {
            sessionRenameError = "当前连接不支持重命名会话"
            return
        }
        val title = sessionRenameDraft.trim()
        if (title.isEmpty()) {
            sessionRenameError = "名称不能为空"
            return
        }
        sessionRenameBusy = true
        sessionRenameError = ""
        bridgeModule.closeKeyboard()
        val connection = activeConnectionId
        repository.renameSession(targetId, title) { _, error ->
            postToUi {
                if (!pageAlive || this.repository !== repository || activeConnectionId != connection) return@postToUi
                sessionRenameBusy = false
                if (error != null) {
                    sessionRenameError = when (error.code) {
                        "title-invalid" -> "会话名称无效（不能为空或只包含空白字符），请修改后重试"
                        "session-not-found" -> "会话不存在，可能已被删除，请刷新列表"
                        else -> error.message
                    }
                    return@postToUi
                }
                sessionRenameVisible = false
                catalogRequestGeneration++
                updateSessionMetadata(targetId) { it.copy(title = title) }
                bridgeModule.toast("会话已重命名")
            }
        }
    }

    // ===== 归档会话 =====

    private fun openArchiveList() {
        dismissKeyboard()
        closeMessageActions()
        closeSessionDrawerImmediately()
        archivedSessions.clear()
        archiveGroups.clear()
        archiveProjectOptions.clear()
        archiveSearch = ""
        archiveProjectFilter = ""
        archiveSort = DshArchiveSort.UPDATED
        archiveMenu = ""
        archiveNotice = ""
        archiveConfirm = null
        archiveBusy = false
        archiveListVisible = true
        archiveOpeningId = ""
        refreshArchiveList()
    }

    private fun closeArchiveList() {
        archiveRequestGeneration++
        archiveListVisible = false
        archiveListLoading = false
        archiveOpeningId = ""
        archiveListError = ""
        archiveBusy = false
        archiveConfirm = null
        archiveMenu = ""
        archivedSessions.clear()
        archiveGroups.clear()
        archiveProjectOptions.clear()
    }

    private fun refreshArchiveList() {
        if (!archiveListVisible || archiveOpeningId.isNotEmpty()) return
        val remote = remoteRepo
        if (remote == null || !remote.isProductReady()) {
            archiveListLoading = false
            archiveListError = "未连接到 Host，请连接后刷新"
            return
        }
        val expected = ++archiveRequestGeneration
        val connection = activeConnectionId
        fun current() = pageAlive && archiveListVisible && expected == archiveRequestGeneration &&
            repository === remote && activeConnectionId == connection
        archiveListLoading = true
        archiveListError = ""
        // 先读 meta（createdAt/cwd），失败也继续，只影响「创建时间」排序。
        remote.loadSessionMeta({ meta ->
            if (!current()) return@loadSessionMeta
            sessionCreatedAt = meta.associate { it.sessionId to it.createdAt }
            loadArchiveCatalog(remote, ::current)
        }, {
            if (!current()) return@loadSessionMeta
            sessionCreatedAt = emptyMap()
            loadArchiveCatalog(remote, ::current)
        })
        setTimeout(pagerId, 35_000) {
            if (current() && archiveListLoading) {
                archiveRequestGeneration++
                archiveListLoading = false
                archiveListError = "归档列表加载超时，请刷新重试"
            }
        }
    }

    private fun loadArchiveCatalog(remote: DshRemoteRepository, current: () -> Boolean) {
        remote.loadSessionCatalog({ catalog ->
            if (!current()) return@loadSessionCatalog
            archiveListLoading = false
            archivedSessions.diffUpdate(catalog.archived) { old, new -> old == new }
            refreshVisibleSessions()
            refreshWorkspaceGroups()
            rebuildArchiveGroups()
        }, { error ->
            if (!current()) return@loadSessionCatalog
            archiveListLoading = false
            archiveListError = "归档列表未更新：${error.message}。请点击刷新重试。"
        })
    }

    private fun rebuildArchiveGroups() {
        val remote = remoteRepo ?: return
        val all = remote.archivedWorkspaceGroups()
        // 项目筛选是「按工作区/文件夹」入口：列出全部工作区，而不是仅有归档会话的工作区。
        archiveProjectOptions.diffUpdate(
            remote.workspaceGroups()
                .filter { it.workspaceId.isNotEmpty() }
                .map { DshArchiveProjectOption(it.workspaceId, it.title) },
        ) { old, new -> old == new }
        var groups = all
        if (archiveProjectFilter.isNotEmpty()) groups = groups.filter { it.workspaceId == archiveProjectFilter }
        val query = archiveSearch.trim()
        if (query.isNotEmpty()) {
            groups = groups.mapNotNull { group ->
                val matched = group.sessions.filter { it.title.contains(query, ignoreCase = true) }
                if (matched.isEmpty()) null else group.copy(sessions = matched)
            }
        }
        val comparator = when (archiveSort) {
            DshArchiveSort.UPDATED -> compareByDescending<DshSession> { it.updatedAt }
            DshArchiveSort.CREATED -> compareByDescending<DshSession> { it.createdAt }
            DshArchiveSort.NAME -> compareBy { it.title.lowercase() }
        }
        val enriched = groups.map { group ->
            group.copy(
                sessions = group.sessions
                    .map { session -> sessionCreatedAt[session.id]?.let { session.copy(createdAt = it) } ?: session }
                    .sortedWith(comparator),
            )
        }
        archiveGroups.diffUpdate(enriched) { old, new -> old == new }
    }

    fun onArchiveSearch(value: String) { archiveSearch = value; rebuildArchiveGroups() }
    fun onArchiveToggleMenu(menu: String) { archiveMenu = menu }
    fun onArchivePickProject(id: String) { archiveProjectFilter = id; archiveMenu = ""; rebuildArchiveGroups() }
    fun onArchivePickSort(value: DshArchiveSort) { archiveSort = value; archiveMenu = ""; rebuildArchiveGroups() }

    fun requestArchiveDeleteSession(sessionId: String) {
        val session = archivedSessions.firstOrNull { it.id == sessionId } ?: return
        archiveConfirm = DshArchiveConfirm(DshArchiveConfirmKind.SESSION, sessionId, session.title, 1)
    }

    /** 取消归档：走 host-plugin unarchive；成功后该会话回到主列表与工作区分组。 */
    fun unarchiveArchivedSession(sessionId: String) {
        if (archiveBusy) return
        val remote = remoteRepo ?: run {
            archiveListError = "未连接 Host"
            return
        }
        val connection = activeConnectionId
        archiveBusy = true
        archiveListError = ""
        archiveNotice = ""
        remote.unarchiveSession(sessionId) { _, error ->
            postToUi {
                if (!pageAlive || this.repository !== remote || activeConnectionId != connection) return@postToUi
                archiveBusy = false
                if (error != null) {
                    archiveListError = "取消归档失败：${error.message}"
                    return@postToUi
                }
                archiveNotice = "已取消归档"
                archivedSessions.diffUpdate(archivedSessions.filterNot { it.id == sessionId }) { old, new -> old == new }
                refreshVisibleSessions()
                refreshWorkspaceGroups()
                rebuildArchiveGroups()
            }
        }
        setTimeout(pagerId, 35_000) {
            if (pageAlive && archiveBusy) {
                archiveBusy = false
                archiveListError = "取消归档超时，请刷新确认结果"
            }
        }
    }

    fun requestArchiveDeleteProject(group: DshWorkspaceGroup) {
        archiveConfirm = DshArchiveConfirm(DshArchiveConfirmKind.PROJECT, group.workspaceId, group.title, group.sessions.size)
    }

    fun requestArchiveDeleteAll() {
        archiveConfirm = DshArchiveConfirm(DshArchiveConfirmKind.ALL, "", "", archivedSessions.size)
    }

    fun cancelArchiveConfirm() { archiveConfirm = null }

    fun archiveDateLabel(timestamp: Long): String =
        if (timestamp <= 0) "" else bridgeModule.dateFormatter(timestamp, "yyyy年M月d日, HH:mm")

    fun confirmArchiveDelete() {
        val target = archiveConfirm ?: return
        if (archiveBusy) return
        val remote = remoteRepo ?: run { archiveListError = "未连接 Host"; return }
        val ids = when (target.kind) {
            DshArchiveConfirmKind.SESSION -> listOf(target.id)
            DshArchiveConfirmKind.PROJECT -> remote.archivedWorkspaceGroups()
                .firstOrNull { it.workspaceId == target.id }?.sessions?.map { it.id } ?: emptyList()
            DshArchiveConfirmKind.ALL -> archivedSessions.map { it.id }
        }
        if (ids.isEmpty()) { archiveConfirm = null; return }
        archiveBusy = true
        archiveConfirm = null
        archiveNotice = ""
        remote.deleteSessions(ids) { deleted, failed ->
            archiveBusy = false
            archiveNotice = if (failed.isEmpty()) "已删除 ${deleted.size} 条会话"
            else "已删除 ${deleted.size} 条，${failed.size} 条失败：${failed.first().second}"
            refreshArchiveList()
        }
        setTimeout(pagerId, 35_000) {
            if (pageAlive && archiveBusy) {
                archiveBusy = false
                archiveListError = "删除超时，请刷新确认结果"
            }
        }
    }

    private fun openArchivedSession(sessionId: String) {
        if (archiveListLoading || archiveOpeningId.isNotEmpty()) return
        val expected = ++archiveRequestGeneration
        val previousSession = activeSessionId
        archiveOpeningId = sessionId
        archiveListError = ""
        fun current() = archiveListVisible && expected == archiveRequestGeneration && activeSessionId == previousSession
        jumpToSession(sessionId, isCurrent = ::current) { ok, message ->
            if (!archiveListVisible || expected != archiveRequestGeneration) return@jumpToSession
            if (ok) closeArchiveList() else {
                archiveOpeningId = ""
                archiveListError = message
            }
        }
        setTimeout(pagerId, 35_000) {
            if (pageAlive && current() && archiveOpeningId.isNotEmpty()) {
                archiveRequestGeneration++
                archiveOpeningId = ""
                archiveListError = "读取历史超时，请重试"
            }
        }
    }

    fun confirmSessionArchive() {
        if (sessionArchiveBusy) return
        val targetId = sessionActionTargetId
        if (targetId.isEmpty()) return
        val repository = remoteRepo ?: run {
            sessionArchiveError = "当前连接不支持归档会话"
            return
        }
        sessionArchiveBusy = true
        sessionArchiveError = ""
        val expectedConnection = activeConnectionId
        repository.archiveSession(targetId) { _, error ->
            postToUi {
                if (!pageAlive || this.repository !== repository || activeConnectionId != expectedConnection) return@postToUi
                sessionArchiveBusy = false
                if (error != null) {
                    sessionArchiveError = error.message
                    return@postToUi
                }
                sessionArchiveVisible = false
                refreshVisibleSessions()
                refreshWorkspaceGroups()
                val catalog = repository.sessionCatalog(sessions.toList())
                val next = catalog.nextActive(activeSessionId.takeUnless { it == targetId })
                if (next != null) {
                    selectMountedSession(next.id)
                    loadRepository(preferredSessionId = next.id, restoreOnError = false)
                } else {
                    cancelStreamingForSessionSwitch()
                    activeSessionId = ""
                    messages = ObservableList()
                    createSession()
                }
            }
        }
    }

    // ===== 删除会话（dsh-session-manager 插件 /delete）=====

    fun confirmSessionDelete() {
        if (sessionDeleteBusy) return
        val targetId = sessionActionTargetId
        if (targetId.isEmpty()) return
        val repository = remoteRepo ?: run {
            sessionDeleteError = "当前连接不支持删除会话"
            return
        }
        sessionDeleteBusy = true
        sessionDeleteError = ""
        val connection = activeConnectionId
        repository.callPlugin("delete", JSONObject().apply { put("sessionId", targetId) }) { _, error ->
            postToUi {
                if (!pageAlive || this.repository !== repository || activeConnectionId != connection) return@postToUi
                sessionDeleteBusy = false
                if (error != null) {
                    sessionDeleteError = error.message
                    return@postToUi
                }
                sessionDeleteVisible = false
                val remaining = sessions.toList().filterNot { it.id == targetId }
                sessions = remaining
                repository.removeSession(targetId)
                sessionMessageStates.remove(targetId)
                sessionCacheStates.remove(targetId)
                sessionMessageReady.remove(targetId)
                conversationPanelIds.remove(targetId)
                refreshVisibleSessions()
                runCatching { localStore?.deleteSession(activeConnectionId, targetId) }
                refreshWorkspaceGroups()
                if (activeSessionId != targetId) return@postToUi
                val next = repository.sessionCatalog(sessions).nextActive(null)
                if (next == null) {
                    createSession()
                } else {
                    selectSession(next.id)
                }
            }
        }
    }

    private fun forkMessage(message: DshMessage) {
        closeMessageActions()
        dismissKeyboard()
        if (messageForkBusy) { bridgeModule.toast("正在创建分支，请稍候"); return }
        val remote = remoteRepo
        if (remote == null || !remote.isProductReady()) { bridgeModule.toast("请先连接 Host 后再分叉"); return }
        // Footer/menu closures can hold a pre-settle row. Resolve the latest metadata by identity.
        val target = messages.firstOrNull { it.id == message.id } ?: run {
            bridgeModule.toast("消息已更新，请重新选择后分叉")
            return
        }
        val sourceSessionId = activeSessionId
        val connection = activeConnectionId
        val version = ++messageForkVersion
        var createdSessionId: String? = null
        messageForkBusy = true
        if (target.sourceSeq != null && !target.streaming) bridgeModule.toast("正在创建分支…")
        fun current() = pageAlive && messageForkVersion == version && repository === remote && activeConnectionId == connection
        remote.forkMessage(sourceSessionId, target) { childSessionId, error ->
            postToUi {
                if (!current()) return@postToUi
                if (error != null || childSessionId == null) {
                    messageForkBusy = false
                    if (error?.code == "message-unsynced" && activeSessionId == sourceSessionId) {
                        bridgeModule.toast("正在同步消息，请稍后再次分叉")
                        loadWebTimeline(sourceSessionId)
                    } else {
                        bridgeModule.toast("分叉失败：${error?.message ?: "Host 未返回新会话 ID"}")
                    }
                    return@postToUi
                }
                createdSessionId = childSessionId
                if (activeSessionId != sourceSessionId) {
                    messageForkBusy = false
                    bridgeModule.toast("分支已创建，可从会话列表打开")
                    loadRepository(preferredSessionId = activeSessionId, restoreOnError = false)
                    return@postToUi
                }
                jumpToSession(childSessionId, isCurrent = { current() && activeSessionId == sourceSessionId }) { ok, detail ->
                    if (!current()) return@jumpToSession
                    messageForkBusy = false
                    bridgeModule.toast(if (ok) "已在新对话中分支" else "分支已创建，打开失败：$detail")
                    loadRepository(preferredSessionId = activeSessionId, restoreOnError = false)
                }
            }
        }
        setTimeout(pagerId, 35_000) {
            if (!current() || !messageForkBusy) return@setTimeout
            messageForkVersion++
            messageForkBusy = false
            bridgeModule.toast(if (createdSessionId == null) "创建分支超时，请刷新会话列表确认结果" else "分支已创建，请从会话列表打开")
        }
    }

    private fun openPluginInventory() {
        dismissKeyboard()
        settingsPageVisible = false
        pluginInventoryVisible = true
        pluginActiveTab = "config"
        pluginSearchInput = ""
        pluginSearchHasText = false
        pluginKeyword = ""
        refreshPluginInventory()
        loadPluginConfig()
    }

    /** 返回键：先关确认弹窗，再收起展开卡片，最后退出插件页。 */
    private fun handlePluginInventoryBack() {
        when {
            pluginConfirmAction.isNotEmpty() -> cancelPluginAction()
            pluginExpandedId.isNotEmpty() -> pluginExpandedId = ""
            else -> closePluginInventory()
        }
    }

    private fun closePluginInventory() {
        pluginRequestVersion++
        pluginInventoryVisible = false
        pluginInventoryLoading = false
        pluginExpandedId = ""
        pluginActionTarget = null
        pluginConfirmAction = ""
        pluginBusyId = ""
        pluginActionError = ""
        pluginNotice = ""
        pluginConfigLoading = false
        pluginConfigError = ""
        pluginConfigCards.clear()
        pluginConfigDrafts = emptyMap()
        pluginConfigSecretDrafts = emptyMap()
        pluginConfigCollapsed = emptySet()
        pluginConfigBusyNamespace = ""
        pluginConfigCardError = emptyMap()
        pluginConfigCardNotice = emptyMap()
        settingsPageVisible = true
    }

    fun selectPluginTab(tab: String) {
        pluginActiveTab = tab
        if (tab == "config" && pluginConfigCards.isEmpty() && !pluginConfigLoading) loadPluginConfig()
    }

    private fun loadPluginConfig() {
        val remote = remoteRepo ?: run {
            pluginConfigLoading = false; pluginConfigError = "请先连接 Host"; return
        }
        val connection = activeConnectionId
        pluginConfigLoading = true
        pluginConfigError = ""
        remote.loadPluginConfig({ state ->
            if (pageAlive && pluginInventoryVisible && connection == activeConnectionId && remote === repository) {
                pluginConfigLoading = false
                pluginConfigWritable = state.writable
                // 首次进入时配置卡默认收起（对齐原版）；后续刷新保留用户展开状态。
                val firstLoad = pluginConfigCards.isEmpty()
                pluginConfigCards.clear()
                pluginConfigCards.addAll(state.cards)
                if (firstLoad) pluginConfigCollapsed = state.cards.map { it.namespace }.toSet()
                pluginConfigDrafts = emptyMap()
                pluginConfigSecretDrafts = emptyMap()
                pluginConfigCardError = emptyMap()
            }
        }, { error ->
            if (pageAlive && pluginInventoryVisible && connection == activeConnectionId && remote === repository) {
                pluginConfigLoading = false
                pluginConfigError = error
            }
        })
    }

    fun pluginConfigDraft(namespace: String, key: String): String {
        val card = pluginConfigCards.firstOrNull { it.namespace == namespace } ?: return ""
        val fallback = card.fields.firstOrNull { it.key == key }?.value ?: ""
        return pluginConfigDrafts["$namespace::$key"] ?: fallback
    }

    fun pluginConfigSecretDraft(namespace: String): String = pluginConfigSecretDrafts[namespace] ?: ""

    fun isPluginConfigCollapsed(namespace: String): Boolean = namespace in pluginConfigCollapsed

    fun hasPluginConfigChanges(namespace: String): Boolean {
        val card = pluginConfigCards.firstOrNull { it.namespace == namespace } ?: return false
        if ((pluginConfigSecretDrafts[namespace] ?: "").isNotEmpty()) return true
        return card.fields.any { it.kind != DshPluginFieldKind.SECRET && pluginConfigDraft(namespace, it.key) != it.value }
    }

    fun onPluginConfigDraft(namespace: String, key: String, value: String) {
        pluginConfigDrafts = pluginConfigDrafts + ("$namespace::$key" to value)
    }

    fun onPluginConfigSecretDraft(namespace: String, value: String) {
        pluginConfigSecretDrafts = pluginConfigSecretDrafts + (namespace to value)
    }

    fun togglePluginConfigCollapsed(namespace: String) {
        pluginConfigCollapsed = if (namespace in pluginConfigCollapsed) {
            pluginConfigCollapsed - namespace
        } else {
            pluginConfigCollapsed + namespace
        }
    }

    fun discardPluginConfigCard(namespace: String) {
        clearPluginConfigDrafts(namespace)
        pluginConfigCardNotice = pluginConfigCardNotice - namespace
    }

    fun savePluginConfigCard(namespace: String) {
        val card = pluginConfigCards.firstOrNull { it.namespace == namespace } ?: return
        if (pluginConfigBusyNamespace.isNotEmpty()) return
        val remote = remoteRepo ?: run {
            pluginConfigCardError = pluginConfigCardError + (namespace to "请先连接 Host"); return
        }
        val ops = JSONArray()
        var invalid = ""
        for (field in card.fields) {
            if (field.kind == DshPluginFieldKind.SECRET) continue
            val draft = pluginConfigDraft(namespace, field.key)
            if (draft == field.value) continue
            val path = JSONArray().apply { put(field.key) }
            if (field.kind == DshPluginFieldKind.NUMBER) {
                val text = draft.trim()
                if (text.isEmpty()) {
                    ops.put(JSONObject().apply { put("op", "unset"); put("path", path) })
                } else {
                    val parsed = text.toIntOrNull()
                    if (parsed == null) {
                        invalid = "「${field.label}」请填数字，或留空使用默认值"
                        break
                    }
                    ops.put(JSONObject().apply { put("op", "set"); put("path", path); put("value", parsed) })
                }
            } else {
                ops.put(JSONObject().apply { put("op", "set"); put("path", path); put("value", draft) })
            }
        }
        if (invalid.isNotEmpty()) {
            pluginConfigCardError = pluginConfigCardError + (namespace to invalid)
            return
        }
        val secret = pluginConfigSecretDraft(namespace).trim()
        if (ops.length() == 0 && secret.isEmpty()) return
        val save = DshPluginConfigSave(
            namespace = namespace,
            ops = ops,
            expectedRevision = card.revision,
            credentialRef = if (secret.isNotEmpty()) card.secretRef else "",
            credentialValue = secret,
        )
        pluginConfigBusyNamespace = namespace
        pluginConfigCardError = pluginConfigCardError - namespace
        pluginConfigCardNotice = pluginConfigCardNotice - namespace
        remote.savePluginConfig(save, {
            if (pageAlive && pluginInventoryVisible) {
                pluginConfigBusyNamespace = ""
                clearPluginConfigDrafts(namespace)
                pluginConfigCardNotice = pluginConfigCardNotice + (namespace to "已保存")
                loadPluginConfig()
            }
        }, { error ->
            if (pageAlive && pluginInventoryVisible) {
                pluginConfigBusyNamespace = ""
                pluginConfigCardError = pluginConfigCardError + (namespace to error)
            }
        })
    }

    private fun clearPluginConfigDrafts(namespace: String) {
        val prefix = "$namespace::"
        pluginConfigDrafts = pluginConfigDrafts.filterKeys { !it.startsWith(prefix) }
        pluginConfigSecretDrafts = pluginConfigSecretDrafts - namespace
        pluginConfigCardError = pluginConfigCardError - namespace
    }

    private fun togglePluginExpanded(entry: DshPluginEntry) {
        pluginExpandedId = if (pluginExpandedId == entry.id) "" else entry.id
    }

    /** 启用直接执行；停用先弹二次确认。 */
    private fun requestPluginToggle(entry: DshPluginEntry, enable: Boolean) {
        if (pluginBusyId.isNotEmpty()) return
        pluginActionTarget = entry
        pluginActionError = ""
        pluginNotice = ""
        if (enable) performPluginAction("enable") else pluginConfirmAction = "disable"
    }

    /** 重载先弹二次确认。 */
    private fun requestPluginReload(entry: DshPluginEntry) {
        if (pluginBusyId.isNotEmpty()) return
        pluginActionTarget = entry
        pluginActionError = ""
        pluginNotice = ""
        pluginConfirmAction = "reload"
    }

    private fun cancelPluginAction() {
        pluginConfirmAction = ""
        pluginActionError = ""
    }

    private fun confirmPluginAction() {
        val action = pluginConfirmAction
        pluginConfirmAction = ""
        if (action.isNotEmpty()) performPluginAction(action)
    }

    private fun performPluginAction(action: String) {
        val entry = pluginActionTarget ?: run {
            pluginActionError = "未选择插件"; return
        }
        val remote = remoteRepo ?: run {
            pluginActionError = "请先连接 Host"; return
        }
        if (pluginBusyId.isNotEmpty()) return
        val connection = activeConnectionId
        pluginBusyId = entry.id
        pluginActionError = ""
        pluginNotice = ""
        remote.pluginAction(entry.id, action, {
            if (pageAlive && connection == activeConnectionId) {
                pluginBusyId = ""
                pluginNotice = "${pluginActionLabel(action)}指令已下发，正在刷新状态"
                refreshPluginInventory()
            }
        }, { error ->
            if (pageAlive && connection == activeConnectionId) {
                pluginBusyId = ""
                pluginActionError = error
            }
        })
        setTimeout(35_000) {
            if (pageAlive && pluginBusyId == entry.id && connection == activeConnectionId) {
                pluginBusyId = ""
                pluginActionError = "操作超时，请刷新确认结果"
            }
        }
    }

    /** 搜索框回调：同步非响应式原生文本，仅用可观察的过滤词触发列表刷新。 */
    fun onPluginKeyword(value: String) {
        pluginSearchInput = value
        pluginSearchHasText = value.isNotEmpty()
        pluginKeyword = value
        applyPluginFilters()
    }

    /** 清除搜索框：清空原生文本与过滤词，并刷新列表。 */
    fun clearPluginKeyword() {
        pluginSearchInput = ""
        pluginSearchHasText = false
        pluginKeyword = ""
        pluginSearchInputView?.setText("")
        applyPluginFilters()
    }

    private fun applyPluginFilters() {
        pluginTotal = pluginInventory.size
        pluginRows.diffUpdate(filterDshPlugins(pluginInventory, pluginKeyword, pluginPhase)) { old, new -> old == new }
    }

    private fun refreshPluginInventory() {
        val remote = remoteRepo ?: run {
            pluginInventoryLoading = false; pluginInventoryError = "请先连接 Host"; return
        }
        val version = ++pluginRequestVersion
        val connection = activeConnectionId
        pluginInventoryLoading = true
        pluginInventoryError = ""
        fun current() = pageAlive && pluginInventoryVisible && version == pluginRequestVersion &&
            remote === repository && connection == activeConnectionId
        remote.loadPluginInventory({ entries ->
            if (current()) {
                pluginInventoryLoading = false
                pluginInventory = entries
                val ids = entries.map { it.id }.toSet()
                if (pluginExpandedId.isNotEmpty() && pluginExpandedId !in ids) pluginExpandedId = ""
                pluginActionTarget = pluginActionTarget?.let { old -> entries.firstOrNull { it.id == old.id } }
                applyPluginFilters()
            }
        }, { error ->
            if (current()) { pluginInventoryLoading = false; pluginInventoryError = error }
        })
        setTimeout(35_000) {
            if (current() && pluginInventoryLoading) {
                pluginRequestVersion++; pluginInventoryLoading = false; pluginInventoryError = "读取插件超时，请刷新重试"
            }
        }
    }

    private fun resetSessionActions() {
        sessionRenameVisible = false; sessionRenameBusy = false; sessionRenameError = ""
        sessionArchiveVisible = false; sessionArchiveBusy = false; sessionArchiveError = ""
        sessionDeleteVisible = false; sessionDeleteBusy = false; sessionDeleteError = ""
        sessionActionTargetId = ""
        pendingSessionIds = emptySet()
        cancelReadableExport()
        cancelExportSelection()
        readableExport = DshTextExportState()
        readableExportSourceText = null
        readableExportDialogVisible = false
    }

    private fun cancelReadableExport() {
        readableExportVersion++
        readableExportWork?.cancel()
        readableExportWork = null
        if (readableExportBusy) readableExport = readableExport.copy(phase = DshTextExportPhase.CANCELLED, error = "")
    }

    private fun closeReadableExportDialog() {
        if (readableExportBusy) cancelReadableExport()
        readableExportDialogVisible = false
    }

    private fun failReadableExport(message: String) {
        readableExport = readableExport.copy(phase = DshTextExportPhase.FAILED, error = message)
    }

    // ===== 分享多选态操作流程 =====

    /** 入口：从 overflow menu「分享消息」进入多选态，必要时先切到目标会话。 */
    private fun beginExportSelection(sessionId: String, preselectId: String = "") {
        if (sessionId.isBlank() || readableExportBusy) return
        closeOverflowMenu()
        closeMessageActions()
        closeSelectTextModal()
        dismissKeyboard()
        if (sessionId != activeSessionId) {
            pendingExportSelectionSessionId = sessionId
            pendingExportSelectionPreselect = preselectId
            selectSession(sessionId)
        } else {
            enterExportSelection(sessionId, preselectId)
        }
    }

    private fun enterExportSelection(sessionId: String, preselectId: String = "") {
        if (sessionId.isBlank() || sessionId != activeSessionId) return
        exportSelectSessionId = sessionId
        // 默认选中点击消息所属的「对话组」：该条 AI 回复 + 触发它的用户 Prompt
        val group = if (preselectId.isNotEmpty()) {
            dshShareGroupForMessage(sessionMessageState(sessionId), preselectId)
        } else null
        exportSelectedGroups = group?.let { setOf(it.key) } ?: emptySet()
        exportFormat = DshExportFormat.HTML
        exportMoreShareVisible = false
        exportSelectMode = true
    }

    /** 会话切换完成后再进入多选态，避免在多选态中对未激活会话操作。 */
    private fun maybeEnterPendingExportSelection() {
        val pending = pendingExportSelectionSessionId
        if (pending.isEmpty() || pending != activeSessionId) return
        val preselect = pendingExportSelectionPreselect
        pendingExportSelectionSessionId = ""
        pendingExportSelectionPreselect = ""
        enterExportSelection(pending, preselect)
    }

    private fun exportSelectionSessionId(): String = exportSelectSessionId.ifEmpty { activeSessionId }

    private fun exportGroups(): List<DshShareGroup> =
        DshExportSelection.groups(sessionMessageState(exportSelectionSessionId()))

    /** 当前多选态下需要在消息列表打勾的消息 id（同组 Prompt 与回复同时勾选）。 */
    private fun exportSelectedMessageIds(): Set<String> =
        DshExportSelection.selectedMessageIds(exportGroups(), exportSelectedGroups)

    private fun toggleExportMessage(messageId: String) {
        if (!exportSelectMode) return
        val next = DshExportSelection.toggledGroupKey(
            sessionMessageState(exportSelectionSessionId()),
            exportSelectedGroups,
            messageId,
        ) ?: return
        exportSelectedGroups = next
        if (exportSelectedGroups.isEmpty()) exportMoreShareVisible = false
    }

    private fun exportTotalCount(): Int = exportGroups().size

    private fun exportSelectedCount(): Int =
        DshExportSelection.selectedCount(exportGroups(), exportSelectedGroups)

    private fun exportAllSelected(): Boolean =
        DshExportSelection.allSelected(exportGroups(), exportSelectedGroups)

    private fun toggleExportSelectAll() {
        exportSelectedGroups = DshExportSelection.toggleAll(exportGroups(), exportSelectedGroups)
        if (exportSelectedGroups.isEmpty()) exportMoreShareVisible = false
    }

    private fun cancelExportSelection() {
        exportSelectMode = false
        exportSelectSessionId = ""
        pendingExportSelectionSessionId = ""
        pendingExportSelectionPreselect = ""
        exportSelectedGroups = emptySet()
        exportMoreShareVisible = false
        exportPdfBusy = false
    }

    /** 流式助手正文优先使用实时内容；其余按已结算正文导出。 */
    private fun exportContentFor(sessionId: String, message: DshMessage): String =
        if (streaming && activeSessionId == sessionId && streamingAssistantId == message.id &&
            streamingAssistantContent.isNotEmpty()
        ) {
            streamingAssistantContent
        } else {
            message.content
        }

    /**
     * 按界面顺序取出所选对话组的正文：用户 Prompt + 该轮最终助手回复。
     * 只导出正文内容，不含工具调用、思考过程与上下文注入。
     */
    private fun exportSelectedMessages(sessionId: String): List<DshMessage> =
        DshExportSelection.selectedMessages(sessionMessageState(sessionId), exportSelectedGroups)

    /** 校验多选态并返回 (sessionId, 标题, 有序消息)；无有效选择时提示并返回 null。 */
    private fun currentExportSelection(): Triple<String, String, List<DshMessage>>? {
        val sessionId = exportSelectionSessionId()
        if (sessionId.isBlank() || !exportSelectMode) return null
        if (exportSelectedGroups.isEmpty()) { bridgeModule.toast("请先选择要分享的对话"); return null }
        val ordered = exportSelectedMessages(sessionId)
        if (ordered.isEmpty()) { bridgeModule.toast("请先选择要分享的对话"); return null }
        val title = sessions.firstOrNull { it.id == sessionId }?.title ?: sessionId
        return Triple(sessionId, title, ordered)
    }

    /** 更多分享：按所选格式（默认 HTML）生成文件并打开系统分享。 */
    private fun confirmExportSelection() {
        if (readableExportBusy) { bridgeModule.toast("正在分享，请稍候"); return }
        val selection = currentExportSelection() ?: return
        val (sessionId, title, ordered) = selection
        val format = exportFormat
        val connection = activeConnectionId
        val text = DshReadableContent.selection(title, sessionId, ordered, format) { exportContentFor(sessionId, it) }
        cancelExportSelection()
        closeSessionDrawer()
        readableExportSourceText = text
        readableExportExtension = format.extension
        readableExport = DshTextExportState(sessionId, connection, title, DshTextExportPhase.WRITING)
        readableExportDialogVisible = true
        writeReadableExport(++readableExportVersion) { text }
    }

    /** 复制内容：按可读文本复制所选对话组到剪贴板。 */
    private fun copyExportSelection() {
        val selection = currentExportSelection() ?: return
        val (sessionId, title, ordered) = selection
        val count = exportSelectedCount()
        val text = DshReadableContent.selection(title, sessionId, ordered, DshExportFormat.TXT) {
            exportContentFor(sessionId, it)
        }
        bridgeModule.copyToPasteboard(text)
        bridgeModule.toast(if (count > 0) "已复制 $count 组对话" else "已复制所选对话")
    }

    /** 更多分享：展开/收起格式选择行。 */
    private fun toggleExportMoreShare() {
        if (exportSelectedGroups.isEmpty()) { bridgeModule.toast("请先选择要分享的对话"); return }
        exportMoreShareVisible = !exportMoreShareVisible
        if (exportMoreShareVisible) exportFormat = DshExportFormat.HTML
    }

    /** 生成 PDF：Android 走原生 WebView 打印，其他端暂不支持。 */
    private fun exportSelectionAsPdf() {
        if (exportPdfBusy) { bridgeModule.toast("正在生成 PDF，请稍候"); return }
        if (!pageData.isAndroid) { bridgeModule.toast("当前平台暂不支持生成 PDF"); return }
        val selection = currentExportSelection() ?: return
        val (sessionId, title, ordered) = selection
        val html = DshReadableContent.selection(title, sessionId, ordered, DshExportFormat.HTML) {
            exportContentFor(sessionId, it)
        }
        exportPdfBusy = true
        val filename = "dsh-session-${currentTimeMillis()}.pdf"
        bridgeModule.htmlToPdf(html, filename) { ok, path, message ->
            exportPdfBusy = false
            if (!pageAlive) return@htmlToPdf
            if (!ok) {
                bridgeModule.toast(message.ifEmpty { "生成 PDF 失败，请重试" })
                return@htmlToPdf
            }
            cancelExportSelection()
            closeSessionDrawer()
            if (path.isEmpty()) {
                // Android 走系统打印（另存为 PDF），无本地路径可直接分享
                bridgeModule.toast(message.ifEmpty { "已打开系统打印，可选择「另存为 PDF」" })
            } else {
                bridgeModule.shareExportFile(path, "application/pdf") { shared, shareMessage ->
                    if (!shared) bridgeModule.toast(shareMessage.ifEmpty { "PDF 已生成，分享失败" })
                }
            }
        }
    }

    private fun exportReadableSession(sessionId: String) {
        if (sessionId.isBlank() || readableExportBusy) return
        val version = ++readableExportVersion
        val connection = activeConnectionId
        val title = sessions.firstOrNull { it.id == sessionId }?.title ?: sessionId
        readableExportSourceText = null
        readableExportExtension = "txt"
        readableExport = DshTextExportState(sessionId, connection, title, DshTextExportPhase.READING)
        closeSessionDrawer()
        readableExportDialogVisible = true
        val remote = remoteRepo
        if (remote == null || !remote.isProductReady()) { failReadableExport("请先连接 Host 后重试"); return }
        fun current() = pageAlive && version == readableExportVersion && repository === remote && activeConnectionId == connection
        remote.loadCompleteHistory(sessionId, { events ->
            if (!current()) return@loadCompleteHistory
            val raw = events.toString()
            writeReadableExport(version) { DshReadableContent.session(title, sessionId, JSONArray(raw)) }
        }, { error ->
            if (current()) failReadableExport(error)
        }, ::current)
        setTimeout(pagerId, 35_000) {
            if (current() && readableExport.phase == DshTextExportPhase.READING) {
                readableExportVersion++
                failReadableExport("读取完整会话超时，请重试")
            }
        }
    }

    /** 消息「分享」：进入统一的多选分享态，并预选当前消息。 */
    private fun shareMessageSelection(message: DshMessage) {
        closeMessageActions()
        if (readableExportBusy) { bridgeModule.toast("正在分享，请稍候"); return }
        beginExportSelection(activeSessionId, preselectId = message.id)
    }

    private fun retryReadableExport() {
        if (readableExportBusy) return
        val text = readableExportSourceText
        if (text == null) exportReadableSession(readableExport.sessionId)
        else writeReadableExport(++readableExportVersion) { text }
    }

    private fun writeReadableExport(version: Int, content: () -> String) {
        readableExport = readableExport.copy(phase = DshTextExportPhase.WRITING, path = "", error = "")
        val dir = exportDir
        val filename = "dsh-session-${currentTimeMillis()}-$version.$readableExportExtension"
        val work = DshLogWork(localReadScope) { cancelled -> publishReadableExport(dir, filename, content(), cancelled) }
        readableExportWork = work
        fun receive() {
            if (!pageAlive || version != readableExportVersion || readableExportWork !== work) return
            val result = work.take()
            if (result == null) { setTimeout(50) { receive() }; return }
            readableExportWork = null
            result.onSuccess {
                readableExport = readableExport.copy(phase = DshTextExportPhase.READY, path = it)
                shareReadableExport()
            }.onFailure { failReadableExport(it.message ?: "无法生成文件，请重试") }
        }
        setTimeout(50) { receive() }
    }

    private fun shareReadableExport() {
        if (readableExport.path.isEmpty() || readableExportBusy) return
        closeSessionDrawer()
        readableExportDialogVisible = true
        val version = readableExportVersion
        val path = readableExport.path
        readableExport = readableExport.copy(phase = DshTextExportPhase.SHARING, error = "")
        bridgeModule.shareExportFile(path) { ok, message ->
            if (!pageAlive || version != readableExportVersion || path != readableExport.path) return@shareExportFile
            if (ok) readableExport = readableExport.copy(phase = DshTextExportPhase.READY)
            else failReadableExport(message.ifEmpty { "无法打开系统分享，请重试" })
        }
        setTimeout(pagerId, 35_000) {
            if (pageAlive && version == readableExportVersion && readableExport.phase == DshTextExportPhase.SHARING) {
                readableExportVersion++
                failReadableExport("系统分享未响应，可重新分享已生成的文件")
            }
        }
    }

    private fun exportActiveSession() {
        val repository = remoteRepo ?: return
        val url = repository.sessionExportUrl(activeSessionId)
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            "link_view",
            JSONObject().apply {
                put("pageName", "link_view")
                put("url", url)
            },
        )
    }

    /** 打开工作区选择：仅远程模式且当前会话尚未开始（blank）时可改工作区。 */
    private fun openWorkspacePicker() {
        if (!isRemoteHost) return
        if (!isBlankSession()) {
            bridgeModule.toast("会话已开始，工作区目录不可修改")
            return
        }
        dismissKeyboard()
        closeSessionDrawer()
        workspacePickerGeneration++
        workspacePickerVisible = true
        workspacePickerScreen = DshWorkspacePickerScreen.RECENT
        workspacePickerBusy = false
        workspacePickerError = ""
        workspaceAddNewName = ""
        refreshWorkspaceGroups()
    }

    private fun closeWorkspacePicker() {
        workspacePickerGeneration++
        workspacePickerVisible = false
        workspacePickerBusy = false
        workspaceAddBusy = false
        workspacePickerError = ""
        workspacePickerScreen = DshWorkspacePickerScreen.RECENT
    }

    /** 返回键/左上返回：ADD 界面回 RECENT，RECENT 界面关闭弹窗。 */
    private fun onWorkspacePickerBack() {
        if (workspacePickerBusy || workspaceAddBusy) return
        if (workspacePickerScreen == DshWorkspacePickerScreen.ADD) {
            workspacePickerScreen = DshWorkspacePickerScreen.RECENT
            workspacePickerError = ""
        } else {
            closeWorkspacePicker()
        }
    }

    /**
     * 选中「最近文件夹」：为当前空白会话切换到该工作区并关闭弹窗。
     * 复用该工作区下已有的空白会话；没有则 `session.create` 新建后再选中。
     */
    private fun switchWorkspaceTo(workspaceId: String) {
        val remote = remoteRepo ?: return
        if (workspacePickerBusy) return
        if (!isBlankSession()) {
            bridgeModule.toast("会话已开始，工作区不可修改")
            return
        }
        if (activeWorkspaceId() == workspaceId) {
            closeWorkspacePicker()
            return
        }
        val generation = ++workspacePickerGeneration
        val connection = activeConnectionId
        val sourceSession = activeSessionId
        workspacePickerBusy = true
        workspacePickerError = ""
        fun current() = pageAlive && workspacePickerVisible && generation == workspacePickerGeneration &&
            remote === this@DshHomePage.repository && connection == activeConnectionId && sourceSession == activeSessionId
        fun fail(message: String) {
            if (!current()) return
            workspacePickerBusy = false
            workspacePickerError = message
        }
        fun openSession(sessionId: String) {
            remote.loadHistory(sessionId, { history ->
                if (!current()) return@loadHistory
                sessionMessageState(sessionId, loadFromDisk = false).diffUpdate(history) { old, new -> old == new }
                sessionMessageReady.add(sessionId)
                selectSession(sessionId)
                closeWorkspacePicker()
            }, { fail("无法读取目标会话：$it") })
        }
        val existing = remote.blankSessionInWorkspace(workspaceId)
        if (existing != null) {
            openSession(existing.id)
            return
        }
        remote.createSession(workspaceId, { sessionId ->
            if (!current()) return@createSession
            remote.loadSessionCatalog({ catalog ->
                if (!current()) return@loadSessionCatalog
                sessions = catalog.sessions
                reorderSessionsByUpdatedAt()
                refreshVisibleSessions()
                refreshWorkspaceGroups()
                preferBlankHomeOnNextLoad = false
                openSession(sessionId)
            }, { error -> fail("无法同步工作区：${error.message}") })
        }, { error -> fail("无法创建会话：$error") }, permission = permissionValue, agentPreset = agentModeValue)
        setTimeout(pagerId, 35_000) {
            if (current() && workspacePickerBusy) {
                fail("切换超时，请重试")
                workspacePickerGeneration++
            }
        }
    }

    private fun openWorkspaceAddFolder() {
        if (!isRemoteHost) return
        dismissKeyboard()
        workspacePickerScreen = DshWorkspacePickerScreen.ADD
        workspacePickerError = ""
        workspaceAddNewName = ""
        loadWorkspaceAddDirectory(null)
    }

    private fun loadWorkspaceAddDirectory(path: String?) {
        val remote = remoteRepo ?: return
        if (workspacePickerBusy) return
        val generation = ++workspacePickerGeneration
        workspaceAddBusy = true
        workspacePickerError = ""
        remote.listDirectory(path) { listing, error ->
            postToUi {
                if (!pageAlive || !workspacePickerVisible || workspacePickerScreen != DshWorkspacePickerScreen.ADD ||
                    generation != workspacePickerGeneration || remote !== this@DshHomePage.repository
                ) return@postToUi
                workspaceAddBusy = false
                if (error != null || listing == null) {
                    workspacePickerError = error?.message ?: "无法读取目录"
                    return@postToUi
                }
                workspaceAddPath = listing.path
                workspaceAddHome = listing.home
                workspaceAddEntries.clear()
                workspaceAddEntries.addAll(listing.entries.filterNot { it.hidden })
            }
        }
    }

    private fun createWorkspaceAddDirectory() {
        val remote = remoteRepo ?: return
        if (workspacePickerBusy || workspaceAddBusy) return
        val name = workspaceAddNewName.trim()
        if (workspaceAddPath.isEmpty() || name.isEmpty()) return
        val generation = workspacePickerGeneration
        workspaceAddBusy = true
        workspacePickerError = ""
        remote.createDirectory(workspaceAddPath, name) { createdPath, error ->
            postToUi {
                if (!pageAlive || !workspacePickerVisible || workspacePickerScreen != DshWorkspacePickerScreen.ADD ||
                    generation != workspacePickerGeneration || remote !== this@DshHomePage.repository
                ) return@postToUi
                workspaceAddBusy = false
                if (error != null || createdPath == null) {
                    workspacePickerError = error?.message ?: "无法创建目录"
                    return@postToUi
                }
                workspaceAddNewName = ""
                loadWorkspaceAddDirectory(createdPath)
            }
        }
    }

    /** 添加文件夹：注册 Host 目录为工作区，成功后直接切换过去并关闭弹窗。 */
    private fun adoptWorkspaceAddDirectory() {
        val remote = remoteRepo ?: return
        val path = workspaceAddPath
        if (path.isEmpty() || workspacePickerBusy || workspaceAddBusy) return
        workspaceAddBusy = true
        workspacePickerError = ""
        remote.createWorkspace(path) { value, error ->
            postToUi {
                if (!pageAlive || !workspacePickerVisible || remote !== this@DshHomePage.repository) return@postToUi
                if (error != null) {
                    workspaceAddBusy = false
                    workspacePickerError = error.message
                    return@postToUi
                }
                // 重新拉取 workspace.list 基线，确保新注册工作区已进入本地投影后再解析其 id。
                remote.loadSessionCatalog({ catalog ->
                    if (!pageAlive || !workspacePickerVisible || remote !== this@DshHomePage.repository) return@loadSessionCatalog
                    sessions = catalog.sessions
                    reorderSessionsByUpdatedAt()
                    refreshVisibleSessions()
                    refreshWorkspaceGroups()
                    val workspaceId = value?.optString("workspaceId")?.takeIf { it.isNotEmpty() }
                        ?: workspaceGroups.firstOrNull { it.path == path }?.workspaceId.orEmpty()
                    if (workspaceId.isEmpty()) {
                        workspaceAddBusy = false
                        workspacePickerError = "Host 尚未返回该目录对应的工作区"
                        return@loadSessionCatalog
                    }
                    workspaceAddBusy = false
                    workspacePickerScreen = DshWorkspacePickerScreen.RECENT
                    switchWorkspaceTo(workspaceId)
                }, { syncError ->
                    if (!pageAlive || !workspacePickerVisible) return@loadSessionCatalog
                    workspaceAddBusy = false
                    workspacePickerError = "无法同步工作区：${syncError.message}"
                })
            }
        }
    }

    private fun openWorkspaceRename(workspaceId: String, currentTitle: String) {
        dismissKeyboard()
        workspaceRenameTargetId = workspaceId
        workspaceRenameDraft = currentTitle
        workspaceActionError = ""
    }

    private fun saveWorkspaceRename() {
        val repository = remoteRepo ?: return
        val workspaceId = workspaceRenameTargetId
        val title = workspaceRenameDraft.trim()
        if (workspaceId.isEmpty() || title.isEmpty()) return
        workspaceActionBusy = true
        workspaceActionError = ""
        repository.renameWorkspace(workspaceId, title) { _, error ->
            postToUi {
                workspaceActionBusy = false
                if (error != null) {
                    workspaceActionError = error.message
                    return@postToUi
                }
                workspaceRenameTargetId = ""
                workspaceRenameDraft = ""
                refreshWorkspaceGroups()
            }
        }
    }

    private fun openWorkspaceDelete(workspaceId: String) {
        workspaceDeleteTargetId = workspaceId
        workspaceActionError = ""
    }

    private fun confirmWorkspaceDelete() {
        val repository = remoteRepo ?: return
        val workspaceId = workspaceDeleteTargetId
        if (workspaceId.isEmpty()) return
        workspaceActionBusy = true
        workspaceActionError = ""
        repository.deleteWorkspace(workspaceId) { _, error ->
            postToUi {
                workspaceActionBusy = false
                if (error != null) {
                    workspaceActionError = error.message
                    return@postToUi
                }
                workspaceDeleteTargetId = ""
                refreshWorkspaceGroups()
            }
        }
    }

    private fun moveWorkspace(workspaceId: String, delta: Int) {
        val repository = remoteRepo ?: return
        val ordered = workspaceGroups.filter { it.workspaceId.isNotEmpty() }
        val index = ordered.indexOfFirst { it.workspaceId == workspaceId }
        if (index < 0) return
        val targetIndex = index + delta
        if (targetIndex < 0 || targetIndex >= ordered.size) return
        val beforeWorkspaceId = if (targetIndex == ordered.lastIndex) {
            null
        } else {
            ordered[targetIndex].workspaceId
        }
        workspaceActionBusy = true
        workspaceActionError = ""
        repository.moveWorkspaceBefore(workspaceId, beforeWorkspaceId) { _, error ->
            postToUi {
                workspaceActionBusy = false
                if (error != null) {
                    workspaceActionError = error.message
                    return@postToUi
                }
                refreshWorkspaceGroups()
            }
        }
    }

    private fun restoreCachedSessions() {
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

    private fun loadApiKeyAsync() {
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

    private fun showCredentialSetupIfNeeded(apiKey: String) {
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

    private fun selectSession(id: String) {
        dismissKeyboard()
        if (id == activeSessionId) {
            return
        }
        if (!sessionMessageReady.contains(id)) {
            pendingSessionSelections.add(id)
            return
        }
        if (!conversationPanelIds.contains(id)) {
            ensureConversationPanel(id)
            addTaskWhenPagerUpdateLayoutFinish {
                if (activeSessionId != id) selectSession(id)
            }
            return
        }
        selectMountedSession(id)
    }

    private fun selectMountedSession(id: String) {
        if (id == activeSessionId) return
        // 离开多选态目标会话时先退出多选态，避免勾选状态挂到别的会话上
        if (exportSelectMode && id != exportSelectSessionId && id != pendingExportSelectionSessionId) {
            cancelExportSelection()
        }
        // 切换会话时兜底关闭长按菜单，覆盖所有切换路径（抽屉/会话栏/新建会话等）。
        closeMessageActions()
        closeSelectTextModal()
        refreshSessionRenderTree(id)
        cancelStreamingForSessionSwitch()
        sessionMessageStates[activeSessionId] = messages
        val nextMessages = sessionMessageState(id, loadFromDisk = false)
        ensureConversationPanel(id)
        messages = nextMessages
        activeSessionId = id
        DshStreamLog.log(LogLevel.INFO, "app.session.selected", "messages=${messages.size}", id)
        scrollMessagesToEnd()
        addTaskWhenPagerUpdateLayoutFinish {
            refreshSessionRenderTree(id)
            if (activeSessionId == id) scrollMessagesToEnd()
        }
        // Invalidate any in-flight request for the previous session before
        // starting the new one, so an old response cannot repaint this view.
        historyRequestGeneration++
        // 清理旧会话的附件加载状态，防止 pendingAttachmentReads 泄漏导致新会话附件被跳过
        pendingAttachmentReads.clear()
        loadMessagesFromDisk(id)
        fetchHostHistory(id)
        postToUi {
            if (activeSessionId == id) loadModels(id)
        }
        draft = ""
        inputView?.setText("")
        applyActiveSessionChrome()
        maybeEnterPendingExportSelection()
    }

    private fun isWebDisclosureExpanded(id: String): Boolean {
        webDisclosureRevision
        return webDisclosureStates[id] == true
    }

    private fun toggleWebDisclosure(id: String) {
        val next = webDisclosureStates[id] != true
        webDisclosureStates[id] = next
        if (!next) {
            webJsonNodeStates.keys.filter { it.startsWith("$id:") }.toList().forEach(webJsonNodeStates::remove)
        }
        webDisclosureRevision += 1
        refreshSessionRenderTree(activeSessionId)
    }

    private fun isWebJsonNodeExpanded(messageId: String, nodeId: String): Boolean {
        webDisclosureRevision
        return webJsonNodeStates["$messageId:$nodeId"] == true
    }

    private fun toggleWebJsonNode(messageId: String, nodeId: String) {
        val key = "$messageId:$nodeId"
        webJsonNodeStates[key] = webJsonNodeStates[key] != true
        webDisclosureRevision += 1
        refreshSessionRenderTree(activeSessionId)
    }

    private fun isBlankSession(sessionId: String = activeSessionId): Boolean =
        sessions.firstOrNull { it.id == sessionId }?.blank == true

    private fun conversationListEpochFor(sessionId: String): Int {
        conversationListEpoch
        return conversationListEpochs[sessionId] ?: 0
    }

    private fun remountConversationList(sessionId: String) {
        conversationListEpochs[sessionId] = (conversationListEpochs[sessionId] ?: 0) + 1
        conversationListEpoch += 1
    }

    private fun applyActiveSessionChrome() {
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

    private fun reconnectLabel(): String = dshReconnectLabel(connectionMode)

    private fun isTurnStatusActive(): Boolean =
        streaming || stopButtonVisible || sessionRunning

    private fun syncTurnStatusTicker() {
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

    private fun syncBusyLabel(): String = dshSyncBusyLabel(connectionMode)

    private fun refreshMountedSessionRenderTrees() {
        conversationPanelIds.toList().forEach { refreshSessionRenderTree(it) }
    }

    private fun refreshSessionRenderTree(sessionId: String) {
        val list = messageScrollerRefs[sessionId]?.view ?: return
        (list.contentView as? ListContentView)?.createRenderViewsOnVisibleRect()
    }

    private fun realizeSessionAfterData(
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

    private fun loadCachedHistory(sessionId: String) {
        messages = sessionMessageState(sessionId, loadFromDisk = false)
        ensureConversationPanel(sessionId)
        loadMessagesFromDisk(sessionId)
    }

    private fun sessionMessageState(
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
    private fun preloadAllSessionMessages() {
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

    private fun loadMessagesFromDisk(
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

    private fun completePendingSessionSelection(sessionId: String) {
        if (!pendingSessionSelections.remove(sessionId)) return
        postToUi {
            if (activeSessionId != sessionId) selectSession(sessionId)
        }
    }

    private fun warmRecentSessionCache(
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

    private fun ensureConversationPanel(sessionId: String) {
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

    private fun sendDraft() {
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

    private fun submitDraft(
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

    private fun stopStream() {
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

    private fun cancelStreamingForSessionSwitch() {
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

    private fun dismissKeyboard() {
        if (!inputFocused && keyboardHeight <= 0f) return
        inputFocused = false
        inputView?.blur()
        bridgeModule.closeKeyboard()
        keyboardHeight = 0f
    }

    private fun openMessageActions(
        message: DshMessage,
        content: String,
        x: Float,
        y: Float,
    ) {
        // 长按事件可能重复触发，菜单已打开时直接忽略，避免重复截图/模糊
        if (messageActionsMessage != null) return
        if (exportSelectMode) return
        bridgeModule.log("openMessageActions id=${message.id} role=${message.role} x=$x y=$y")
        dismissKeyboard()
        messageActionsX = x
        messageActionsY = y
        menuBlurUri = ""
        val sessionId = activeSessionId
        val remote = repository
        // 菜单是页面内覆盖层，必须先截图模糊再显示菜单，否则模糊图会包含菜单自身
        blurModule.captureBlur(24) { uri ->
            if (!pageAlive || activeSessionId != sessionId || repository !== remote) return@captureBlur
            menuBlurUri = uri
            messageActionsMessage = message
        }
    }

    private fun closeMessageActions() {
        messageActionsMessage = null
        menuBlurUri = ""
    }

    /** 复制回答正文；工具卡片使用独立复制入口，完整记录由分享承载。 */
    private fun copyMessageBody(message: DshMessage) {
        val text = DshReadableContent.copyText(sessionMessageState(activeSessionId), message.id) { m ->
            if (streaming && streamingAssistantId == m.id && streamingAssistantContent.isNotEmpty()) {
                streamingAssistantContent
            } else {
                m.content
            }
        }
        if (text.isEmpty()) {
            bridgeModule.toast("没有可复制的内容")
            return
        }
        bridgeModule.copyToPasteboard(text)
        bridgeModule.toast("已复制")
        copiedMessageId = message.id
        setTimeout(pagerId, 1500) {
            if (copiedMessageId == message.id) copiedMessageId = ""
        }
    }

    /** 复制长按菜单目标消息所在回合的完整正文 */
    private fun copyMessageActionsText() {
        val message = messageActionsMessage ?: return
        closeMessageActions()
        copyMessageBody(message)
    }

    /** 「选择文本」：打开弹窗，以单个可选中文本节点承载完整正文，供原生选区复制 */
    private fun selectMessageActionsText() {
        val message = messageActionsMessage ?: return
        closeMessageActions()
        if (streaming && streamingAssistantId == message.id) {
            bridgeModule.toast("内容生成中，请稍候")
            return
        }
        val text = DshReadableContent.copyText(sessionMessageState(activeSessionId), message.id) { m ->
            if (streaming && streamingAssistantId == m.id && streamingAssistantContent.isNotEmpty()) {
                streamingAssistantContent
            } else {
                m.content
            }
        }
        if (text.isEmpty()) {
            bridgeModule.toast("没有可复制的内容")
            return
        }
        selectTextModalContent = text
        selectTextModalVisible = true
    }

    private fun closeSelectTextModal() {
        selectTextModalVisible = false
        selectTextModalContent = ""
    }

    private fun onMessageFooterAction(message: DshMessage, action: DshMessageFooterAction) {
        when (action) {
            DshMessageFooterAction.COPY -> copyMessageBody(message)
            DshMessageFooterAction.BRANCH -> forkMessage(message)
            DshMessageFooterAction.SHARE -> shareMessageSelection(message)
            DshMessageFooterAction.GOOD,
            DshMessageFooterAction.BAD -> {
                // 反馈后续实现，本期先完成 UI
            }
        }
    }

    private fun messageActionsItems(): ObservableList<DshMessageActionItem> {
        val result = ObservableList<DshMessageActionItem>()
        result.addAll(listOf(
            DshMessageActionItem("复制", "copy.svg", { copyMessageActionsText() }),
            DshMessageActionItem("选择文本", "text-select.svg", { selectMessageActionsText() }),
            DshMessageActionItem("好的回答", "like.svg", { closeMessageActions() }),
            DshMessageActionItem("有问题的回答", "dislike.svg", { closeMessageActions() }),
            DshMessageActionItem("在新对话中分支", "branch.svg", { messageActionsMessage?.let(::forkMessage) }),
            DshMessageActionItem("分享", "share.svg", { messageActionsMessage?.let(::shareMessageSelection) }),
        ))
        return result
    }

    private fun updateKeyboard(params: KeyboardParams) {
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

    private fun effectiveKeyboardHeight(rawHeight: Float): Float {
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

    private fun loadModels(sessionId: String) {
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

    private fun openModelPicker() {
        if (sessions.isEmpty()) return
        dismissKeyboard()
        commandSheetVisible = false
        modelEffortsVisible = false
        modelPickerVisible = true
        modelPickerBusy = true
        modelPickerError = ""
        loadModels(activeSessionId)
    }

    private fun selectModel(option: DshModelOption) {
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
    private fun selectModelEffort(effortId: String) {
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
    private fun toggleVoice() {
        dismissKeyboard()
        commandSheetVisible = false
        // 切换模式时若正在录音，先取消，避免遗留会话
        if (voiceRecording) {
            voiceCancelRequested = true
            bridgeModule.cancelVoiceRecognition()
            finishVoiceRecord(cancelled = true)
        }
        voiceActive = !voiceActive
        if (!voiceActive) resetVoiceVisual()
    }

    /** 按住说话：按下，开始语音识别并显示录音浮层。 */
    private fun beginVoiceRecord(startPageY: Float) {
        if (voiceRecording) return
        voiceActive = true
        dismissKeyboard()
        voiceHoldStartY = startPageY
        voiceStopping = false
        voiceCancelRequested = false
        voiceCancelArmed = false
        voiceCommittedText = ""
        voicePartialText = ""
        voiceLastLevelAt = 0L
        voiceStopFallbackScheduled = false
        voiceWaveformModel.clear()
        voiceWaveformRevision += 1
        voiceRecording = true
        bridgeModule.startVoiceRecognition { raw -> handleVoiceEvent(raw) }
        voiceDecayGeneration += 1
        scheduleVoiceDecay(voiceDecayGeneration)
    }

    /** 按住说话：移动，上滑超过阈值进入取消态。 */
    private fun updateVoiceHold(currentPageY: Float) {
        if (!voiceRecording) return
        voiceCancelArmed = dshVoiceCancelArmed(voiceHoldStartY, currentPageY)
    }

    /** 按住说话：松手，取消或提交识别。 */
    private fun endVoiceRecord() {
        if (!voiceRecording || voiceStopping) return
        voiceStopping = true
        if (voiceCancelArmed) {
            voiceCancelRequested = true
            bridgeModule.cancelVoiceRecognition()
            finishVoiceRecord(cancelled = true)
            return
        }
        bridgeModule.stopVoiceRecognition()
        // 若最终结果已提前到达（系统自动结束），可直接提交；否则等待 final/end 或兜底超时
        if (voiceCommittedText.isNotEmpty()) {
            finishVoiceRecord(cancelled = false)
            return
        }
        if (!voiceStopFallbackScheduled) {
            voiceStopFallbackScheduled = true
            // 兜底定时器绑定本次录音代次：新一轮录音开始后旧定时器不再结束它。
            val generation = voiceDecayGeneration
            setTimeout(pagerId, DSH_VOICE_STOP_TIMEOUT_MS) {
                if (generation != voiceDecayGeneration) return@setTimeout
                voiceStopFallbackScheduled = false
                if (voiceRecording) finishVoiceRecord(cancelled = voiceCancelRequested)
            }
        }
    }

    /** 处理原生语音识别事件（ready/level/partial/final/error/end）。 */
    private fun handleVoiceEvent(raw: String) {
        if (raw.isEmpty() || !pageAlive) return
        val event = runCatching { JSONObject(raw) }.getOrNull() ?: return
        when (event.optString("event")) {
            "ready" -> Unit
            "level" -> appendVoiceLevel(event.optDouble("level").toFloat())
            "partial" -> {
                val text = event.optString("text")
                if (text.isNotEmpty()) voicePartialText = text
            }
            "final" -> {
                val text = event.optString("text")
                if (text.isNotEmpty()) {
                    voiceCommittedText = text
                    voicePartialText = text
                }
            }
            "error" -> {
                val message = dshVoiceErrorMessage(
                    code = event.optString("code"),
                    locale = settingsSnapshot.localeValue,
                    fallback = event.optString("message"),
                )
                val wasRecording = voiceRecording
                finishVoiceRecord(cancelled = true)
                if (wasRecording) bridgeModule.toast(message)
            }
            "end" -> {
                // 仅当已松手/已取消时才结束；录音中系统自动结束则保留结果，待松手提交
                if (voiceStopping || voiceCancelRequested) {
                    finishVoiceRecord(cancelled = voiceCancelRequested)
                }
            }
        }
    }

    /** 音量事件节流后写入滚动窗口，驱动浮层方块高度。 */
    private fun appendVoiceLevel(level: Float) {
        if (!voiceRecording) return
        val now = DateTime.currentTimestamp()
        if (now - voiceLastLevelAt < DSH_VOICE_LEVEL_INTERVAL_MS) return
        voiceLastLevelAt = now
        pushVoiceLevel(level)
    }

    /** 把新音量推入滚动窗口左移一格；每次自增 revision 触发波形重绘。 */
    private fun pushVoiceLevel(level: Float) {
        voiceWaveformModel.push(level)
        voiceWaveformRevision += 1
    }

    /**
     * 无新音量采样时让波形平滑衰减，避免原生 RMS 停止上报（静音/识别暂停）后
     * 波形看起来「卡住不动」。
     */
    private fun scheduleVoiceDecay(generation: Int) {
        if (!voiceRecording || generation != voiceDecayGeneration) return
        val now = DateTime.currentTimestamp()
        if (now - voiceLastLevelAt >= DSH_VOICE_DECAY_GAP_MS) {
            if (voiceWaveformModel.decay()) voiceWaveformRevision += 1
        }
        setTimeout(pagerId, DSH_VOICE_DECAY_TICK_MS) { scheduleVoiceDecay(generation) }
    }

    /** 原生输入框挂载完成：记录实例；若有语音转文字待回填则写入。 */
    private fun onInputViewCreated(view: TextAreaView?) {
        if (view == null) return
        inputView = view
        pendingVoiceDraft?.let { text ->
            pendingVoiceDraft = null
            view.setText(text)
        }
    }

    /** 结束录音：取消丢弃，或把最终文本写入输入框并切回文字模式。 */
    private fun finishVoiceRecord(cancelled: Boolean) {
        if (!voiceRecording) return
        voiceRecording = false
        // 递增代次，使仍在等待的旧兜底定时器/权限回调失效
        voiceDecayGeneration += 1
        // 会话结束，释放跨事件回调
        bridgeModule.releaseVoiceRecognition()
        val text = dshVoiceResolvedText(voiceCommittedText, voicePartialText)
        val shouldCommit = dshVoiceShouldCommit(cancelled, text)
        resetVoiceVisual()
        if (shouldCommit) {
            // 切回文字模式后 TextArea 会重新挂载，待其 ref 回调时回填原生文本
            voiceActive = false
            draft = text
            pendingVoiceDraft = text
        }
    }

    private fun resetVoiceVisual() {
        voicePartialText = ""
        voiceCommittedText = ""
        voiceCancelArmed = false
        voiceStopping = false
        voiceCancelRequested = false
        voiceWaveformModel.clear()
        voiceWaveformRevision += 1
    }

    /** 附件方块点击：拍照/相册走平台取图，文件走系统文档选择器 + host-plugin 落盘。 */
    private fun onAttachmentTile(tile: DshCommandSheetTile) {
        commandSheetVisible = false
        when (tile) {
            DshCommandSheetTile.CAMERA -> pickImageFrom("camera")
            DshCommandSheetTile.GALLERY -> pickImageFrom("album")
            DshCommandSheetTile.FILE -> pickFileFrom()
        }
    }

    /** 平台选文件 → 解析 → 大小/数量预检 → 加入输入区草稿；取消静默，失败 toast。 */
    private fun pickFileFrom() {
        val expectedSession = activeSessionId
        val expectedConnection = activeConnectionId
        bridgeModule.pickFile { raw ->
            if (!pageAlive || expectedSession != activeSessionId || expectedConnection != activeConnectionId) return@pickFile
            val result = runCatching {
                com.tencent.kuikly.core.nvi.serialization.json.JSONObject(raw)
            }.getOrNull()
            if (result == null || !result.optBoolean("ok")) {
                if (result?.optBoolean("cancelled") == true) return@pickFile
                val error = result?.optString("error").orEmpty().ifEmpty { "选择文件失败" }
                bridgeModule.toast(error)
                return@pickFile
            }
            val picked = try {
                parsePickedFiles(result)
            } catch (t: Throwable) {
                emptyList()
            }
            if (picked.isEmpty()) {
                bridgeModule.toast("文件解析失败")
                return@pickFile
            }
            DshAttachmentIntake.planFiles(pendingFiles.toList(), picked).forEach { intake ->
                when (intake) {
                    is DshFileIntake.Accepted -> pendingFiles.add(intake.file)
                    is DshFileIntake.Rejected -> bridgeModule.toast(intake.reason)
                }
            }
            attachmentEpoch += 1
        }
    }

    private fun removePendingFile(clientId: String) {
        pendingFiles.removeAll { it.clientId == clientId }
        attachmentEpoch += 1
    }

    private fun retryPendingFile(clientId: String) {
        val index = pendingFiles.indexOfFirst { it.clientId == clientId }
        if (index < 0) return
        val file = pendingFiles[index]
        if (file.state != DshFileDraftState.FAILED) return
        pendingFiles[index] = file.copy(
            state = DshFileDraftState.SELECTED,
            error = "",
            handle = "",
            path = "",
            sha256 = "",
        )
        attachmentEpoch += 1
    }

    /**
     * 逐个上传文件草稿；全部成功回调 true，任一步失败保留 FAILED 状态并回调 false。
     * 上传成功后回填 path/handle，供 prompt 组装。
     */
    private fun uploadPendingFiles(
        sessionId: String,
        files: List<DshPendingFile>,
        onDone: (Boolean) -> Unit,
    ) {
        val hostRepository = remoteRepo
        if (hostRepository == null) {
            onDone(false)
            return
        }
        files.forEach { file ->
            val idx = pendingFiles.indexOfFirst { it.clientId == file.clientId }
            if (idx >= 0) {
                pendingFiles[idx] = pendingFiles[idx].copy(state = DshFileDraftState.UPLOADING, error = "")
            }
        }
        attachmentEpoch += 1
        var index = 0
        fun uploadNext() {
            if (index >= files.size) {
                onDone(true)
                return
            }
            val file = files[index]
            hostRepository.uploadAttachment(sessionId, file.name, file.mediaType, file.dataBase64) { uploaded, error ->
                if (!pageAlive || activeSessionId != sessionId) return@uploadAttachment
                val idx = pendingFiles.indexOfFirst { it.clientId == file.clientId }
                if (error != null || uploaded == null) {
                    if (idx >= 0) {
                        pendingFiles[idx] = pendingFiles[idx].copy(
                            state = DshFileDraftState.FAILED,
                            error = error?.message ?: "上传失败",
                        )
                    }
                    attachmentEpoch += 1
                    bridgeModule.toast(error?.message ?: "附件上传失败")
                    onDone(false)
                    return@uploadAttachment
                }
                if (idx >= 0) {
                    pendingFiles[idx] = pendingFiles[idx].copy(
                        state = DshFileDraftState.SELECTED,
                        path = uploaded.path,
                        sha256 = uploaded.sha256,
                        handle = uploaded.handle,
                        error = "",
                    )
                }
                attachmentEpoch += 1
                index += 1
                uploadNext()
            }
        }
        uploadNext()
    }

    /** 平台取图 → 解析 → imageLimits 预检 → 加入输入区草稿；取消静默，失败 toast。 */
    private fun pickImageFrom(source: String) {
        val expectedSession = activeSessionId
        val expectedConnection = activeConnectionId
        bridgeModule.pickImage(source) { raw ->
            if (!pageAlive || expectedSession != activeSessionId || expectedConnection != activeConnectionId) return@pickImage
            val result = runCatching {
                com.tencent.kuikly.core.nvi.serialization.json.JSONObject(raw)
            }.getOrNull()
            if (result == null || !result.optBoolean("ok")) {
                if (result?.optBoolean("cancelled") == true) return@pickImage
                val error = result?.optString("error").orEmpty().ifEmpty { "取图失败" }
                DshStreamLog.log(LogLevel.ERROR, "app.image.failed", "source=$source error='${DshStreamLog.preview(error)}'", expectedSession)
                bridgeModule.toast(error)
                return@pickImage
            }
            val picked = try {
                parsePickedImages(result)
            } catch (t: Throwable) {
                DshStreamLog.log(LogLevel.ERROR, "app.image.failed", "phase=parse error='${DshStreamLog.preview(t.message.orEmpty())}'", expectedSession)
                emptyList()
            }
            if (picked.isEmpty()) {
                bridgeModule.toast("取图解析失败")
                return@pickImage
            }
            val limits = imageLimits ?: DshImageLimits.DEFAULT
            val intakes = try {
                DshAttachmentIntake.planImages(pendingImages.toList(), picked, limits)
            } catch (t: Throwable) {
                DshStreamLog.log(LogLevel.ERROR, "app.image.failed", "phase=validate error='${DshStreamLog.preview(t.message.orEmpty())}'", expectedSession)
                picked.map { DshImageIntake.Accepted(it) }
            }
            intakes.forEach { intake ->
                when (intake) {
                    is DshImageIntake.Accepted -> {
                        val pending = intake.image
                        pendingImages.add(pending)
                        DshStreamLog.log(
                            LogLevel.INFO, "app.image.selected",
                            "source=$source mediaType=${pending.mediaType} width=${pending.width} height=${pending.height} bytes=${pending.bytes}",
                            expectedSession,
                        )
                    }
                    is DshImageIntake.Rejected -> {
                        val pending = intake.image
                        pendingImages.add(pending)
                        bridgeModule.toast(intake.reason)
                        DshStreamLog.log(
                            LogLevel.WARN, "app.image.rejected",
                            "source=$source mediaType=${pending.mediaType} width=${pending.width} height=${pending.height} bytes=${pending.bytes} reason=${intake.reason}",
                            expectedSession,
                        )
                    }
                }
            }
            attachmentEpoch += 1
        }
    }

    /** 保存预览图片到系统相册，成功/失败 toast 反馈。 */
    private fun saveImageToGallery(dataUrl: String) {
        bridgeModule.saveImage(dataUrl) { raw ->
            val result = runCatching {
                com.tencent.kuikly.core.nvi.serialization.json.JSONObject(raw)
            }.getOrNull()
            if (result?.optBoolean("cancelled") == true) return@saveImage
            if (result != null && result.optBoolean("ok")) {
                bridgeModule.toast("已保存到相册")
            } else {
                val error = result?.optString("error").orEmpty().ifEmpty { "保存失败" }
                bridgeModule.toast(error)
            }
        }
    }

    private fun removePendingImage(clientId: String) {
        pendingImages.removeAll { it.clientId == clientId }
        attachmentEpoch += 1
    }

    private fun retryPendingImage(clientId: String) {
        val index = pendingImages.indexOfFirst { it.clientId == clientId }
        if (index < 0) return
        val image = pendingImages[index]
        if (image.state == DshImageDraftState.INVALID) {
            bridgeModule.toast(image.error.ifEmpty { "图片不符合发送要求，请重新选择" })
            return
        }
        if (image.state != DshImageDraftState.FAILED) return
        pendingImages[index] = image.copy(state = DshImageDraftState.SELECTED, error = "")
        attachmentEpoch += 1
    }

    /** 切换「+」命令半屏面板；打开时收起键盘 */
    private fun toggleCommandSheet() {
        dismissKeyboard()
        commandSheetVisible = !commandSheetVisible
    }

    /** 点击命令：把 "/命令 " 写入输入框（补全草稿 + 原生输入框文本），并关闭面板 */
    private fun insertCommand(command: DshCommand) {
        insertDraftText("/${command.name} ")
        commandSheetVisible = false
    }

    /** 把文本写入 draft 状态，并同步到原生输入框（draft observable 不会自动回流到 TextArea）。 */
    private fun insertDraftText(text: String) {
        draft = text
        inputView?.setText(text)
    }

    private fun queueAssistantDelta(id: String, delta: String) {
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

    private fun queueReasoningDelta(id: String, delta: String) {
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

    private fun flushAssistantDelta() {
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
    private fun ensureStreamingAssistantSegment() {
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

    private fun insertLiveAssistantRow() {
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
    private fun ensureLiveMessageCell() {
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
    private fun splitStreamingAssistantBeforeTool() {
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

    private fun updateStreamingMessage(content: String, streaming: Boolean, isReasoning: Boolean = false) {
        val index = messages.indexOfFirst { it.id == streamingAssistantId }
        if (index < 0) return
        messages[index] = messages[index].copy(
            content = content,
            streaming = streaming,
            isReasoning = isReasoning,
        )
        if (index >= messages.size - 1) realizeVisibleMessages()
    }

    private fun finalizeStreamingReasoning() {
        if (streamingReasoningId.isEmpty()) return
        val index = messages.indexOfFirst { it.id == streamingReasoningId }
        if (index >= 0) {
            messages[index] = messages[index].copy(streaming = false, isReasoning = true)
        }
    }

    private fun scrollMessagesToEnd() {
        if (!followListTail) return
        val generation = ++scrollSettleGeneration
        ensureLiveMessageCell()
        realizeVisibleMessages()
        addTaskWhenPagerUpdateLayoutFinish {
            settleScrollToEnd(generation, 0)
        }
    }

    private fun scrollMessagesToMessage(messageId: String) {
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
    private fun settleScrollToEnd(generation: Int, attempt: Int) {
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

    private fun realizeVisibleMessages() {
        val scroller = messageScrollerRefs[activeSessionId]?.view ?: return
        val content = scroller.contentView as? ListContentView ?: return
        content.flexNode.markDirty()
        content.createRenderViewsOnVisibleRect()
    }

    private fun onConversationUserScroll(params: ScrollParams) {
        val maxOffset = (params.contentHeight - params.viewHeight).coerceAtLeast(0f)
        val nearBottom = params.offsetY >= maxOffset - FOLLOW_LIST_SLACK_PX
        if (nearBottom) {
            followListTail = true
            return
        }
        if (params.isDragging) cancelFollowListTail()
    }

    private fun cancelFollowListTail() {
        followListTail = false
        scrollSettleGeneration += 1
    }

    private fun pinFollowListTail() {
        followListTail = true
    }

    private fun scrollMessagesToEndAfterLayout() {
        if (!followListTail) return
        val scroller = messageScrollerRefs[activeSessionId]?.view ?: return
        val contentHeight = scroller.contentView?.flexNode?.layoutFrame?.height ?: return
        val viewportHeight = scroller.flexNode?.layoutFrame?.height ?: return
        scroller.setContentOffset(0f, (contentHeight - viewportHeight).coerceAtLeast(0f), animated = false)
    }

    private fun settleScrollToMessage(messageId: String, generation: Int, attempt: Int) {
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

    private fun settleStreamingMessage(role: DshMessageRole, content: String) {
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

    private fun releaseStreamingUi() {
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

    private fun persistMessages(sessionId: String) {
        val snapshot = messages.toList()
        sessionMessageStates[sessionId] = messages
        runCatching { localStore?.replaceMessages(activeConnectionId, sessionId, snapshot) }
    }

    private fun replaceMessagesIfChanged(next: List<DshMessage>, force: Boolean = false) {
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

    private fun applyMessagesInPlace(next: List<DshMessage>) {
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
        private const val NO_ACTIVE_WORKSPACE = "__none__"
        private const val BG = 0xFFF7F9FA
        private const val LOCAL_ENGINE_URL = "http://127.0.0.1:3080"
        private const val ENGINE_CONNECT_RETRIES = 60
        private const val ENGINE_RETRY_DELAY_MS = 1_000
        private const val ANIMATION_DURATION_MS = 240
        private const val ANIMATION_DURATION_S = 0.24f
        private const val STREAM_FLUSH_INTERVAL_MS = 16
        private const val CONNECTION_CAPSULE_HOLD_MS = 1_500
        private const val CONNECTION_CAPSULE_FADE_MS = 300
    }
}
