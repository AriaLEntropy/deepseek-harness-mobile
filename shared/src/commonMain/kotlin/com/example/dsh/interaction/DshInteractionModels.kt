package com.example.dsh.interaction

internal data class DshPendingApproval(
    val rpcId: String,
    val sessionId: String,
    val approvalId: String,
    val toolName: String,
    val callId: String?,
    val reason: String?,
    val command: String? = null,
)

internal data class DshPendingQuestionOption(
    val label: String,
    val description: String,
)

internal data class DshPendingQuestionItem(
    val id: String,
    val question: String,
    val header: String,
    val detail: String,
    val options: List<DshPendingQuestionOption>,
    val multiSelect: Boolean,
)

internal data class DshPendingQuestion(
    val rpcId: String,
    val sessionId: String,
    val questions: List<DshPendingQuestionItem>,
)

internal data class DshQuestionDraft(
    val selected: List<String> = emptyList(),
    val custom: String = "",
    val skipped: Boolean = false,
)
