package com.example.dsh.ui.home

import com.example.dsh.base.setTimeout
import com.example.dsh.message.isRemoteCatalogInvalidationEvent
import com.example.dsh.message.parseGoalProjection
import com.example.dsh.connection.DshEngineModule
import com.example.dsh.connection.DshRelayModule
import com.example.dsh.connection.DshRelayPhase
import com.example.dsh.connection.DshSseModule
import com.example.dsh.connection.DshSshConfig
import com.example.dsh.connection.DshSshPhase
import com.example.dsh.connection.supportsRelayBridge
import com.example.dsh.host.DshConnectionMode
import com.example.dsh.host.DshHostRepository
import com.example.dsh.host.DshRemoteHostRepository
import com.example.dsh.host.DshHostRuntimePhase
import com.example.dsh.host.DshHostRuntimeState
import com.example.dsh.attachment.DshImageLimits
import com.example.dsh.message.DshMessage
import com.example.dsh.message.DshMessageRole
import com.example.dsh.session.DshRemoteProfile
import com.example.dsh.host.DshRemoteRepository
import com.example.dsh.session.DshSessionCacheState
import com.example.dsh.session.DshSessionCatalog
import com.example.dsh.session.DshSessionCatalogLoader
import com.example.dsh.log.DshStreamLog
import com.example.dsh.log.LogLevel
import com.example.dsh.base.bridgeModule
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.example.dsh.base.setTimeout
import kotlinx.coroutines.cancel
import com.example.dsh.connection.dshConnectionModeLabel
import com.example.dsh.connection.dshReconnectLabel
import com.example.dsh.connection.dshSyncBusyLabel
import com.example.dsh.host.DshHostConnection
import com.example.dsh.host.DshWebSocketModule
import com.example.dsh.base.setTimeout
import com.example.dsh.connection.DshSshSettingsValidation
import com.example.dsh.connection.dshValidateSshSettings
import com.tencent.kuikly.core.timer.setTimeout

internal fun DshHomePage.loadRepository(preferredSessionId: String? = null, restoreOnError: Boolean = true) {
    val hostRepository = repository ?: return
    val expectedConnection = activeConnectionId
    val requestGeneration = ++catalogRequestGeneration
    val requestedActiveId = ui.activeSessionId
    fun current() = pageAlive && repository === hostRepository && activeConnectionId == expectedConnection &&
        requestGeneration == catalogRequestGeneration && connectionCoordinator.isActive(ui.connectionMode)
    val onLoaded: (DshSessionCatalog) -> Unit = loaded@{ catalog ->
        if (!current()) return@loaded
        val loaded = catalog.sessions
        val loadedIds = loaded.map { it.id }.toSet()
        ui.sessions.map { it.id }
            .filterNot { loadedIds.contains(it) }
            .forEach {
                sessionMessageStates.remove(it)
                sessionCacheStates.remove(it)
                sessionMessageReady.remove(it)
                ui.conversationPanelIds.remove(it)
            }
        if (isRemoteHost) {
            loaded.forEach { sessionCacheStates[it.id] = DshSessionCacheState.STALE }
        }
        // 会话列表按消息时间（updatedAt = 最新消息时间）从新到旧排序，不按创建时间。
        ui.sessions = loaded.toList()
        reorderSessionsByUpdatedAt()
        refreshVisibleSessions()
        runCatching { localStore?.replaceSessions(activeConnectionId, ui.sessions.toList()) }
        preloadAllSessionMessages()
        connectionLabel = if (loaded.isEmpty()) "已连接 · 无会话" else "已连接 · 正在同步远程历史"
        refreshWorkspaceGroups()
        val selectionChanged = ui.activeSessionId != requestedActiveId
        val preferred = if (selectionChanged) ui.activeSessionId else preferredSessionId
        val nextId = catalog.forReload(preferred, preferBlankHomeOnNextLoad && !selectionChanged)?.id
        preferBlankHomeOnNextLoad = false
        if (nextId != null) {
            if (ui.activeSessionId != nextId) cancelStreamingForSessionSwitch()
            ui.activeSessionId = nextId
            ui.sessionRunning = loaded.firstOrNull { it.id == ui.activeSessionId }?.running == true
            refreshQueueDock()
            refreshJobsPanel()
            refreshPendingInteractions()
            loadModels(ui.activeSessionId)
            loadHistory(ui.activeSessionId, scrollToEndAfterLoad = false)
            if (ui.streaming || ui.stopButtonVisible || ui.sessionRunning) {
                resyncStreamingWithHost(ui.activeSessionId, "session-list")
            }
        } else {
            preferBlankHomeOnNextLoad = false
            cancelStreamingForSessionSwitch()
            ui.activeSessionId = ""
            ui.messages = ObservableList()
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
        if (ui.sessions.isEmpty()) {
            ui.messages.clear()
            ui.messages.add(DshMessage("load-error", DshMessageRole.ERROR, error))
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

internal fun DshHomePage.startConnection() {
    val generation = connectionCoordinator.begin(ui.connectionMode)
    when (ui.connectionMode) {
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

internal fun DshHomePage.loadSshConfig() {
    val profile = runCatching { localStore?.loadRemoteProfile() }.getOrNull()
    ui.sshHost = profile?.host.orEmpty()
    ui.sshUser = profile?.username.orEmpty()
    ui.sshPort = profile?.sshPort?.toString() ?: "22"
    ui.sshDshPort = profile?.remoteDshPort?.toString() ?: "3080"
    ui.sshKeyId = profile?.keyId.orEmpty()
    ui.sshFingerprint = profile?.hostFingerprint.orEmpty()
    ui.sshKeyLabel = if (ui.sshKeyId.isEmpty()) "未导入私钥" else "已导入私钥"
}

internal fun DshHomePage.startRelayEngine(generation: Long) {
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
                if (state.hostId.isNotEmpty()) ui.remoteProfileId = state.hostId
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

internal fun DshHomePage.startSshEngine(generation: Long) {
    if (ui.sshHost.isBlank() || ui.sshUser.isBlank() || ui.sshKeyId.isBlank()) {
        connectionLabel = "请配置 SSH 连接"
        openConnectionSettings()
        return
    }
    val module = acquireModule<DshEngineModule>(DshEngineModule.MODULE_NAME)
    engineModule = module
    connectionLabel = "正在连接 SSH"
    module.startSsh(DshSshConfig(
        host = ui.sshHost,
        port = ui.sshPort.toIntOrNull() ?: 22,
        username = ui.sshUser,
        remoteDshPort = ui.sshDshPort.toIntOrNull() ?: 3080,
        keyId = ui.sshKeyId,
        hostFingerprint = ui.sshFingerprint,
        keyPassphrase = ui.sshKeyPassphrase,
    )) { state ->
        if (!isCurrent(generation, DshConnectionMode.SSH)) return@startSsh
        when (state.phase) {
            DshSshPhase.FINGERPRINT_REQUIRED -> {
                ui.sshFingerprint = state.message
                ui.sshSettingsError = "首次连接需要确认主机指纹：${state.message}"
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
                ui.sshSettingsError = state.message
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

internal fun DshHomePage.connectRemoteEngine(baseUrl: String, token: String = "") {
    resetSessionActions()
    messageForkVersion++
    messageForkBusy = false
    closeArchiveList()
    ui.sessionArchiveVisible = false
    ui.sessionArchiveBusy = false
    ui.sessionArchiveError = ""
    (remoteRepo)?.stop()
    repository = DshRemoteHostRepository(
        network = acquireModule<NetworkModule>(NetworkModule.MODULE_NAME),
        webSocket = acquireModule<DshWebSocketModule>(DshWebSocketModule.MODULE_NAME),
        connection = DshHostConnection(baseUrl, token),
        pagerId = pagerId,
        onState = { state -> handleHostRuntimeState(state) },
        onQueueSnapshot = { sessionId ->
            if (sessionId == ui.activeSessionId) {
                refreshQueueDock()
                refreshPendingInteractions()
            }
        },
        onJobsSnapshot = { sessionId ->
            if (sessionId == ui.activeSessionId) refreshJobsPanel()
        },
        onSessionStatus = { sessionId, running ->
            updateSessionMetadata(sessionId) { it.copy(running = running) }
            if (sessionId == ui.activeSessionId) {
                val wasRunning = ui.sessionRunning
                ui.sessionRunning = running
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
            if (sessionId == ui.activeSessionId) {
                when (key) {
                    "goal" -> ui.goalSnapshot = parseGoalProjection(value)
                    "imageLimits" -> {
                        val limits = runCatching {
                            DshImageLimits.fromJson(
                                com.tencent.kuikly.core.nvi.serialization.json.JSONObject(value),
                            )
                        }.getOrNull()
                        if (limits != null) ui.imageLimits = limits
                    }
                }
            }
        },
        onSessionEvent = { sessionId, event ->
            // HostProtocol records each event with its original type and metadata.
            // UI projection must not duplicate it or mislabel assistant/message as a chunk.
            if (sessionId == ui.activeSessionId) {
                when (event.type) {
                    "turn/start" -> streamingSourceSeq = null
                    "assistant/chunk" -> if (ui.streaming) streamingSourceSeq = event.seq
                    "tool/call" -> showRunningTool(event)
                    "tool/result" -> settleRunningTool(event)
                    "user/message" -> showContextInjection(event)
                    "assistant/message" -> showAssistantBlocks(event)
                }
            }
        },
        onRemoteEvent = { event ->
            if (ui.activeSessionId.isNotEmpty() && isRemoteCatalogInvalidationEvent(event)) {
                loadSkills(ui.activeSessionId)
                loadModels(ui.activeSessionId)
            }
        },
        onPendingInteraction = { sessionId ->
            refreshPendingSessionIds()
            if (sessionId == ui.activeSessionId) {
                refreshPendingInteractions()
                loadWebTimeline(sessionId, scrollToEndAfterLoad = true)
            }
        },
    )
    loadRepository(preferredSessionId = ui.activeSessionId)
}

internal fun DshHomePage.handleHostRuntimeState(state: DshHostRuntimeState) {
    if (!connectionCoordinator.isActive(ui.connectionMode)) return
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
    DshStreamLog.log(connLogLevel, connLogType, "$connLogType mode=$ui.connectionMode$connErr", null, null)
    if (ui.archiveListVisible && state.phase in listOf(DshHostRuntimePhase.RECONNECTING, DshHostRuntimePhase.ERROR, DshHostRuntimePhase.STOPPED)) {
        archiveRequestGeneration++
        ui.archiveListLoading = false
        ui.archiveOpeningId = ""
        ui.archiveListError = "连接已中断，请连接后刷新归档列表"
        ui.archivedSessions.clear()
        ui.archiveGroups.clear()
        ui.archiveProjectOptions.clear()
        ui.archiveConfirm = null
        ui.archiveBusy = false
    }
    if (state.phase == DshHostRuntimePhase.READY && wasReconnecting) {
        loadRepository(preferredSessionId = ui.activeSessionId)
    }
    syncTurnStatusTicker()
}

internal fun DshHomePage.connectLocalEngine(apiKey: String) {
    connectionLabel = "本地内核启动中"
    repository = DshHostRepository(
        network = acquireModule<NetworkModule>(NetworkModule.MODULE_NAME),
        sse = acquireModule<DshSseModule>(DshSseModule.MODULE_NAME),
        connection = DshHostConnection(DshHomePage.LOCAL_ENGINE_URL),
        pagerId = pagerId,
    )
    syncLocalCredential(apiKey, 0)
}

internal fun DshHomePage.syncLocalCredential(apiKey: String, attempt: Int) {
    val hostRepository = repository ?: return
    hostRepository.saveDeepSeekApiKey(apiKey, {
        connectionLabel = "已连接"
        loadRepository()
    }, { error ->
        if (attempt < DshHomePage.ENGINE_CONNECT_RETRIES) {
            connectionLabel = "本地内核启动中"
            setTimeout(pagerId, DshHomePage.ENGINE_RETRY_DELAY_MS) {
                syncLocalCredential(apiKey, attempt + 1)
            }
        } else {
            connectionLabel = "内核启动失败"
            ui.messages.clear()
            ui.messages.add(DshMessage(
                "engine-start-error",
                DshMessageRole.ERROR,
                "本地 DeepSeek Harness 内核暂未就绪：$error",
            ))
        }
    })
}

internal fun DshHomePage.saveDeepSeekApiKey() {
    val key = ui.apiKeyDraft.trim()
    when {
        key.isEmpty() -> {
            ui.credentialSetupError = "请输入 API Key 后继续。"
            return
        }
        key.any { it.code !in 0x21..0x7E } -> {
            ui.credentialSetupError = "API Key 格式错误，请检查后重试。"
            return
        }
    }
    ui.credentialSetupBusy = true
    ui.credentialSetupError = ""
    if (sshMode) {
        val hostRepository = repository
        if (hostRepository == null) {
            ui.credentialSetupBusy = false
            ui.credentialSetupError = "远程 DSH 尚未就绪"
            return
        }
        hostRepository.saveDeepSeekApiKey(key, {
            postToUi {
                ui.apiKeyDraft = ""
                apiKeyInputView?.setText("")
                ui.credentialSetupBusy = false
                updateCredentialSetupVisibility(false)
                dismissKeyboard()
                connectionLabel = "远程 DSH 已更新"
                loadRepository()
            }
        }, { error ->
            postToUi {
                ui.credentialSetupBusy = false
                ui.credentialSetupError = "无法修改电脑端 DSH：$error"
            }
        })
        return
    }
    val saved = runCatching { localStore?.saveApiKey(key) }
    if (saved.isFailure || localStore == null) {
        ui.credentialSetupBusy = false
        ui.credentialSetupError = saved.exceptionOrNull()?.message ?: "本地数据库不可用"
        return
    }
    ui.apiKeyDraft = ""
    apiKeyInputView?.setText("")
    ui.credentialSetupBusy = false
    ui.credentialSetupError = ""
    updateCredentialSetupVisibility(false)
    dismissKeyboard()
    pendingApiKey = key
    if (engineReady) {
        connectLocalEngine(key)
    } else {
        connectionLabel = "等待本地内核启动"
    }
}

internal fun DshHomePage.connectionModeLabel(): String = dshConnectionModeLabel(ui.connectionMode)

internal fun DshHomePage.disconnectFromHost() {
    DshStreamLog.log(LogLevel.INFO, "connect.disconnect", "connect.disconnect by user", null, null)
    closeSettingsPage()
    stopCurrentEngine()
    bridgeModule.toast("已断开连接")
}

internal fun DshHomePage.openConnectionSettings(preserveError: Boolean = false) {
    dismissKeyboard()
    ui.commandSheetVisible = false
    if (!preserveError) ui.sshSettingsError = ""
    updateSshSettingsVisibility(true)
}

internal fun DshHomePage.updateSshSettingsVisibility(visible: Boolean) {
    ui.sshSettingsVisible = visible
    if (pageData.isAndroid || pageData.isIOS) {
        bridgeModule.setSystemBarsDimmed(visible)
    }
}

internal fun DshHomePage.setConnectionMode(useSsh: Boolean) {
    ui.connectionMode = if (useSsh) DshConnectionMode.SSH else DshConnectionMode.RELAY
    ui.sshSettingsError = ""
}

internal fun DshHomePage.pickSshKey() {
    bridgeModule.pickSshKey { uri ->
        if (uri.isEmpty()) return@pickSshKey
        ui.sshSettingsBusy = true
        bridgeModule.importSshKey(uri) { keyId ->
            postToUi {
                ui.sshSettingsBusy = false
                if (keyId.isEmpty()) {
                    ui.sshSettingsError = "无法导入 SSH 私钥"
                } else {
                    ui.sshKeyId = keyId
                    ui.sshKeyLabel = "已导入私钥"
                    ui.sshSettingsError = ""
                }
            }
        }
    }
}

internal fun DshHomePage.trustSshFingerprint() {
    if (ui.sshFingerprint.isBlank()) return
    acquireModule<DshEngineModule>(DshEngineModule.MODULE_NAME).trustSshFingerprint(ui.sshFingerprint)
    runCatching {
        localStore?.saveRemoteProfile(DshRemoteProfile(
            host = ui.sshHost.trim(),
            sshPort = ui.sshPort.toIntOrNull() ?: 22,
            username = ui.sshUser.trim(),
            remoteDshPort = ui.sshDshPort.toIntOrNull() ?: 3080,
            keyId = ui.sshKeyId,
            hostFingerprint = ui.sshFingerprint,
        ))
    }
    ui.sshSettingsError = "正在使用已确认的主机指纹连接"
}

internal fun DshHomePage.saveConnectionSettings() {
    if (sshMode) {
        when (val validation = dshValidateSshSettings(ui.sshHost, ui.sshUser, ui.sshPort, ui.sshDshPort, ui.sshKeyId)) {
            is DshSshSettingsValidation.Invalid -> ui.sshSettingsError = validation.message
            is DshSshSettingsValidation.Valid -> {
                runCatching { localStore?.saveRemoteProfile(DshRemoteProfile(
                    host = ui.sshHost.trim(),
                    sshPort = validation.sshPort,
                    username = ui.sshUser.trim(),
                    remoteDshPort = validation.dshPort,
                    keyId = ui.sshKeyId,
                    hostFingerprint = ui.sshFingerprint,
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

internal fun DshHomePage.stopCurrentEngine() {
    resetSessionActions()
    cancelReadableExport()
    pluginRequestVersion++
    ui.pluginInventoryLoading = false
    pluginInventory = emptyList(); ui.pluginRows.clear(); ui.pluginTotal = 0
    pluginSearchInput = ""; ui.pluginSearchHasText = false; ui.pluginKeyword = ""
    ui.pluginExpandedId = ""; ui.pluginActionTarget = null; ui.pluginConfirmAction = ""; ui.pluginBusyId = ""
    ui.pluginActionError = ""; ui.pluginNotice = ""
    ui.pluginConfigLoading = false; ui.pluginConfigCards.clear(); ui.pluginConfigDrafts = emptyMap()
    ui.pluginConfigSecretDrafts = emptyMap(); ui.pluginConfigCollapsed = emptySet()
    ui.pluginConfigBusyNamespace = ""; ui.pluginConfigCardError = emptyMap(); ui.pluginConfigCardNotice = emptyMap()
    if (ui.pluginInventoryVisible) ui.pluginInventoryError = "连接已断开，请连接 Host 后刷新"
    timelineReadVersion++
    val mode = connectionCoordinator.activeModeOr(ui.connectionMode)
    connectionCoordinator.stop()
    (remoteRepo)?.stop()
    repository = null
    ui.goalSnapshot = null
    ui.goalActionBusy = false
    ui.goalActionError = ""
    streamHandle?.cancel()
    streamHandle = null
    when (mode) {
        DshConnectionMode.RELAY -> acquireModule<DshRelayModule>(DshRelayModule.MODULE_NAME).disconnect()
        DshConnectionMode.SSH -> engineModule?.stopSsh()
        DshConnectionMode.LOCAL -> engineModule?.stop()
    }
    engineReady = false
}

internal fun DshHomePage.isCurrent(generation: Long, mode: DshConnectionMode): Boolean =
    connectionCoordinator.accepts(generation, mode)

internal fun DshHomePage.openConnectionSetup() {
    acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
        "connection_setup",
        JSONObject().apply {
            put("pageName", "connection_setup")
            // 从主页返回连接页是主动改设置/断开，跳过自动连接，避免立刻又被弹回主页。
            put("skipAutoConnect", true)
        },
    )
}

internal fun DshHomePage.reconnectLabel(): String = dshReconnectLabel(ui.connectionMode)

internal fun DshHomePage.syncBusyLabel(): String = dshSyncBusyLabel(ui.connectionMode)
