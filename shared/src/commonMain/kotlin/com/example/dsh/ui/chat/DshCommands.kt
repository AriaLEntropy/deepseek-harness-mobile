package com.example.dsh.ui.chat

import com.tencent.kuikly.core.reactive.collection.ObservableList

/**
 * 命令点击后的执行类型，对齐 web 端的命令分发决策表
 * （`packages/client/ui-commands/src/client/service.ts` · `dispatch`）：
 *
 * - [DIRECT_EXECUTE]：host 的 bare command（未声明参数输入），菜单点选即直接执行，
 *   不写入输入框、也不作为普通消息下发；
 * - [POPUP]：客户端装饰/贡献命令（如 `/permission`、`/model`），点选弹出对应选择器；
 * - [WRITE_INPUT]：host 命令声明了参数输入（`input.hint`），点选把 `/name ` 写入输入框
 *   并给出参数提示，等用户补全参数后回车执行。
 */
internal enum class DshCommandKind { DIRECT_EXECUTE, POPUP, WRITE_INPUT }

/** [DshCommandKind.POPUP] 命令在移动端对应的选择器。 */
internal enum class DshCommandPopup { NONE, PERMISSION, MODEL }

/**
 * 命令：点击输入框「+」图标弹出的半屏面板、以及输入 `/` 上方的建议浮层里的命令列表项。
 * 与 web 命令目录一致（keyword + description），并携带移动端用于分派的执行类型元数据。
 *
 * @property hint 参数提示（`input.hint`），仅 [DshCommandKind.WRITE_INPUT] 命令使用；与后端文案一致。
 * @property popup [DshCommandKind.POPUP] 命令要弹出的选择器。
 */
internal data class DshCommand(
    val name: String,
    val description: String,
    val kind: DshCommandKind,
    val hint: String = "",
    val popup: DshCommandPopup = DshCommandPopup.NONE,
)

/** 原版 + 菜单里展示的全部命令（顺序与 web 命令目录一致） */
internal fun dshAllCommands(): List<DshCommand> = listOf(
    DshCommand("compact", "Compact older conversation history", DshCommandKind.DIRECT_EXECUTE),
    DshCommand("export", "Download this Session log as a ZIP archive", DshCommandKind.DIRECT_EXECUTE),
    DshCommand("feedback", "record feedback about this session", DshCommandKind.WRITE_INPUT, hint = "<text>"),
    DshCommand(
        "goal",
        "set or view the goal for a long-running task",
        DshCommandKind.WRITE_INPUT,
        hint = "[<objective>|clear|edit <objective>|pause|resume]",
    ),
    DshCommand(
        "permission",
        "Switch the permission preset (sandbox mode + approval policy)",
        DshCommandKind.POPUP,
        popup = DshCommandPopup.PERMISSION,
    ),
    DshCommand("plan", "Enter or leave plan mode", DshCommandKind.WRITE_INPUT, hint = "[off|message]"),
    DshCommand("model", "选择本会话使用的模型", DshCommandKind.POPUP, popup = DshCommandPopup.MODEL),
)

/** 命令面板列表的可观察缓存：diffUpdate 驱动 vfor 增量渲染 */
internal val dshCommandCatalog = ObservableList<DshCommand>().apply {
    diffUpdate(dshAllCommands()) { old, new -> old.name == new.name }
}

/**
 * 输入 "/前缀" 时，返回名称以该前缀开头的命令（不区分大小写）。
 * 空前缀（刚输入单独 "/"）时返回全部命令，保证命令组始终位于技能组上方。
 *
 * 大小写取舍：web 端命令候选（`ui-commands/service.ts` 的 `fuzzyCandidates`）对命令名与查询串
 * 双双 `toLowerCase()`，即大小写不敏感；skill 源（`ui-skill/src/client/index.ts`）才是
 * 大小写敏感的 `name.startsWith(query)`。移动端命令侧保持与后端命令一致的大小写不敏感前缀匹配，
 * 技能侧另见 `dshSkillsMatching`（已对齐为大小写敏感）。
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

/** 按名称（不含前导 `/`）查命令，找不到返回 null。 */
internal fun dshCommandNamed(name: String): DshCommand? =
    dshAllCommands().firstOrNull { it.name == name }

/** 从输入框草稿里取出斜杠前缀段（不含空格后的正文），形如 "/comp"；不以 / 开头返回空串 */
internal fun dshCommandPrefixFromDraft(draft: String): String {
    val trimmed = draft.trimStart()
    if (!trimmed.startsWith("/")) return ""
    val end = trimmed.indexOf(' ')
    return if (end < 0) trimmed else trimmed.substring(0, end)
}

/**
 * 草稿当前所处的命令参数提示：草稿以 `/<name>` 开头、尚未输入参数（`/name` 或 `/name ` 后无正文）时，
 * 返回该 [DshCommandKind.WRITE_INPUT] 命令的 [DshCommand.hint]；否则返回空串。
 * 与 web 输入框里命令 claim 态的 ghost 参数提示对应。
 */
internal fun dshCommandHintForDraft(draft: String): String {
    val trimmed = draft.trim()
    if (!trimmed.startsWith("/")) return ""
    val space = trimmed.indexOf(' ')
    val token = if (space < 0) trimmed else trimmed.substring(0, space)
    val args = if (space < 0) "" else trimmed.substring(space + 1).trim()
    if (args.isNotEmpty()) return ""
    val name = token.removePrefix("/")
    val command = dshCommandNamed(name) ?: return ""
    return if (command.kind == DshCommandKind.WRITE_INPUT) command.hint else ""
}
