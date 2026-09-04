package com.example.dsh.chat

import com.tencent.kuikly.core.reactive.collection.ObservableList

/**
 * 命令：点击输入框「+」图标弹出的半屏面板里的命令列表项。
 * 与原版（web dsh-011）的命令目录一致：keyword + description。
 */
internal data class DshCommand(
    val name: String,
    val description: String,
)

/** 原版 + 菜单里展示的全部命令（顺序与 web 命令目录一致） */
internal fun dshAllCommands(): List<DshCommand> = listOf(
    DshCommand("compact", "Compact older conversation history"),
    DshCommand("export", "Download this Session log as a ZIP archive"),
    DshCommand("feedback", "record feedback about this session"),
    DshCommand("goal", "set or view the goal for a long-running task"),
    DshCommand("permission", "Switch the permission preset (sandbox mode + approval policy)"),
    DshCommand("plan", "Enter or leave plan mode"),
    DshCommand("model", "选择本会话使用的模型"),
)

/** 命令面板列表的可观察缓存：diffUpdate 驱动 vfor 增量渲染 */
internal val dshCommandCatalog = ObservableList<DshCommand>().apply {
    diffUpdate(dshAllCommands()) { old, new -> old.name == new.name }
}

/**
 * 输入 "/前缀" 时，返回名称以该前缀开头的命令（不区分大小写）。
 * 空前缀（刚输入单独 "/"）时返回全部命令，保证命令组始终位于技能组上方。
 * 用于输入框上方的命令 / 技能建议浮层。
 */
internal fun dshCommandsMatching(prefix: String): List<DshCommand> {
    val query = prefix.trim().lowercase()
    if (query.isEmpty()) return dshAllCommands()
    return dshAllCommands().filter { it.name.lowercase().startsWith(query) }
}

/**
 * 是否还有"可补全"的命令：存在命令是当前前缀的严格前缀（前缀 != 命令名）。
 * 完整输完命令名（如 /compact）时返回 false，避免灰色提示把它自己再列一遍。
 */
internal fun dshCommandCompletable(prefix: String): Boolean {
    val query = prefix.trim().lowercase()
    if (query.isEmpty()) return false
    return dshAllCommands().any { it.name.lowercase().startsWith(query) && it.name.lowercase() != query }
}

/** 从输入框草稿里取出斜杠前缀段（不含空格后的正文），形如 "/comp"；不以 / 开头返回空串 */
internal fun dshCommandPrefixFromDraft(draft: String): String {
    val trimmed = draft.trimStart()
    if (!trimmed.startsWith("/")) return ""
    val end = trimmed.indexOf(' ')
    return if (end < 0) trimmed else trimmed.substring(0, end)
}