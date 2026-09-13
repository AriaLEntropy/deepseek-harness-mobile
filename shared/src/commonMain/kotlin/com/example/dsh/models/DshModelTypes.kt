package com.example.dsh.models

internal enum class DshToolCardType {
    GENERIC,
    TERMINAL,
    READ,
    DIFF,
    SEARCH,
    WEB,
    JSON,
}

internal data class DshJsonNode(
    val key: String,
    val label: String,
    val preview: String,
    val children: List<DshJsonNode> = emptyList(),
    val depth: Int = 0,
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
    val reasoningEfforts: List<DshReasoningEffort> = emptyList(),
    val selected: Boolean = false,
)

// 单个模型的推理等级选项（id 用于提交，name 用于展示）

// 单个模型的推理等级选项（id 用于提交，name 用于展示）
internal data class DshReasoningEffort(
    val id: String,
    val name: String,
    val description: String = "",
)

internal data class DshAgentPresetOption(
    val id: String,
    val name: String,
    val description: String = "",
    val isDefault: Boolean = false,
)

internal data class DshSettingsChoice(
    val value: String,
    val label: String = "",
    val description: String = "",
)

internal data class DshSettingsSnapshot(
    val writable: Boolean = false,
    val permissionPreset: String = "",
    val permissionChoices: List<DshSettingsChoice> = emptyList(),
    val permissionRevision: Int = 0,
    val localeValue: String = "",
    val localeRevision: Int = 0,
    val themeValue: String = "",
    val themeRevision: Int = 0,
    val defaultModelProvider: String = "",
    val defaultModelLabel: String = "",
    val defaultModelRevision: Int = 0,
)
