package com.example.dsh.ui.home

/**
 * 统一 Agent 审批中心（v86 审批视图）的纯逻辑
 *
 * 这里只有「输入 -> 输出」的函数，不碰 Kuikly 渲染、也不持有状态：
 * 卡片数据来自 [approvalItems]，筛选段、勾选中的选项、已处理记录都由页面
 * （DshBusHomePage）持有，发命令统一走 `BusCommands.approve`
 *
 * 之所以单独成文件：审批视图的卡片不会因处理完就消失（快照 2 秒轮询会把它
 * 原样投回来），所以「这条算不算已处理」「这一屏该渲染哪几行」必须能在
 * commonTest 里逐条断言，不能只靠肉眼看渲染结果
 */

/** 审批视图顶部筛选段：全部 / 待审批 / 等待输入（对齐 HTML `approve-filters`）。 */
internal const val BUS_APPROVAL_FILTER_ALL = "all"
internal const val BUS_APPROVAL_FILTER_DECISION = "decision"
internal const val BUS_APPROVAL_FILTER_REPLY = "reply"

/** 一个筛选段：id、chip 文案、还剩下多少条没处理。 */
internal data class BusApprovalFilterTab(val id: String, val label: String, val count: Int)

/**
 * 已处理记录：`BusApprovalItem.key` -> 当时点的是批准（true）还是拒绝（false）
 *
 * 快照不会立刻把请求撤掉，所以「处理过没有」只能由页面本地记；
 * 值用来决定标题后面追加「已批准」还是「已拒绝」（回复类卡片固定「已回复」）
 */
internal typealias BusApprovalAnswered = Map<String, Boolean>

/** 卡片归属的筛选段：`replyOnly` 走「等待输入」，其余（命令/改动/权限）走「待审批」。 */
internal fun busApprovalFilterId(item: BusApprovalItem): String =
    if (item.replyOnly) BUS_APPROVAL_FILTER_REPLY else BUS_APPROVAL_FILTER_DECISION

/**
 * 三个筛选段及其计数
 *
 * 已处理的卡片留在原位，但不再计数——HTML 里「全部 3」会随每次点批准递减
 */
internal fun busApprovalFilterTabs(
    items: List<BusApprovalItem>,
    answered: BusApprovalAnswered,
): List<BusApprovalFilterTab> {
    val open = items.filterNot { it.key in answered }
    return listOf(
        BusApprovalFilterTab(BUS_APPROVAL_FILTER_ALL, "全部", open.size),
        BusApprovalFilterTab(
            BUS_APPROVAL_FILTER_DECISION,
            "待审批",
            open.count { !it.replyOnly },
        ),
        BusApprovalFilterTab(
            BUS_APPROVAL_FILTER_REPLY,
            "等待输入",
            open.count { it.replyOnly },
        ),
    )
}

/** 当前筛选段下要渲染的卡片；未知 id（含空串）一律回落「全部」。 */
internal fun busApprovalVisibleItems(
    items: List<BusApprovalItem>,
    filter: String,
): List<BusApprovalItem> = when (filter) {
    BUS_APPROVAL_FILTER_DECISION -> items.filter { !it.replyOnly }
    BUS_APPROVAL_FILTER_REPLY -> items.filter { it.replyOnly }
    else -> items
}

/** 当前筛选段里还有没有没处理的卡片（决定要不要出空态）。 */
internal fun busApprovalHasOpenItems(
    items: List<BusApprovalItem>,
    filter: String,
    answered: BusApprovalAnswered,
): Boolean = busApprovalVisibleItems(items, filter).any { it.key !in answered }

/**
 * `vforLazy` 只能吃一维列表：当前段的卡片，末尾固定挂一个空态行
 *
 * 空态行本身是否显示由渲染层用 `vif` 决定（它读页面的 answered 状态），
 * 这样处理完最后一张卡时只换掉这一块，不会把整列重建、丢滚动位置
 */
internal fun busApprovalRows(
    items: List<BusApprovalItem>,
    filter: String,
): List<BusApprovalRow> =
    busApprovalVisibleItems(items, filter).map { BusApprovalRow.Card(it) } + BusApprovalRow.Empty

/** 审批列表的行：一张卡，或者末尾那条空态。 */
internal sealed class BusApprovalRow {
    data class Card(val item: BusApprovalItem) : BusApprovalRow()
    object Empty : BusApprovalRow()
}

/** `pendingRequest.kind` -> 卡片上的请求说明（HTML：`Claude Code · 请求执行命令`）。 */
internal fun busApprovalKindLabel(kind: String): String = when (kind) {
    "command" -> "请求执行命令"
    "fileChange" -> "请求修改文件"
    "permission" -> "请求权限"
    "input" -> "等待你的回复"
    else -> "等待处理"
}

/** 处理完追加到标题后的状态字样。 */
internal fun busApprovalDecisionLabel(item: BusApprovalItem, allow: Boolean): String = when {
    item.replyOnly -> "已回复"
    allow -> "已批准"
    else -> "已拒绝"
}

/** 标题 + 已处理字样；没处理过就只给原标题。 */
internal fun busApprovalTitle(
    item: BusApprovalItem,
    answered: BusApprovalAnswered,
): String {
    val allow = answered[item.key] ?: return item.title
    return "${item.title}（${busApprovalDecisionLabel(item, allow)}）"
}

/**
 * 等宽命令块要显示的内容；只有走「批准 / 拒绝」的卡片才有，回复类卡片为空串
 *
 * 优先 `detail`（协议里放的是命令原文或 diff 摘要，HTML 那块 `npm test -- --filter settings`
 * 就是它），电脑端没给 `detail` 时才退回 `summary` 这句话标题
 */
internal fun busApprovalCommandText(item: BusApprovalItem): String =
    if (item.replyOnly) "" else item.detail.ifBlank { item.summary }

/**
 * 14/20 的问题正文行
 *
 * 回复类卡片优先逐条列出 `questions[].question`（HTML 的「先看哪份日志？」）；
 * 没有问题时按命令块同一口径取文本（先 `detail`，退回 `summary`）；
 * 命令/权限类卡片走命令块，不再重复一遍正文
 */
internal fun busApprovalProseLines(item: BusApprovalItem): List<String> {
    if (!item.replyOnly) return emptyList()
    val questions = item.questions.map { it.question }.filter { it.isNotBlank() }
    val fallback = item.detail.ifBlank { item.summary }
    return questions.ifEmpty { listOf(fallback).filter { it.isNotBlank() } }
}

/** 勾选状态的存储 key：一张卡可能带多个问题，必须按「卡片|问题」分格存。 */
internal fun busApprovalSelectionKey(itemKey: String, questionId: String): String =
    "$itemKey|$questionId"

/**
 * 把任意来源的勾选清洗成该问题合法的 label 列表：
 * 丢掉不在选项里的、丢掉空串、按选项原序去重；单选最多留一个
 */
internal fun busApprovalNormalizeLabels(
    question: BusApprovalQuestion,
    labels: List<String>,
): List<String> {
    val chosen = labels.toSet()
    val kept = question.options.map { it.first }.filter { it.isNotBlank() && it in chosen }
    return if (question.multiSelect) kept else kept.take(1)
}

/**
 * 点一下选项：已选中就取消（多选可以清空到空），未选中则加入；
 * 单选直接互斥换成它
 */
internal fun busApprovalToggleOption(
    question: BusApprovalQuestion,
    selected: List<String>,
    label: String,
): List<String> {
    if (label.isBlank() || question.options.none { it.first == label }) return selected
    val current = busApprovalNormalizeLabels(question, selected)
    return if (label in current) {
        current - label
    } else if (question.multiSelect) {
        busApprovalNormalizeLabels(question, current + label)
    } else {
        listOf(label)
    }
}

/** 这张卡当前问题里有没有勾选（决定主操作按钮是实心还是描边）。 */
internal fun busApprovalHasSelection(
    item: BusApprovalItem,
    selection: Map<String, List<String>>,
): Boolean = busApprovalAnswers(item, selection).isNotEmpty()

/**
 * 组装 `approve.answers`：`questionId` -> 选中的 label 列表
 *
 * 一个都没选的问题整个省略（协议规定空答案要和旧客户端一致，不带 `answers` 键）；
 * 勾选里混进的脏 label / 别的问题的 label 也在这里被过滤掉
 */
internal fun busApprovalAnswers(
    item: BusApprovalItem,
    selection: Map<String, List<String>>,
): Map<String, List<String>> {
    val answers = LinkedHashMap<String, List<String>>()
    item.questions.forEach { question ->
        val selected = selection[busApprovalSelectionKey(item.key, question.id)].orEmpty()
        val labels = busApprovalNormalizeLabels(question, selected)
        if (labels.isNotEmpty()) {
            answers[question.id] = labels
        }
    }
    return answers
}

/**
 * 能不能点一下选项就直接发送
 *
 * 只带一个单选问题的回复类卡片才是（HTML 的 `relay.log` / `tunnel.log` 两个按钮）；
 * 多选、带多个问题、或者需要表态的命令/权限卡都要先勾再点主操作
 */
internal fun busApprovalQuickSend(item: BusApprovalItem): Boolean {
    val question = item.questions.singleOrNull() ?: return false
    return item.replyOnly && !question.multiSelect
}

/** 主操作按钮文案：回复类是「发送回复」，其余是「批准」。 */
internal fun busApprovalPrimaryLabel(item: BusApprovalItem): String =
    if (item.replyOnly) "发送回复" else "批准"
