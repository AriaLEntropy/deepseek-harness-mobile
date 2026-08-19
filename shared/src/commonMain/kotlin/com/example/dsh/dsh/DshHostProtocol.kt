package com.example.dsh.dsh

import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.log.KLog
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.timer.setTimeout

/** The browser Host carrier's logical paths, mirrored for the native client. */
internal object DshHostProtocol {
    const val API_PREFIX = "/api"
    const val MUX_EVENTS_PATH = "$API_PREFIX/events.mux"
    const val HOST_EVENTS_PATH = "$API_PREFIX/events.host"

    const val HOST_DESCRIBE = "host.describe"
    const val WORKSPACE_LIST = "workspace.list"
    const val SESSION_LIST = "session.list"
    const val SESSION_CREATE = "session.create"
    const val SESSION_HISTORY = "session.history"
    const val SESSION_MODELS = "session.models"
    const val SESSION_SELECT_MODEL = "session.selectModel"
    const val SESSION_PROMPT = "session.prompt"
    const val SESSION_CANCEL = "session.cancel"
    const val SETTINGS_DESCRIBE = "settings.describe"
    const val CREDENTIALS_DESCRIBE = "credentials.describe"
    const val CREDENTIALS_SET = "credentials.set"
    const val LLM_PROVIDERS = "llm.providers"
}

internal data class DshHostConnection(
    val baseUrl: String,
    val token: String = "",
)

/**
 * Real DSH Host client.
 *
 * The Host owns the DeepSeek key and the agent loop. This client only sends
 * `/api` RPCs. Live replies arrive over the Host's `events.mux` SSE stream;
 * history polling is retained only as disconnect recovery.
 */
internal class DshHostRepository(
    private val network: NetworkModule,
    private val sse: DshSseModule,
    private val connection: DshHostConnection,
    private val pagerId: String,
) : DshRepository {
    private var rpcSequence = 0

    override fun loadCredentialSetup(
        onSuccess: (DshCredentialSetup) -> Unit,
        onError: (String) -> Unit,
    ) {
        request(DshHostProtocol.LLM_PROVIDERS, JSONObject()) { providersValue, providersError ->
            if (providersError != null || providersValue == null) {
                onError(providersError ?: "llm.providers returned an empty result")
                return@request
            }
            val providers = providersValue.optJSONArray("providers") ?: JSONArray()
            var officialProviderActive = false
            for (index in 0 until providers.length()) {
                val provider = providers.optJSONObject(index) ?: continue
                if (provider.optString("provider") == DEEPSEEK_PROVIDER
                    && provider.optString("settingsNs") == DEEPSEEK_SETTINGS_NS
                    && provider.optBoolean("active")) {
                    officialProviderActive = true
                    break
                }
            }
            if (!officialProviderActive) {
                onSuccess(DshCredentialSetup(false, false, false))
                return@request
            }
            request(DshHostProtocol.SETTINGS_DESCRIBE, JSONObject()) { settingsValue, settingsError ->
                if (settingsError != null || settingsValue == null) {
                    onError(settingsError ?: "settings.describe returned an empty result")
                    return@request
                }
                val namespaces = settingsValue.optJSONArray("namespaces") ?: JSONArray()
                var credentialRef = DEEPSEEK_CREDENTIAL_REF
                var namespaceFound = false
                for (index in 0 until namespaces.length()) {
                    val namespace = namespaces.optJSONObject(index) ?: continue
                    if (namespace.optString("ns") != DEEPSEEK_SETTINGS_NS) continue
                    namespaceFound = true
                    credentialRef = namespace.optJSONObject("value")
                        ?.optString("apiKeyEnv")
                        ?.takeIf { it.isNotEmpty() }
                        ?: DEEPSEEK_CREDENTIAL_REF
                    break
                }
                val settingsWritable = settingsValue.optBoolean("writable") && namespaceFound
                request(DshHostProtocol.CREDENTIALS_DESCRIBE, JSONObject().apply {
                    put("refs", JSONArray().apply { put(credentialRef) })
                }) { credentialsValue, credentialsError ->
                    if (credentialsError != null || credentialsValue == null) {
                        onError(credentialsError ?: "credentials.describe returned an empty result")
                        return@request
                    }
                    val credential = credentialsValue.optJSONObject("credentials")
                        ?.optJSONObject(credentialRef)
                    onSuccess(DshCredentialSetup(
                        providerAvailable = true,
                        configured = credential?.optBoolean("configured") == true,
                        writable = settingsWritable && credential?.optBoolean("writable") == true,
                        credentialRef = credentialRef,
                    ))
                }
            }
        }
    }

    override fun saveDeepSeekApiKey(
        apiKey: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        request(DshHostProtocol.CREDENTIALS_SET, JSONObject().apply {
            put("ref", DEEPSEEK_CREDENTIAL_REF)
            put("value", apiKey)
        }) { _, error ->
            if (error != null) onError(error) else onSuccess()
        }
    }

    override fun loadModels(
        sessionId: String,
        onSuccess: (DshSessionModels) -> Unit,
        onError: (String) -> Unit,
    ) {
        request(DshHostProtocol.SESSION_MODELS, JSONObject().apply {
            put("sessionId", sessionId)
        }) { value, error ->
            if (error != null || value == null) {
                onError(error ?: "session.models returned an empty result")
                return@request
            }
            val current = value.optJSONObject("current") ?: JSONObject()
            val currentProvider = current.optString("provider")
            val currentModel = current.optString("model")
            val currentEffort = current.optString("reasoningEffort").takeIf { it.isNotEmpty() }
            val groups = value.optJSONArray("groups") ?: JSONArray()
            val options = mutableListOf<DshModelOption>()
            for (groupIndex in 0 until groups.length()) {
                val group = groups.optJSONObject(groupIndex) ?: continue
                val provider = group.optString("id")
                val providerName = group.optString("name").ifEmpty { provider }
                val models = group.optJSONArray("models") ?: JSONArray()
                for (modelIndex in 0 until models.length()) {
                    val model = models.optJSONObject(modelIndex) ?: continue
                    val modelId = model.optString("id")
                    if (provider.isEmpty() || modelId.isEmpty()) continue
                    val reasoning = model.optJSONObject("reasoning")
                    val defaultEffort = reasoning?.optString("defaultEffort")?.takeIf { it.isNotEmpty() }
                    val selected = provider == currentProvider && modelId == currentModel
                    options += DshModelOption(
                        provider = provider,
                        providerName = providerName,
                        model = modelId,
                        name = model.optString("name").ifEmpty { modelId },
                        description = model.optString("description"),
                        reasoningEffort = if (selected) currentEffort ?: defaultEffort else defaultEffort,
                        selected = selected,
                    )
                }
            }
            val selected = options.firstOrNull { it.selected } ?: DshModelOption(
                provider = currentProvider,
                providerName = currentProvider,
                model = currentModel,
                name = currentModel.ifEmpty { "选择模型" },
                reasoningEffort = currentEffort,
                selected = true,
            )
            onSuccess(DshSessionModels(selected, options, value.optBoolean("routable")))
        }
    }

    override fun selectModel(
        sessionId: String,
        option: DshModelOption,
        onSuccess: (DshModelOption) -> Unit,
        onError: (String) -> Unit,
    ) {
        request(DshHostProtocol.SESSION_SELECT_MODEL, JSONObject().apply {
            put("sessionId", sessionId)
            put("provider", option.provider)
            put("model", option.model)
            option.reasoningEffort?.let { put("reasoningEffort", it) }
        }) { value, error ->
            if (error != null || value == null) {
                onError(error ?: "session.selectModel returned an empty result")
                return@request
            }
            val selected = value.optJSONObject("selected") ?: JSONObject()
            onSuccess(option.copy(
                provider = selected.optString("provider").ifEmpty { option.provider },
                model = selected.optString("model").ifEmpty { option.model },
                reasoningEffort = selected.optString("reasoningEffort").takeIf { it.isNotEmpty() },
                selected = true,
            ))
        }
    }

    override fun loadSessions(onSuccess: (List<DshSession>) -> Unit, onError: (String) -> Unit) {
        request(DshHostProtocol.SESSION_LIST, JSONObject()) { value, error ->
            if (error != null || value == null) {
                onError(error ?: "session.list returned an empty result")
                return@request
            }
            val items = value.optJSONArray("items") ?: JSONArray()
            val sessions = buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    if (item.optBoolean("blank")) continue
                    val id = item.optString("sessionId")
                    if (id.isEmpty()) continue
                    val cwd = item.optString("cwd")
                    val projections = item.optJSONObject("projections")?.optJSONObject("values")
                    val title = projections?.optString("title")?.takeIf { it.isNotEmpty() }
                        ?: cwd.substringAfterLast('/').ifEmpty { id }
                    add(DshSession(
                        id = id,
                        title = title,
                        workspace = cwd.substringAfterLast('/').ifEmpty { "Host" },
                        updatedLabel = "",
                        running = item.optBoolean("running"),
                    ))
                }
            }
            onSuccess(sessions)
        }
    }

    override fun createSession(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        request(DshHostProtocol.SESSION_CREATE, JSONObject()) { value, error ->
            if (error != null || value == null) {
                onError(error ?: "session.create returned an empty result")
                return@request
            }
            val sessionId = value.optString("sessionId")
            if (sessionId.isEmpty()) {
                onError("session.create returned no sessionId")
            } else {
                onSuccess(sessionId)
            }
        }
    }

    override fun loadHistory(sessionId: String, onSuccess: (List<DshMessage>) -> Unit, onError: (String) -> Unit) {
        history(sessionId, onSuccess, onError)
    }

    override fun streamReply(
        pagerId: String,
        sessionId: String,
        prompt: String,
        onDelta: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    ): DshStreamHandle {
        var eventHandle: DshSseHandle? = null
        var promptSent = false
        var promptAccepted = false
        var fallbackRequested = false
        var fallbackStarted = false
        var completed = false
        var promptObserved = false
        val accumulated = StringBuilder()
        var finalMessage = ""
        var streamFailure = ""
        lateinit var handle: HostStreamHandle

        fun closeEvents() {
            eventHandle?.close()
            eventHandle = null
        }

        fun finishWithError(message: String) {
            if (completed || handle.cancelled) return
            completed = true
            closeEvents()
            onError(message)
        }

        fun finishSuccessfully(content: String) {
            if (completed || handle.cancelled) return
            completed = true
            closeEvents()
            onComplete(content)
        }

        fun startPollingRecovery() {
            if (!promptAccepted || fallbackStarted || completed || handle.cancelled) return
            fallbackStarted = true
            closeEvents()
            pollReply(
                sessionId = sessionId,
                handle = handle,
                previous = accumulated.toString(),
                onDelta = { delta ->
                    accumulated.append(delta)
                    onDelta(delta)
                },
                onComplete = { result -> finishSuccessfully(result) },
                onError = { error -> finishWithError(error) },
            )
        }

        fun sendPrompt() {
            if (promptSent || completed || handle.cancelled) return
            promptSent = true
            request(DshHostProtocol.SESSION_PROMPT, JSONObject().apply {
                put("sessionId", sessionId)
                put("mode", "queue")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", prompt)
                    })
                })
            }) { value, error ->
                if (handle.cancelled || completed) return@request
                if (error != null) {
                    finishWithError(error)
                    return@request
                }
                promptAccepted = true
                val command = value?.optJSONObject("command")
                if (command?.optString("kind") == "success") {
                    finishSuccessfully(command.optString("text"))
                    return@request
                }
                if (fallbackRequested) startPollingRecovery()
            }
        }

        fun requestFallback() {
            if (completed || handle.cancelled) return
            fallbackRequested = true
            closeEvents()
            if (!promptSent) sendPrompt()
            if (promptAccepted) startPollingRecovery()
        }

        fun handleFrame(rawFrame: String) {
            if (fallbackStarted || completed || handle.cancelled) return
            val envelope = runCatching { JSONObject(rawFrame) }.getOrNull() ?: return
            val payload = envelope.optJSONObject("payload") ?: return
            when (payload.optString("type")) {
                "stream/error" -> {
                    val message = payload.optJSONObject("error")?.optString("message")
                        ?.takeIf { it.isNotEmpty() }
                        ?: "Host event stream failed"
                    streamFailure = message
                    requestFallback()
                }
                "session/event" -> {
                    if (payload.optString("sessionId") != sessionId) return
                    val event = payload.optJSONObject("event") ?: return
                    val data = event.optJSONObject("data") ?: JSONObject()
                    when (event.optString("type")) {
                        "user/message" -> {
                            val source = data.optJSONObject("source")
                            val text = textFromBlocks(data.optJSONArray("content"))
                            if (source?.optString("kind") == "user" && text == prompt) {
                                promptObserved = true
                            }
                        }
                        "assistant/chunk" -> {
                            if (!promptObserved) return
                            val chunk = data.optJSONObject("chunk") ?: return
                            if (chunk.optString("type") == "text-delta") {
                                val delta = chunk.optString("text")
                                if (delta.isNotEmpty()) {
                                    streamFailure = ""
                                    accumulated.append(delta)
                                    onDelta(delta)
                                }
                            } else if (chunk.optString("type") == "finish") {
                                val reason = chunk.optJSONObject("reason")
                                if (reason?.optString("kind") == "error") {
                                    streamFailure = reason.optJSONObject("failure")
                                        ?.optString("message")
                                        .orEmpty()
                                }
                            }
                        }
                        "assistant/message" -> {
                            if (!promptObserved) return
                            val message = data.optJSONObject("message") ?: data
                            textFromBlocks(message.optJSONArray("content"))
                                .takeIf { it.isNotEmpty() }
                                ?.let {
                                    streamFailure = ""
                                    finalMessage = it
                                }
                        }
                        "turn/end" -> {
                            if (!promptObserved) return
                            val reason = data.optJSONObject("reason")
                            val error = reason?.optJSONObject("error")?.optString("message")
                                ?.takeIf { it.isNotEmpty() }
                                ?: streamFailure.takeIf { it.isNotEmpty() }
                            if (error != null) {
                                finishWithError(error)
                            } else {
                                finishSuccessfully(accumulated.toString().ifEmpty { finalMessage })
                            }
                        }
                    }
                }
            }
        }

        handle = HostStreamHandle {
            closeEvents()
            cancel(sessionId)
        }
        val eventsUrl = "${connection.baseUrl.trimEnd('/')}${DshHostProtocol.MUX_EVENTS_PATH}"
        eventHandle = runCatching {
            sse.connect(eventsUrl, connection.token) { event ->
                when (event.kind) {
                    DshSseEventKind.OPEN -> sendPrompt()
                    DshSseEventKind.FRAME -> handleFrame(event.data)
                    DshSseEventKind.ERROR,
                    DshSseEventKind.CLOSED -> requestFallback()
                }
            }
        }.getOrElse {
            KLog.e("DshEventStream", "connect failed: ${it.message.orEmpty()}")
            fallbackRequested = true
            null
        }
        if (eventHandle == null) requestFallback()
        setTimeout(pagerId, SSE_OPEN_TIMEOUT_MS) {
            if (!promptSent && !completed && !handle.cancelled) requestFallback()
        }
        return handle
    }

    private fun pollReply(
        sessionId: String,
        handle: HostStreamHandle,
        previous: String,
        onDelta: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (handle.cancelled) return
        history(sessionId, { snapshot ->
            if (handle.cancelled) return@history
            val latest = snapshot.lastOrNull { it.role == DshMessageRole.ASSISTANT }?.content.orEmpty()
            if (latest.length > previous.length && latest.startsWith(previous)) {
                onDelta(latest.substring(previous.length))
            } else if (latest != previous && latest.isNotEmpty()) {
                onDelta(latest)
            }
            val lastUserSeq = snapshot.lastOrNull { it.role == DshMessageRole.USER }
                ?.id?.substringAfter("user-")?.toIntOrNull() ?: -1
            val completed = snapshot.any {
                it.id.startsWith("turn-end-")
                    && (it.id.substringAfter("turn-end-").toIntOrNull() ?: -1) > lastUserSeq
            }
            val failure = snapshot.lastOrNull {
                it.role == DshMessageRole.ERROR
                    && it.id.startsWith("turn-error-")
                    && (it.id.substringAfter("turn-error-").toIntOrNull() ?: -1) > lastUserSeq
            }?.content
            if (completed) {
                if (failure.isNullOrEmpty()) onComplete(latest) else onError(failure)
            }
            else setTimeout(pagerId, POLL_INTERVAL_MS) {
                pollReply(sessionId, handle, latest, onDelta, onComplete, onError)
            }
        }, onError)
    }

    private fun history(sessionId: String, onSuccess: (List<DshMessage>) -> Unit, onError: (String) -> Unit) {
        request(DshHostProtocol.SESSION_HISTORY, JSONObject().apply {
            put("sessionId", sessionId)
            put("maxMessages", 50)
        }) { value, error ->
            if (error != null || value == null) {
                onError(error ?: "session.history returned an empty result")
                return@request
            }
            onSuccess(parseHistory(value.optJSONArray("events") ?: JSONArray()))
        }
    }

    private fun cancel(sessionId: String) {
        request(DshHostProtocol.SESSION_CANCEL, JSONObject().apply {
            put("sessionId", sessionId)
        }) { _, _ -> }
    }

    private fun request(method: String, payload: JSONObject, callback: (JSONObject?, String?) -> Unit) {
        val body = JSONObject().apply {
            put("type", "client-request")
            put("rpcId", "dsh-native-${++rpcSequence}")
            put("method", method)
            put("payload", payload)
        }
        val headers = JSONObject().apply {
            put("Content-Type", "application/json")
            if (connection.token.isNotEmpty()) put("Authorization", "Bearer ${connection.token}")
        }
        val url = "${connection.baseUrl.trimEnd('/')}${DshHostProtocol.API_PREFIX}/$method"
        network.httpRequest(url, true, body, headers, null, REQUEST_TIMEOUT_SECONDS) { data, success, errorMsg, response ->
            if (!success) {
                callback(null, "$method failed (${response.statusCode ?: 0}): $errorMsg")
                return@httpRequest
            }
            val result = data.optJSONObject("result")
            if (result == null) {
                callback(null, "$method returned an invalid RPC envelope")
                return@httpRequest
            }
            if (!result.optBoolean("ok")) {
                callback(null, result.optJSONObject("error")?.optString("message") ?: "$method failed")
                return@httpRequest
            }
            callback(result.optJSONObject("value"), null)
        }
    }

    private fun parseHistory(events: JSONArray): List<DshMessage> {
        val messages = mutableListOf<DshMessage>()
        val partials = mutableMapOf<String, StringBuilder>()
        for (index in 0 until events.length()) {
            val entry = events.optJSONObject(index) ?: continue
            val event = entry.optJSONObject("event") ?: entry
            val seq = event.optInt("seq", index)
            val type = event.optString("type")
            val data = event.optJSONObject("data") ?: continue
            when (type) {
                "user/message" -> {
                    val text = textFromBlocks(data.optJSONArray("content"))
                    if (text.isNotEmpty()) messages += DshMessage("user-$seq", DshMessageRole.USER, text)
                }
                "assistant/chunk" -> {
                    val key = "${data.optInt("turn")}:${data.optInt("step")}"
                    val chunk = data.optJSONObject("chunk")
                    val text = chunk?.optString("text").orEmpty()
                    if (text.isNotEmpty()) partials.getOrPut(key) { StringBuilder() }.append(text)
                    val reason = chunk?.optJSONObject("reason")
                    if (chunk?.optString("type") == "finish" && reason?.optString("kind") == "error") {
                        val failure = reason.optJSONObject("failure")?.optString("message").orEmpty()
                        if (failure.isNotEmpty()) messages += DshMessage(
                            "turn-error-$seq",
                            DshMessageRole.ERROR,
                            failure,
                        )
                    }
                }
                "assistant/message" -> {
                    val message = data.optJSONObject("message") ?: data
                    val text = textFromBlocks(message.optJSONArray("content"))
                    val key = "${data.optInt("turn")}:${data.optInt("step")}"
                    val merged = if (text.isNotEmpty()) text else partials[key]?.toString().orEmpty()
                    if (merged.isNotEmpty()) messages += DshMessage("assistant-$seq", DshMessageRole.ASSISTANT, merged)
                    partials.remove(key)
                }
                "tool/call" -> {
                    val name = data.optString("name")
                    messages += DshMessage("tool-$seq", DshMessageRole.TOOL, "正在执行 $name", toolName = name)
                }
                "turn/end" -> {
                    val reason = data.optJSONObject("reason")
                    val error = reason?.optJSONObject("error")?.optString("message").orEmpty()
                    if (error.isNotEmpty() && messages.none {
                            it.role == DshMessageRole.ERROR && it.content == error
                        }) {
                        messages += DshMessage("turn-error-$seq", DshMessageRole.ERROR, error)
                    }
                    messages += DshMessage(
                        "turn-end-$seq",
                        DshMessageRole.TOOL,
                        "",
                        hidden = true,
                    )
                }
            }
        }
        partials.forEach { (key, text) ->
            if (text.isNotEmpty()) messages += DshMessage("partial-$key", DshMessageRole.ASSISTANT, text.toString(), streaming = true)
        }
        return messages.filter { it.hidden || (it.content.isNotEmpty() && !it.isRuntimeContextSnapshot()) }
    }

    private fun textFromBlocks(blocks: JSONArray?): String {
        if (blocks == null) return ""
        return buildString {
            for (index in 0 until blocks.length()) {
                val block = blocks.optJSONObject(index) ?: continue
                if (block.optString("type") == "text") append(block.optString("text"))
            }
        }
    }

    private class HostStreamHandle(private val cancelAction: () -> Unit) : DshStreamHandle {
        var cancelled = false

        override fun cancel() {
            if (cancelled) return
            cancelled = true
            cancelAction()
        }
    }

    private companion object {
        const val DEEPSEEK_PROVIDER = "deepseek-official"
        const val DEEPSEEK_SETTINGS_NS = "llm-deepseek"
        const val DEEPSEEK_CREDENTIAL_REF = "DEEPSEEK_API_KEY"
        const val POLL_INTERVAL_MS = 450
        const val SSE_OPEN_TIMEOUT_MS = 3_000
        const val REQUEST_TIMEOUT_SECONDS = 30
    }
}
