package com.example.dsh.host

import com.example.dsh.log.DshStreamLog
import com.example.dsh.log.LogLevel
import com.example.dsh.platform.clientTimeZoneId
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.example.dsh.host.DshWebSocketModule
import com.example.dsh.host.dshEncodeQueryComponent
import com.example.dsh.host.DshHostConnection
import com.example.dsh.host.DshHostConnectionRuntime
import com.example.dsh.host.DshHostProtocol
import com.example.dsh.host.dshParseSchemaChoices
import com.example.dsh.host.dshSessionEventLevel
import com.example.dsh.host.dshSessionEventSummary
import com.example.dsh.host.dshShouldLogSessionEvent
import com.example.dsh.host.DshWebTimelineParser
import com.example.dsh.message.isRuntimeContextSnapshot
import com.example.dsh.host.pendingInteractionRpcId
import com.example.dsh.host.textFromBlocks
import com.example.dsh.models.DshAgentPresetOption
import com.example.dsh.models.DshCredentialSetup
import com.example.dsh.session.DshDirectoryEntry
import com.example.dsh.session.DshDirectoryListing
import com.example.dsh.host.DshDownlinkFrame
import com.example.dsh.host.DshEventStream
import com.example.dsh.session.DshGoalSnapshot
import com.example.dsh.session.DshHistoryLoader
import com.example.dsh.host.DshHostRuntimePhase
import com.example.dsh.host.DshHostRuntimeState
import com.example.dsh.host.DshHostStore
import com.example.dsh.attachment.DshImageDraftState
import com.example.dsh.host.dshIsTransportInterrupt
import com.example.dsh.session.DshJobItem
import com.example.dsh.models.dshLoadModelsSettings
import com.example.dsh.message.DshMessage
import com.example.dsh.message.DshMessageFork
import com.example.dsh.message.DshMessageRole
import com.example.dsh.models.DshModelOption
import com.example.dsh.models.DshModelsSettings
import com.example.dsh.interaction.DshPendingApproval
import com.example.dsh.attachment.DshPendingImage
import com.example.dsh.interaction.DshPendingQuestion
import com.example.dsh.interaction.DshPendingQuestionItem
import com.example.dsh.interaction.DshPendingQuestionOption
import com.example.dsh.plugin.DshPluginConfigSave
import com.example.dsh.plugin.DshPluginConfigState
import com.example.dsh.plugin.DshPluginEntry
import com.example.dsh.session.DshQueueItem
import com.example.dsh.host.DshRawSessionEvent
import com.example.dsh.models.DshReasoningEffort
import com.example.dsh.host.DshRpcError
import com.example.dsh.session.DshSession
import com.example.dsh.session.DshSessionModels
import com.example.dsh.models.DshSettingsChoice
import com.example.dsh.models.DshSettingsSnapshot
import com.example.dsh.session.DshSkill
import com.example.dsh.host.DshStreamHandle
import com.example.dsh.attachment.DshUploadedFile
import com.example.dsh.message.DshWebTimelineItem
import com.example.dsh.session.DshWorkspaceGroup
import com.example.dsh.plugin.parseDshPluginConfig
import com.example.dsh.plugin.parseDshPluginInventory
import com.example.dsh.session.DshSessionCatalog
import com.example.dsh.session.DshSessionCatalogLoader
import com.tencent.kuikly.core.timer.setTimeout

internal class DshRemoteHostRepository(
    network: NetworkModule,
    webSocket: DshWebSocketModule,
    private val connection: DshHostConnection,
    private val pagerId: String,
    onState: (DshHostRuntimeState) -> Unit = {},
    onQueueSnapshot: (String) -> Unit = {},
    onJobsSnapshot: (String) -> Unit = {},
    onSessionStatus: (String, Boolean) -> Unit = { _, _ -> },
    onProjection: (String, String, String, Int) -> Unit = { _, _, _, _ -> },
    onSessionEvent: (String, DshRawSessionEvent) -> Unit = { _, _ -> },
    onRemoteEvent: (String) -> Unit = {},
    onPendingInteraction: (String) -> Unit = {},
) : DshRemoteRepository {
    internal val store = DshHostStore()

    override val archivedSessionIds: Set<String> get() = store.archivedSessionIds

    override fun removeSession(sessionId: String) {
        store.sessions.remove(sessionId)
    }

    override fun sessionCatalog(sessions: List<DshSession>): DshSessionCatalog =
        DshSessionCatalog(sessions, store.archivedSessionIds, store.workspaceBaseline)
    private val onQueueSnapshotHandler = onQueueSnapshot
    private val onJobsSnapshotHandler = onJobsSnapshot
    private val onSessionStatusHandler = onSessionStatus
    private val onProjectionHandler = onProjection
    private val onSessionEventHandler = onSessionEvent
    private val onRemoteEventHandler = onRemoteEvent
    private val onPendingInteractionHandler = onPendingInteraction
    private val runtime = DshHostConnectionRuntime(
        network = network,
        webSocket = webSocket,
        connection = connection,
        pagerId = pagerId,
        onFrame = ::handleFrame,
        onState = onState,
        onWorkspaceBaseline = { value ->
            val archived = value.optJSONArray("archivedSessionIds")
            val archivedIds = buildSet {
                if (archived != null) for (index in 0 until archived.length()) {
                    archived.optString(index)?.takeIf { it.isNotEmpty() }?.let(::add)
                }
            }
            store.replaceWorkspaceBaseline(value.optJSONArray("items")?.toString() ?: "[]", archivedIds)
        },
        onSessionBaseline = { value -> store.replaceSessions(parseSessions(value)) },
        onQueueSnapshot = onQueueSnapshot,
        onJobsSnapshot = onJobsSnapshot,
        onSessionStatus = onSessionStatus,
        onProjection = onProjectionHandler,
        onSessionEvent = onSessionEventHandler,
        onRemoteEvent = onRemoteEvent,
    )
    private val activeStreams = mutableMapOf<String, ActiveStream>()
    private val sessionCatalogLoader = DshSessionCatalogLoader(
        request = { method, callback -> call(method, JSONObject(), callback) },
        parseSessions = ::parseSessions,
        commit = { catalog ->
            store.replaceWorkspaceBaseline(catalog.workspaceJson, catalog.archivedIds)
            store.replaceSessions(catalog.sessions)
        },
        connectionGeneration = { runtime.currentState().generation },
    )

    private data class ActiveStream(
        val sessionId: String,
        val promptRpcId: String,
        val onDelta: (String, Boolean) -> Unit,
        val onComplete: (String) -> Unit,
        val onError: (String) -> Unit,
        var observed: Boolean = true,
        val accumulated: StringBuilder = StringBuilder(),
        var finalMessage: String = "",
        var failure: String = "",
    )

    fun currentConnectionState(): DshHostRuntimeState = runtime.currentState()
    override fun loadPluginInventory(onSuccess: (List<DshPluginEntry>) -> Unit, onError: (String) -> Unit) {
        runtime.loadPluginInventory { value, error ->
            if (error != null || value == null) onError(error?.message ?: "插件清单为空响应")
            else runCatching { parseDshPluginInventory(value) }.onSuccess(onSuccess)
                .onFailure { onError(it.message ?: "插件清单解析失败") }
        }
    }
    override fun pluginAction(entryId: String, action: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        runtime.pluginAction(entryId, action) { _, error ->
            if (error != null) onError(error.message) else onSuccess()
        }
    }
    override fun isProductReady(): Boolean = runtime.currentState().phase == DshHostRuntimePhase.READY
    override fun stop() {
        sessionCatalogLoader.invalidate()
        runtime.stop()
    }

    override fun loadSessionCatalog(onSuccess: (DshSessionCatalog) -> Unit, onError: (DshRpcError) -> Unit) =
        sessionCatalogLoader.load(onSuccess, onError)

    override fun respondApproval(
        rpcId: String,
        sessionId: String,
        approvalId: String,
        outcome: String,
        callback: (Boolean, String) -> Unit,
    ) {
        if (outcome != "allowed-once" && outcome != "rejected") {
            callback(false, "非法审批结果")
            return
        }
        runtime.respond(rpcId, JSONObject().apply {
            put("sessionId", sessionId)
            put("approvalId", approvalId)
            put("outcome", outcome)
        }, callback)
    }

    override fun respondQuestion(
        rpcId: String,
        sessionId: String,
        answer: JSONObject,
        callback: (Boolean, String) -> Unit,
    ) {
        runtime.respond(rpcId, JSONObject().apply {
            put("sessionId", sessionId)
            put("answer", answer)
        }, callback)
    }

    /**
     * 用户主动关闭提问流程：以 ok=false + code=cancelled 拒绝整个等待，
     * 与原版 apiproxy respond 的 cancelled 分支一致（对应 wire 上 ASK_CANCELLED）。
     */
    override fun respondQuestionCancel(
        rpcId: String,
        sessionId: String,
        callback: (Boolean, String) -> Unit,
    ) {
        runtime.respond(
            rpcId,
            JSONObject().apply { put("sessionId", sessionId) },
            callback,
            ok = false,
            errorCode = "cancelled",
            errorMessage = "the user closed this question request",
        )
    }

    override fun clearPending(rpcId: String) {
        store.removePending(rpcId)
    }

    override fun loadCredentialSetup(onSuccess: (DshCredentialSetup) -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.LLM_PROVIDERS, JSONObject()) { providersValue, providersError ->
            if (providersError != null || providersValue == null) {
                onError(providersError?.message ?: "llm.providers 返回为空")
                return@call
            }
            val providers = providersValue.optJSONArray("providers") ?: JSONArray()
            val active = (0 until providers.length()).any { index ->
                val provider = providers.optJSONObject(index) ?: return@any false
                provider.optString("provider") == DEEPSEEK_PROVIDER &&
                    provider.optString("settingsNs") == DEEPSEEK_SETTINGS_NS && provider.optBoolean("active")
            }
            if (!active) {
                onSuccess(DshCredentialSetup(false, false, false))
                return@call
            }
            call(DshHostProtocol.SETTINGS_DESCRIBE, JSONObject()) { settingsValue, settingsError ->
                if (settingsError != null || settingsValue == null) {
                    onError(settingsError?.message ?: "settings.describe 返回为空")
                    return@call
                }
                var credentialRef = DEEPSEEK_CREDENTIAL_REF
                var namespaceFound = false
                val namespaces = settingsValue.optJSONArray("namespaces") ?: JSONArray()
                for (index in 0 until namespaces.length()) {
                    val namespace = namespaces.optJSONObject(index) ?: continue
                    if (namespace.optString("ns") != DEEPSEEK_SETTINGS_NS) continue
                    namespaceFound = true
                    credentialRef = namespace.optJSONObject("value")?.optString("apiKeyEnv")
                        ?.takeIf { it.isNotEmpty() } ?: credentialRef
                    break
                }
                call(DshHostProtocol.CREDENTIALS_DESCRIBE, JSONObject().apply {
                    put("refs", JSONArray().apply { put(credentialRef) })
                }) { credentialsValue, credentialsError ->
                    if (credentialsError != null || credentialsValue == null) {
                        onError(credentialsError?.message ?: "credentials.describe 返回为空")
                        return@call
                    }
                    val credential = credentialsValue.optJSONObject("credentials")?.optJSONObject(credentialRef)
                    onSuccess(DshCredentialSetup(
                        true,
                        credential?.optBoolean("configured") == true,
                        settingsValue.optBoolean("writable") && namespaceFound && credential?.optBoolean("writable") == true,
                        credentialRef,
                    ))
                }
            }
        }
    }

    override fun saveDeepSeekApiKey(apiKey: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.CREDENTIALS_SET, JSONObject().apply {
            put("ref", DEEPSEEK_CREDENTIAL_REF)
            put("value", apiKey)
        }) { _, error -> if (error == null) onSuccess() else onError(error.message) }
    }

    override fun loadModelsSettings(onSuccess: (DshModelsSettings) -> Unit, onError: (String) -> Unit) {
        dshLoadModelsSettings(
            call = { method, payload, callback ->
                call(method, payload) { value, error -> callback(value, error?.message) }
            },
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    override fun mutateSetting(ns: String, ops: JSONArray, expectedRevision: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.SETTINGS_MUTATE, JSONObject().apply {
            put("ns", ns)
            put("ops", ops)
            if (expectedRevision > 0) put("expectedRevision", expectedRevision)
        }) { _, error -> if (error == null) onSuccess() else onError(error.message) }
    }

    override fun setCredential(ref: String, value: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.CREDENTIALS_SET, JSONObject().apply {
            put("ref", ref)
            put("value", value)
        }) { _, error -> if (error == null) onSuccess() else onError(error.message) }
    }

    override fun unsetCredential(ref: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.CREDENTIALS_UNSET, JSONObject().apply {
            put("ref", ref)
        }) { _, error -> if (error == null) onSuccess() else onError(error.message) }
    }

    override fun loadAgentPresets(onSuccess: (List<DshAgentPresetOption>) -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.AGENT_PRESET_LIST, JSONObject()) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "agentPreset.list 返回为空")
                return@call
            }
            val presets = value.optJSONArray("presets") ?: JSONArray()
            val result = mutableListOf<DshAgentPresetOption>()
            for (index in 0 until presets.length()) {
                val preset = presets.optJSONObject(index) ?: continue
                val id = preset.optString("id")
                if (id.isEmpty()) continue
                result += DshAgentPresetOption(
                    id,
                    preset.optString("name").ifEmpty { id },
                    preset.optString("description"),
                    preset.optBoolean("isDefault"),
                )
            }
            onSuccess(result)
        }
    }

    override fun loadHostVersion(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.HOST_DESCRIBE, JSONObject()) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "host.describe 返回为空")
                return@call
            }
            onSuccess(value.optString("version").ifEmpty { "未知版本" })
        }
    }

    override fun describeSettings(onSuccess: (DshSettingsSnapshot) -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.SETTINGS_DESCRIBE, JSONObject()) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "settings.describe 返回为空")
                return@call
            }
            var writable = value.optBoolean("writable")
            var permissionPreset = ""
            var permissionChoices = emptyList<DshSettingsChoice>()
            var permissionRevision = 0
            var localeValue = ""
            var localeRevision = 0
            var themeValue = ""
            var themeRevision = 0
            var defaultModelProvider = ""
            var defaultModelLabel = ""
            var defaultModelRevision = 0
            val namespaces = value.optJSONArray("namespaces") ?: JSONArray()
            for (index in 0 until namespaces.length()) {
                val namespace = namespaces.optJSONObject(index) ?: continue
                val ns = namespace.optString("ns")
                val nsValue = namespace.optJSONObject("value") ?: JSONObject()
                when (ns) {
                    "permission" -> {
                        permissionPreset = nsValue.optString("defaultPreset")
                        permissionRevision = namespace.optInt("revision")
                        permissionChoices = dshParseSchemaChoices(namespace.optJSONObject("schema"), "defaultPreset")
                    }
                    "locale" -> {
                        localeValue = nsValue.optString("preference")
                        localeRevision = namespace.optInt("revision")
                    }
                    "ui-theme" -> {
                        themeValue = nsValue.optString("preference")
                        themeRevision = namespace.optInt("revision")
                    }
                    "agent-default-model" -> {
                        defaultModelProvider = nsValue.optString("provider")
                        val providerName = nsValue.optString("providerName").ifEmpty { defaultModelProvider }
                        val model = nsValue.optString("model")
                        defaultModelLabel = if (model.isEmpty()) {
                            "未设置"
                        } else {
                            val effort = nsValue.optString("reasoningEffort").takeIf { it.isNotEmpty() }
                            if (effort == null) "$providerName · $model" else "$providerName · $model · $effort"
                        }
                        defaultModelRevision = namespace.optInt("revision")
                    }
                }
            }
            onSuccess(DshSettingsSnapshot(
                writable = writable,
                permissionPreset = permissionPreset,
                permissionChoices = permissionChoices,
                permissionRevision = permissionRevision,
                localeValue = localeValue,
                localeRevision = localeRevision,
                themeValue = themeValue,
                themeRevision = themeRevision,
                defaultModelProvider = defaultModelProvider,
                defaultModelLabel = defaultModelLabel,
                defaultModelRevision = defaultModelRevision,
            ))
        }
    }

    override fun updateSetting(ns: String, patch: JSONObject, expectedRevision: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.SETTINGS_UPDATE, JSONObject().apply {
            put("ns", ns)
            put("patch", patch)
            if (expectedRevision > 0) put("expectedRevision", expectedRevision)
        }) { _, error -> if (error == null) onSuccess() else onError(error.message) }
    }

    override fun loadPluginConfig(onSuccess: (DshPluginConfigState) -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.SETTINGS_DESCRIBE, JSONObject()) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "settings.describe 返回为空")
                return@call
            }
            runCatching { parseDshPluginConfig(value) }
                .onSuccess(onSuccess)
                .onFailure { onError(it.message ?: "插件配置解析失败") }
        }
    }

    override fun savePluginConfig(save: DshPluginConfigSave, onSuccess: () -> Unit, onError: (String) -> Unit) {
        fun writeSettings() {
            if (save.ops.length() == 0) {
                onSuccess()
                return
            }
            mutateSetting(save.namespace, save.ops, save.expectedRevision, onSuccess, onError)
        }
        if (save.credentialRef.isNotEmpty() && save.credentialValue.isNotEmpty()) {
            setCredential(save.credentialRef, save.credentialValue, { writeSettings() }, onError)
        } else {
            writeSettings()
        }
    }

    override fun loadModels(sessionId: String, onSuccess: (DshSessionModels) -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.SESSION_MODELS, JSONObject().apply { put("sessionId", sessionId) }) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "session.models 返回为空")
                return@call
            }
            val current = value.optJSONObject("current") ?: JSONObject()
            val currentProvider = current.optString("provider")
            val currentModel = current.optString("model")
            val currentEffort = current.optString("reasoningEffort").takeIf { it.isNotEmpty() }
            val options = mutableListOf<DshModelOption>()
            val groups = value.optJSONArray("groups") ?: JSONArray()
            for (groupIndex in 0 until groups.length()) {
                val group = groups.optJSONObject(groupIndex) ?: continue
                val provider = group.optString("id")
                val providerName = group.optString("name").ifEmpty { provider }
                val models = group.optJSONArray("models") ?: JSONArray()
                for (modelIndex in 0 until models.length()) {
                    val model = models.optJSONObject(modelIndex) ?: continue
                    val id = model.optString("id")
                    if (provider.isEmpty() || id.isEmpty()) continue
                    val reasoning = model.optJSONObject("reasoning")
                    val efforts = mutableListOf<DshReasoningEffort>()
                    reasoning?.optJSONArray("efforts")?.let { array ->
                        for (ei in 0 until array.length()) {
                            val e = array.optJSONObject(ei) ?: continue
                            val eid = e.optString("id")
                            if (eid.isEmpty()) continue
                            efforts += DshReasoningEffort(eid, e.optString("name").ifEmpty { eid }, e.optString("description"))
                        }
                    }
                    val effort = reasoning?.optString("defaultEffort")?.takeIf { it.isNotEmpty() }
                    options += DshModelOption(
                        provider, providerName, id, model.optString("name").ifEmpty { id }, model.optString("description"),
                        if (provider == currentProvider && id == currentModel) currentEffort ?: effort else effort,
                        efforts,
                        provider == currentProvider && id == currentModel,
                    )
                }
            }
            val selected = options.firstOrNull { it.selected } ?: DshModelOption(
                currentProvider, currentProvider, currentModel, currentModel.ifEmpty { "选择模型" }, reasoningEffort = currentEffort, selected = true,
            )
            onSuccess(DshSessionModels(selected, options, value.optBoolean("routable")))
        }
    }

    override fun selectModel(sessionId: String, option: DshModelOption, onSuccess: (DshModelOption) -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.SESSION_SELECT_MODEL, JSONObject().apply {
            put("sessionId", sessionId); put("provider", option.provider); put("model", option.model)
            option.reasoningEffort?.let { put("reasoningEffort", it) }
        }) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "session.selectModel 返回为空")
                return@call
            }
            val selected = value.optJSONObject("selected") ?: JSONObject()
            onSuccess(option.copy(
                provider = selected.optString("provider").ifEmpty { option.provider },
                model = selected.optString("model").ifEmpty { option.model },
                reasoningEffort = selected.optString("reasoningEffort").takeIf { it.isNotEmpty() }
                    ?: option.reasoningEffort,
                reasoningEfforts = option.reasoningEfforts,
                selected = true,
            ))
        }
    }

    override fun loadSessions(onSuccess: (List<DshSession>) -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.SESSION_LIST, JSONObject()) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "session.list 返回为空")
                return@call
            }
            val sessions = parseSessions(value)
            store.replaceSessions(sessions)
            onSuccess(sessions)
        }
    }

    /** 会话产生新消息时刷新其 updatedAt（消息时间），供抽屉 workspaceGroups 实时重排。 */
    override fun touchSessionActivity(sessionId: String, updatedAt: Long) {
        val current = store.sessions[sessionId] ?: return
        if (current.updatedAt >= updatedAt) return
        store.sessions[sessionId] = current.copy(updatedAt = updatedAt)
    }

    private fun parseSessions(value: JSONObject): List<DshSession> {
        val items = value.optJSONArray("items") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("sessionId")
                if (id.isEmpty()) continue
                val projections = item.optJSONObject("projections")?.optJSONObject("values")
                add(DshSession(
                    id = id,
                    title = projections?.optString("title")?.takeIf { it.isNotEmpty() } ?: "尚无标题",
                    workspace = "Host",
                    updatedLabel = item.optLong("updatedAt").takeIf { it > 0 }?.toString().orEmpty(),
                    updatedAt = item.optLong("updatedAt"),
                    running = item.optBoolean("running"), blank = item.optBoolean("blank"), cwd = item.optString("cwd"),
                    parentSessionId = item.optString("parentSessionId").takeIf { it.isNotEmpty() },
                    origin = item.optString("origin").takeIf { it.isNotEmpty() },
                    agentPreset = item.optString("agentPreset").takeIf { it.isNotEmpty() },
                    permission = item.optString("permission").takeIf { it.isNotEmpty() },
                ))
            }
        }
    }

    override fun createSession(workspaceId: String?, onSuccess: (String) -> Unit, onError: (String) -> Unit, permission: String?, agentPreset: String?) {
        val payload = JSONObject()
        workspaceId?.takeIf { it.isNotEmpty() }?.let { payload.put("workspaceId", it) }
        permission?.takeIf { it.isNotEmpty() }?.let { payload.put("permission", it) }
        agentPreset?.takeIf { it.isNotEmpty() }?.let { payload.put("agentPreset", it) }
        call(DshHostProtocol.SESSION_CREATE, payload) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "session.create 返回为空")
                return@call
            }
            val id = value.optString("sessionId")
            if (id.isEmpty()) onError("session.create 未返回 sessionId") else onSuccess(id)
        }
    }

    override fun loadHistory(sessionId: String, onSuccess: (List<DshMessage>) -> Unit, onError: (String) -> Unit) {
        loadCompleteHistory(sessionId, { onSuccess(parseHistory(it)) }, onError)
    }

    override fun loadCompleteHistory(sessionId: String, onSuccess: (JSONArray) -> Unit,
        onError: (String) -> Unit, isCurrent: () -> Boolean) {
        DshHistoryLoader(
            request = { payload, callback -> call(DshHostProtocol.SESSION_HISTORY, payload, callback) },
            generation = { runtime.currentState().generation },
            schedule = { work -> setTimeout(pagerId, 0) { work() } },
        ).loadAll(sessionId, onSuccess, onError, isCurrent)
    }

    override fun loadWebTimeline(
        sessionId: String,
        onSuccess: (List<DshWebTimelineItem>) -> Unit,
        onError: (String) -> Unit,
        isCurrent: () -> Boolean,
    ) {
        loadCompleteHistory(sessionId, { onSuccess(DshWebTimelineParser.parseWebTimeline(it)) }, onError, isCurrent)
    }

    override fun loadSkills(sessionId: String, onSuccess: (List<DshSkill>) -> Unit, onError: (String) -> Unit) {
        call(DshHostProtocol.SKILL_LIST, JSONObject().apply { put("sessionId", sessionId) }) { value, error ->
            if (error != null || value == null) {
                onError(error?.message ?: "skill.list 返回为空")
                return@call
            }
            val skills = buildList {
                val items = value.optJSONArray("skills") ?: JSONArray()
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val name = item.optString("name")
                    if (name.isEmpty()) continue
                    add(DshSkill(name, item.optString("description"), item.optString("whenToUse"), item.optBoolean("modelInvocable", true)))
                }
            }
            onSuccess(skills)
        }
    }

    override fun goalEdit(sessionId: String, goal: DshGoalSnapshot, objective: String, callback: (DshRpcError?) -> Unit) =
        goalMutation(
            DshHostProtocol.GOAL_EDIT,
            sessionId,
            goal,
            enrich = { it.put("objective", objective) },
            callback = callback,
        )

    override fun goalPause(sessionId: String, goal: DshGoalSnapshot, callback: (DshRpcError?) -> Unit) =
        goalMutation(DshHostProtocol.GOAL_PAUSE, sessionId, goal, callback = callback)

    override fun goalResume(sessionId: String, goal: DshGoalSnapshot, callback: (DshRpcError?) -> Unit) =
        goalMutation(DshHostProtocol.GOAL_RESUME, sessionId, goal, callback = callback)

    override fun goalClear(sessionId: String, goal: DshGoalSnapshot, callback: (DshRpcError?) -> Unit) =
        goalMutation(DshHostProtocol.GOAL_CLEAR, sessionId, goal, callback = callback)

    private fun goalMutation(
        method: String,
        sessionId: String,
        goal: DshGoalSnapshot,
        enrich: (JSONObject) -> Unit = {},
        callback: (DshRpcError?) -> Unit,
    ) {
        call(method, JSONObject().apply {
            put("sessionId", sessionId)
            put("ref", JSONObject().apply { put("id", goal.id); put("revision", goal.revision) })
            enrich(this)
        }) { _, error -> callback(error) }
    }

    override fun loadAttachment(
        sessionId: String,
        attachmentId: String,
        callback: (String?, String?) -> Unit,
    ) {
        call(DshHostProtocol.SESSION_ATTACHMENT, JSONObject().apply {
            put("sessionId", sessionId)
            put("attachmentId", attachmentId)
        }) { value, error ->
            val data = value?.optString("data").orEmpty()
            val mediaType = value?.optJSONObject("attachment")?.optString("mediaType")?.takeIf { it.isNotEmpty() }
                ?: "image/png"
            if (error != null || data.isEmpty()) callback(null, error?.message ?: "attachment 返回为空")
            else callback("data:$mediaType;base64,$data", null)
        }
    }

    /**
     * 通过 host-plugin 上传通用文件（pre-0.1.5 backport）。
     * 返回 Host 工作目录内的落盘路径与 prompt handle；字节不进入会话日志。
     */
    override fun uploadAttachment(
        sessionId: String,
        name: String,
        mediaType: String,
        dataBase64: String,
        callback: (DshUploadedFile?, DshRpcError?) -> Unit,
    ) {
        runtime.callPluginPath(DshHostProtocol.ATTACHMENT_UPLOAD_PATH, JSONObject().apply {
            put("sessionId", sessionId)
            put("name", name)
            if (mediaType.isNotEmpty()) put("mediaType", mediaType)
            put("data", dataBase64)
        }) { value, error ->
            val attachment = value?.optJSONObject("attachment")
            if (error != null || attachment == null) {
                callback(null, error ?: DshRpcError("attachment-failed", "附件上传返回为空"))
                return@callPluginPath
            }
            val path = attachment.optString("path")
            val handle = attachment.optString("handle")
            if (path.isEmpty() || handle.isEmpty()) {
                callback(null, DshRpcError("attachment-failed", "附件上传缺少落盘路径"))
                return@callPluginPath
            }
            callback(DshUploadedFile(
                name = attachment.optString("name").ifEmpty { name },
                mediaType = attachment.optString("mediaType").ifEmpty { mediaType },
                bytes = attachment.optLong("bytes", 0L).takeIf { it > 0L }
                    ?: attachment.optString("bytes").toLongOrNull() ?: 0L,
                sha256 = attachment.optString("sha256"),
                path = path,
                handle = handle,
            ), null)
        }
    }

    override fun queue(sessionId: String): List<DshQueueItem> {
        val raw = store.queueSnapshots[sessionId] ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val message = item.optJSONObject("message") ?: JSONObject()
                val text = textFromBlocks(message.optJSONArray("content"))
                add(DshQueueItem(
                    id = item.optString("id"),
                    placement = item.optString("placement"),
                    preview = text.lineSequence().firstOrNull().orEmpty(),
                    text = text.takeIf { it.isNotEmpty() },
                ))
            }
        }.filter { it.id.isNotEmpty() && it.placement == "queued" }
    }

    override fun pendingInteractions(sessionId: String): Pair<DshPendingApproval?, DshPendingQuestion?> {
        var approval: DshPendingApproval? = null
        var question: DshPendingQuestion? = null
        store.pendingInteractions.forEach { (rpcId, raw) ->
            val payload = runCatching { JSONObject(raw) }.getOrNull() ?: return@forEach
            if (payload.optString("sessionId") != sessionId) return@forEach
            when (payload.optString("type")) {
                "approval/requested" -> approval = DshPendingApproval(
                    rpcId = rpcId,
                    sessionId = sessionId,
                    approvalId = payload.optString("approvalId"),
                    toolName = payload.optString("toolName"),
                    callId = payload.optString("callId").takeIf { it.isNotEmpty() },
                    reason = payload.optString("reason").takeIf { it.isNotEmpty() },
                    command = approvalCommand(sessionId, payload.optString("callId")),
                )
                "question/requested" -> {
                    val questions = payload.optJSONArray("questions") ?: JSONArray()
                    question = DshPendingQuestion(
                        rpcId = rpcId,
                        sessionId = sessionId,
                        questions = (0 until questions.length()).mapNotNull { index ->
                            val item = questions.optJSONObject(index) ?: return@mapNotNull null
                            val options = item.optJSONArray("options") ?: JSONArray()
                            DshPendingQuestionItem(
                                id = item.optString("id"),
                                question = item.optString("question"),
                                header = item.optString("header"),
                                detail = item.optString("detail"),
                                options = (0 until options.length()).mapNotNull { optionIndex ->
                                    val option = options.optJSONObject(optionIndex) ?: return@mapNotNull null
                                    DshPendingQuestionOption(
                                        label = option.optString("label"),
                                        description = option.optString("description"),
                                    )
                                },
                                multiSelect = item.optBoolean("multiSelect") || item.optBoolean("multi_select"),
                            )
                        },
                    )
                }
            }
        }
        return approval to question
    }

    override fun jobs(sessionId: String): List<DshJobItem> {
        val raw = store.jobSnapshots[sessionId] ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id.isEmpty()) return@mapNotNull null
            DshJobItem(
                id = id,
                kind = item.optString("kind"),
                label = item.optString("label"),
                status = item.optString("status"),
                detail = item.optString("detail"),
                startedAt = item.optLong("startedAt"),
                finishedAt = item.optString("finishedAt").takeIf { it.isNotEmpty() }?.toLongOrNull(),
            )
        }
    }

    override fun workspaceGroups(includeArchived: Boolean, archivedOnly: Boolean): List<DshWorkspaceGroup> {
        val raw = store.workspaceBaseline
        val workspaces = runCatching { JSONArray(raw) }.getOrNull() ?: JSONArray()
        val archived = store.archivedSessionIds
        val sessionById = store.sessions.values
            .filterNot { it.blank || (!includeArchived && archived.contains(it.id)) || (archivedOnly && !archived.contains(it.id)) }
            .associateBy { it.id }
        val grouped = mutableSetOf<String>()
        val groups = (0 until workspaces.length()).mapNotNull { index ->
            val workspace = workspaces.optJSONObject(index) ?: return@mapNotNull null
            val workspaceId = workspace.optString("workspaceId")
            if (workspaceId.isEmpty()) return@mapNotNull null
            val sessionIds = workspace.optJSONArray("sessionIds") ?: JSONArray()
            val sessions = (0 until sessionIds.length()).mapNotNull { sessionIndex ->
                val sessionId = sessionIds.optString(sessionIndex)
                sessionId?.takeIf { it.isNotEmpty() }?.let(grouped::add)
                sessionById[sessionId]
            }.sortedByDescending { it.updatedAt }
            DshWorkspaceGroup(
                workspaceId = workspaceId,
                title = workspace.optString("title").ifEmpty { workspaceId },
                path = workspace.optString("path"),
                sessions = sessions,
            )
        }
        val ungrouped = sessionById.values.filterNot { grouped.contains(it.id) }.sortedByDescending { it.updatedAt }
        val result = if (ungrouped.isEmpty()) groups else groups + DshWorkspaceGroup(
            workspaceId = "",
            title = "未分组",
            path = "",
            sessions = ungrouped,
        )
        return if (archivedOnly) result.filter { it.sessions.isNotEmpty() } else result
    }

    override fun workspaceIdForSession(sessionId: String): String? {
        val workspaces = runCatching { JSONArray(store.workspaceBaseline) }.getOrNull() ?: JSONArray()
        for (index in 0 until workspaces.length()) {
            val workspace = workspaces.optJSONObject(index) ?: continue
            val sessionIds = workspace.optJSONArray("sessionIds") ?: continue
            for (sessionIndex in 0 until sessionIds.length()) {
                if (sessionIds.optString(sessionIndex) == sessionId) {
                    return workspace.optString("workspaceId").takeIf { it.isNotEmpty() }
                }
            }
        }
        return null
    }

    override fun blankSessionInWorkspace(workspaceId: String?): DshSession? {
        if (workspaceId == null) return store.unarchivedBlankSession()
        val workspaces = runCatching { JSONArray(store.workspaceBaseline) }.getOrNull() ?: JSONArray()
        for (index in 0 until workspaces.length()) {
            val workspace = workspaces.optJSONObject(index) ?: continue
            if (workspace.optString("workspaceId") != workspaceId) continue
            val sessionIds = workspace.optJSONArray("sessionIds") ?: continue
            return store.unarchivedBlankSession((0 until sessionIds.length()).mapNotNull { sessionIds.optString(it) })
        }
        return null
    }

    private fun approvalCommand(sessionId: String, callId: String): String? {
        if (callId.isEmpty()) return null
        val events = store.sessionEvents[sessionId] ?: return null
        events.forEach { event ->
            if (event.type != "tool/call") return@forEach
            val payload = runCatching { JSONObject(event.raw) }.getOrNull() ?: return@forEach
            val data = payload.optJSONObject("data") ?: return@forEach
            if (data.optString("callId") != callId) return@forEach
            val arguments = data.opt("arguments") ?: return@forEach
            return when (arguments) {
                is String -> arguments
                else -> {
                    val obj = arguments as? JSONObject
                    obj?.optString("command")?.takeIf { it.isNotEmpty() } ?: arguments.toString()
                }
            }
        }
        return null
    }

    override fun updateQueue(
        sessionId: String,
        itemId: String,
        action: JSONObject,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        call(DshHostProtocol.SESSION_UPDATE_QUEUE, JSONObject().apply {
            put("sessionId", sessionId)
            put("itemId", itemId)
            put("action", action)
        }) { value, error -> callback(value, error) }
    }

    override fun renameSession(
        sessionId: String,
        title: String,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        call(DshHostProtocol.SESSION_RENAME, JSONObject().apply {
            put("sessionId", sessionId)
            put("title", title)
        }) { value, error ->
            if (error == null) {
                sessionCatalogLoader.invalidate()
                store.sessions[sessionId]?.let { store.sessions[sessionId] = it.copy(title = title) }
            }
            callback(value, error)
        }
    }

    override fun forkMessage(
        sessionId: String,
        message: DshMessage,
        callback: (String?, DshRpcError?) -> Unit,
    ) {
        DshMessageFork { payload, reply -> call(DshHostProtocol.SESSION_FORK, payload, reply) }
            .fork(sessionId, message) { childId, error ->
                if (error == null) sessionCatalogLoader.invalidate()
                callback(childId, error)
            }
    }

    override fun archiveSession(
        sessionId: String,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        call(DshHostProtocol.WORKSPACE_ARCHIVE_SESSION, JSONObject().apply {
            put("sessionId", sessionId)
        }) { value, error ->
            if (error == null) {
                sessionCatalogLoader.invalidate()
                // This projection follows a successful Host mutation, never an optimistic local archive.
                store.replaceWorkspaceBaseline(store.workspaceBaseline, store.archivedSessionIds + sessionId)
            }
            callback(value, error)
        }
    }

    /**
     * 取消归档：调用 host-plugin 的 `/api/session-manager/unarchive`。
     * 官方 DSH 没有公开的 unarchive RPC，归档集合由 WorkspaceRegistry 私有持有；
     * 成功写入会触发 `host/archived-sessions-changed`，这里也同步移除本地投影，
     * 避免等待下一次目录刷新。
     */
    override fun unarchiveSession(
        sessionId: String,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        callPlugin("unarchive", JSONObject().apply { put("sessionId", sessionId) }) { value, error ->
            if (error == null) {
                sessionCatalogLoader.invalidate()
                store.replaceWorkspaceBaseline(store.workspaceBaseline, store.archivedSessionIds - sessionId)
            }
            callback(value, error)
        }
    }

    override fun sessionExportUrl(sessionId: String, includeDescendants: Boolean): String {
        val encodedSessionId = dshEncodeQueryComponent(sessionId)
        return "${connection.baseUrl.trimEnd('/')}${DshHostProtocol.SESSION_EXPORT_PATH}" +
            "?sessionId=$encodedSessionId" +
            "&includeDescendants=${if (includeDescendants) "true" else "false"}"
    }

    override fun listDirectory(
        path: String?,
        callback: (DshDirectoryListing?, DshRpcError?) -> Unit,
    ) {
        val payload = JSONObject()
        path?.takeIf { it.isNotEmpty() }?.let { payload.put("path", it) }
        call(DshHostProtocol.HOST_LIST_DIRECTORY, payload) { value, error ->
            if (error != null || value == null) {
                callback(null, error ?: DshRpcError("internal", "host.listDirectory failed"))
                return@call
            }
            callback(parseDirectoryListing(value), null)
        }
    }

    override fun createDirectory(
        path: String,
        name: String,
        callback: (String?, DshRpcError?) -> Unit,
    ) {
        call(DshHostProtocol.HOST_CREATE_DIRECTORY, JSONObject().apply {
            put("path", path)
            put("name", name)
        }) { value, error ->
            if (error != null || value == null) {
                callback(null, error ?: DshRpcError("internal", "host.createDirectory failed"))
                return@call
            }
            callback(value.optString("path").takeIf { it.isNotEmpty() }, null)
        }
    }

    override fun createWorkspace(
        path: String,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        call(DshHostProtocol.WORKSPACE_CREATE, JSONObject().apply {
            put("path", path)
        }) { value, error -> callback(value, error) }
    }

    override fun renameWorkspace(
        workspaceId: String,
        title: String,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        call(DshHostProtocol.WORKSPACE_RENAME, JSONObject().apply {
            put("workspaceId", workspaceId)
            put("title", title)
        }) { value, error -> callback(value, error) }
    }

    override fun deleteWorkspace(
        workspaceId: String,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        call(DshHostProtocol.WORKSPACE_DELETE, JSONObject().apply {
            put("workspaceId", workspaceId)
        }) { value, error -> callback(value, error) }
    }

    override fun moveWorkspaceBefore(
        workspaceId: String,
        beforeWorkspaceId: String?,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) {
        val payload = JSONObject().apply {
            put("workspaceId", workspaceId)
            beforeWorkspaceId?.takeIf { it.isNotEmpty() }?.let { put("beforeWorkspaceId", it) }
        }
        call(DshHostProtocol.WORKSPACE_INSERT_BEFORE, payload) { value, error ->
            callback(value, error)
        }
    }

    private fun parseDirectoryListing(value: JSONObject): DshDirectoryListing {
        fun entries(array: JSONArray?): List<DshDirectoryEntry> = buildList {
            if (array == null) return@buildList
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                add(DshDirectoryEntry(
                    name = entry.optString("name"),
                    path = entry.optString("path"),
                    hidden = entry.optBoolean("hidden"),
                ))
            }
        }
        return DshDirectoryListing(
            path = value.optString("path"),
            home = value.optString("home"),
            crumbs = entries(value.optJSONArray("crumbs")),
            entries = entries(value.optJSONArray("entries")),
            truncated = value.optBoolean("truncated"),
        )
    }

    override fun streamReply(pagerId: String, sessionId: String, prompt: String, onDelta: (String) -> Unit, onComplete: (String) -> Unit, onError: (String) -> Unit): DshStreamHandle {
        val call = runtime.call(DshHostProtocol.SESSION_PROMPT, JSONObject().apply {
            put("sessionId", sessionId); put("mode", "queue")
            put("content", JSONArray().apply { put(JSONObject().apply { put("type", "text"); put("text", prompt) }) })
            put("clientTimeZone", clientTimeZoneId())
        }) { value, error, rpcId ->
            if (error != null) {
                if (dshIsTransportInterrupt(error.code, error.message)) {
                    DshStreamLog.i("prompt.hold-for-resync session=$sessionId rpcId=$rpcId code=${error.code}")
                    return@call
                }
                activeStreams.remove(rpcId); onError(error.message); return@call
            }
            val command = value?.optJSONObject("command")
            if (command != null) {
                activeStreams.remove(rpcId); onComplete(command.optString("text"))
            }
        }
        activeStreams[call.rpcId] = ActiveStream(sessionId, call.rpcId, { text, _ -> onDelta(text) }, onComplete, onError)
        return object : DshStreamHandle {
            private var cancelled = false
            override fun cancel() {
                if (cancelled) return
                cancelled = true
                activeStreams.remove(call.rpcId)
                call.cancel()
                runtime.call(DshHostProtocol.SESSION_CANCEL, JSONObject().apply { put("sessionId", sessionId) }) { _, _, _ -> }
            }
        }
    }

    override fun streamReply(
        pagerId: String,
        sessionId: String,
        prompt: String,
        onDelta: (String, Boolean) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    ): DshStreamHandle {
        val call = runtime.call(DshHostProtocol.SESSION_PROMPT, JSONObject().apply {
            put("sessionId", sessionId); put("mode", "queue")
            put("content", JSONArray().apply { put(JSONObject().apply { put("type", "text"); put("text", prompt) }) })
            put("clientTimeZone", clientTimeZoneId())
        }) { value, error, rpcId ->
            if (error != null) {
                if (dshIsTransportInterrupt(error.code, error.message)) {
                    DshStreamLog.i("prompt.hold-for-resync session=$sessionId rpcId=$rpcId code=${error.code}")
                    return@call
                }
                activeStreams.remove(rpcId); onError(error.message); return@call
            }
            val command = value?.optJSONObject("command")
            if (command != null) {
                activeStreams.remove(rpcId); onComplete(command.optString("text"))
            }
        }
        activeStreams[call.rpcId] = ActiveStream(sessionId, call.rpcId, onDelta, onComplete, onError)
        return object : DshStreamHandle {
            private var cancelled = false
            override fun cancel() {
                if (cancelled) return
                cancelled = true
                activeStreams.remove(call.rpcId)
                call.cancel()
                runtime.call(DshHostProtocol.SESSION_CANCEL, JSONObject().apply { put("sessionId", sessionId) }) { _, _, _ -> }
            }
        }
    }

    /**
     * 携带图片的会话发送：content 按官方 PromptContentPart 构造
     * [{type:"text",text}, {type:"image",mediaType,data,name}...]。
     * 仅发送通过预检的图片（SELECTED/UPLOADING/SENT），失败或超限项不进入 wire。
     * Host 落盘后以 ImageAttachmentRef 进入消息事实；本地草稿不写历史。
     */
    override fun streamReplyWithImages(
        pagerId: String,
        sessionId: String,
        prompt: String,
        images: List<DshPendingImage>,
        onDelta: (String, Boolean) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    ): DshStreamHandle {
        val content = JSONArray()
        if (prompt.isNotEmpty()) {
            content.put(JSONObject().apply { put("type", "text"); put("text", prompt) })
        }
        images.forEach { image ->
            if (image.state == DshImageDraftState.INVALID || image.dataBase64.isEmpty()) return@forEach
            content.put(JSONObject().apply {
                put("type", "image")
                put("mediaType", image.mediaType)
                put("data", image.dataBase64)
                if (image.name.isNotEmpty()) put("name", image.name)
            })
        }
        val call = runtime.call(DshHostProtocol.SESSION_PROMPT, JSONObject().apply {
            put("sessionId", sessionId); put("mode", "queue")
            put("content", content)
            put("clientTimeZone", clientTimeZoneId())
        }) { value, error, rpcId ->
            if (error != null) {
                if (dshIsTransportInterrupt(error.code, error.message)) {
                    DshStreamLog.i("prompt.hold-for-resync session=$sessionId rpcId=$rpcId code=${error.code}")
                    return@call
                }
                activeStreams.remove(rpcId); onError(error.message); return@call
            }
            val command = value?.optJSONObject("command")
            if (command != null) {
                activeStreams.remove(rpcId); onComplete(command.optString("text"))
            }
        }
        activeStreams[call.rpcId] = ActiveStream(sessionId, call.rpcId, onDelta, onComplete, onError)
        return object : DshStreamHandle {
            private var cancelled = false
            override fun cancel() {
                if (cancelled) return
                cancelled = true
                activeStreams.remove(call.rpcId)
                call.cancel()
                runtime.call(DshHostProtocol.SESSION_CANCEL, JSONObject().apply { put("sessionId", sessionId) }) { _, _, _ -> }
            }
        }
    }

    override fun adoptLiveStream(
        sessionId: String,
        onDelta: (String, Boolean) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    ): DshStreamHandle {
        detachLiveStreams(sessionId)
        val rpcId = "adopted-$sessionId"
        activeStreams[rpcId] = ActiveStream(sessionId, rpcId, onDelta, onComplete, onError)
        DshStreamLog.i("prompt.adopt-live session=$sessionId rpcId=$rpcId")
        return object : DshStreamHandle {
            private var cancelled = false
            override fun cancel() {
                if (cancelled) return
                cancelled = true
                activeStreams.remove(rpcId)
                runtime.call(DshHostProtocol.SESSION_CANCEL, JSONObject().apply { put("sessionId", sessionId) }) { _, _, _ -> }
            }
        }
    }

    override fun detachLiveStreams(sessionId: String) {
        val removed = activeStreams.entries
            .filter { it.value.sessionId == sessionId }
            .map { it.key }
        removed.forEach(activeStreams::remove)
        if (removed.isNotEmpty()) {
            DshStreamLog.i("prompt.detach-live session=$sessionId count=${removed.size}")
        }
    }

    private fun call(method: String, payload: JSONObject, callback: (JSONObject?, DshRpcError?) -> Unit) =
        runtime.call(method, payload) { value, error, _ -> callback(value, error) }

    private fun handleFrame(frame: DshDownlinkFrame) {
        val envelope = runCatching { JSONObject(frame.raw) }.getOrNull()
        if (envelope == null) {
            DshStreamLog.log(LogLevel.WARN, "host.frame.parse-error", "host.frame stream=${frame.stream.name.lowercase()} parse-error chars=${frame.raw.length}", null, null)
            return
        }
        val payload = envelope.optJSONObject("payload")
        if (payload == null) {
            DshStreamLog.log(LogLevel.INFO, "host.frame.no-payload", "host.frame stream=${frame.stream.name.lowercase()} no-payload envelopeType=${envelope.optString("type")} chars=${frame.raw.length}", null, null)
            return
        }
        val frameType = payload.optString("type")
        val inboundEvent = payload.optJSONObject("event")
        // session/event 在下方只记一次摘要；其他 mux/host 帧只记关键元数据。
        if (frameType != "session/event") {
            DshStreamLog.log(LogLevel.INFO, "host.frame", "host.frame stream=${frame.stream.name.lowercase()} type=$frameType session=${payload.optString("sessionId")} event=${inboundEvent?.optString("type").orEmpty()} seq=${inboundEvent?.optInt("seq", -1) ?: -1} chars=${frame.raw.length}", payload.optString("sessionId").takeIf { it.isNotEmpty() }, null)
        }
        if (frame.stream == DshEventStream.HOST) {
            handleHostFrame(payload)
            return
        }
        if (frame.stream != DshEventStream.MUX) return
        when (frameType) {
            "session/subscribed" -> {
                store.applySubscribed(payload.optString("sessionId"), payload.optInt("lastSeq", -1))
                return
            }
            "session/queue" -> {
                store.replaceQueue(payload.optString("sessionId"), payload.optJSONArray("items")?.toString() ?: "[]")
                onQueueSnapshotHandler(payload.optString("sessionId"))
                return
            }
            "session/jobs" -> {
                store.replaceJobs(payload.optString("sessionId"), payload.optJSONArray("jobs")?.toString() ?: "[]")
                onJobsSnapshotHandler(payload.optString("sessionId"))
                return
            }
            "session/projection" -> {
                val value = payload.optJSONObject("value")?.toString() ?: payload.optString("value")
                onProjectionHandler(payload.optString("sessionId"), payload.optString("key"), value, payload.optInt("seq", -1))
                return
            }
            "approval/requested", "question/requested" -> {
                val rpcId = pendingInteractionRpcId(envelope, payload)
                if (rpcId.isEmpty()) {
                    DshStreamLog.log(LogLevel.WARN, "host.frame.invalid", "type=$frameType reason=empty-rpcId",
                        payload.optString("sessionId").takeIf { it.isNotEmpty() })
                    return
                }
                store.putPending(rpcId, payload.toString())
                onPendingInteractionHandler(payload.optString("sessionId"))
                return
            }
            "approval/resolved", "question/resolved" -> {
                val rpcId = pendingInteractionRpcId(envelope, payload)
                    .ifEmpty { payload.optString("questionRpcId") }
                    .ifEmpty { payload.optString("approvalId") }
                store.removePending(rpcId)
                onPendingInteractionHandler(payload.optString("sessionId"))
                return
            }
        }
        if (frameType != "session/event") return
        val sessionId = payload.optString("sessionId")
        val event = payload.optJSONObject("event") ?: return
        val type = event.optString("type")
        val seq = event.optInt("seq", -1)
        // Preserve the optional host-computed tool view. History entries and
        // live mux frames must feed the same remote tool model.
        val eventEnvelope = JSONObject().apply {
            put("event", event)
            payload.optJSONObject("view")?.let { put("view", it) }
        }
        store.applySessionEvent(sessionId, seq, type, eventEnvelope.toString())
        if (seq > -1) onSessionEventHandler(sessionId, DshRawSessionEvent(seq, type, eventEnvelope.toString()))
        val data = event.optJSONObject("data") ?: JSONObject()
        // 会话事件摘要日志：type 沿用 DSH 事件原值（turn/start、tool/call 等）。
        // 只记元数据（sessionId / 事件类型 / seq / rpcId / 载荷大小），不写 delta 正文、工具 JSON 或附件 Base64；
        // 逐 token 的 chunk delta 不落库，只记结构性 chunk。
        val evtRpcId = sessionEventSource(data)?.optString("rpcId").orEmpty().takeIf { it.isNotEmpty() }
        if (dshShouldLogSessionEvent(type, data)) {
            DshStreamLog.log(
                dshSessionEventLevel(type, data),
                type,
                dshSessionEventSummary(seq, type, data),
                sessionId,
                evtRpcId,
            )
        }
        val source = sessionEventSource(data)
        val rpcId = source?.optString("rpcId").orEmpty()
        // 非当前会话也会收到订阅事件，已写入 store；没有 UI 流接收者是正常情况。
        val active = resolveActiveStream(sessionId, type, rpcId) ?: return
        when (type) {
            "user/message" -> {
                val kind = source?.optString("kind").orEmpty()
                if (kind.isEmpty() || kind == "user") active.observed = true
            }
            "assistant/chunk" -> {
                val chunk = data.optJSONObject("chunk") ?: return
                val chunkType = chunk.optString("type")
                val text = chunk.optString("text").ifEmpty { chunk.optString("delta") }
                when (chunkType) {
                    "text-delta", "text_delta", "text" -> text.takeIf { it.isNotEmpty() }?.let {
                        active.observed = true
                        active.accumulated.append(it)
                        active.onDelta(it, false)
                    }
                    "reasoning-delta", "reasoning_delta" -> text.takeIf { it.isNotEmpty() }?.let {
                        active.observed = true
                        active.onDelta(it, true)
                    }
                }
                if (chunkType == "finish") {
                    val reason = chunk.optJSONObject("reason")
                    if (reason?.optString("kind") == "error") {
                        active.failure = reason.optJSONObject("failure")?.optString("message").orEmpty()
                    }
                }
            }
            "assistant/message" -> {
                val message = data.optJSONObject("message") ?: data
                active.finalMessage = textFromBlocks(message.optJSONArray("content"))
            }
            "turn/end" -> {
                activeStreams.remove(active.promptRpcId)
                val reason = data.optJSONObject("reason")
                val error = reason?.optJSONObject("error")?.optString("message")?.takeIf { it.isNotEmpty() }
                    ?: active.failure.takeIf { it.isNotEmpty() }
                val completed = active.accumulated.toString().ifEmpty { active.finalMessage }
                if (error != null) active.onError(error) else {
                    active.onComplete(completed)
                }
            }
        }
    }

    private fun sessionEventSource(data: JSONObject): JSONObject? =
        data.optJSONObject("source") ?: data.optJSONObject("message")?.optJSONObject("source")

    private fun resolveActiveStream(sessionId: String, type: String, rpcId: String): ActiveStream? {
        if (rpcId.isNotEmpty()) {
            activeStreams[rpcId]?.takeIf { it.sessionId == sessionId }?.let { return it }
        }
        return activeStreams.values.lastOrNull { it.sessionId == sessionId }
    }

    private fun handleHostFrame(payload: JSONObject) {
        when (payload.optString("type")) {
            "host/remote-event" -> {
                onRemoteEventHandler(payload.optString("event"))
                return
            }
            "host/session-added" -> {
                val id = payload.optString("sessionId")
                if (id.isEmpty()) return
                store.applySessionAdded(DshSession(
                    id = id,
                    title = "尚无标题",
                    workspace = "Host",
                    updatedLabel = "",
                    updatedAt = payload.optLong("updatedAt"),
                    blank = true,
                    cwd = payload.optString("cwd"),
                    parentSessionId = payload.optString("parentSessionId").takeIf { it.isNotEmpty() },
                    origin = payload.optString("origin").takeIf { it.isNotEmpty() },
                    agentPreset = payload.optString("agentPreset").takeIf { it.isNotEmpty() },
                    permission = payload.optString("permission").takeIf { it.isNotEmpty() },
                ))
            }
            "host/session-status" -> {
                val id = payload.optString("sessionId")
                val current = store.sessions[id] ?: return
                val running = payload.optBoolean("running")
                store.sessions[id] = current.copy(running = running, blank = if (running) false else current.blank)
                onSessionStatusHandler(id, running)
            }
            "host/session-removed" -> {
                val id = payload.optString("sessionId")
                store.sessions.remove(id)
                store.sessionEvents.remove(id)
                store.queueSnapshots.remove(id)
                store.jobSnapshots.remove(id)
                store.projections.remove(id)
            }
            "host/workspace-order-changed" -> {
                val order = payload.optJSONArray("workspaceIds")?.toString() ?: return
                store.reorderWorkspaces(order)
            }
        }
    }

    private fun parseHistory(events: JSONArray): List<DshMessage> {
        val messages = mutableListOf<DshMessage>()
        val partials = mutableMapOf<String, StringBuilder>()
        for (index in 0 until events.length()) {
            val entry = events.optJSONObject(index) ?: continue
            val event = entry.optJSONObject("event") ?: entry
            val view = entry.optJSONObject("view")
            val viewValue = view?.optJSONObject("view")
            val seq = event.optInt("seq", index)
            val type = event.optString("type")
            val data = event.optJSONObject("data") ?: continue
            val firstNewMessage = messages.size
            when (type) {
                "user/message" -> textFromBlocks(data.optJSONArray("content")).takeIf { it.isNotEmpty() }?.let {
                    messages += DshMessage("user-$seq", DshMessageRole.USER, it)
                }
                "assistant/chunk" -> {
                    val key = "${data.optInt("turn")}:${data.optInt("step")}"
                    val chunk = data.optJSONObject("chunk")
                    val text = chunk?.optString("text").orEmpty()
                    if (text.isNotEmpty()) partials.getOrPut(key) { StringBuilder() }.append(text)
                    if (chunk?.optString("type") == "finish" && chunk.optJSONObject("reason")?.optString("kind") == "error") {
                        chunk.optJSONObject("reason")?.optJSONObject("failure")?.optString("message")?.takeIf { it.isNotEmpty() }?.let {
                            messages += DshMessage("turn-error-$seq", DshMessageRole.ERROR, it)
                        }
                    }
                }
                "assistant/message" -> {
                    val message = data.optJSONObject("message") ?: data
                    val key = "${data.optInt("turn")}:${data.optInt("step")}"
                    val text = textFromBlocks(message.optJSONArray("content")).ifEmpty { partials[key]?.toString().orEmpty() }
                    if (text.isNotEmpty()) messages += DshMessage("assistant-$seq", DshMessageRole.ASSISTANT, text)
                    partials.remove(key)
                }
                "tool/call" -> messages += DshMessage("tool-$seq", DshMessageRole.TOOL, "正在执行 ${data.optString("name").ifEmpty { "工具" }}", toolName = data.optString("name").takeIf { it.isNotEmpty() })
                "turn/end" -> data.optJSONObject("reason")?.optJSONObject("error")?.optString("message")?.takeIf { it.isNotEmpty() }?.let {
                    messages += DshMessage("turn-error-$seq", DshMessageRole.ERROR, it)
                }
            }
            for (messageIndex in firstNewMessage until messages.size) {
                messages[messageIndex] = messages[messageIndex].copy(sourceSeq = event.optInt("seq", -1).takeIf { it >= 0 })
            }
        }
        partials.forEach { (key, text) -> if (text.isNotEmpty()) messages += DshMessage("partial-$key", DshMessageRole.ASSISTANT, text.toString(), streaming = true) }
        return messages.filterNot { it.isRuntimeContextSnapshot() }
    }

    /**
     * 调用 DSH 插件 HTTP 端点，转发到当前连接 runtime。
     */
    override fun callPlugin(
        endpoint: String,
        payload: JSONObject,
        callback: (JSONObject?, DshRpcError?) -> Unit,
    ) = runtime.callPlugin(endpoint, payload, callback)

    private companion object {
        const val DEEPSEEK_PROVIDER = "deepseek-official"
        const val DEEPSEEK_SETTINGS_NS = "llm-deepseek"
        const val DEEPSEEK_CREDENTIAL_REF = "DEEPSEEK_API_KEY"
        const val HISTORY_PAGE_MESSAGES = 80
    }
}
