package com.example.dsh.ui.chat

import com.example.dsh.message.DshMessage

/**
 * 对话页的纯逻辑：模型芯片文案、工具摘要、正文分块、审批标题
 *
 * 全部是「输入 -> 输出」的函数，不碰 Kuikly、不持有状态，页面与单测共用，
 * 避免同一套文案在输入条、浮层和消息流里各写一遍
 */

/** 正文分块：一段、一个小标题、一组项目符号、一组有序步骤或一段代码 */
internal sealed class BusChatBlock {
    data class Paragraph(val text: String) : BusChatBlock()
    data class Heading(val text: String) : BusChatBlock()
    data class Bullets(val items: List<String>) : BusChatBlock()
    data class Numbered(val items: List<String>) : BusChatBlock()
    data class Code(val text: String) : BusChatBlock()
}

/**
 * Markdown 正文 → 分块
 *
 * 只认对话里真正会出现的几种：段落、`#` 标题、`-`/`*` 列表、`1.` 步骤、``` 代码块
 * 行内 `**`、反引号与链接语法会被抹平，正文里不再出现等宽字体或星号
 */
internal fun busChatBlocks(content: String): List<BusChatBlock> {
    if (content.isBlank()) return emptyList()
    val blocks = ArrayList<BusChatBlock>()
    val lines = content.replace("\r\n", "\n").split('\n')
    val paragraph = ArrayList<String>()
    val bullets = ArrayList<String>()
    val numbered = ArrayList<String>()
    var code: StringBuilder? = null

    fun flushParagraph() {
        if (paragraph.isEmpty()) return
        blocks += BusChatBlock.Paragraph(busChatPlainInline(paragraph.joinToString("\n")))
        paragraph.clear()
    }

    fun flushBullets() {
        if (bullets.isEmpty()) return
        blocks += BusChatBlock.Bullets(bullets.map(::busChatPlainInline))
        bullets.clear()
    }

    fun flushNumbered() {
        if (numbered.isEmpty()) return
        blocks += BusChatBlock.Numbered(numbered.map(::busChatPlainInline))
        numbered.clear()
    }

    fun flushAll() {
        flushParagraph()
        flushBullets()
        flushNumbered()
    }

    for (raw in lines) {
        val line = raw.trimEnd()
        if (line.trimStart().startsWith("```")) {
            val open = code
            if (open == null) {
                flushAll()
                code = StringBuilder()
            } else {
                blocks += BusChatBlock.Code(open.toString().trimEnd())
                code = null
            }
            continue
        }
        val current = code
        if (current != null) {
            if (current.isNotEmpty()) current.append('\n')
            current.append(raw)
            continue
        }
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> flushAll()
            trimmed.startsWith("#") -> {
                flushAll()
                blocks += BusChatBlock.Heading(busChatPlainInline(trimmed.trimStart('#').trim()))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                flushParagraph()
                flushNumbered()
                bullets += trimmed.drop(2).trim()
            }
            busChatNumberedPrefix(trimmed) != null -> {
                flushParagraph()
                flushBullets()
                numbered += busChatNumberedPrefix(trimmed).orEmpty()
            }
            else -> {
                flushBullets()
                flushNumbered()
                paragraph += line
            }
        }
    }
    if (code != null) blocks += BusChatBlock.Code(code.toString().trimEnd())
    flushAll()
    return blocks
}

/** `1. xxx` / `1) xxx` 的后半段；不是有序步骤时返回 null */
private fun busChatNumberedPrefix(line: String): String? {
    var index = 0
    while (index < line.length && line[index].isDigit()) index += 1
    if (index == 0 || index >= line.length) return null
    val sep = line[index]
    if (sep != '.' && sep != ')' && sep != '、') return null
    val rest = line.substring(index + 1).trim()
    return rest.ifEmpty { null }
}

/** 行内 Markdown 抹平：去星号、反引号和链接外壳，只留可读文字 */
internal fun busChatPlainInline(text: String): String {
    var out = text
        .replace("**", "")
        .replace("__", "")
        .replace("~~", "")
        .replace("`", "")
    val link = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
    out = link.replace(out) { it.groupValues[1] }
    return out.trim()
}

/**
 * 模型芯片文案：只显示后缀 + 强度
 *
 * `DeepSeek-V4-Flash` → `V4-Flash · High`；没有连字符时保留原名
 * 强度缺省只显示模型名，空数据回落 `默认模型`
 */
internal fun busChatModelChipLabel(
    displayName: String,
    modelId: String,
    effort: String,
): String {
    val name = busChatModelSuffix(displayName.ifBlank { modelId })
    val suffix = busChatEffortLabel(effort)
    return when {
        name.isEmpty() -> "默认模型"
        suffix.isEmpty() -> name
        else -> "$name · $suffix"
    }
}

/** 去掉厂商前缀：第一个 `-` 之后的部分；只有一个词时原样返回 */
internal fun busChatModelSuffix(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return ""
    val dash = trimmed.indexOf('-')
    if (dash <= 0 || dash == trimmed.lastIndex) return trimmed
    return trimmed.substring(dash + 1)
}

/** Agent 选择行的副标题：厂商/连接器类型 + 本机 */
internal fun busChatAgentSubtitle(source: String): String {
    val provider = when (source) {
        "codex" -> "OpenAI"
        "claude" -> "Anthropic"
        "deepseek", "dsh" -> "DeepSeek"
        "acp" -> "通用 ACP 连接器"
        else -> "本机 Agent"
    }
    return if (provider == "通用 ACP 连接器" || provider == "本机 Agent") {
        provider
    } else {
        "$provider · 本机"
    }
}

/** 推理等级：已知档位用设计稿的写法，未知档位只把首字母抬高 */
internal fun busChatEffortLabel(effort: String): String {
    val value = effort.trim()
    if (value.isEmpty()) return ""
    return when (value.lowercase()) {
        "minimal" -> "Minimal"
        "low" -> "Low"
        "medium" -> "Medium"
        "high" -> "High"
        "xhigh" -> "XHigh"
        "max" -> "Max"
        else -> value.replaceFirstChar { it.uppercaseChar() }
    }
}

/** 工具组折叠头：查看 N 个步骤，写入/编辑按 toolName 与正文里的关键词归类 */
internal fun busChatToolSummary(tools: List<DshMessage>): String {
    val steps = tools.size
    val writes = tools.count { busChatToolMatches(it, "write", "create", "写入", "新建") }
    val edits = tools.count {
        !busChatToolMatches(it, "write", "create", "写入", "新建") &&
            busChatToolMatches(it, "edit", "replace", "patch", "编辑", "修改")
    }
    return buildString {
        append("查看 $steps 个步骤")
        if (writes > 0) append("，写入 $writes 个文件")
        if (edits > 0) append("，编辑 $edits 个文件")
    }
}

/** 折叠态那句「查看 N 个步骤」，展开态换成「收起 N 个步骤」；补充段不动 */
internal fun busChatToolSummaryHead(head: String, expanded: Boolean): String =
    if (expanded) head.replace("查看", "收起") else head

/** 工具组摘要拆成两段：13/600 的「查看 N 个步骤」+ 12.5/400 的写入/编辑补充 */
internal data class BusChatToolSummaryParts(val head: String, val tail: String)

internal fun busChatToolSummaryParts(tools: List<DshMessage>): BusChatToolSummaryParts {
    val summary = busChatToolSummary(tools)
    val cut = summary.indexOf('，')
    return if (cut < 0) {
        BusChatToolSummaryParts(summary, "")
    } else {
        BusChatToolSummaryParts(summary.substring(0, cut), summary.substring(cut))
    }
}

private fun busChatToolMatches(message: DshMessage, vararg keys: String): Boolean {
    val name = message.toolName.orEmpty().lowercase()
    if (keys.any { name.contains(it.lowercase()) }) return true
    val head = message.content.lineSequence().firstOrNull().orEmpty().lowercase()
    return keys.any { it.length > 1 && head.contains(it.lowercase()) }
}

/** 工具列表的一行：标签 + 摘要 + 是否失败 */
internal data class BusChatStep(
    val label: String,
    val summary: String,
    val failed: Boolean,
)

/** 工具消息 → 单步行；摘要取第一行，失败只留图标 */
internal fun busChatSteps(tools: List<DshMessage>): List<BusChatStep> = tools.map { tool ->
    BusChatStep(
        label = busChatToolLabel(tool),
        summary = busChatToolLine(tool),
        failed = tool.toolError,
    )
}

internal fun busChatToolLabel(message: DshMessage): String {
    val name = message.toolName.orEmpty().lowercase()
    val head = message.content.lineSequence().firstOrNull().orEmpty().lowercase()
    return when {
        name.contains("think") || head.contains("深度思考") -> "深度思考"
        name.contains("terminal") || name.contains("bash") || name.contains("shell") -> "执行命令"
        name.contains("write") || head.contains("写入") -> "写入文件"
        name.contains("edit") || head.contains("编辑") -> "编辑文件"
        name.contains("read") || head.contains("读取") -> "读取文件"
        name.contains("web") || name.contains("search") -> "联网搜索"
        name.contains("diff") -> "查看改动"
        name.isNotBlank() && name != "tool" -> message.toolName.orEmpty()
        else -> "执行命令"
    }
}

/** 单步摘要：第一行非空正文，超长由渲染层省略 */
internal fun busChatToolLine(message: DshMessage): String =
    message.content.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()

/** 审批卡标题：`需要批准：写入 src/payment/refund.ts`，回复类改「需要回复」 */
internal fun busChatApprovalTitle(kind: String, summary: String): String {
    val detail = summary.trim().ifBlank {
        when (kind) {
            "command" -> "执行命令"
            "fileChange" -> "修改文件"
            "permission" -> "授予权限"
            "input" -> "回答这个问题"
            else -> "处理请求"
        }
    }
    return if (kind == "input") "需要回复：$detail" else "需要批准：$detail"
}

/** 审批卡代码井：最多两行，空行丢弃 */
internal fun busChatApprovalCodeLines(detail: String, max: Int = 2): List<String> {
    if (detail.isBlank()) return emptyList()
    return detail.replace("\r\n", "\n")
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(max)
}

/** 自动批准只在支持且不在 outsideProject 的项目里出现 */
internal fun busChatCanShowAutoApprove(canAutoApprove: Boolean, hasProject: Boolean): Boolean =
    canAutoApprove && hasProject

/** 只读说明文案 */
internal const val BUS_CHAT_READONLY_HINT = "这个会话只能在电脑上继续"

/**
 * 消息行间距：换轮 24 / 同轮 8 / 工具后接正文 14
 *
 * 工具组已经吸收相邻工具行，所以只需要区分「用户开新轮」与「工具之后的正文」
 */
internal fun busChatRowGap(previous: BusChatRow?, current: BusChatRow): Float {
    if (previous == null) return 0f
    val currentMessage = (current as? BusChatRow.Message)?.message
    if (currentMessage?.role == com.example.dsh.message.DshMessageRole.USER) {
        return BusChatTokens.gapTurn
    }
    if (currentMessage?.role == com.example.dsh.message.DshMessageRole.TOOL || current is BusChatRow.ToolGroup) {
        return BusChatTokens.gapWithinTurn
    }
    val previousIsTool = previous is BusChatRow.ToolGroup ||
        (previous as? BusChatRow.Message)?.message?.role == com.example.dsh.message.DshMessageRole.TOOL
    return if (previousIsTool) BusChatTokens.gapWithinTurn + 6f else BusChatTokens.gapWithinTurn
}

/** 输入条空闲 / 运行中的占位文案 */
internal const val BUS_CHAT_PLACEHOLDER_IDLE = "给智能体发消息"
internal const val BUS_CHAT_PLACEHOLDER_RUNNING = "等待任务同步完成…"

/**
 * 「从列表移除」的本机隐藏集合
 *
 * FR-5.6 只要求从本机列表拿掉，不删电脑上的会话；这里只保留到进程结束，
 * 快照刷新回来也不会重新冒出来，重启 app 后恢复
 */
internal object BusChatLocalHidden {
    private val ids = mutableSetOf<String>()

    fun hide(taskId: String) {
        if (taskId.isNotBlank()) ids += taskId
    }

    fun isHidden(taskId: String): Boolean = taskId.isNotBlank() && taskId in ids
}
