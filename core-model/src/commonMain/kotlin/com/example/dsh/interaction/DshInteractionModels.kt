package com.example.dsh.interaction

data class DshPendingApproval(
    val rpcId: String,
    val sessionId: String,
    val approvalId: String,
    val toolName: String,
    val callId: String?,
    val reason: String?,
    val command: String? = null,
)

data class DshPendingQuestionOption(
    val label: String,
    val description: String,
)

data class DshPendingQuestionItem(
    val id: String,
    val question: String,
    val header: String,
    val detail: String,
    val options: List<DshPendingQuestionOption>,
    val multiSelect: Boolean,
)

data class DshPendingQuestion(
    val rpcId: String,
    val sessionId: String,
    val questions: List<DshPendingQuestionItem>,
)

data class DshQuestionDraft(
    val selected: List<String> = emptyList(),
    val custom: String = "",
    val skipped: Boolean = false,
)
