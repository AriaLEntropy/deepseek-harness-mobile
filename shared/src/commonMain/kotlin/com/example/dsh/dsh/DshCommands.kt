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
 * 命令 claim 态参数提示的**展示文案**。对齐 web：`hint.plan` / `hint.goal`(含 active) 由
 * conversation 字典本地化覆盖后端原始 `input.hint`，其余命令（feedback/permission）沿用后端原始 hint。
 */
internal fun dshCommandDisplayHint(command: DshCommand, goalActive: Boolean): String = when (command.name) {
    "plan" -> "描述你的任务以生成计划"
    "goal" -> if (goalActive) {
        "当前目标进行中。可输入 edit 修改 / pause 暂停 / resume 继续 / clear 清除"
    } else {
        "输入目标，智能体将持续执行"
    }
    else -> command.hint
}

/**
 * 命令参数提示：仅当输入框处于 claim 态（裸 `/name` 或恰好一个尾随空格 `/name `）时返回展示提示；
 * 其余任何输入（参数、多余空格）都返回空串（提示消失），删除回到 claim 态时再次出现。
 */
internal fun dshCommandHintForDraft(draft: String, goalActive: Boolean): String {
    val t = draft.trimStart()
    if (!t.startsWith("/")) return ""
    val token = t.trimEnd()
    if (t != token && t != "$token ") return ""
    val name = token.removePrefix("/")
    if (name.isEmpty() || name.contains(' ') || name.contains('\n')) return ""
    val command = dshCommandNamed(name) ?: return ""
    return if (command.kind == DshCommandKind.WRITE_INPUT) dshCommandDisplayHint(command, goalActive) else ""
}

/**
 * 草稿开头被 claim 的命令 token（含其后的单个空格，形如 `/plan `），仅写入类命令返回非空。
 * 用于把命令 token 高亮成 web 的 claim 态颜色（`--dsw-alias-state-warn-label`）。
 */
internal fun dshClaimedCommandToken(draft: String): String {
    if (!draft.startsWith("/")) return ""
    val space = draft.indexOf(' ')
    val token = if (space < 0) draft else draft.substring(0, space)
    val command = dshCommandNamed(token.removePrefix("/")) ?: return ""
    if (command.kind != DshCommandKind.WRITE_INPUT) return ""
    return if (space < 0) token else draft.substring(0, space + 1)
}

/**
 * 草稿开头的技能引用 token（含前导 `/` 与其后的单个空格，形如 `/dsh-xxx `），命中用户可调用技能时
 * 返回非空（**允许后跟参数**）。用于把技能引用高亮成 web 的引用色（`--dsw-alias-state-business-primary`）。
 */
internal fun dshClaimedSkillToken(draft: String, skills: List<DshSkill>): String =
    dshClaimedSkillTokenByName(draft, skills.map { it.name })

/** 同上，但按技能名列表判定（供消息气泡渲染使用）。 */
internal fun dshClaimedSkillTokenByName(draft: String, skillNames: List<String>): String {
    if (!draft.startsWith("/")) return ""
    val space = draft.indexOf(' ')
    val token = if (space < 0) draft else draft.substring(0, space)
    val name = token.removePrefix("/")
    if (name.isEmpty()) return ""
    if (skillNames.none { it == name }) return ""
    return if (space < 0) token else draft.substring(0, space + 1)
}

/** 若整行是一条内置命令（形如 `/name ...`），返回命令名（不含斜杠）；否则空串。 */
internal fun dshBuiltinCommandName(line: String): String {
    val trimmed = line.trimStart()
    if (!trimmed.startsWith("/")) return ""
    val token = trimmed.removePrefix("/").substringBefore(' ').substringBefore('\n').substringBefore('\t')
    return if (dshCommandNamed(token) != null) token else ""
}
