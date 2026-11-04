package com.example.dsh.ui.home

/**
 * Agent 连接器 → 官方图标资产（对照设计参考里的 agent-icons）。
 *
 * 协议里 source 是连接器类型（codex/claude/dsh/hermes/pi/openclaw/acp），
 * acp 的具体形态只体现在 connectorId（如 opencode、claude-code），
 * 所以两个字段都要参与匹配；未知 ACP 或清单缺字段时回退终端占位图。
 *
 * 资产放在 shared/src/commonMain/assets/，页面统一用
 * ImageUri.commonAssets(BusAgentVisuals.assetFor(task.source, task.connectorId)) 取图。
 */
internal object BusAgentVisuals {
    /** 未知连接器的回退图标 */
    const val FALLBACK: String = "agent-default.svg"

    const val CODEX: String = "agent-codex.jpg"
    const val CLAUDE: String = "agent-claude.jpg"
    const val DEEPSEEK: String = "agent-deepseek.png"
    const val OPENCODE: String = "agent-opencode.svg"
    const val HERMES: String = "agent-hermes.svg"
    const val PI: String = "agent-pi.svg"
    const val OPENCLAW: String = "agent-openclaw.svg"

    /**
     * 解析连接器图标资产名。
     *
     * connectorId 优先于 source：acp 连接器只有 connectorId 能区分 opencode/claude-code。
     * [agentKey] / [agentName] 只在非 acp 时兜底，防止把通用 ACP 连接器误认成专属品牌。
     */
    fun assetFor(
        source: String?,
        connectorId: String? = null,
        agentKey: String? = null,
        agentName: String? = null,
    ): String = busAgentAsset(source, connectorId, agentKey, agentName)
}

/**
 * 纯函数版本，便于单测；匹配规则见 [BusAgentVisuals]。
 */
internal fun busAgentAsset(
    source: String?,
    connectorId: String?,
    agentKey: String? = null,
    agentName: String? = null,
): String {
    busAgentAssetOrNull(connectorId)?.let { return it }
    busAgentAssetOrNull(source)?.let { return it }
    if (source != "acp") {
        busAgentAssetOrNull(agentKey)?.let { return it }
        busAgentAssetOrNull(agentName)?.let { return it }
    }
    return BusAgentVisuals.FALLBACK
}

/** 把 "Claude-Code"、"opencode" 之类的标识归一成小写字母数字。 */
private fun normalizeAgentId(value: String?): String =
    value.orEmpty().lowercase().filter { it.isLetterOrDigit() }

private fun busAgentAssetOrNull(rawId: String?): String? {
    val id = normalizeAgentId(rawId)
    if (id.isEmpty()) return null
    return when {
        // opencode 的归一化结果以 codex 结尾，必须先于 codex 判断
        id.contains("opencode") -> BusAgentVisuals.OPENCODE
        id.contains("openclaw") -> BusAgentVisuals.OPENCLAW
        id.contains("deepseek") -> BusAgentVisuals.DEEPSEEK
        id.startsWith("dsh") -> BusAgentVisuals.DEEPSEEK
        id.contains("claude") || id.contains("anthropic") -> BusAgentVisuals.CLAUDE
        id.contains("codex") -> BusAgentVisuals.CODEX
        id.contains("hermes") -> BusAgentVisuals.HERMES
        id == "pi" -> BusAgentVisuals.PI
        else -> null
    }
}
