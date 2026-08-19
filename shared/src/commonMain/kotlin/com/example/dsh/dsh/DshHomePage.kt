package com.example.dsh.dsh

import com.example.dsh.base.BasePager
import com.example.dsh.base.Utils
import com.example.dsh.base.bridgeModule
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vforLazy
import com.tencent.kuikly.core.layout.FlexAlign
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.reactive.handler.observableList
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.views.compose.Button
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.KeyboardParams
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.ListContentView
import com.tencent.kuikly.core.views.ListView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** First usable DSH surface: local sessions, streaming Markdown, and a composer. */
@Page("home")
internal class DshHomePage : BasePager() {
    private var repository: DshRepository? = null
    private var localStore: DshLocalStore? = null
    private var engineModule: DshEngineModule? = null
    private var engineReady = false
    private var pendingApiKey = ""

    private var sessions by observableList<DshSession>()
    private var messages by observableList<DshMessage>()
    private var conversationPanelIds by observableList<String>()
    private var eagerSessionIds by observableList<String>()
    private var activeSessionId by observable("session-1")
    private var draft by observable("")
    private var streaming by observable(false)
    private var stopButtonVisible by observable(false)
    private var connectionDotPhase by observable(0)
    private var streamingAssistantContent by observable("")
    private var keyboardHeight by observable(0f)
    private var keyboardAnimation by observable(Animation.easeInOut(ANIMATION_DURATION_S))
    private var connectionLabel by observable("本地内核启动中")
    private var apiKeyDraft by observable("")
    private var credentialSetupVisible by observable(false)
    private var credentialSetupBusy by observable(false)
    private var credentialSetupError by observable("")
    private var credentialSetupTitle by observable("添加一个 API Key 开始使用")
    private var sessionDrawerVisible by observable(false)
    private var sessionDrawerAnimated by observable(false)
    private var sessionDrawerMaskAnimated by observable(false)
    private var sessionDrawerMaskAnimation by observable(Animation.linear(0f))
    private var modelPickerVisible by observable(false)
    private var modelPickerBusy by observable(false)
    private var modelPickerError by observable("")
    private var selectedModelLabel by observable("选择模型")
    private var modelOptions by observableList<DshModelOption>()
    private var attachmentMenuVisible by observable(false)
    private var voiceActive by observable(false)
    private var topBarRef: ViewRef<com.tencent.kuikly.core.views.DivView>? = null
    private var inputView: InputView? = null
    private var apiKeyInputView: InputView? = null
    private var streamHandle: DshStreamHandle? = null
    private val messageScrollerRefs = mutableMapOf<String, ViewRef<ListView<*, *>>>()
    private var historyRequestGeneration = 0
    private val sessionMessageStates = mutableMapOf<String, ObservableList<DshMessage>>()
    private val sessionMessageReady = mutableSetOf<String>()
    private val pendingSessionSelections = mutableSetOf<String>()
    private val localReadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pendingLocalMessageReads = mutableSetOf<String>()
    private var inputFocused = false
    private var streamingAssistantId = ""
    private val pendingAssistantDelta = StringBuilder()
    private var assistantFlushScheduled = false
    private var scrollSettleGeneration = 0
    private var perfTraceSequence = 0

    override fun created() {
        super.created()
        val startedAt = TimeSource.Monotonic.markNow()
        perfLog("startup.created.begin", startedAt)
        val databaseDir = pageData.params.optString("databaseDir")
        if (databaseDir.isNotEmpty()) {
            localStore = runCatching {
                createDshLocalStore("$databaseDir/dsh.db")
            }.getOrNull()
        }
        animateConnectionDots()
        sessionMessageStates[activeSessionId] = messages
        prepareEagerSession(activeSessionId)
        restoreCachedSessions()
        perfLog("startup.restoreCachedSessions.done", startedAt)
        preloadAllSessionMessages()
        perfLog("startup.preloadAllSessionMessages.scheduled", startedAt)
        loadApiKeyAsync()
        setTimeout(pagerId, SESSION_CACHE_WARM_START_DELAY_MS) {
            warmRecentSessionCache()
        }
        startEmbeddedEngine()
        perfLog("startup.created.end", startedAt)
    }

    override fun pageWillDestroy() {
        localReadScope.cancel()
        super.pageWillDestroy()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        val wide = pagerData.pageViewWidth >= 720f
        return {
            View {
                attr {
                    flex(1f)
                    flexDirectionColumn()
                    backgroundColor(Color(BG))
                    paddingTop(pagerData.statusBarHeight)
                }

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
                    View {
                        ref { ctx.topBarRef = it }
                        DshTopBar(
                            title = { ctx.sessions.firstOrNull { it.id == ctx.activeSessionId }?.title ?: "DeepSeek Harness" },
                            connection = { ctx.connectionLabel },
                            dotPhase = { ctx.connectionDotPhase },
                        )
                    }

                    if (wide) {
                        View {
                            attr {
                                flex(1f)
                                flexDirectionRow()
                            }
                            DshConversation(
                                conversationIds = { ctx.conversationPanelIds },
                                eagerConversationIds = { ctx.eagerSessionIds },
                                activeConversationId = { ctx.activeSessionId },
                                messagesForSession = { ctx.sessionMessageState(it) },
                                streaming = { ctx.streaming },
                                streamingContent = { ctx.streamingAssistantContent },
                                scrollerRef = { id, ref -> ctx.messageScrollerRefs[id] = ref },
                                draft = { ctx.draft },
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
                                modelLabel = { ctx.selectedModelLabel },
                                attachmentMenuVisible = { ctx.attachmentMenuVisible },
                                voiceActive = { ctx.voiceActive },
                                onOpenModels = { ctx.openModelPicker() },
                                onToggleAttachments = {
                                    ctx.dismissKeyboard()
                                    ctx.attachmentMenuVisible = !ctx.attachmentMenuVisible
                                },
                                onToggleVoice = { ctx.toggleVoice() },
                            )
                        }
                    } else {
                        DshConversation(
                            conversationIds = { ctx.conversationPanelIds },
                            eagerConversationIds = { ctx.eagerSessionIds },
                            activeConversationId = { ctx.activeSessionId },
                            messagesForSession = { ctx.sessionMessageState(it) },
                            streaming = { ctx.streaming },
                            streamingContent = { ctx.streamingAssistantContent },
                            scrollerRef = { id, ref -> ctx.messageScrollerRefs[id] = ref },
                            draft = { ctx.draft },
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
                            modelLabel = { ctx.selectedModelLabel },
                            attachmentMenuVisible = { ctx.attachmentMenuVisible },
                            voiceActive = { ctx.voiceActive },
                            onOpenModels = { ctx.openModelPicker() },
                            onToggleAttachments = {
                                ctx.dismissKeyboard()
                                ctx.attachmentMenuVisible = !ctx.attachmentMenuVisible
                            },
                            onToggleVoice = { ctx.toggleVoice() },
                        )
                    }

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

                vif({ ctx.sessionDrawerVisible }) {
                    DshSessionDrawer(
                        sessions = { ctx.sessions },
                        activeId = { ctx.activeSessionId },
                        animated = { ctx.sessionDrawerAnimated },
                        onClose = { ctx.closeSessionDrawer() },
                        onOpenSettings = { ctx.openCredentialSettings() },
                        onNewSession = { ctx.createSession() },
                        onSelect = { id ->
                            ctx.closeSessionDrawer()
                            setTimeout(ctx.pagerId, 0) {
                                ctx.selectSession(id)
                            }
                        },
                    )
                }

                vif({ ctx.modelPickerVisible }) {
                    DshModelPicker(
                        options = { ctx.modelOptions },
                        busy = { ctx.modelPickerBusy },
                        error = { ctx.modelPickerError },
                        onClose = { ctx.modelPickerVisible = false },
                        onSelect = { ctx.selectModel(it) },
                    )
                }

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
                    )
                }
            }
        }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        topBarRef?.view?.event {
            click {
                this@DshHomePage.dismissKeyboard()
                this@DshHomePage.openSessionDrawer()
            }
        }
        addTaskWhenPagerUpdateLayoutFinish {
            refreshMountedSessionRenderTrees()
        }
    }

    private fun openSessionDrawer() {
        if (sessionDrawerVisible) return
        // Mount transparent first, then start drawer and mask on the same frame.
        sessionDrawerMaskAnimation = Animation.easeInOut(0.24f)
        sessionDrawerMaskAnimated = false
        sessionDrawerAnimated = false
        sessionDrawerVisible = true
        setTimeout(pagerId, 16) {
            sessionDrawerAnimated = true
            sessionDrawerMaskAnimated = true
        }
        setTimeout(pagerId, ANIMATION_DURATION_MS) {
            warmRecentSessionCache()
        }
    }

    private fun animateConnectionDots() {
        setTimeout(pagerId, CONNECTION_DOT_INTERVAL_MS) {
            // The phase lives on the page, so avoid invalidating the whole
            // conversation and drawer tree while the drawer is animating or
            // after the connection has already become stable.
            if (!sessionDrawerVisible && !isConnectionReadyLabel(connectionLabel)) {
                connectionDotPhase = (connectionDotPhase + 1) % CONNECTION_DOT_COUNT
            }
            animateConnectionDots()
        }
    }

    private fun closeSessionDrawer() {
        if (!sessionDrawerVisible) return
        // Reverse the opening transition: fade the mask out while the drawer closes.
        sessionDrawerMaskAnimation = Animation.easeInOut(ANIMATION_DURATION_S)
        sessionDrawerMaskAnimated = false
        sessionDrawerAnimated = false
        setTimeout(pagerId, ANIMATION_DURATION_MS) {
            sessionDrawerVisible = false
        }
    }

    private fun loadRepository(preferredSessionId: String? = null) {
        val hostRepository = repository ?: return
        hostRepository.loadSessions({ loaded ->
            sessions.clear()
            sessions.addAll(loaded)
            runCatching { localStore?.saveSessions(loaded) }
            preloadAllSessionMessages()
            connectionLabel = if (loaded.isEmpty()) "已连接 · 无会话" else "已连接"
            if (loaded.isNotEmpty()) {
                activeSessionId = loaded.firstOrNull { it.id == preferredSessionId }?.id ?: loaded.first().id
                prepareEagerSession(activeSessionId)
                loadModels(activeSessionId)
                loadHistory(activeSessionId)
            } else {
                messages.clear()
                messages.add(
                    DshMessage(
                        id = "no-session",
                        role = DshMessageRole.ERROR,
                        content = "当前还没有会话，打开左上角菜单后点击“新会话”即可开始。",
                    ),
                )
            }
        }, { error ->
            connectionLabel = "内核连接失败"
            restoreCachedSessions()
            if (sessions.isEmpty()) {
                messages.clear()
                messages.add(DshMessage("load-error", DshMessageRole.ERROR, error))
            } else {
                connectionLabel = "连接失败 · 已显示缓存"
            }
        })
    }

    private fun startEmbeddedEngine() {
        if (!pageData.params.optBoolean("embeddedEngine")) {
            connectionLabel = "当前平台不支持内置 Harness"
            return
        }
        val module = acquireModule<DshEngineModule>(DshEngineModule.MODULE_NAME)
        engineModule = module
        module.start { state ->
            when (state.phase) {
                DshEnginePhase.IDLE -> connectionLabel = "准备本地内核"
                DshEnginePhase.PREPARING -> {
                    connectionLabel = if (state.progress > 0) "解压内核 ${state.progress}%" else state.message
                }
                DshEnginePhase.STARTING -> connectionLabel = state.message.ifEmpty { "本地内核启动中" }
                DshEnginePhase.READY -> {
                    engineReady = true
                    connectionLabel = "本地内核已就绪"
                    if (pendingApiKey.isNotEmpty() && repository == null) {
                        connectLocalEngine(pendingApiKey)
                    }
                }
                DshEnginePhase.ERROR -> {
                    engineReady = false
                    connectionLabel = "内核启动失败"
                    messages.clear()
                    messages.add(DshMessage("engine-error", DshMessageRole.ERROR, state.message))
                }
                DshEnginePhase.STOPPED -> {
                    engineReady = false
                    connectionLabel = "本地内核已停止"
                }
                DshEnginePhase.UNSUPPORTED -> connectionLabel = "当前平台不支持内置 Harness"
            }
        }
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
        attachmentMenuVisible = false
        //closeSessionDrawer()
        credentialSetupTitle = "设置 DeepSeek API Key"
        credentialSetupError = ""
        apiKeyDraft = pendingApiKey
        updateCredentialSetupVisibility(true)
    }

    private fun closeCredentialSettings() {
        dismissKeyboard()
        updateCredentialSetupVisibility(false)
    }

    private fun updateCredentialSetupVisibility(visible: Boolean) {
        credentialSetupVisible = visible
        if (pageData.isAndroid) {
            bridgeModule.setSystemBarsDimmed(visible)
        }
    }

    private fun createSession() {
        val traceId = ++perfTraceSequence
        val startedAt = TimeSource.Monotonic.markNow()
        perfLog("newSession.$traceId.click", startedAt)
        val hostRepository = repository ?: run {
            connectionLabel = "请先配置 API Key"
            openCredentialSettings()
            return
        }
        dismissKeyboard()
        closeSessionDrawer()
        perfLog("newSession.$traceId.ui.cleared", startedAt)
        perfLog("newSession.$traceId.host.create.request", startedAt)
        hostRepository.createSession({ sessionId ->
            perfLog("newSession.$traceId.host.create.response:$sessionId", startedAt)
            val created = DshSession(
                id = sessionId,
                title = "新会话",
                workspace = "Host",
                updatedLabel = "",
            )
            // Keep the existing sessions when creating a new one. Clearing
            // this list also rewrites SQLite with only the newly created row.
            if (sessions.none { it.id == created.id }) {
                sessions.add(0, created)
            }
            runCatching { localStore?.saveSessions(sessions.toList()) }
            activeSessionId = sessionId
            messages = ObservableList()
            sessionMessageStates[sessionId] = messages
            sessionMessageReady.add(sessionId)
            prepareEagerSession(sessionId)
            perfLog("newSession.$traceId.ui.ready", startedAt)
            draft = ""
            inputView?.setText("")
            setTimeout(pagerId, 0) {
                if (activeSessionId == sessionId) loadModels(sessionId)
            }
        }, { error ->
            perfLog("newSession.$traceId.host.create.error:$error", startedAt)
            connectionLabel = "新会话创建失败"
            messages.add(DshMessage("session-create-error-${messages.size}", DshMessageRole.ERROR, error))
        })
    }

    private fun loadHistory(sessionId: String) {
        val requestGeneration = ++historyRequestGeneration

        // Show the selected session immediately. The Host history request is
        // remote and can take a moment, so keeping the previous list here
        // makes a session switch look stuck.
        messages = sessionMessageState(sessionId)
        ensureConversationPanel(sessionId)

        val hostRepository = repository ?: return
        hostRepository.loadHistory(sessionId, { loaded ->
            if (requestGeneration != historyRequestGeneration || activeSessionId != sessionId) return@loadHistory
            sessionMessageReady.add(sessionId)
            replaceMessagesIfChanged(loaded)
            runCatching { localStore?.saveMessages(sessionId, loaded) }
            completePendingSessionSelection(sessionId)
            realizeSessionAfterData(sessionId)
        }, { error ->
            if (requestGeneration != historyRequestGeneration || activeSessionId != sessionId) return@loadHistory
            if (messages.isNotEmpty()) {
                connectionLabel = "内核连接失败 · 已显示缓存"
            } else {
                messages.add(DshMessage("history-error", DshMessageRole.ERROR, error))
            }
        })
    }

    private fun restoreCachedSessions() {
        val store = localStore ?: return
        val cached = runCatching { store.loadSessions() }.getOrDefault(emptyList())
        if (cached.isEmpty()) return
        sessions.clear()
        sessions.addAll(cached)
        val firstSessionId = cached.first().id
        val firstMessages = runCatching { store.loadMessages(firstSessionId).orEmpty() }
            .getOrDefault(emptyList())
            .filterNot { it.isRuntimeContextSnapshot() }
        activeSessionId = firstSessionId
        val state = sessionMessageStates[firstSessionId] ?: ObservableList()
        if (state.size == 1 && state.firstOrNull()?.id == "api-key-required") state.clear()
        if (state.isEmpty() && firstMessages.isNotEmpty()) state.addAll(firstMessages)
        sessionMessageStates[firstSessionId] = state
        sessionMessageReady.add(firstSessionId)
        messages = state
        prepareEagerSession(firstSessionId)
        scrollMessagesToEnd()
    }

    private fun loadApiKeyAsync() {
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
                } else if (engineReady && repository == null) {
                    connectLocalEngine(apiKey)
                }
            }
        }
    }

    private fun showCredentialSetupIfNeeded(apiKey: String) {
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
        val traceId = ++perfTraceSequence
        val startedAt = TimeSource.Monotonic.markNow()
        perfLog("switch.$traceId.request:$id", startedAt)
        dismissKeyboard()
        if (id == activeSessionId) {
            perfLog("switch.$traceId.same-session", startedAt)
            return
        }
        if (!sessionMessageReady.contains(id)) {
            pendingSessionSelections.add(id)
            perfLog("switch.$traceId.wait-data", startedAt)
            return
        }
        prepareEagerSession(id)
        if (!conversationPanelIds.contains(id) || !messageScrollerRefs.containsKey(id)) {
            // Mount the target ListView first. Changing activeSessionId in the
            // same frame would make the new panel visible before its native
            // render tree and Markdown children exist.
            ensureConversationPanel(id)
            addTaskWhenPagerUpdateLayoutFinish {
                perfLog("switch.$traceId.panel.layout-finished", startedAt)
                if (activeSessionId != id) selectSession(id)
            }
            return
        }
        selectMountedSession(id, traceId, startedAt)
    }

    private fun selectMountedSession(id: String, traceId: Int = 0, startedAt: TimeMark? = null) {
        if (id == activeSessionId) return
        perfLog("switch.$traceId.mounted.begin", startedAt)
        refreshSessionRenderTree(id)
        cancelStreamingForSessionSwitch()
        sessionMessageStates[activeSessionId] = messages
        val nextMessages = sessionMessageState(id, loadFromDisk = false)
        ensureConversationPanel(id)
        messages = nextMessages
        activeSessionId = id
        perfLog("switch.$traceId.active-state-swapped", startedAt)
        scrollMessagesToEnd()
        addTaskWhenPagerUpdateLayoutFinish {
            refreshSessionRenderTree(id)
            perfLog("switch.$traceId.layout.realized", startedAt)
            if (activeSessionId == id) scrollMessagesToEnd()
        }
        // Invalidate any in-flight request for the previous session before
        // starting the new one, so an old response cannot repaint this view.
        historyRequestGeneration++
        loadMessagesFromDisk(id)
        setTimeout(pagerId, 0) {
            if (activeSessionId == id) loadModels(id)
        }
        draft = ""
        inputView?.setText("")
        perfLog("switch.$traceId.end", startedAt)
    }

    private fun refreshMountedSessionRenderTrees() {
        conversationPanelIds.toList().forEach { refreshSessionRenderTree(it) }
    }

    private fun refreshSessionRenderTree(sessionId: String) {
        val list = messageScrollerRefs[sessionId]?.view ?: return
        (list.contentView as? ListContentView)?.createRenderViewsOnVisibleRect()
    }

    private fun perfLog(stage: String, startedAt: TimeMark? = null) {
        val elapsed = startedAt?.elapsedNow()?.inWholeMilliseconds?.let { " +${it}ms" } ?: ""
        Utils.logToNative(pagerId, "[DshPerf] $stage$elapsed")
    }

    private fun realizeSessionAfterData(sessionId: String) {
        prepareEagerSession(sessionId)
        refreshSessionRenderTree(sessionId)
        addTaskWhenPagerUpdateLayoutFinish {
            refreshSessionRenderTree(sessionId)
            if (activeSessionId == sessionId) scrollMessagesToEnd()
        }
        setTimeout(pagerId, 16) {
            refreshSessionRenderTree(sessionId)
            if (activeSessionId == sessionId) scrollMessagesToEnd()
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
    ): ObservableList<DshMessage> {
        sessionMessageStates[sessionId]?.let { return it }
        val state = ObservableList<DshMessage>()
        sessionMessageStates[sessionId] = state
        if (loadFromDisk) loadMessagesFromDisk(sessionId)
        return state
    }

    private fun prepareEagerSession(sessionId: String) {
        if (!eagerSessionIds.contains(sessionId)) eagerSessionIds.add(sessionId)
        ensureConversationPanel(sessionId)
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
        if (pending.isEmpty()) return
        localReadScope.launch {
            pending.forEach { sessionId ->
                val readStartedAt = TimeSource.Monotonic.markNow()
                val loaded = runCatching { store.loadMessages(sessionId).orEmpty() }
                    .getOrDefault(emptyList())
                    .filterNot { it.isRuntimeContextSnapshot() }
                val queryFinishedAt = TimeSource.Monotonic.markNow()
                val queryMs = readStartedAt.elapsedNow().inWholeMilliseconds
                setTimeout(pagerId, 0) {
                    pendingLocalMessageReads.remove(sessionId)
                    val state = sessionMessageStates[sessionId] ?: return@setTimeout
                    sessionMessageReady.add(sessionId)
                    val uiWaitMs = queryFinishedAt.elapsedNow().inWholeMilliseconds
                    perfLog(
                        "sessionData.disk.done:$sessionId messages=${loaded.size} query=${queryMs}ms uiWait=${uiWaitMs}ms",
                        readStartedAt,
                    )
                    if (state.isEmpty() && loaded.isNotEmpty()) {
                        state.addAll(loaded)
                        perfLog("sessionData.ui.applied:$sessionId messages=${loaded.size}")
                    }
                    if (sessionId == activeSessionId || pendingSessionSelections.contains(sessionId)) {
                        prepareEagerSession(sessionId)
                    }
                    realizeSessionAfterData(sessionId)
                    completePendingSessionSelection(sessionId)
                }
            }
        }
    }

    private fun loadMessagesFromDisk(sessionId: String) {
        if (localStore == null || !pendingLocalMessageReads.add(sessionId)) return
        localReadScope.launch {
            val readStartedAt = TimeSource.Monotonic.markNow()
                val loaded = runCatching { localStore?.loadMessages(sessionId).orEmpty() }
                    .getOrDefault(emptyList())
                    .filterNot { it.isRuntimeContextSnapshot() }
                val queryFinishedAt = TimeSource.Monotonic.markNow()
                val queryMs = readStartedAt.elapsedNow().inWholeMilliseconds
                setTimeout(pagerId, 0) {
                pendingLocalMessageReads.remove(sessionId)
                val state = sessionMessageStates[sessionId] ?: return@setTimeout
                sessionMessageReady.add(sessionId)
                val uiWaitMs = queryFinishedAt.elapsedNow().inWholeMilliseconds
                perfLog(
                    "sessionData.disk.done:$sessionId messages=${loaded.size} query=${queryMs}ms uiWait=${uiWaitMs}ms",
                    readStartedAt,
                )
                // A remote history response or a new local prompt wins over
                // a disk snapshot that finishes later. The state is keyed by
                // session ID, so an inactive session can be updated safely.
                if (state.isEmpty() && loaded.isNotEmpty()) {
                    state.addAll(loaded)
                    perfLog("sessionData.ui.applied:$sessionId messages=${loaded.size}")
                }
                ensureConversationPanel(sessionId)
                realizeSessionAfterData(sessionId)
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
    ) {
        if (index >= sessionIds.size) return
        sessionMessageState(sessionIds[index], loadFromDisk = true)
        if (sessionMessageReady.contains(sessionIds[index]) &&
            (sessionIds[index] == activeSessionId || pendingSessionSelections.contains(sessionIds[index]))) {
            ensureConversationPanel(sessionIds[index])
        }
        setTimeout(pagerId, SESSION_CACHE_WARM_INTERVAL_MS) {
            warmRecentSessionCache(sessionIds, index + 1)
        }
    }

    private fun ensureConversationPanel(sessionId: String) {
        if (conversationPanelIds.contains(sessionId)) return
        if (conversationPanelIds.size >= CONVERSATION_PANEL_CACHE_LIMIT) {
            val evictIndex = conversationPanelIds.indexOfFirst { it != activeSessionId }
            if (evictIndex >= 0) {
                val evictedId = conversationPanelIds.removeAt(evictIndex)
                messageScrollerRefs.remove(evictedId)
            }
        }
        conversationPanelIds.add(sessionId)
    }

    private fun sendDraft() {
        dismissKeyboard()
        val prompt = draft.trim()
        if (prompt.isEmpty() || streaming) return
        val hostRepository = repository
        if (hostRepository == null) {
            connectionLabel = "本地内核尚未连接"
            messages.add(DshMessage(
                "send-engine-error-${messages.size}",
                DshMessageRole.ERROR,
                "本地 Harness 尚未连接，请稍候再试。",
            ))
            return
        }
        if (sessions.isEmpty()) {
            connectionLabel = "正在创建会话"
            hostRepository.createSession({ sessionId ->
                sessions.add(DshSession(sessionId, "新会话", "Host", ""))
                runCatching { localStore?.saveSessions(sessions.toList()) }
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
            })
            return
        }
        val sessionId = activeSessionId
        val user = DshMessage("user-${messages.size}", DshMessageRole.USER, prompt)
        val assistantId = "assistant-${messages.size}"
        messages.add(user)
        sessionMessageStates[sessionId] = messages
        scrollMessagesToEnd()
        streamingAssistantId = assistantId
        streamingAssistantContent = ""
        pendingAssistantDelta.setLength(0)
        draft = ""
        inputView?.setText("")
        streaming = true
        stopButtonVisible = true
        connectionLabel = "正在生成"
        streamHandle = hostRepository.streamReply(
            pagerId = pagerId,
            sessionId = sessionId,
            prompt = prompt,
            onDelta = { delta -> queueAssistantDelta(assistantId, delta) },
            onComplete = { result ->
                flushAssistantDelta()
                val completedContent = result.ifEmpty { streamingAssistantContent }
                streaming = false
                stopButtonVisible = false
                settleStreamingMessage(DshMessageRole.ASSISTANT, completedContent)
                persistMessages(sessionId)
                connectionLabel = "已连接"
                streamHandle = null
            },
            onError = { error ->
                flushAssistantDelta()
                streaming = false
                stopButtonVisible = false
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
        flushAssistantDelta()
        val stoppedContent = streamingAssistantContent + "\n\n*已停止*"
        streaming = false
        stopButtonVisible = false
        settleStreamingMessage(DshMessageRole.ASSISTANT, stoppedContent)
        persistMessages(activeSessionId)
        connectionLabel = "已连接"
    }

    private fun cancelStreamingForSessionSwitch() {
        if (!streaming && !stopButtonVisible) return
        streamHandle?.cancel()
        streamHandle = null
        val partial = streamingAssistantContent + pendingAssistantDelta.toString()
        if (streamingAssistantId.isNotEmpty() && partial.isNotBlank()) {
            messages.add(DshMessage(streamingAssistantId, DshMessageRole.ASSISTANT, partial))
        }
        streamingAssistantId = ""
        pendingAssistantDelta.setLength(0)
        streamingAssistantContent = ""
        assistantFlushScheduled = false
        streaming = false
        stopButtonVisible = false
    }

    private fun dismissKeyboard() {
        if (!inputFocused && keyboardHeight <= 0f) return
        inputFocused = false
        inputView?.blur()
        bridgeModule.closeKeyboard()
        keyboardHeight = 0f
    }

    private fun updateKeyboard(params: KeyboardParams) {
        keyboardAnimation = Animation.easeInOut(ANIMATION_DURATION_S)
        keyboardHeight = effectiveKeyboardHeight(params.height)
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
        hostRepository.loadModels(sessionId, { loaded ->
            selectedModelLabel = loaded.current.name
            modelOptions.clear()
            modelOptions.addAll(loaded.options)
            modelPickerBusy = false
            modelPickerError = if (loaded.routable) "" else "当前模型不可用，请选择其他模型。"
        }, { error ->
            modelPickerBusy = false
            modelPickerError = error
        })
    }

    private fun openModelPicker() {
        if (sessions.isEmpty()) return
        dismissKeyboard()
        attachmentMenuVisible = false
        modelPickerVisible = true
        modelPickerBusy = true
        modelPickerError = ""
        loadModels(activeSessionId)
    }

    private fun selectModel(option: DshModelOption) {
        val hostRepository = repository ?: return
        modelPickerBusy = true
        modelPickerError = ""
        hostRepository.selectModel(activeSessionId, option, { selected ->
            selectedModelLabel = selected.name
            modelPickerBusy = false
            modelPickerVisible = false
            val currentOptions = modelOptions.toList()
            modelOptions.clear()
            modelOptions.addAll(currentOptions.map {
                it.copy(selected = it.provider == selected.provider && it.model == selected.model)
            })
        }, { error ->
            modelPickerBusy = false
            modelPickerError = error
        })
    }

    private fun toggleVoice() {
        dismissKeyboard()
        attachmentMenuVisible = false
        voiceActive = !voiceActive
        connectionLabel = if (voiceActive) "正在聆听" else "已连接"
    }

    private fun queueAssistantDelta(id: String, delta: String) {
        if (delta.isEmpty()) return
        if (streamingAssistantId != id) return
        pendingAssistantDelta.append(delta)
        if (assistantFlushScheduled) return
        assistantFlushScheduled = true
        setTimeout(pagerId, STREAM_FLUSH_INTERVAL_MS) {
            assistantFlushScheduled = false
            flushAssistantDelta()
        }
    }

    private fun flushAssistantDelta() {
        if (streamingAssistantId.isEmpty() || pendingAssistantDelta.isEmpty()) return
        streamingAssistantContent += pendingAssistantDelta.toString()
        pendingAssistantDelta.setLength(0)
        scrollMessagesToEnd()
    }

    private fun scrollMessagesToEnd() {
        val generation = ++scrollSettleGeneration
        addTaskWhenPagerUpdateLayoutFinish {
            settleScrollToEnd(generation, 0)
        }
    }

    /**
     * Markdown and LazyLoop can add/layout children over several frames.
     * Re-apply the bottom offset while that burst settles, otherwise the first
     * offset is calculated from a shorter content height and the user sees the
     * list walk down a few screens after launch.
     */
    private fun settleScrollToEnd(generation: Int, attempt: Int) {
        if (generation != scrollSettleGeneration) return
        scrollMessagesToEndAfterLayout()
        if (attempt >= SCROLL_SETTLE_ATTEMPTS) return
        setTimeout(pagerId, SCROLL_SETTLE_DELAYS_MS[attempt]) {
            addTaskWhenPagerUpdateLayoutFinish {
                settleScrollToEnd(generation, attempt + 1)
            }
        }
    }

    private fun scrollMessagesToEndAfterLayout() {
        val scroller = messageScrollerRefs[activeSessionId]?.view ?: return
        val contentHeight = scroller.contentView?.flexNode?.layoutFrame?.height ?: return
        val viewportHeight = scroller.flexNode?.layoutFrame?.height ?: return
        scroller.setContentOffset(0f, (contentHeight - viewportHeight).coerceAtLeast(0f), animated = false)
    }

    private fun settleStreamingMessage(role: DshMessageRole, content: String) {
        val id = streamingAssistantId
        if (id.isNotEmpty()) {
            messages.add(DshMessage(id, role, content, streaming = false))
            scrollMessagesToEnd()
        }
        streamingAssistantId = ""
        pendingAssistantDelta.setLength(0)
    }

    private fun persistMessages(sessionId: String) {
        val snapshot = messages.toList()
        sessionMessageStates[sessionId] = messages
        runCatching { localStore?.saveMessages(sessionId, snapshot) }
    }

    private fun replaceMessagesIfChanged(next: List<DshMessage>) {
        val filtered = next.filterNot { it.isRuntimeContextSnapshot() }
        if (messages.toList() == filtered) return
        messages.clear()
        messages.addAll(filtered)
        sessionMessageStates[activeSessionId] = messages
    }

    companion object {
        private const val BG = 0xFFF7F9FA
        private const val LOCAL_ENGINE_URL = "http://127.0.0.1:3080"
        private const val ENGINE_CONNECT_RETRIES = 60
        private const val ENGINE_RETRY_DELAY_MS = 1_000
        private const val ANIMATION_DURATION_MS = 240
        private const val ANIMATION_DURATION_S = 0.24f
        private const val STREAM_FLUSH_INTERVAL_MS = 100
    }
}

private fun ViewContainer<*, *>.DshCredentialSetupModal(
    title: () -> String,
    busy: () -> Boolean,
    error: () -> String,
    inputRef: (ViewRef<InputView>) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
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
                flexDirectionColumn()
                padding(24f)
                borderRadius(18f)
                backgroundColor(Color.WHITE)
            }
            View {
                attr {
                    height(32f)
                    flexDirectionRow()
                    alignItemsCenter()
                }
                Text {
                    attr {
                        text(title())
                        flex(1f)
                        fontSize(20f)
                        fontWeightBold()
                        color(Color(0xFF1F2933))
                    }
                }
                View {
                    attr {
                        size(32f, 32f)
                        allCenter()
                    }
                    Image {
                        attr {
                            src(ImageUri.commonAssets("x.svg"))
                            size(20f, 20f)
                        }
                    }
                    DshHitButton { if (!busy()) onClose() }
                }
            }
            Text {
                attr {
                    text("配置 DeepSeek 官方模型，即可开始使用。")
                    marginTop(8f)
                    fontSize(14f)
                    lineHeight(21f)
                    color(Color(0xFF6B7680))
                }
            }
            Text {
                attr {
                    text("API Key")
                    marginTop(22f)
                    fontSize(13f)
                    fontWeightMedium()
                    color(Color(0xFF343E47))
                }
            }
            View {
                attr {
                    height(46f)
                    marginTop(8f)
                    borderRadius(8f)
                    border(Border(1f, BorderStyle.SOLID, Color(
                        if (error().isEmpty()) 0xFFD9DEE3 else 0xFFD44949,
                    )))
                    backgroundColor(Color(0xFFF9FAFB))
                    paddingLeft(12f)
                    paddingRight(12f)
                }
                Input {
                    ref { inputRef(it) }
                    attr {
                        flex(1f)
                        fontSize(15f)
                        color(Color(0xFF222C35))
                        placeholder("输入 DeepSeek API Key")
                        placeholderColor(Color(0xFF98A1A9))
                        keyboardTypePassword()
                        returnKeyTypeDone()
                        autofocus(true)
                        editable(!busy())
                    }
                    event {
                        textDidChange { onApiKeyChange(it.text) }
                        inputReturn { if (!busy()) onSave() }
                    }
                }
            }
            vif({ error().isNotEmpty() }) {
                Text {
                    attr {
                        text(error())
                        marginTop(8f)
                        fontSize(12f)
                        lineHeight(18f)
                        color(Color(0xFFBF3535))
                    }
                }
            }
            View {
                attr {
                    marginTop(24f)
                    height(40f)
                    flexDirectionRow()
                    justifyContentFlexEnd()
                }
                Button {
                    attr {
                        width(132f)
                        height(40f)
                        borderRadius(8f)
                        backgroundColor(Color(if (busy()) 0xFFB7C8FE else 0xFF4176E6))
                        titleAttr {
                            text(if (busy()) "保存中..." else "保存并继续")
                            fontSize(14f)
                            color(Color.WHITE)
                        }
                    }
                    event { click { if (!busy()) onSave() } }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.DshSessionDrawer(
    sessions: () -> ObservableList<DshSession>,
    activeId: () -> String,
    animated: () -> Boolean,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewSession: () -> Unit,
    onSelect: (String) -> Unit,
) {
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            flexDirectionRow()
            backgroundColor(Color(0x00000000))
        }
        View {
            attr {
                width((pagerData.pageViewWidth - 44f).coerceAtMost(340f))
                height(pagerData.pageViewHeight)
                flexDirectionColumn()
                paddingTop(pagerData.statusBarHeight + 10f)
                paddingLeft(14f)
                paddingRight(14f)
                paddingBottom(18f)
                backgroundColor(Color(0xFFF9FAFB))
                transform(Translate(if (animated()) 0f else -1f, 0f))
                animation(Animation.easeOut(0.24f), animated())
            }
            View {
                attr {
                    height(48f)
                    flexDirectionRow()
                    alignItemsCenter()
                }
                Image {
                    attr {
                        src(ImageUri.commonAssets("wordmark.svg"))
                        width(118f)
                        height(28f)
                    }
                }
                View { attr { flex(1f) } }
                View {
                    attr { size(38f, 38f); allCenter() }
                    Image { attr { src(ImageUri.commonAssets("x.svg")); size(22f, 22f) } }
                    event { click { onClose() } }
                }
            }
            View {
                attr {
                    height(42f)
                    marginTop(8f)
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(12f)
                    paddingRight(12f)
                    borderRadius(9f)
                    backgroundColor(Color(0xFFF1F3F5))
                }
                Image { attr { src(ImageUri.commonAssets("plus.svg")); size(20f, 20f) } }
                Text {
                    attr {
                        text("新会话")
                        marginLeft(10f)
                        fontSize(14f)
                        fontWeightMedium()
                        color(Color(0xFF32373C))
                    }
                }
                event { click { onNewSession() } }
            }
            View {
                attr {
                    height(42f)
                    marginTop(8f)
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(12f)
                    paddingRight(12f)
                    borderRadius(9f)
                    backgroundColor(Color(0x00000000))
                }
                Image { attr { src(ImageUri.commonAssets("sliders.svg")); size(20f, 20f) } }
                Text {
                    attr {
                        text("设置")
                        marginLeft(10f)
                        fontSize(14f)
                        fontWeightMedium()
                        color(Color(0xFF555D64))
                    }
                }
                event { click { onOpenSettings() } }
            }
            Text {
                attr {
                    text("会话")
                    marginTop(20f)
                    marginBottom(8f)
                    fontSize(12f)
                    color(Color(0xFF8B9298))
                }
            }
            Scroller {
                attr { flex(1f) }
                vfor({ sessions() }) { session ->
                    View {
                        attr {
                            height(48f)
                            marginBottom(4f)
                            flexDirectionRow()
                            alignItemsCenter()
                            paddingLeft(12f)
                            paddingRight(10f)
                            borderRadius(9f)
                            backgroundColor(Color(
                                if (activeId() == session.id) 0xFFE3E6EA else 0x00FFFFFF,
                            ))
                        }
                        View {
                            attr {
                                size(7f, 7f)
                                borderRadius(4f)
                                backgroundColor(Color(if (session.running) 0xFF4176E6 else 0xFFADB2B8))
                            }
                        }
                        View {
                            attr {
                                flex(1f)
                                marginLeft(10f)
                                flexDirectionColumn()
                                justifyContentCenter()
                            }
                            Text {
                                attr {
                                    text(session.title)
                                    lines(1)
                                    fontSize(14f)
                                    color(Color(0xFF2B3136))
                                }
                            }
                            Text {
                                attr {
                                    text(session.workspace)
                                    lines(1)
                                    marginTop(2f)
                                    fontSize(10f)
                                    color(Color(0xFF969DA3))
                                }
                            }
                        }
                        event { click { onSelect(session.id) } }
                    }
                }
            }
        }
        View {
            attr {
                flex(1f)
                height(pagerData.pageViewHeight)
            }
            event { click { onClose() } }
        }
    }
}

private fun ViewContainer<*, *>.DshModelPicker(
    options: () -> ObservableList<DshModelOption>,
    busy: () -> Boolean,
    error: () -> String,
    onClose: () -> Unit,
    onSelect: (DshModelOption) -> Unit,
) {
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            flexDirectionColumn()
            justifyContentFlexEnd()
            backgroundColor(Color(0x55000000))
        }
        View {
            attr { flex(1f) }
            event { click { onClose() } }
        }
        View {
            attr {
                height((pagerData.pageViewHeight * 0.62f).coerceAtMost(540f))
                flexDirectionColumn()
                padding(18f)
                borderRadius(20f)
                backgroundColor(Color.WHITE)
            }
            View {
                attr { height(40f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text("选择模型")
                        fontSize(18f)
                        fontWeightBold()
                        color(Color(0xFF252B30))
                    }
                }
                View { attr { flex(1f) } }
                View {
                    attr { size(36f, 36f); allCenter() }
                    Image { attr { src(ImageUri.commonAssets("x.svg")); size(21f, 21f) } }
                    event { click { onClose() } }
                }
            }
            vif({ error().isNotEmpty() }) {
                Text {
                    attr {
                        text(error())
                        marginTop(6f)
                        marginBottom(6f)
                        fontSize(12f)
                        color(Color(0xFFBF3535))
                    }
                }
            }
            vif({ busy() && options().isEmpty() }) {
                Text {
                    attr {
                        text("正在加载模型...")
                        marginTop(24f)
                        fontSize(14f)
                        color(Color(0xFF7D858C))
                    }
                }
            }
            Scroller {
                attr { flex(1f); marginTop(8f) }
                vfor({ options() }) { option ->
                    View {
                        attr {
                            minHeight(58f)
                            marginBottom(6f)
                            flexDirectionRow()
                            alignItemsCenter()
                            padding(10f, 12f, 10f, 12f)
                            borderRadius(10f)
                            backgroundColor(Color(if (option.selected) 0xFFF0F3FA else 0xFFF8F8F9))
                        }
                        View {
                            attr { flex(1f); flexDirectionColumn() }
                            Text {
                                attr {
                                    text(option.name)
                                    fontSize(14f)
                                    fontWeightMedium()
                                    color(Color(0xFF2C3237))
                                }
                            }
                            Text {
                                attr {
                                    text(option.providerName + if (option.description.isEmpty()) "" else " · ${option.description}")
                                    marginTop(3f)
                                    lines(1)
                                    fontSize(11f)
                                    color(Color(0xFF8B939A))
                                }
                            }
                        }
                        if (option.selected) {
                            Text { attr { text("✓"); fontSize(17f); color(Color(0xFF4176E6)) } }
                        }
                        event { click { if (!busy()) onSelect(option) } }
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.DshTopBar(
    title: () -> String,
    connection: () -> String,
    dotPhase: () -> Int,
) {
    View {
        attr {
            height(58f)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(12f)
            paddingRight(14f)
            backgroundColor(Color.WHITE)
            borderBottom(Border(1f, BorderStyle.SOLID, Color(0xFFEBEEF2)))
        }
        View {
            attr { size(38f, 38f); allCenter() }
            Image {
                attr {
                    src(ImageUri.commonAssets("menu.svg"))
                    size(26f, 26f)
                }
            }
        }
        Text {
            attr {
                text(title())
                marginLeft(10f)
                maxWidth(pagerData.pageViewWidth - 142f)
                fontSize(17f)
                fontWeightMedium()
                color(Color(0xFF0F1115))
                lines(1)
            }
        }
        View { attr { flex(1f) } }
        View {
            attr {
                size(36f, 36f)
                borderRadius(18f)
                backgroundColor(Color(if (isConnectionReadyLabel(connection())) 0xFFEAF8F0 else 0xFFF1F4F8))
                allCenter()
            }
            vif({ isConnectionReadyLabel(connection()) }) {
                View {
                    attr {
                        size(12f, 12f)
                        borderRadius(6f)
                        backgroundColor(Color(0xFF2EAF67))
                    }
                }
            }
            velse {
                View {
                    attr {
                        width(22f)
                        height(18f)
                        flexDirectionRow()
                        alignItemsCenter()
                        justifyContentCenter()
                    }
                    repeat(CONNECTION_DOT_COUNT) { index ->
                        Text {
                            attr {
                                width(7f)
                                text("•")
                                fontSize(14f)
                                lineHeight(18f)
                                color(Color(0xFF64748B))
                                opacity(if (dotPhase() == index) 1f else 0.3f)
                                transform(Translate(0f, if (dotPhase() == index) -3f else 0f))
                                animation(Animation.easeInOut(CONNECTION_DOT_ANIMATION_S), dotPhase() == index)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.DshSessionRail(
    sessions: () -> ObservableList<DshSession>,
    activeId: () -> String,
    compact: Boolean,
    onSelect: (String) -> Unit,
) {
    View {
        attr {
            if (compact) {
                height(92f)
                flexDirectionRow()
            } else {
                width(236f)
                flexDirectionColumn()
            }
            backgroundColor(Color(0xFFF7F7F8))
            padding(14f)
        }
        Text {
            attr {
                text("会话")
                fontSize(13f)
                color(Color(0xFF6F7378))
                marginBottom(9f)
            }
        }
        if (compact) {
            Scroller {
                attr {
                    flex(1f)
                    flexDirectionRow()
                }
                vfor({ sessions() }) { session ->
                    DshSessionButton(session, activeId() == session.id, onSelect)
                }
            }
        } else {
            Scroller {
                attr { flex(1f) }
                vfor({ sessions() }) { session ->
                    DshSessionButton(session, activeId() == session.id, onSelect)
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.DshSessionButton(
    session: DshSession,
    active: Boolean,
    onSelect: (String) -> Unit,
) {
    Button {
        attr {
            height(48f)
            width(if (active) 220f else 220f)
            marginBottom(4f)
            borderRadius(7f)
            backgroundColor(Color(if (active) 0xFFE4EDFD else 0x00000000))
            titleAttr {
                text(session.title)
                color(Color(if (active) 0xFF4176E6 else 0xFF3E4247))
                fontSize(13f)
            }
        }
        event { click { onSelect(session.id) } }
    }
}

private fun ViewContainer<*, *>.DshConversation(
    conversationIds: () -> ObservableList<String>,
    eagerConversationIds: () -> ObservableList<String>,
    activeConversationId: () -> String,
    messagesForSession: (String) -> ObservableList<DshMessage>,
    streaming: () -> Boolean,
    streamingContent: () -> String,
    scrollerRef: (String, ViewRef<ListView<*, *>>) -> Unit,
    draft: () -> String,
    keyboardHeight: () -> Float,
    stopButtonVisible: () -> Boolean,
    keyboardAnimation: () -> Animation,
    inputRef: (com.tencent.kuikly.core.base.ViewRef<InputView>) -> Unit,
    onInputFocusChange: (Boolean) -> Unit,
    onDraftChange: (String) -> Unit,
    onKeyboardHeightChange: (KeyboardParams) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onDismissKeyboard: () -> Unit,
    modelLabel: () -> String,
    attachmentMenuVisible: () -> Boolean,
    voiceActive: () -> Boolean,
    onOpenModels: () -> Unit,
    onToggleAttachments: () -> Unit,
    onToggleVoice: () -> Unit,
) {
    View {
        attr {
            flex(1f)
            width(pagerData.pageViewWidth)
            flexDirectionColumn()
            backgroundColor(Color.WHITE)
        }
        View {
            attr {
                height(48f)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(16f)
                paddingRight(16f)
                backgroundColor(Color(0xFFF9FAFB))
                borderBottom(Border(1f, BorderStyle.SOLID, Color(0xFFEBEEF2)))
            }
            View {
                attr {
                    size(30f, 30f)
                    borderRadius(15f)
                    allCenter()
                    backgroundColor(Color(0xFFE9ECE8))
                }
                Image {
                    attr {
                        src(ImageUri.commonAssets("fish.svg"))
                        size(22f, 22f)
                    }
                }
            }
            Text {
                attr {
                    text("DeepSeek")
                    marginLeft(10f)
                    fontSize(15f)
                    color(Color(0xFF1E2A32))
                    fontWeightBold()
                }
            }
            View { attr { flex(1f) } }
            Text {
                attr {
                    text(if (streaming()) "生成中" else "在线")
                    fontSize(11f)
                    color(Color(0xFF8B99A3))
                }
            }
            event { click { onDismissKeyboard() } }
        }
        View {
            attr {
                flex(1f)
                flexDirectionColumn()
                transform(Translate(0f, offsetY = -keyboardHeight()))
                animation(keyboardAnimation(), keyboardHeight())
            }
            View {
                attr {
                flex(1f)
                width(pagerData.pageViewWidth)
                backgroundColor(Color.WHITE)
            }
            vfor({ conversationIds() }) { sessionId ->
                List {
                    val listView = this
                    ref { scrollerRef(sessionId, it) }
                    attr {
                        absolutePositionAllZero()
                        width(pagerData.pageViewWidth)
                        padding(16f, 18f, 20f, 18f)
                        firstContentLoadMaxIndex(CHAT_INITIAL_RENDER_COUNT)
                        preloadViewDistance(pagerData.pageViewHeight)
                        // Keep cached conversation lists mounted so the first
                        // switch only changes opacity and z-order instead of
                        // creating a native ListView/Markdown tree on demand.
                        visibility(true)
                        opacity(if (activeConversationId() == sessionId) 1f else 0f)
                        touchEnable(activeConversationId() == sessionId)
                        zIndex(if (activeConversationId() == sessionId) 1 else 0)
                    }
                    event {
                        click { onDismissKeyboard() }
                        dragBegin { onDismissKeyboard() }
                        register("touchDown", { onDismissKeyboard() })
                    }
                    vif({ eagerConversationIds().contains(sessionId) }) {
                        vfor({ messagesForSession(sessionId) }) { message ->
                            View {
                                attr {
                                    width((pagerData.pageViewWidth - 36f).coerceAtLeast(0f))
                                }
                                DshMessageRow(message, streaming() && activeConversationId() == sessionId)
                            }
                        }
                    }
                    velse {
                        listView.vforLazy(
                            { messagesForSession(sessionId) },
                            maxLoadItem = CHAT_MAX_RENDERED_MESSAGES,
                        ) { message, _, _ ->
                            View {
                                attr {
                                    width((pagerData.pageViewWidth - 36f).coerceAtLeast(0f))
                                }
                                DshMessageRow(message, false)
                            }
                        }
                    }
                    vif({ streaming() && activeConversationId() == sessionId }) {
                        View {
                            attr {
                                width((pagerData.pageViewWidth - 36f).coerceAtLeast(0f))
                            }
                            DshStreamingMessageRow(streamingContent)
                        }
                    }
                }
            }
        }
            View {
                attr {
                    height(COMPOSER_HEIGHT)
                    width(pagerData.pageViewWidth)
                    flexDirectionColumn()
                    padding(12f, 14f, 12f, 14f)
                    backgroundColor(Color.WHITE)
                    borderRadius(22f)
                    border(Border(1f, BorderStyle.SOLID, Color(0xFFE1E5EE)))
                }
            Input {
                ref { inputRef(it) }
                attr {
                    height(58f)
                    backgroundColor(Color(0x00FFFFFF))
                    fontSize(15f)
                    color(Color(0xFF28323C))
                    placeholder(if (voiceActive()) "正在聆听..." else "请输入您的问题...")
                    placeholderColor(Color(0xFF91A0AA))
                    returnKeyTypeSend()
                    editable(!voiceActive())
                }
                event {
                    inputFocus { onInputFocusChange(true) }
                    textDidChange { onDraftChange(it.text) }
                    keyboardHeightChange { onKeyboardHeightChange(it) }
                    inputBlur {
                        onInputFocusChange(false)
                        onKeyboardHeightChange(KeyboardParams(0f, 0.24f))
                    }
                    inputReturn { onSend() }
                }
            }

            vif({ attachmentMenuVisible() }) {
                View {
                    attr {
                        height(82f)
                        marginBottom(8f)
                        flexDirectionColumn()
                        padding(8f)
                        borderRadius(10f)
                        backgroundColor(Color(0xFFF5F6F7))
                    }
                    View {
                        attr {
                            height(32f)
                            flexDirectionRow()
                            alignItemsCenter()
                            paddingLeft(8f)
                        }
                        Text { attr { text("图片"); fontSize(14f); color(Color(0xFF3B4147)) } }
                        View { attr { flex(1f) } }
                        Text { attr { text("PNG / JPG / WebP / GIF"); fontSize(11f); color(Color(0xFF9098A0)) } }
                    }
                    View {
                        attr {
                            height(32f)
                            flexDirectionRow()
                            alignItemsCenter()
                            paddingLeft(8f)
                        }
                        Text { attr { text("文件"); fontSize(14f); color(Color(0xFF3B4147)) } }
                        View { attr { flex(1f) } }
                        Text { attr { text("选择本地文件"); fontSize(11f); color(Color(0xFF9098A0)) } }
                    }
                }
            }

            View {
                attr {
                    height(48f)
                    flexDirectionRow()
                    alignItemsCenter()
                }
                View {
                    attr {
                        width(184f)
                        height(40f)
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingLeft(12f)
                        paddingRight(9f)
                        borderRadius(20f)
                        border(Border(1f, BorderStyle.SOLID, Color(0xFFCFD3D6)))
                    }
                    Text {
                        attr {
                            text(modelLabel())
                            flex(1f)
                            lines(1)
                            fontSize(14f)
                            color(Color(0xFF31363B))
                        }
                    }
                    Image {
                        attr {
                            src(ImageUri.commonAssets("chevron-down.svg"))
                            size(18f, 18f)
                        }
                    }
                    DshHitButton(onOpenModels)
                }
                View { attr { flex(1f) } }
                View {
                    attr { size(40f, 40f); allCenter() }
                    Image { attr { src(ImageUri.commonAssets("sliders.svg")); size(22f, 22f) } }
                }
                View {
                    attr {
                        size(48f, 48f)
                        marginLeft(6f)
                        borderRadius(24f)
                        allCenter()
                        backgroundColor(Color(
                            when {
                                stopButtonVisible() -> 0xFFE05252
                                voiceActive() -> 0xFF679EFE
                                else -> 0xFF4176E6
                            },
                        ))
                    }
                    vif({ stopButtonVisible() }) {
                        Image {
                            attr {
                                src(ImageUri.commonAssets("square.svg"))
                                size(23f, 23f)
                            }
                        }
                    }
                    velse {
                        Image {
                            attr {
                                src(ImageUri.commonAssets(if (draft().isEmpty()) "mic.svg" else "send.svg"))
                                size(23f, 23f)
                            }
                        }
                    }
                    DshHitButton {
                            when {
                                stopButtonVisible() -> onStop()
                                draft().isNotEmpty() -> onSend()
                                else -> onToggleVoice()
                            }
                    }
                }
            }
            }
        }
    }
}

private fun ViewContainer<*, *>.DshStreamingMessageRow(content: () -> String) {
    View {
        attr {
            width((pagerData.pageViewWidth - 36f).coerceAtMost(620f).coerceAtLeast(0f))
            flexDirectionColumn()
            alignItemsFlexStart()
            marginBottom(18f)
        }
        Text {
            attr {
                text("DeepSeek")
                fontSize(11f)
                color(Color(0xFF84939D))
                marginBottom(5f)
            }
        }
        DshMarkdown {
            attr {
                contentWidth = (pagerData.pageViewWidth - 36f).coerceAtLeast(0f)
                this.content = content().ifEmpty { "正在生成..." }
                streaming = true
                darkMode = false
            }
        }
    }
}

private fun ViewContainer<*, *>.DshMessageRow(message: DshMessage, pageStreaming: Boolean) {
    if (message.hidden) return
    val isUser = message.role == DshMessageRole.USER
    val isError = message.role == DshMessageRole.ERROR
        View {
            attr {
                flexDirectionColumn()
                alignItems(if (isUser) FlexAlign.FLEX_END else FlexAlign.FLEX_START)
                marginBottom(18f)
        }
        Text {
            attr {
                text(when (message.role) {
                    DshMessageRole.USER -> "你"
                    DshMessageRole.TOOL -> message.toolName ?: "工具"
                    DshMessageRole.ERROR -> "错误"
                    DshMessageRole.ASSISTANT -> "DeepSeek"
                })
                fontSize(11f)
                color(Color(if (isError) 0xFFC23B3B else 0xFF84939D))
                marginBottom(5f)
            }
        }
        View {
            attr {
                if (!isUser && !isError) {
                    width((pagerData.pageViewWidth - 36f).coerceAtMost(620f).coerceAtLeast(0f))
                }
                maxWidth(620f)
                padding(if (isUser) 10f else 0f, if (isUser) 14f else 0f, if (isUser) 10f else 0f, if (isUser) 14f else 0f)
                borderRadius(if (isUser) 18f else 0f)
                backgroundColor(Color(
                    when {
                        isUser -> 0xFFEDF3FE
                        isError -> 0xFFFFEEEE
                        else -> 0x00FFFFFF
                    },
                ))
            }
            if (isUser || isError) {
                Text {
                    attr {
                        text(message.content)
                        lines(Int.MAX_VALUE)
                        fontSize(15f)
                        color(Color(if (isUser) 0xFF34415B else 0xFFB53232))
                    }
                }
            } else {
                    DshMarkdown {
                        attr {
                            contentWidth = (pagerData.pageViewWidth - 36f).coerceAtLeast(0f)
                            content = message.content.ifEmpty { "正在生成..." }
                        streaming = message.streaming && pageStreaming
                        darkMode = false
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.DshHitButton(onClick: () -> Unit) {
    View {
        attr {
            absolutePositionAllZero()
            backgroundColor(Color(0x00000000))
        }
        event { click { onClick() } }
    }
}

private fun isConnectionReadyLabel(label: String): Boolean {
    return label.startsWith("已连接")
}

private const val COMPOSER_HEIGHT = 142f
private const val CONNECTION_DOT_COUNT = 3
private const val CONNECTION_DOT_INTERVAL_MS = 260
private const val CONNECTION_DOT_ANIMATION_S = 0.18f
private const val CHAT_INITIAL_RENDER_COUNT = 6
private const val CHAT_MAX_RENDERED_MESSAGES = 16
private const val SESSION_CACHE_WARM_LIMIT = 7
private const val SESSION_CACHE_WARM_INTERVAL_MS = 16
private const val SESSION_CACHE_WARM_START_DELAY_MS = 600
private const val CONVERSATION_PANEL_CACHE_LIMIT = 8
private const val SCROLL_SETTLE_ATTEMPTS = 6
private val SCROLL_SETTLE_DELAYS_MS = intArrayOf(0, 16, 32, 64, 120, 200)
