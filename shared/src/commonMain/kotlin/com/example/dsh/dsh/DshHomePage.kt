package com.example.dsh.dsh

import com.example.dsh.base.BasePager
import com.example.dsh.base.bridgeModule
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.vfor
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
    private var activeSessionId by observable("session-1")
    private var draft by observable("")
    private var streaming by observable(false)
    private var keyboardHeight by observable(0f)
    private var keyboardAnimation by observable(Animation.easeInOut(ANIMATION_DURATION_S))
    private var connectionLabel by observable("本地内核启动中")
    private var apiKeyDraft by observable("")
    private var credentialSetupVisible by observable(false)
    private var credentialSetupBusy by observable(false)
    private var credentialSetupError by observable("")
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

    override fun created() {
        super.created()
        val databaseDir = pageData.params.optString("databaseDir")
        if (databaseDir.isNotEmpty()) {
            localStore = runCatching {
                createDshLocalStore("$databaseDir/dsh.db")
            }.getOrNull()
        }
        val apiKey = localStore?.let { store ->
            runCatching { store.loadApiKey() }.getOrDefault("")
        }.orEmpty()
        pendingApiKey = apiKey
        restoreCachedSessions()
        if (apiKey.isEmpty()) {
            connectionLabel = "等待配置"
            credentialSetupVisible = true
            messages.add(
                DshMessage(
                    id = "api-key-required",
                    role = DshMessageRole.ASSISTANT,
                    content = "输入 DeepSeek API Key 后即可开始使用本地 Agent。",
                ),
            )
        }
        startEmbeddedEngine()
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
                        )
                    }

                    if (wide) {
                        View {
                            attr {
                                flex(1f)
                                flexDirectionRow()
                            }
                            DshConversation(
                                messages = { ctx.messages },
                                streaming = { ctx.streaming },
                                draft = { ctx.draft },
                                keyboardHeight = { ctx.keyboardHeight },
                                inputRef = { ctx.inputView = it.view },
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
                            messages = { ctx.messages },
                            streaming = { ctx.streaming },
                            draft = { ctx.draft },
                            keyboardHeight = { ctx.keyboardHeight },
                            inputRef = { ctx.inputView = it.view },
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
                        onSelect = {
                            ctx.closeSessionDrawer()
                            ctx.selectSession(it)
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
                        busy = { ctx.credentialSetupBusy },
                        error = { ctx.credentialSetupError },
                        inputRef = { ctx.apiKeyInputView = it.view },
                        onApiKeyChange = {
                            ctx.apiKeyDraft = it
                            ctx.credentialSetupError = ""
                        },
                        onSave = { ctx.saveDeepSeekApiKey() },
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
    }

    private fun closeSessionDrawer() {
        if (!sessionDrawerVisible) return
        // Closing removes the mask immediately; only the drawer and page slide back.
        sessionDrawerMaskAnimation = Animation.linear(0f)
        sessionDrawerMaskAnimated = false
        sessionDrawerAnimated = false
        setTimeout(pagerId, ANIMATION_DURATION_MS) {
            sessionDrawerVisible = false
        }
    }

    private fun loadRepository() {
        val hostRepository = repository ?: return
        hostRepository.loadSessions({ loaded ->
            sessions.clear()
            sessions.addAll(loaded)
            runCatching { localStore?.saveSessions(loaded) }
            connectionLabel = if (loaded.isEmpty()) "已连接 · 无会话" else "已连接"
            if (loaded.isNotEmpty()) {
                activeSessionId = loaded.first().id
                loadModels(activeSessionId)
                loadHistory(activeSessionId)
            } else {
                messages.clear()
                messages.add(
                    DshMessage(
                        id = "no-session",
                        role = DshMessageRole.ERROR,
                        content = "Host 中没有可用会话，请创建工作区和新会话。",
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
        credentialSetupVisible = false
        dismissKeyboard()
        pendingApiKey = key
        if (engineReady) {
            connectLocalEngine(key)
        } else {
            connectionLabel = "等待本地内核启动"
        }
    }

    private fun loadHistory(sessionId: String) {
        val hostRepository = repository ?: return
        hostRepository.loadHistory(sessionId, { loaded ->
            messages.clear()
            messages.addAll(loaded)
            runCatching { localStore?.saveMessages(sessionId, loaded) }
        }, { error ->
            val cached = runCatching { localStore?.loadMessages(sessionId).orEmpty() }.getOrDefault(emptyList())
            messages.clear()
            if (cached.isNotEmpty()) {
                messages.addAll(cached)
                connectionLabel = "内核连接失败 · 已显示缓存"
            } else {
                messages.add(DshMessage("history-error", DshMessageRole.ERROR, error))
            }
        })
    }

    private fun restoreCachedSessions() {
        val cached = runCatching { localStore?.loadSessions().orEmpty() }.getOrDefault(emptyList())
        if (cached.isEmpty()) return
        sessions.clear()
        sessions.addAll(cached)
        activeSessionId = cached.first().id
        val cachedMessages = runCatching { localStore?.loadMessages(activeSessionId).orEmpty() }.getOrDefault(emptyList())
        messages.clear()
        messages.addAll(cachedMessages)
    }

    private fun selectSession(id: String) {
        dismissKeyboard()
        if (id == activeSessionId) return
        stopStream()
        activeSessionId = id
        loadModels(id)
        loadHistory(id)
        draft = ""
        inputView?.setText("")
    }

    private fun sendDraft() {
        dismissKeyboard()
        val prompt = draft.trim()
        if (prompt.isEmpty() || streaming || repository == null || sessions.isEmpty()) return
        val sessionId = activeSessionId
        val user = DshMessage("user-${messages.size}", DshMessageRole.USER, prompt)
        val assistantId = "assistant-${messages.size}"
        messages.add(user)
        messages.add(DshMessage(assistantId, DshMessageRole.ASSISTANT, "", streaming = true))
        draft = ""
        inputView?.setText("")
        streaming = true
        connectionLabel = "正在生成"
        streamHandle = repository?.streamReply(
            pagerId = pagerId,
            sessionId = sessionId,
            prompt = prompt,
            onDelta = { delta -> updateAssistant(assistantId, delta, append = true) },
            onComplete = { result ->
                streaming = false
                updateAssistant(assistantId, result, append = false)
                persistMessages(sessionId)
                connectionLabel = "已连接"
                streamHandle = null
            },
            onError = { error ->
                streaming = false
                updateAssistant(assistantId, error, append = false, role = DshMessageRole.ERROR)
                persistMessages(sessionId)
                connectionLabel = "已连接"
                streamHandle = null
            },
        )
    }

    private fun stopStream() {
        dismissKeyboard()
        streamHandle?.cancel()
        streamHandle = null
        if (!streaming) return
        streaming = false
        val last = messages.lastOrNull()
        if (last?.role == DshMessageRole.ASSISTANT && last.streaming) {
            updateAssistant(last.id, "\n\n*已停止*", append = true)
        }
        persistMessages(activeSessionId)
        connectionLabel = "已连接"
    }

    private fun dismissKeyboard() {
        inputView?.blur()
        bridgeModule.closeKeyboard()
        keyboardHeight = 0f
    }

    private fun updateKeyboard(params: KeyboardParams) {
        keyboardAnimation = Animation.easeInOut(ANIMATION_DURATION_S)
        keyboardHeight = params.height.coerceAtLeast(0f)
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

    private fun updateAssistant(
        id: String,
        content: String,
        append: Boolean,
        role: DshMessageRole = DshMessageRole.ASSISTANT,
    ) {
        val current = messages.toList()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return
        val old = current[index]
        val updated = current.toMutableList()
        updated[index] = old.copy(
            role = role,
            content = if (append) old.content + content else content,
            streaming = streaming && role == DshMessageRole.ASSISTANT,
        )
        messages.clear()
        messages.addAll(updated)
    }

    private fun persistMessages(sessionId: String) {
        runCatching { localStore?.saveMessages(sessionId, messages.toList()) }
    }

    companion object {
        private const val BG = 0xFFF7F9FA
        private const val LOCAL_ENGINE_URL = "http://127.0.0.1:3080"
        private const val ENGINE_CONNECT_RETRIES = 60
        private const val ENGINE_RETRY_DELAY_MS = 1_000
        private const val ANIMATION_DURATION_MS = 240
        private const val ANIMATION_DURATION_S = 0.24f
    }
}

private fun ViewContainer<*, *>.DshCredentialSetupModal(
    busy: () -> Boolean,
    error: () -> String,
    inputRef: (ViewRef<InputView>) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
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
            Text {
                attr {
                    text("添加一个 API Key 开始使用")
                    fontSize(20f)
                    fontWeightBold()
                    color(Color(0xFF1F2933))
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
                backgroundColor(Color(0xFFEDF3FE))
                allCenter()
            }
            Text {
                attr {
                    text(if (connection().contains("正在")) "..." else "●")
                    fontSize(13f)
                    color(Color(0xFF4176E6))
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
    messages: () -> ObservableList<DshMessage>,
    streaming: () -> Boolean,
    draft: () -> String,
    keyboardHeight: () -> Float,
    keyboardAnimation: () -> Animation,
    inputRef: (com.tencent.kuikly.core.base.ViewRef<InputView>) -> Unit,
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
        Scroller {
            attr {
                flex(1f)
                padding(16f, 18f, 20f, 18f)
                animation(keyboardAnimation(), keyboardHeight())
            }
            event {
                click { onDismissKeyboard() }
                dragBegin { onDismissKeyboard() }
                register("touchDown", { onDismissKeyboard() })
            }
            vfor({ messages() }) { message ->
                View {
                    DshMessageRow(message, streaming())
                }
            }
        }
        View {
            attr {
                height(142f + keyboardHeight())
                flexDirectionColumn()
                padding(12f, 14f, 12f, 14f)
                backgroundColor(Color.WHITE)
                borderRadius(22f)
                border(Border(1f, BorderStyle.SOLID, Color(0xFFE1E5EE)))
                animation(keyboardAnimation(), keyboardHeight())
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
                    textDidChange { onDraftChange(it.text) }
                    keyboardHeightChange { onKeyboardHeightChange(it) }
                    inputBlur {
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
                    attr { size(40f, 40f); marginLeft(4f); allCenter() }
                    Image { attr { src(ImageUri.commonAssets("plus.svg")); size(25f, 25f) } }
                    DshHitButton(onToggleAttachments)
                }
                View {
                    attr {
                        size(48f, 48f)
                        marginLeft(6f)
                        borderRadius(24f)
                        allCenter()
                        backgroundColor(Color(
                            if (voiceActive()) 0xFF679EFE else 0xFF4176E6,
                        ))
                    }
                    if (streaming()) {
                        Text { attr { text("■"); fontSize(17f); color(Color.WHITE) } }
                    } else {
                        Image {
                            attr {
                                src(ImageUri.commonAssets(if (draft().isEmpty()) "mic.svg" else "send.svg"))
                                size(23f, 23f)
                            }
                        }
                    }
                    DshHitButton {
                            when {
                                streaming() -> onStop()
                                draft().isNotEmpty() -> onSend()
                                else -> onToggleVoice()
                            }
                    }
                }
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
