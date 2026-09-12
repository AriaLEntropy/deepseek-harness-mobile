package com.example.dsh.home

import com.example.dsh.base.*
import com.example.dsh.chat.*
import com.example.dsh.connection.*
import com.example.dsh.conversation.*
import com.example.dsh.diagnostics.DshCrashMarker
import com.example.dsh.diagnostics.DshLogWork
import com.example.dsh.diagnostics.DshLogPageContract
import com.example.dsh.home.*
import com.example.dsh.infrastructure.*
import com.example.dsh.rendering.*
import com.example.dsh.storage.*
import com.example.dsh.theme.*
import com.example.dsh.web.*
import com.example.dsh.base.BasePager
import com.example.dsh.base.bridgeModule
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
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.KeyboardParams
import com.tencent.kuikly.core.views.ListContentView
import com.tencent.kuikly.core.views.ScrollParams
import com.tencent.kuikly.core.views.ScrollerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private const val SESSION_CACHE_WARM_LIMIT = 7
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
    private var sessionDrawerMaskAnimated by observable(false)
    private var sessionDrawerMaskAnimation by observable(Animation.linear(0f))
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
    private var commandSheetVisible by observable(false)
    private var voiceActive by observable(false)
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
    // ===== 会话 topbar overflow menu 与会话管理动作 =====
    private var overflowMenuVisible by observable(false)

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
    private var sessionSort by observable(DshSessionSort.UPDATED)
    private var sessionSortMenuOpen by observable(false)
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
        setTimeout(pagerId, 0) { startConnection() }
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
        val wide = pagerData.pageViewWidth >= 720f
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
                            onOpenOverflow = { ctx.openOverflowMenu() },
                            colors = { this@DshHomePage.themeColors },
                        )
                    }
                }

                // ===== 主内容容器 =====
                // 撑满剩余空间，容纳下方的会话栏/对话区/详情面板与抽屉遮罩。
                // 会话抽屉打开时整体右移（translate），露出右侧变暗的边缘，带位移动画。
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
                                    ctx.messageRowRefs[ctx.messageRowKey(sessionId, messageId)] = ref
                                },
                                draft = { ctx.draft },
                                skills = { ctx.skills },
                                onPickSkill = { ctx.insertDraftText("/$it ") },
                                keyboardHeight = { ctx.keyboardHeight },
                                stopButtonVisible = { ctx.stopButtonVisible },
                                inputRef = { ctx.inputView = it.view },
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
                                onToggleVoice = { ctx.toggleVoice() },
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
                                ctx.messageRowRefs[ctx.messageRowKey(sessionId, messageId)] = ref
                            },
                            draft = { ctx.draft },
                            skills = { ctx.skills },
                            onPickSkill = { ctx.insertDraftText("/$it ") },
                            keyboardHeight = { ctx.keyboardHeight },
                            stopButtonVisible = { ctx.stopButtonVisible },
                            inputRef = { ctx.inputView = it.view },
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
                            onToggleVoice = { ctx.toggleVoice() },
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
                        )
                    }

                    // -- 会话抽屉「遮罩层」--：全屏半透明黑盖在主内容上，点击关闭抽屉。
                    vif({ ctx.sessionDrawerVisible }) {
                        View {
                            attr {
                                absolutePositionAllZero()
                                backgroundColor(Color(0x55000000))
                                opacity(if (ctx.sessionDrawerMaskAnimated) 1f else 0f)
                                animation(ctx.sessionDrawerMaskAnimation, ctx.sessionDrawerMaskAnimated)
                            }
                            event { click { ctx.closeSessionDrawer() } }
                        }
                    }
                }

                // ===== 会话抽屉 =====
                // 从左侧滑出的侧栏（覆盖在主内容之上）：会话列表、工作区分组、
                // 新建会话、连接设置入口；点击遮罩或选择会话后关闭。
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
                        onOpenOverflowFor = { ctx.openOverflowMenuFor(it) },
                        sessionSort = { ctx.sessionSort },
                        sortMenuOpen = { ctx.sessionSortMenuOpen },
                        onToggleSortMenu = { ctx.toggleSessionSortMenu() },
                        onPickSessionSort = { ctx.pickSessionSort(it) },
                        statusBarHeight = ctx.pagerData.statusBarHeight,
                        pageViewWidth = ctx.pagerData.pageViewWidth,
                        onClose = { ctx.closeSessionDrawer() },
                        onOpenSettings = { ctx.openSettingsPage() },
                        onOpenArchive = { ctx.openArchiveList() },
                        onNewSession = { ctx.createSession() },
                        onSelect = { id ->
                            ctx.closeSessionDrawer()
                            setTimeout(ctx.pagerId, 0) {
                                ctx.selectSession(id)
                            }
                        },
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
                        onRefresh = { ctx.refreshArchiveList() },
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
                            if (ctx.settingsPageVisible) {
                                ctx.applyDefaultModel(option)
                            } else {
                                ctx.selectModel(option)
                            }
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
                        onPickDefaultModel = { ctx.openDefaultModelPicker() },
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
                    DshPluginInventoryView(
                        rows = { ctx.pluginRows }, total = { ctx.pluginTotal }, loading = { ctx.pluginInventoryLoading },
                        error = { ctx.pluginInventoryError }, keyword = { ctx.pluginKeyword },
                        expandedId = { ctx.pluginExpandedId }, busyId = { ctx.pluginBusyId },
                        actionError = { ctx.pluginActionError }, actionNotice = { ctx.pluginNotice },
                        confirmEntry = { ctx.pluginActionTarget }, confirmAction = { ctx.pluginConfirmAction },
                        onKeyword = { ctx.pluginKeyword = it; ctx.applyPluginFilters() },
                        onRefresh = { ctx.refreshPluginInventory() }, onClose = { ctx.closePluginInventory() },
                        onToggleExpand = { ctx.togglePluginExpanded(it) },
                        onToggleEnabled = { entry, enable -> ctx.requestPluginToggle(entry, enable) },
                        onReload = { ctx.requestPluginReload(it) },
                        onConfirm = { ctx.confirmPluginAction() },
                        onCancelConfirm = { ctx.cancelPluginAction() },
                        colors = { ctx.themeColors },
                    )
                }

                // ===== 设置页「模型」详情页（对齐电脑端 settings.models） =====
                vif({ ctx.modelsPageVisible }) {
                    DshModelsPage(
                        loading = { ctx.modelsLoading },
                        error = { ctx.modelsError },
                        writable = { ctx.modelsWritable },
                        providers = { ctx.modelsProviders },
                        editingProvider = { ctx.modelsEditingProvider },
                        draftBaseUrl = { ctx.modelsDraftBaseUrl },
                        draftApiKey = { ctx.modelsDraftApiKey },
                        draftModels = { ctx.modelsDraftModels },
                        saving = { ctx.modelsSaving },
                        saveError = { ctx.modelsSaveError },
                        deleteTarget = { ctx.modelsDeleteTarget },
                        deleting = { ctx.modelsDeleting },
                        onClose = { ctx.closeModelsPage() },
                        onRetry = { ctx.reloadModelsSettings() },
                        onEdit = { ctx.openProviderEditor(it) },
                        onBaseUrlChange = { ctx.modelsDraftBaseUrl = it; ctx.modelsSaveError = "" },
                        onApiKeyChange = { ctx.modelsDraftApiKey = it; ctx.modelsSaveError = "" },
                        onModelChange = { index, field, value ->
                            ctx.updateDraftModel(index, field, value)
                            ctx.modelsSaveError = ""
                        },
                        onAddModel = { ctx.addDraftModel() },
                        onRemoveModel = { ctx.removeDraftModel(it) },
                        onApply = { ctx.applyProviderEditor(it) },
                        onRequestDelete = { ctx.requestRemoveProvider(it) },
                        onConfirmDelete = { ctx.confirmRemoveProvider() },
                        onCancelDelete = { ctx.modelsDeleteTarget = null },
                        colors = { this@DshHomePage.themeColors },
                    )
                }

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
                // ===== 会话 topbar overflow menu 与会话管理动作 =====
                DshOverflowMenu(
                    visible = { ctx.overflowMenuVisible },
                    actions = { ctx.overflowActions() },
                    onSelect = { ctx.onOverflowAction(it) },
                    onDismiss = { ctx.closeOverflowMenu() },
                    statusBarHeight = ctx.pagerData.statusBarHeight,
                    pageViewWidth = ctx.pagerData.pageViewWidth,
                    colors = { this@DshHomePage.themeColors },
                )
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
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        addTaskWhenPagerUpdateLayoutFinish {
            refreshMountedSessionRenderTrees()
        }
    }

    private fun openSessionDrawer() {
        if (sessionDrawerVisible) return
        if (isConnectionReadyLabel(connectionLabel)) {
            connectionCapsuleVisible = false
            connectionCapsuleFadeOut = false
        }
        // 抽屉是独立 Modal 窗口，菜单的透明捕获层够不着它；打开抽屉前先关闭长按菜单，
        // 否则切换会话后菜单仍会残留。
        closeMessageActions()
        closeSelectTextModal()
        ensureSessionCreatedAt()
        // Mount transparent first, then start drawer and mask on the same frame.
        sessionDrawerMaskAnimation = Animation.easeInOut(0.24f)
        sessionDrawerMaskAnimated = false
        sessionDrawerAnimated = false
        sessionDrawerVisible = true
        setTimeout(pagerId, 16) {
            sessionDrawerAnimated = true
            sessionDrawerMaskAnimated = true
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
        // Reverse the opening transition: fade the mask out while the drawer closes.
        sessionDrawerMaskAnimation = Animation.easeInOut(ANIMATION_DURATION_S)
        sessionDrawerMaskAnimated = false
        sessionDrawerAnimated = false
        setTimeout(pagerId, ANIMATION_DURATION_MS) {
            sessionDrawerVisible = false
        }
    }

    private fun closeSessionDrawerImmediately() {
        if (!sessionDrawerVisible) return
        overflowTargetSessionId = ""
        sessionDrawerMaskAnimation = Animation.linear(0f)
        sessionDrawerMaskAnimated = false
        sessionDrawerAnimated = false
        sessionDrawerVisible = false
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
        syncVisibleSessions(sessions, visibleSessions, (repository as? DshRemoteRepository)?.store?.archivedSessionIds.orEmpty())
        refreshPendingSessionIds()
    }

    private fun refreshPendingSessionIds() {
        val remote = repository as? DshRemoteRepository
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
                    (repository as? DshRemoteRepository)?.stop()
                    repository = null
                    connectionLabel = "扫码连接重试中"
                    syncTurnStatusTicker()
                }
                DshRelayPhase.STOPPED -> {
                    engineReady = false
                    relayEngineEndpoint = ""
                    (repository as? DshRemoteRepository)?.stop()
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
        (repository as? DshRemoteRepository)?.stop()
        repository = DshRemoteRepository(
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
                setTimeout(pagerId, 0) {
                    apiKeyDraft = ""
                    apiKeyInputView?.setText("")
                    credentialSetupBusy = false
                    updateCredentialSetupVisibility(false)
                    dismissKeyboard()
                    connectionLabel = "远程 DSH 已更新"
                    loadRepository()
                }
            }, { error ->
                setTimeout(pagerId, 0) {
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
    }

    private fun applyChatExpandInModal(enabled: Boolean) {
        chatExpandInModal = enabled
        runCatching {
            acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
                .setString(DSH_PREF_EXPAND_MODAL, if (enabled) "1" else "0")
        }
    }

    private fun applyChatShowConnectors(enabled: Boolean) {
        chatShowConnectors = enabled
        runCatching {
            acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
                .setString(DSH_PREF_SHOW_CONNECTORS, if (enabled) "1" else "0")
        }
    }

    private fun applyChatShowResultCards(enabled: Boolean) {
        chatShowResultCards = enabled
        runCatching {
            acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME)
                .setString(DSH_PREF_SHOW_RESULT_CARDS, if (enabled) "1" else "0")
        }
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
        modelsPageVisible = true
        reloadModelsSettings()
    }

    private fun closeModelsPage() {
        modelsPageVisible = false
        modelsEditingProvider = ""
        modelsDraftApiKey = ""
        modelsSaveError = ""
        modelsDeleteTarget = null
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
            modelsError = ""
            modelsLoading = false
        }, {
            modelsLoading = false
            if (showLoading) modelsError = it else bridgeModule.toast("模型设置刷新失败：$it")
        })
    }

    private fun openProviderEditor(provider: DshProviderConfig) {
        if (modelsEditingProvider == provider.provider) {
            modelsEditingProvider = ""
            return
        }
        modelsEditingProvider = provider.provider
        modelsDraftBaseUrl = provider.baseUrl
        modelsDraftApiKey = ""
        modelsSaveError = ""
        modelsDraftModels.clear()
        // 保留 raw，保存时才能带出未在编辑器展示的字段（如容量）。
        modelsDraftModels.addAll(provider.models.map { it.copy() })
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
        if (modelsDraftModels.any { it.id.trim().isEmpty() }) {
            modelsSaveError = "模型 ID 不能为空。"
            return
        }
        val ids = modelsDraftModels.map { it.id.trim() }
        if (ids.size != ids.toSet().size) {
            modelsSaveError = "模型 ID 不能重复。"
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
                modelsDraftApiKey = ""
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
            if (modelsEditingProvider == provider.provider) modelsEditingProvider = ""
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

    private fun connectionModeLabel(): String = when (connectionMode) {
        DshConnectionMode.LOCAL -> "本地模式"
        DshConnectionMode.RELAY -> "扫码连接"
        DshConnectionMode.SSH -> "SSH 连接"
    }

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
                settingsChoiceOptions.add(DshSettingsChoice("light", "浅色"))
                settingsChoiceOptions.add(DshSettingsChoice("dark", "深色"))
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

    private fun openDefaultModelPicker() {
        val sessionId = activeSessionId
        if (sessionId.isEmpty()) return
        val repo = repository ?: return
        repo.loadModels(sessionId, {
            if (!pageAlive || repository !== repo || activeSessionId != sessionId) return@loadModels
            modelOptions = ObservableList(it.options.toMutableList())
            modelPickerError = ""
            modelEffortsVisible = false
            modelPickerVisible = true
        }, {
            modelPickerError = it
        })
    }

    private fun applyDefaultModel(option: DshModelOption) {
        val repo = repository ?: return
        val patch = JSONObject().apply {
            put("provider", option.provider)
            put("model", option.model)
            option.reasoningEffort?.let { put("reasoningEffort", it) }
        }
        repo.updateSetting("agent-default-model", patch, settingsSnapshot.defaultModelRevision, {
            modelPickerVisible = false
            reloadSettings(showLoading = false)
        }, {
            modelPickerVisible = false
            bridgeModule.toast("默认模型设置失败：$it")
        })
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
                setTimeout(pagerId, 0) {
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
            val port = sshPort.toIntOrNull()
            val dshPort = sshDshPort.toIntOrNull()
            when {
                sshHost.isBlank() -> sshSettingsError = "请输入 SSH 主机地址"
                sshUser.isBlank() -> sshSettingsError = "请输入 SSH 用户名"
                port == null || port !in 1..65535 -> sshSettingsError = "SSH 端口无效"
                dshPort == null || dshPort !in 1..65535 -> sshSettingsError = "远程 DSH 端口无效"
                sshKeyId.isBlank() -> sshSettingsError = "请先导入 SSH 私钥"
                else -> {
                    runCatching { localStore?.saveRemoteProfile(DshRemoteProfile(
                        host = sshHost.trim(),
                        sshPort = port,
                        username = sshUser.trim(),
                        remoteDshPort = dshPort,
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
        pluginExpandedId = ""; pluginActionTarget = null; pluginConfirmAction = ""; pluginBusyId = ""
        pluginActionError = ""; pluginNotice = ""
        if (pluginInventoryVisible) pluginInventoryError = "连接已断开，请连接 Host 后刷新"
        timelineReadVersion++
        val mode = connectionCoordinator.activeModeOr(connectionMode)
        connectionCoordinator.stop()
        (repository as? DshRemoteRepository)?.stop()
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
        val remote = repository as? DshRemoteRepository ?: return
        if (goalActionBusy) return
        goalActionBusy = true
        goalActionError = ""
        action(remote, goal) { error ->
            setTimeout(pagerId, 0) {
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
        val currentWorkspaceId = if (isRemoteHost) {
            remoteRepository?.workspaceIdForSession(activeSessionId)
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
            setTimeout(pagerId, 0) { loadModels(blankSession.id) }
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
            setTimeout(pagerId, 0) {
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
        val hostRepository = repository as? DshRemoteRepository ?: return
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
        (repository as? DshRemoteRepository)?.detachLiveStreams(sessionId)
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
        val hostRepository = repository as? DshRemoteRepository ?: return
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
        val remote = repository as? DshRemoteRepository ?: return
        skills.clear()
        remote.loadSkills(sessionId, onSuccess = { loaded ->
            if (!isRemoteHost || activeSessionId != sessionId) return@loadSkills
            skills.clear()
            skills.addAll(loaded)
        })
    }

    private fun loadAttachment(sessionId: String, attachmentId: String) {
        if (attachmentDataUrl(attachmentId) != null || !pendingAttachmentReads.add(attachmentId)) return
        val hostRepository = repository as? DshRemoteRepository ?: return
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
                    val inlineUrl = com.example.dsh.conversation.inlineImageDataUrl(block)
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
        val repository = repository as? DshRemoteRepository ?: return
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
        val repository = repository as? DshRemoteRepository ?: return
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
        if (!isRemoteHost) {
            workspaceGroups = ObservableList()
            workspacePickerFolders.clear()
            return
        }
        val repository = repository as? DshRemoteRepository ?: return
        val byId = sessions.associateBy { it.id }
        val comparator = sessionSortComparator()
        val groups = repository.workspaceGroups().map { group ->
            group.copy(sessions = group.sessions.map { byId[it.id] ?: it }.sortedWith(comparator))
        }
        if (groups != workspaceGroups.toList()) workspaceGroups = ObservableList(groups.toMutableList())
        val folders = groups.filter { it.workspaceId.isNotEmpty() }
        workspacePickerFolders.clear()
        workspacePickerFolders.addAll(folders)
    }

    private fun sessionSortComparator(): Comparator<DshSession> = when (sessionSort) {
        DshSessionSort.UPDATED -> compareByDescending { it.updatedAt }
        DshSessionSort.CREATED -> compareByDescending { sessionCreatedAt[it.id] ?: it.createdAt }
        DshSessionSort.NAME -> compareBy { it.title.lowercase() }
    }

    fun toggleSessionSortMenu() { sessionSortMenuOpen = !sessionSortMenuOpen }

    fun pickSessionSort(value: DshSessionSort) {
        sessionSort = value
        sessionSortMenuOpen = false
        refreshWorkspaceGroups()
    }

    /** 首次打开抽屉时按需拉取会话 createdAt（Host 插件 meta），失败不影响主列表。 */
    private fun ensureSessionCreatedAt() {
        if (sessionCreatedAt.isNotEmpty()) return
        val remote = repository as? DshRemoteRepository ?: return
        remote.loadSessionMeta({ meta ->
            if (!pageAlive || repository !== remote) return@loadSessionMeta
            sessionCreatedAt = meta.associate { it.sessionId to it.createdAt }
            refreshWorkspaceGroups()
        }, {})
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
        val repository = repository as? DshRemoteRepository ?: return
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
        val repository = repository as? DshRemoteRepository ?: return
        val approval = pendingApproval ?: return
        interactionBusy = true
        repository.respondApproval(
            rpcId = approval.rpcId,
            sessionId = approval.sessionId,
            approvalId = approval.approvalId,
            outcome = outcome,
        ) { accepted, reason ->
            setTimeout(pagerId, 0) {
                interactionBusy = false
                if (!accepted) {
                    connectionLabel = interactionFailureLabel(reason)
                    return@setTimeout
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
        val repository = repository as? DshRemoteRepository
        if (repository == null) {
            return
        }
        questionError = ""
        interactionBusy = true
        repository.respondQuestionCancel(question.rpcId, question.sessionId) { accepted, reason ->
            setTimeout(pagerId, 0) {
                interactionBusy = false
                if (!accepted) {
                    questionError = interactionFailureLabel(reason)
                    return@setTimeout
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
        val repository = repository as? DshRemoteRepository
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
            setTimeout(pagerId, 0) {
                interactionBusy = false
                if (!accepted) {
                    questionError = interactionFailureLabel(reason)
                    return@setTimeout
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

    private fun interactionFailureLabel(reason: String): String = when (reason) {
        "not-pending" -> "这个问题已经失效，请等 Agent 重新提问"
        "bad-response" -> "提交未被接受，请再选一次后重试"
        "缺少请求编号" -> "这个问题已失效，请等 Agent 重新提问"
        "连接尚未就绪" -> "连接尚未就绪，请稍后再试"
        else -> reason.ifEmpty { "提交失败，请重试" }
    }

    private fun editQueueItem(itemId: String) {
        val item = queueItems.firstOrNull { it.id == itemId } ?: return
        val text = item.text ?: return
        queueDockExpanded = true
        queueEditingId = itemId
        queueEditingText = text
    }

    private fun saveQueueItem(itemId: String) {
        val repository = repository as? DshRemoteRepository ?: return
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
            setTimeout(pagerId, 0) {
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
        val repository = repository as? DshRemoteRepository ?: return
        if (queueActionBusy) return
        queueActionBusy = true
        repository.updateQueue(
            sessionId = activeSessionId,
            itemId = itemId,
            action = action,
        ) { _, _ ->
            setTimeout(pagerId, 0) {
                queueActionBusy = false
                refreshQueueDock()
            }
        }
    }

    private fun renameActiveSession() {
        val repository = repository as? DshRemoteRepository ?: return
        val current = sessions.firstOrNull { it.id == activeSessionId } ?: return
        val title = current.title.takeIf { it != "尚无标题" && it != "新会话" } ?: ""
        if (title.isBlank()) return
        repository.renameSession(activeSessionId, title) { _, _ ->
            setTimeout(pagerId, 0) { loadRepository(preferredSessionId = activeSessionId) }
        }
    }

    private fun archiveActiveSession() {
        val repository = repository as? DshRemoteRepository ?: return
        repository.archiveSession(activeSessionId) { _, _ ->
            setTimeout(pagerId, 0) {
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
        (repository as? DshRemoteRepository)?.touchSessionActivity(sessionId, now)
        reorderSessionsByUpdatedAt()
        refreshWorkspaceGroups()
        refreshVisibleSessions()
        runCatching { localStore?.replaceSessions(activeConnectionId, sessions.toList()) }
    }

    // ===== 会话 topbar overflow menu：日志 / 重命名 / 归档 / 删除 =====

    fun openOverflowMenu() {
        if (overflowMenuVisible) return
        closeMessageActions()
        closeSelectTextModal()
        overflowTargetSessionId = activeSessionId
        overflowMenuVisible = true
    }

    /** 从会话抽屉某行的 ⋯ 打开 overflow menu：锁定目标会话，抽屉保持开启。 */
    fun openOverflowMenuFor(sessionId: String) {
        if (overflowMenuVisible) return
        closeMessageActions()
        closeSelectTextModal()
        overflowTargetSessionId = sessionId
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
        val remote = repository as? DshRemoteRepository
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
        val repository = repository as? DshRemoteRepository ?: run {
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
            setTimeout(pagerId, 0) {
                if (!pageAlive || this.repository !== repository || activeConnectionId != connection) return@setTimeout
                sessionRenameBusy = false
                if (error != null) {
                    sessionRenameError = when (error.code) {
                        "title-invalid" -> "会话名称无效（不能为空或只包含空白字符），请修改后重试"
                        "session-not-found" -> "会话不存在，可能已被删除，请刷新列表"
                        else -> error.message
                    }
                    return@setTimeout
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
        val remote = repository as? DshRemoteRepository
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
        val remote = repository as? DshRemoteRepository ?: return
        val all = remote.archivedWorkspaceGroups()
        archiveProjectOptions.diffUpdate(
            all.filter { it.workspaceId.isNotEmpty() }.map { DshArchiveProjectOption(it.workspaceId, it.title) },
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
        val remote = repository as? DshRemoteRepository ?: run {
            archiveListError = "未连接 Host"
            return
        }
        val connection = activeConnectionId
        archiveBusy = true
        archiveListError = ""
        archiveNotice = ""
        remote.unarchiveSession(sessionId) { _, error ->
            setTimeout(pagerId, 0) {
                if (!pageAlive || this.repository !== remote || activeConnectionId != connection) return@setTimeout
                archiveBusy = false
                if (error != null) {
                    archiveListError = "取消归档失败：${error.message}"
                    return@setTimeout
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
        val remote = repository as? DshRemoteRepository ?: run { archiveListError = "未连接 Host"; return }
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
        val repository = repository as? DshRemoteRepository ?: run {
            sessionArchiveError = "当前连接不支持归档会话"
            return
        }
        sessionArchiveBusy = true
        sessionArchiveError = ""
        val expectedConnection = activeConnectionId
        repository.archiveSession(targetId) { _, error ->
            setTimeout(pagerId, 0) {
                if (!pageAlive || this.repository !== repository || activeConnectionId != expectedConnection) return@setTimeout
                sessionArchiveBusy = false
                if (error != null) {
                    sessionArchiveError = error.message
                    return@setTimeout
                }
                sessionArchiveVisible = false
                refreshVisibleSessions()
                refreshWorkspaceGroups()
                val catalog = DshSessionCatalog(sessions.toList(), repository.store.archivedSessionIds, repository.store.workspaceBaseline)
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
        val repository = repository as? DshRemoteRepository ?: run {
            sessionDeleteError = "当前连接不支持删除会话"
            return
        }
        sessionDeleteBusy = true
        sessionDeleteError = ""
        val connection = activeConnectionId
        repository.callPlugin("delete", JSONObject().apply { put("sessionId", targetId) }) { _, error ->
            setTimeout(pagerId, 0) {
                if (!pageAlive || this.repository !== repository || activeConnectionId != connection) return@setTimeout
                sessionDeleteBusy = false
                if (error != null) {
                    sessionDeleteError = error.message
                    return@setTimeout
                }
                sessionDeleteVisible = false
                val remaining = sessions.toList().filterNot { it.id == targetId }
                sessions = remaining
                repository.store.sessions.remove(targetId)
                sessionMessageStates.remove(targetId)
                sessionCacheStates.remove(targetId)
                sessionMessageReady.remove(targetId)
                conversationPanelIds.remove(targetId)
                refreshVisibleSessions()
                runCatching { localStore?.deleteSession(activeConnectionId, targetId) }
                refreshWorkspaceGroups()
                if (activeSessionId != targetId) return@setTimeout
                val next = DshSessionCatalog(sessions, repository.store.archivedSessionIds, repository.store.workspaceBaseline).nextActive(null)
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
        val remote = repository as? DshRemoteRepository
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
            setTimeout(pagerId, 0) {
                if (!current()) return@setTimeout
                if (error != null || childSessionId == null) {
                    messageForkBusy = false
                    if (error?.code == "message-unsynced" && activeSessionId == sourceSessionId) {
                        bridgeModule.toast("正在同步消息，请稍后再次分叉")
                        loadWebTimeline(sourceSessionId)
                    } else {
                        bridgeModule.toast("分叉失败：${error?.message ?: "Host 未返回新会话 ID"}")
                    }
                    return@setTimeout
                }
                createdSessionId = childSessionId
                if (activeSessionId != sourceSessionId) {
                    messageForkBusy = false
                    bridgeModule.toast("分支已创建，可从会话列表打开")
                    loadRepository(preferredSessionId = activeSessionId, restoreOnError = false)
                    return@setTimeout
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
        settingsPageVisible = false
        pluginInventoryVisible = true
        refreshPluginInventory()
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
        settingsPageVisible = true
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
        val remote = repository as? DshRemoteRepository ?: run {
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

    private fun applyPluginFilters() {
        pluginTotal = pluginInventory.size
        pluginRows.diffUpdate(filterDshPlugins(pluginInventory, pluginKeyword, pluginPhase)) { old, new -> old == new }
    }

    private fun refreshPluginInventory() {
        val remote = repository as? DshRemoteRepository ?: run {
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
        dshShareGroups(sessionMessageState(exportSelectionSessionId()))

    /** 当前多选态下需要在消息列表打勾的消息 id（同组 Prompt 与回复同时勾选）。 */
    private fun exportSelectedMessageIds(): Set<String> =
        exportGroups().filter { it.key in exportSelectedGroups }
            .flatMap { it.selectableIds }.toSet()

    private fun toggleExportMessage(messageId: String) {
        if (!exportSelectMode || messageId.isBlank()) return
        val group = dshShareGroupForMessage(sessionMessageState(exportSelectionSessionId()), messageId) ?: return
        exportSelectedGroups = if (group.key in exportSelectedGroups) {
            exportSelectedGroups - group.key
        } else {
            exportSelectedGroups + group.key
        }
        if (exportSelectedGroups.isEmpty()) exportMoreShareVisible = false
    }

    private fun exportTotalCount(): Int = exportGroups().size

    private fun exportSelectedCount(): Int {
        // 只统计当前列表仍存在的选中组，避免流式/历史刷新后残留 key 造成计数不一致
        val keys = exportGroups().map { it.key }.toSet()
        return exportSelectedGroups.count { it in keys }
    }

    private fun exportAllSelected(): Boolean {
        val keys = exportGroups().map { it.key }
        return keys.isNotEmpty() && keys.all { it in exportSelectedGroups }
    }

    private fun toggleExportSelectAll() {
        exportSelectedGroups = if (exportAllSelected()) emptySet() else exportGroups().map { it.key }.toSet()
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
    private fun exportSelectedMessages(sessionId: String): List<DshMessage> {
        val messages = sessionMessageState(sessionId)
        val selectedIds = dshShareGroups(messages)
            .filter { it.key in exportSelectedGroups }
            .flatMap { it.selectableIds }
            .toSet()
        return messages.filter { it.id in selectedIds && !it.hidden }
    }

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
        val remote = repository as? DshRemoteRepository
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
        val repository = repository as? DshRemoteRepository ?: return
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
            bridgeModule.toast("会话已开始，工作区不可修改")
            return
        }
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
        val remote = repository as? DshRemoteRepository ?: return
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
        workspacePickerScreen = DshWorkspacePickerScreen.ADD
        workspacePickerError = ""
        workspaceAddNewName = ""
        loadWorkspaceAddDirectory(null)
    }

    private fun loadWorkspaceAddDirectory(path: String?) {
        val remote = repository as? DshRemoteRepository ?: return
        if (workspacePickerBusy) return
        val generation = ++workspacePickerGeneration
        workspaceAddBusy = true
        workspacePickerError = ""
        remote.listDirectory(path) { listing, error ->
            setTimeout(pagerId, 0) {
                if (!pageAlive || !workspacePickerVisible || workspacePickerScreen != DshWorkspacePickerScreen.ADD ||
                    generation != workspacePickerGeneration || remote !== this@DshHomePage.repository
                ) return@setTimeout
                workspaceAddBusy = false
                if (error != null || listing == null) {
                    workspacePickerError = error?.message ?: "无法读取目录"
                    return@setTimeout
                }
                workspaceAddPath = listing.path
                workspaceAddHome = listing.home
                workspaceAddEntries.clear()
                workspaceAddEntries.addAll(listing.entries.filterNot { it.hidden })
            }
        }
    }

    private fun createWorkspaceAddDirectory() {
        val remote = repository as? DshRemoteRepository ?: return
        if (workspacePickerBusy || workspaceAddBusy) return
        val name = workspaceAddNewName.trim()
        if (workspaceAddPath.isEmpty() || name.isEmpty()) return
        val generation = workspacePickerGeneration
        workspaceAddBusy = true
        workspacePickerError = ""
        remote.createDirectory(workspaceAddPath, name) { createdPath, error ->
            setTimeout(pagerId, 0) {
                if (!pageAlive || !workspacePickerVisible || workspacePickerScreen != DshWorkspacePickerScreen.ADD ||
                    generation != workspacePickerGeneration || remote !== this@DshHomePage.repository
                ) return@setTimeout
                workspaceAddBusy = false
                if (error != null || createdPath == null) {
                    workspacePickerError = error?.message ?: "无法创建目录"
                    return@setTimeout
                }
                workspaceAddNewName = ""
                loadWorkspaceAddDirectory(createdPath)
            }
        }
    }

    /** 添加文件夹：注册 Host 目录为工作区，成功后直接切换过去并关闭弹窗。 */
    private fun adoptWorkspaceAddDirectory() {
        val remote = repository as? DshRemoteRepository ?: return
        val path = workspaceAddPath
        if (path.isEmpty() || workspacePickerBusy || workspaceAddBusy) return
        workspaceAddBusy = true
        workspacePickerError = ""
        remote.createWorkspace(path) { value, error ->
            setTimeout(pagerId, 0) {
                if (!pageAlive || !workspacePickerVisible || remote !== this@DshHomePage.repository) return@setTimeout
                if (error != null) {
                    workspaceAddBusy = false
                    workspacePickerError = error.message
                    return@setTimeout
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
        workspaceRenameTargetId = workspaceId
        workspaceRenameDraft = currentTitle
        workspaceActionError = ""
    }

    private fun saveWorkspaceRename() {
        val repository = repository as? DshRemoteRepository ?: return
        val workspaceId = workspaceRenameTargetId
        val title = workspaceRenameDraft.trim()
        if (workspaceId.isEmpty() || title.isEmpty()) return
        workspaceActionBusy = true
        workspaceActionError = ""
        repository.renameWorkspace(workspaceId, title) { _, error ->
            setTimeout(pagerId, 0) {
                workspaceActionBusy = false
                if (error != null) {
                    workspaceActionError = error.message
                    return@setTimeout
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
        val repository = repository as? DshRemoteRepository ?: return
        val workspaceId = workspaceDeleteTargetId
        if (workspaceId.isEmpty()) return
        workspaceActionBusy = true
        workspaceActionError = ""
        repository.deleteWorkspace(workspaceId) { _, error ->
            setTimeout(pagerId, 0) {
                workspaceActionBusy = false
                if (error != null) {
                    workspaceActionError = error.message
                    return@setTimeout
                }
                workspaceDeleteTargetId = ""
                refreshWorkspaceGroups()
            }
        }
    }

    private fun moveWorkspace(workspaceId: String, delta: Int) {
        val repository = repository as? DshRemoteRepository ?: return
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
            setTimeout(pagerId, 0) {
                workspaceActionBusy = false
                if (error != null) {
                    workspaceActionError = error.message
                    return@setTimeout
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
            setTimeout(pagerId, 0) {
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
        setTimeout(pagerId, 0) {
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

    private fun reconnectLabel(): String = when (connectionMode) {
        DshConnectionMode.SSH -> "远程连接重建中"
        DshConnectionMode.RELAY -> "扫码连接重建中"
        DshConnectionMode.LOCAL -> "本地 DSH 连接重建中"
    }

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

    private fun syncBusyLabel(): String = when (connectionMode) {
        DshConnectionMode.SSH -> "远程 DSH 正在同步，暂不能发送"
        DshConnectionMode.RELAY -> "扫码连接正在同步，暂不能发送"
        DshConnectionMode.LOCAL -> "本地 DSH 正在同步，暂不能发送"
    }

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
                setTimeout(pagerId, 0) {
                    pendingLocalMessageReads.remove(sessionId)
                    val state = sessionMessageStates[sessionId] ?: return@setTimeout
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
            setTimeout(pagerId, 0) {
                pendingLocalMessageReads.remove(sessionId)
                val state = sessionMessageStates[sessionId] ?: return@setTimeout
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
        setTimeout(pagerId, 0) {
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
            setTimeout(pagerId, 0) {
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
        dismissKeyboard()
        val prompt = draft.trim()
        val sendableImages = pendingImages.filter { it.state != DshImageDraftState.INVALID && it.dataBase64.isNotEmpty() }
        if ((prompt.isEmpty() && sendableImages.isEmpty()) || streaming) return
        val hostRepository = repository as? DshRemoteRepository
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
        val user = DshMessage(
            "user-${messages.size}",
            DshMessageRole.USER,
            prompt,
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
        streaming = true
        stopButtonVisible = true
        connectionLabel = "正在生成"
        syncTurnStatusTicker()
        touchSessionActivity(sessionId)
        streamHandle = hostRepository.streamReplyWithImages(
            pagerId = pagerId,
            sessionId = sessionId,
            prompt = prompt,
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
        // 主动停止时，已随 prompt 发出的图片不再留在输入区
        pendingImages.removeAll { it.state == DshImageDraftState.UPLOADING }
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

    private fun selectedReasoningEffortName(option: DshModelOption): String =
        option.reasoningEfforts.firstOrNull { it.id == option.reasoningEffort }?.name
            ?: option.reasoningEffort
            ?: ""

    private fun toggleVoice() {
        dismissKeyboard()
        commandSheetVisible = false
        voiceActive = !voiceActive
        connectionLabel = if (voiceActive) "正在聆听" else "已连接"
    }

    /** 附件方块点击：拍照/相册走平台取图，文件暂未开放 */
    private fun onAttachmentTile(tile: DshCommandSheetTile) {
        commandSheetVisible = false
        when (tile) {
            DshCommandSheetTile.CAMERA -> pickImageFrom("camera")
            DshCommandSheetTile.GALLERY -> pickImageFrom("album")
            DshCommandSheetTile.FILE -> bridgeModule.toast("文件上传暂未开放")
        }
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
            var accepted = pendingImages.toList()
            picked.forEach { pending ->
                val rejection = try {
                    DshImageValidator.rejectBatch(accepted, listOf(pending), limits)
                } catch (t: Throwable) {
                    DshStreamLog.log(LogLevel.ERROR, "app.image.failed", "phase=validate error='${DshStreamLog.preview(t.message.orEmpty())}'", expectedSession)
                    null
                }
                if (rejection != null) {
                    val reason = DshImageValidator.rejectionText(rejection)
                    pendingImages.add(pending.copy(state = DshImageDraftState.INVALID, error = reason))
                    bridgeModule.toast(reason)
                    DshStreamLog.log(
                        LogLevel.WARN, "app.image.rejected",
                        "source=$source mediaType=${pending.mediaType} width=${pending.width} height=${pending.height} bytes=${pending.bytes} reason=$reason",
                        expectedSession,
                    )
                } else {
                    pendingImages.add(pending)
                    accepted = accepted + pending
                    DshStreamLog.log(
                        LogLevel.INFO, "app.image.selected",
                        "source=$source mediaType=${pending.mediaType} width=${pending.width} height=${pending.height} bytes=${pending.bytes}",
                        expectedSession,
                    )
                }
            }
            attachmentEpoch += 1
        }
    }

    /** 兼容单张（旧字段）与多张（`images` 数组）两种原生返回。 */
    private fun parsePickedImages(result: com.tencent.kuikly.core.nvi.serialization.json.JSONObject): List<DshPendingImage> {
        val array = result.optJSONArray("images")
        if (array != null) {
            return buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    parsePickedImage(item, i)?.let(::add)
                }
            }
        }
        return listOfNotNull(parsePickedImage(result, 0))
    }

    private fun parsePickedImage(
        item: com.tencent.kuikly.core.nvi.serialization.json.JSONObject,
        index: Int,
    ): DshPendingImage? {
        val dataUrl = item.optString("dataUrl")
        if (dataUrl.isEmpty()) return null
        return DshPendingImage(
            clientId = "img-${currentTimeMillis()}-$index",
            mediaType = item.optString("mediaType"),
            name = item.optString("name").ifEmpty { "image" },
            dataBase64 = dataUrl.substringAfter("base64,"),
            previewDataUrl = dataUrl,
            bytes = item.optString("bytes").toLongOrNull() ?: 0L,
            width = item.optString("width").toIntOrNull() ?: 0,
            height = item.optString("height").toIntOrNull() ?: 0,
        )
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

    private fun messageRowKey(sessionId: String, messageId: String): String = "$sessionId:$messageId"

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
