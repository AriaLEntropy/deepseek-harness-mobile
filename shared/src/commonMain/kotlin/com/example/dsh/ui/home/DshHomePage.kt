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
import kotlinx.coroutines.launch
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
import com.example.dsh.search.DshSessionSearchHit

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

    /** Page UI state lives in a dedicated state holder; the page only orchestrates and renders. */
    internal val ui = DshHomeState(this)
    internal var repository: DshRepository? = null
    /** 仅远程模式可用的能力面；本地模式为 null。 */
    internal val remoteRepo: DshRemoteRepository? get() = repository as? DshRemoteRepository

    /** 下一帧执行：统一 Kuikly 的 setTimeout(pagerId, 0) 写法，明确“稍后执行”意图。
     *  必须从 Kuikly context / 主线程调用（Kotlin→native 桥有线程断言）；
     *  后台协程（localReadScope 等）调用时经 mainScope 派发到主队列，避免线程断言崩溃。 */
    internal fun postToUi(block: () -> Unit) {
        mainScope.launch { setTimeout(pagerId, 0) { block() } }
    }
    internal var localStore: DshLocalStore? = null
    internal var logJumpNotifyRef: CallbackRef? = null

    internal var pageAlive = true
    internal var exportDir = ""
    internal var crashImportWork: DshLogWork<Boolean>? = null
    internal var readableExportWork: DshLogWork<String>? = null
    internal var readableExportVersion = 0
    internal val readableExportBusy: Boolean get() = ui.readableExport.busy
    internal var readableExportSourceText: String? = null
    internal var readableExportExtension = "txt"
    internal var exportSelectSessionId = ""
    internal var pendingExportSelectionSessionId = ""
    internal var pendingExportSelectionPreselect = ""
    internal var timelineReadVersion = 0
    /** 搜索框原生文本：非响应式，只喂给 Input 的 text()，避免每次按键重设文本与原生输入互相覆盖。 */
    internal var pluginSearchInput = ""
    internal var pluginSearchInputView: InputView? = null
    internal var pluginInventory = emptyList<DshPluginEntry>()
    internal var pluginRequestVersion = 0
    internal var engineModule: DshEngineModule? = null
    internal var engineReady = false
    internal var relayEngineEndpoint = ""
    internal var pendingApiKey = ""
    internal val sshMode: Boolean
        get() = ui.connectionMode == DshConnectionMode.SSH
    internal val isRemoteHost: Boolean
        get() = ui.connectionMode == DshConnectionMode.RELAY || ui.connectionMode == DshConnectionMode.SSH
    internal val sessionScope: DshSessionScope
        get() = DshSessionScope(ui.connectionMode, ui.remoteProfileId)
    internal val activeConnectionId: String
        get() = sessionScope.storageKey

    internal var preferBlankHomeOnNextLoad = true
    internal var connectionLabel: String
        get() = ui._connectionLabel
        set(value) {
            if (ui._connectionLabel != value) {
                ui._connectionLabel = value
                onConnectionLabelChanged(value)
            }
        }
    internal var connectionCapsuleVersion = 0
    internal var modelRequestVersion = 0
    internal var pendingDangerPermission: DshPermissionOption? = null
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
    internal val sessionMessageReady = mutableSetOf<String>()
    internal val pendingSessionSelections = mutableSetOf<String>()
    internal val localReadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    internal val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    internal val pendingLocalMessageReads = mutableSetOf<String>()
    internal val sessionCacheStates = mutableMapOf<String, DshSessionCacheState>()
    internal var inputFocused = false
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
    internal val cachedAttachmentDataUrls = mutableMapOf<String, String>()
    internal val pendingAttachmentReads = mutableSetOf<String>()
    internal var turnStatusMark: TimeMark? = null
    internal var turnStatusTickerGeneration = 0
    internal var turnStatusClockBucket = -1L
    internal var workspaceAddInputView: InputView? = null
    internal var workspacePickerGeneration = 0

    internal val overlayBackCallback = object : BackPressCallback() {
        override fun handleOnBackPressed() {
            when {

                ui.expandedPayload != null -> closeExpandedModal()
                ui.personalizationPageVisible -> closePersonalizationPage()
                ui.exportSelectMode -> cancelExportSelection()
                ui.settingsChoiceKind.isNotEmpty() -> { if (!ui.settingsChoiceBusy) ui.settingsChoiceKind = "" }
                ui.pluginInventoryVisible -> handlePluginInventoryBack()
                ui.readableExportDialogVisible -> closeReadableExportDialog()
                ui.selectTextModalVisible -> closeSelectTextModal()
                ui.sessionDeleteVisible -> { if (!ui.sessionDeleteBusy) ui.sessionDeleteVisible = false }
                ui.sessionArchiveVisible -> { if (!ui.sessionArchiveBusy) ui.sessionArchiveVisible = false }
                ui.sessionRenameVisible -> cancelSessionRename()
                ui.archiveListVisible -> closeArchiveList()
                ui.agentModePickerVisible -> ui.agentModePickerVisible = false
                ui.riskConfirmVisible -> ui.riskConfirmVisible = false
                ui.permissionPickerVisible -> ui.permissionPickerVisible = false
                ui.modelPickerVisible -> { if (ui.modelEffortsVisible) ui.modelEffortsVisible = false else ui.modelPickerVisible = false }
                ui.commandSheetVisible -> ui.commandSheetVisible = false
                ui.overflowMenuVisible -> closeOverflowMenu()
                ui.workspacePickerVisible -> onWorkspacePickerBack()
                ui.credentialSetupVisible -> closeCredentialSettings()
                ui.sshSettingsVisible -> updateSshSettingsVisibility(false)
                ui.modelsDeleteTarget != null -> { if (!ui.modelsDeleting) ui.modelsDeleteTarget = null }
                ui.modelsPageVisible -> closeModelsPage()
                ui.settingsPageVisible -> closeSettingsPage()

                ui.sessionSearchVisible -> closeSessionSearch()
                ui.sessionDrawerVisible -> closeSessionDrawer()
                else -> acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
            }
        }
    }

    internal var archiveRequestGeneration = 0L
    /** 搜索框原生文本：非响应式，只喂给 Input 的 text()，避免每次按键重设文本与原生输入互相覆盖。 */
    internal var sessionSearchInput = ""
    internal var sessionSearchInputView: InputView? = null
    /** 正在按需拉取历史的会话 id 集合：去重，避免同一次搜索重复请求。 */
    internal val sessionSearchHistoryLoading = mutableSetOf<String>()
    internal var drawerOrderScope = ""
    internal var dragOriginPageY = 0f
    internal var prefsModule: SharedPreferencesModule? = null
    internal var catalogRequestGeneration = 0L
    internal var messageForkBusy = false
    internal var messageForkVersion = 0
    /** 附件上传阶段的发送防重入：上传完成后用首次点击的快照继续发送。 */
    internal var sendInFlight = false
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
            ui.chatProcessMode = dshProcessDisplayFromValue(prefs.getItem(DSH_PREF_PROCESS_DISPLAY).orEmpty())
            ui.chatExpandInModal = prefs.getItem(DSH_PREF_EXPAND_MODAL) == "1"
            ui.chatShowConnectors = prefs.getItem(DSH_PREF_SHOW_CONNECTORS) != "0"
            ui.chatShowResultCards = prefs.getItem(DSH_PREF_SHOW_RESULT_CARDS) != "0"
        }
        ui.connectionMode = when (pageData.params.optString("connectionMode")) {
            "relay" -> DshConnectionMode.RELAY
            "ssh", "remote" -> DshConnectionMode.SSH
            else -> DshConnectionMode.RELAY
        }
        ui.remoteProfileId = pageData.params.optString("profileId").ifEmpty { DshSessionScope.DEFAULT_REMOTE_PROFILE_ID }
        loadSshConfig()
        restoreCachedSessions()
        if (ui.sessions.isEmpty()) {
            sessionMessageStates[ui.activeSessionId] = ui.messages
            ensureConversationPanel(ui.activeSessionId)
        }
        ensureConversationPanel(ui.activeSessionId)
        preloadAllSessionMessages()
        loadApiKeyAsync()
        setTimeout(pagerId, SESSION_CACHE_WARM_START_DELAY_MS) {
            warmRecentSessionCache(scrollToEndAfterLoad = false)
        }
        postToUi { startConnection() }
        getBackPressHandler().addCallback(overlayBackCallback)
        DshStreamLog.i("app.page.created page=home mode=$ui.connectionMode")
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
        if (ui.voiceUi.recording) {
            bridgeModule.cancelVoiceRecognition()
            ui.voiceUi = ui.voiceUi.copy(recording = false)
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
