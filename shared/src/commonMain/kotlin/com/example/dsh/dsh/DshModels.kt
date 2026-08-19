package com.example.dsh.dsh

/** The small client-side model used by the first DSH surface. */
internal data class DshSession(
    val id: String,
    val title: String,
    val workspace: String,
    val updatedLabel: String,
    val running: Boolean = false,
)

internal enum class DshMessageRole {
    USER,
    ASSISTANT,
    TOOL,
    ERROR,
}

internal data class DshMessage(
    val id: String,
    val role: DshMessageRole,
    val content: String,
    val streaming: Boolean = false,
    val toolName: String? = null,
    val hidden: Boolean = false,
)

internal data class DshCredentialSetup(
    val providerAvailable: Boolean,
    val configured: Boolean,
    val writable: Boolean,
    val credentialRef: String = "DEEPSEEK_API_KEY",
)

internal data class DshModelOption(
    val provider: String,
    val providerName: String,
    val model: String,
    val name: String,
    val description: String = "",
    val reasoningEffort: String? = null,
    val selected: Boolean = false,
)

internal data class DshSessionModels(
    val current: DshModelOption,
    val options: List<DshModelOption>,
    val routable: Boolean,
)

internal interface DshStreamHandle {
    fun cancel()
}

internal interface DshRepository {
    fun loadCredentialSetup(
        onSuccess: (DshCredentialSetup) -> Unit,
        onError: (String) -> Unit,
    )

    fun saveDeepSeekApiKey(
        apiKey: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    )

    fun loadModels(
        sessionId: String,
        onSuccess: (DshSessionModels) -> Unit,
        onError: (String) -> Unit,
    )

    fun selectModel(
        sessionId: String,
        option: DshModelOption,
        onSuccess: (DshModelOption) -> Unit,
        onError: (String) -> Unit,
    )

    fun loadSessions(
        onSuccess: (List<DshSession>) -> Unit,
        onError: (String) -> Unit,
    )

    fun createSession(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
    )

    fun loadHistory(
        sessionId: String,
        onSuccess: (List<DshMessage>) -> Unit,
        onError: (String) -> Unit,
    )
    fun streamReply(
        pagerId: String,
        sessionId: String,
        prompt: String,
        onDelta: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    ): DshStreamHandle
}
