package com.example.dsh.dsh

import com.tencent.kuikly.core.reactive.collection.ObservableList

/**
 * 命令点击后的执行类型，对齐 web 端的命令分发决策表
 * （`packages/client/ui-commands/src/client/service.ts` · `dispatch`）：
 *
 * - [DshCommandKind.DIRECT_EXECUTE]：host 的 bare command（未声明参数输入），菜单点选即直接执行，
 *   不写入输入框、也不作为普通消息下发；
 * - [DshCommandKind.POPUP]：客户端装饰/贡献命令（如 `/permission`、`/model`），点选弹出对应选择器；
 * - [DshCommandKind.WRITE_INPUT]：host 命令声明了参数输入（`input.hint`），点选把 `/name ` 写入输入框
 *   并给出参数提示，等用户补全参数后回车执行。
 */
internal enum class DshCommandKind { DIRECT_EXECUTE, POPUP, WRITE_INPUT }

/** [DshCommandKind.POPUP] 命令要弹出的选择器。 */
internal enum class DshCommandPopup { NONE, PERMISSION, MODEL }

/**
 * 命令：输入 `/` 上方的建议浮层里的命令列表项。
 * 与 web 命令目录一致（keyword + description），并携带用于分派的执行类型元数据。
 *
 * @property hint 参数提示（`input.hint`），仅写入类命令使用；与后端文案一致。
 * @property popup [DshCommandKind.POPUP] 命令要弹出的选择器。
 */
internal data class DshCommand(
    val name: String,
    val description: String,
    val kind: DshCommandKind,
    val hint: String = "",
    val popup: DshCommandPopup = DshCommandPopup.NONE,
)

/** 全部命令（顺序与 web 命令目录一致）。 */
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
        hint = "<preset>",
        popup = DshCommandPopup.PERMISSION,
    ),
    DshCommand("plan", "Enter or leave plan mode", DshCommandKind.WRITE_INPUT, hint = "[off|message]"),
    DshCommand("model", "选择本会话使用的模型", DshCommandKind.POPUP, popup = DshCommandPopup.MODEL),
)

private val commandFilterCache = ObservableList<DshCommand>()

/**
 * 命令建议：按斜杠前缀做**不区分大小写**的前缀匹配（对齐 web 命令源的 `toLowerCase()` 语义），
 * 空前缀返回全部，供刚输入单独 "/" 时展示命令组。查询串保留原始内容（含尾部空格），
 * 这样点选命令写入 "/name " 后前缀不再命中，浮层随输入关闭。
 */
internal fun visibleCommandList(query: String): ObservableList<DshCommand> {
    val q = query.lowercase()
    val next = if (q.isEmpty()) dshAllCommands() else dshAllCommands().filter { it.name.lowercase().startsWith(q) }
    commandFilterCache.diffUpdate(next) { old, new -> old.name == new.name }
    return commandFilterCache
}

/** 按名称（不含前导 `/`）查命令，找不到返回 null。 */
internal fun dshCommandNamed(name: String): DshCommand? = dshAllCommands().firstOrNull { it.name == name }

/**
 * 草稿当前所处的命令参数提示：草稿以 `/<name>` 开头、尚未输入参数（`/name` 或 `/name ` 后无正文）时，
 * 返回该写入类命令的 [DshCommand.hint]；否则返回空串。与 web 输入框里命令 claim 态的 ghost 提示对应。
 */
internal fun dshCommandHintForDraft(draft: String): String {
    val trimmed = draft.trim()
    if (!trimmed.startsWith("/")) return ""
    val space = trimmed.indexOf(' ')
    val token = if (space < 0) trimmed else trimmed.substring(0, space)
    val args = if (space < 0) "" else trimmed.substring(space + 1).trim()
    if (args.isNotEmpty()) return ""
    val command = dshCommandNamed(token.removePrefix("/")) ?: return ""
    return if (command.kind == DshCommandKind.WRITE_INPUT) command.hint else ""
}
