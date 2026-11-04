package com.example.dsh.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 审批中心纯逻辑单测：筛选分组与计数、单选/多选答案组装、replyOnly 分流、
 * 已处理态去重、kind -> 文案映射。渲染层不在此处断言
 */
class BusApprovalModelTest {

    // ---------- 筛选分组与计数 ----------

    @Test
    fun filterTabsSplitDecisionAndReplyGroups() {
        val items = listOf(
            approvalItem(key = "a", kind = "command"),
            approvalItem(key = "b", kind = "permission"),
            approvalItem(key = "c", kind = "input", replyOnly = true),
        )
        val tabs = busApprovalFilterTabs(items, emptyMap())
        assertEquals(
            listOf("全部" to 3, "待审批" to 2, "等待输入" to 1),
            tabs.map { it.label to it.count },
        )
        assertEquals(
            listOf(BUS_APPROVAL_FILTER_ALL, BUS_APPROVAL_FILTER_DECISION, BUS_APPROVAL_FILTER_REPLY),
            tabs.map { it.id },
        )
    }

    @Test
    fun filterTabsStopCountingAnsweredCards() {
        val items = listOf(
            approvalItem(key = "a", kind = "command"),
            approvalItem(key = "b", kind = "permission"),
            approvalItem(key = "c", kind = "input", replyOnly = true),
        )
        // 处理掉一张命令卡：卡片留在原位，但三处计数都要跟着减
        val tabs = busApprovalFilterTabs(items, mapOf("a" to true))
        assertEquals(listOf(2, 1, 1), tabs.map { it.count })
        assertEquals(listOf(1, 0, 1), busApprovalFilterTabs(items, mapOf("a" to true, "b" to false)).map { it.count })
        assertEquals(
            listOf(0, 0, 0),
            busApprovalFilterTabs(items, mapOf("a" to true, "b" to false, "c" to true)).map { it.count },
        )
    }

    @Test
    fun filterIdRoutesByReplyOnly() {
        assertEquals(BUS_APPROVAL_FILTER_DECISION, busApprovalFilterId(approvalItem("a", kind = "command")))
        assertEquals(BUS_APPROVAL_FILTER_DECISION, busApprovalFilterId(approvalItem("b", kind = "fileChange")))
        assertEquals(BUS_APPROVAL_FILTER_REPLY, busApprovalFilterId(approvalItem("c", kind = "input", replyOnly = true)))
    }

    @Test
    fun visibleItemsFollowFilterAndFallBackToAll() {
        val decision = approvalItem("a", kind = "command")
        val reply = approvalItem("b", kind = "input", replyOnly = true)
        val items = listOf(decision, reply)
        assertEquals(listOf(decision), busApprovalVisibleItems(items, BUS_APPROVAL_FILTER_DECISION))
        assertEquals(listOf(reply), busApprovalVisibleItems(items, BUS_APPROVAL_FILTER_REPLY))
        assertEquals(items, busApprovalVisibleItems(items, BUS_APPROVAL_FILTER_ALL))
        // 未知段（含首屏空串）不吞卡片
        assertEquals(items, busApprovalVisibleItems(items, ""))
        assertEquals(items, busApprovalVisibleItems(items, "ghost"))
    }

    @Test
    fun rowsAppendEmptyRowAfterEveryFilter() {
        val decision = approvalItem("a", kind = "command")
        val reply = approvalItem("b", kind = "input", replyOnly = true)
        val items = listOf(decision, reply)
        assertEquals(
            listOf(BusApprovalRow.Card(decision), BusApprovalRow.Empty),
            busApprovalRows(items, BUS_APPROVAL_FILTER_DECISION),
        )
        assertEquals(
            listOf(BusApprovalRow.Card(decision), BusApprovalRow.Card(reply), BusApprovalRow.Empty),
            busApprovalRows(items, BUS_APPROVAL_FILTER_ALL),
        )
        // 一张卡都没有时也只剩空态行，空态才有地方渲染
        assertEquals(listOf(BusApprovalRow.Empty), busApprovalRows(emptyList(), BUS_APPROVAL_FILTER_ALL))
    }

    @Test
    fun hasOpenItemsOnlyCountsUnanswered() {
        val items = listOf(
            approvalItem("a", kind = "command"),
            approvalItem("b", kind = "input", replyOnly = true),
        )
        assertTrue(busApprovalHasOpenItems(items, BUS_APPROVAL_FILTER_ALL, emptyMap()))
        assertFalse(busApprovalHasOpenItems(items, BUS_APPROVAL_FILTER_ALL, mapOf("a" to true, "b" to true)))
        // 「待审批」段里没有等待输入的卡，所以它被处理完不影响该段
        assertTrue(busApprovalHasOpenItems(items, BUS_APPROVAL_FILTER_DECISION, mapOf("b" to true)))
        assertFalse(busApprovalHasOpenItems(items, BUS_APPROVAL_FILTER_DECISION, mapOf("a" to false)))
        assertTrue(busApprovalHasOpenItems(emptyList(), BUS_APPROVAL_FILTER_REPLY, emptyMap()).not())
    }

    // ---------- kind -> 文案 ----------

    @Test
    fun kindLabelsCoverProtocolKindsAndUnknown() {
        assertEquals("请求执行命令", busApprovalKindLabel("command"))
        assertEquals("请求修改文件", busApprovalKindLabel("fileChange"))
        assertEquals("请求权限", busApprovalKindLabel("permission"))
        assertEquals("等待你的回复", busApprovalKindLabel("input"))
        assertEquals("等待处理", busApprovalKindLabel(""))
        assertEquals("等待处理", busApprovalKindLabel("futureKind"))
    }

    @Test
    fun decisionLabelsDistinguishApproveRejectAndReply() {
        assertEquals("已批准", busApprovalDecisionLabel(approvalItem("a", kind = "command"), allow = true))
        assertEquals("已拒绝", busApprovalDecisionLabel(approvalItem("a", kind = "command"), allow = false))
        // 回复类卡片没有拒绝语义，只有 allow=true
        assertEquals("已回复", busApprovalDecisionLabel(approvalItem("a", kind = "input", replyOnly = true), allow = true))
        assertEquals("已回复", busApprovalDecisionLabel(approvalItem("a", kind = "input", replyOnly = true), allow = false))
    }

    @Test
    fun titleAppendsStateOnlyForAnsweredCards() {
        val item = approvalItem("a", kind = "command", title = "重构设置页并补测试")
        assertEquals("重构设置页并补测试", busApprovalTitle(item, emptyMap()))
        assertEquals(
            "重构设置页并补测试（已批准）",
            busApprovalTitle(item, mapOf("a" to true)),
        )
        assertEquals(
            "重构设置页并补测试（已拒绝）",
            busApprovalTitle(item, mapOf("a" to false)),
        )
        // 别的卡被处理过，不能污染这张卡的标题
        assertEquals("重构设置页并补测试", busApprovalTitle(item, mapOf("b" to true)))
    }

    // ---------- 命令块与问题正文 ----------

    @Test
    fun commandBlockShowsRequestDetailAndFallsBackToSummary() {
        // 设计稿的等宽块里是命令原文，来自 pendingRequest.detail
        assertEquals(
            "npm test -- --filter settings",
            busApprovalCommandText(
                approvalItem(
                    "a",
                    kind = "command",
                    summary = "Claude Code · 请求执行命令",
                    detail = "npm test -- --filter settings",
                ),
            ),
        )
        // 电脑端没给 detail（旧数据）才退回 summary
        assertEquals(
            "npm test -- --filter settings",
            busApprovalCommandText(approvalItem("b", kind = "command", summary = "npm test -- --filter settings")),
        )
        assertEquals(
            "",
            busApprovalCommandText(approvalItem("c", kind = "input", replyOnly = true, summary = "先看哪份日志？")),
        )
        // 回复类卡片即使带了 detail 也不出命令块
        assertEquals(
            "",
            busApprovalCommandText(
                approvalItem(
                    "d",
                    kind = "input",
                    replyOnly = true,
                    summary = "先看哪份日志？",
                    detail = "relay.log 是本次那份",
                ),
            ),
        )
    }

    @Test
    fun proseLinesPreferQuestionsThenDetailThenSummaryForReplyCards() {
        val asked = approvalItem(
            "b",
            kind = "input",
            replyOnly = true,
            summary = "先看哪份日志？",
            detail = "两份都能看，本次超时集中在握手",
            questions = listOf(question("0", "先看哪份日志？", multiSelect = true, "relay.log", "tunnel.log")),
        )
        assertEquals(listOf("先看哪份日志？"), busApprovalProseLines(asked))
        // 没问题可列时按命令块同一口径：先 detail，退回 summary
        assertEquals(
            listOf("两份都能看，本次超时集中在握手"),
            busApprovalProseLines(
                approvalItem(
                    "c",
                    kind = "input",
                    replyOnly = true,
                    summary = "现在就把它们升级到修复版本吗？",
                    detail = "两份都能看，本次超时集中在握手",
                ),
            ),
        )
        assertEquals(
            listOf("现在就把它们升级到修复版本吗？"),
            busApprovalProseLines(
                approvalItem("c", kind = "input", replyOnly = true, summary = "现在就把它们升级到修复版本吗？"),
            ),
        )
        // 命令卡只出命令块，不把摘要再念一遍
        assertEquals(
            emptyList<String>(),
            busApprovalProseLines(approvalItem("a", kind = "command", summary = "git merge pr-42")),
        )
    }

    // ---------- 勾选与答案组装 ----------

    @Test
    fun singleSelectKeepsOneLabelInOptionOrder() {
        val scope = question("scope", "批准范围", false, "只这一次", "以后都允许")
        assertEquals(listOf("以后都允许"), busApprovalNormalizeLabels(scope, listOf("以后都允许")))
        // 乱序勾选也按选项原序回给协议；单选最多留一个
        assertEquals(listOf("只这一次"), busApprovalNormalizeLabels(scope, listOf("只这一次", "以后都允许")))
    }

    @Test
    fun multiSelectKeepsEveryValidLabelOnce() {
        val logs = question("0", "先看哪份日志？", true, "relay.log", "tunnel.log")
        assertEquals(
            listOf("relay.log", "tunnel.log"),
            busApprovalNormalizeLabels(logs, listOf("tunnel.log", "relay.log", "tunnel.log")),
        )
        assertEquals(emptyList<String>(), busApprovalNormalizeLabels(logs, emptyList()))
    }

    @Test
    fun normalizeDropsUnknownAndBlankLabels() {
        val logs = question("0", "先看哪份日志？", true, "relay.log", "tunnel.log")
        assertEquals(
            listOf("relay.log"),
            busApprovalNormalizeLabels(logs, listOf("relay.log", "别的卡的答案", "", "  ")),
        )
    }

    @Test
    fun toggleOptionHandlesRadioMultiAndClear() {
        val single = question("scope", "批准范围", false, "只这一次", "以后都允许")
        val multi = question("0", "先看哪份日志？", true, "relay.log", "tunnel.log")

        assertEquals(listOf("只这一次"), busApprovalToggleOption(single, emptyList(), "只这一次"))
        // 单选：再点另一个直接换掉
        assertEquals(listOf("以后都允许"), busApprovalToggleOption(single, listOf("只这一次"), "以后都允许"))
        // 单选：再点自己取消（清空）
        assertEquals(emptyList<String>(), busApprovalToggleOption(single, listOf("以后都允许"), "以后都允许"))

        assertEquals(listOf("relay.log"), busApprovalToggleOption(multi, emptyList(), "relay.log"))
        assertEquals(listOf("relay.log", "tunnel.log"), busApprovalToggleOption(multi, listOf("relay.log"), "tunnel.log"))
        // 多选：点掉最后一个就是清空
        assertEquals(
            listOf("relay.log"),
            busApprovalToggleOption(multi, listOf("relay.log", "tunnel.log"), "tunnel.log"),
        )
        assertEquals(emptyList<String>(), busApprovalToggleOption(multi, listOf("relay.log"), "relay.log"))
        // 不在选项里的 label（含空串）不改状态
        assertEquals(listOf("relay.log"), busApprovalToggleOption(multi, listOf("relay.log"), "nope"))
        assertEquals(listOf("relay.log"), busApprovalToggleOption(multi, listOf("relay.log"), ""))
    }

    @Test
    fun toggleOptionDropsStaleLabelsOfSameQuestion() {
        val logs = question("0", "先看哪份日志？", true, "relay.log", "tunnel.log")
        // 电脑端把某个选项撤了：残留 label 在勾选时就被清掉，不会跟着发出去
        assertEquals(
            listOf("relay.log", "tunnel.log"),
            busApprovalToggleOption(logs, listOf("relay.log", "gone.log"), "tunnel.log"),
        )
    }

    @Test
    fun answersMapQuestionIdToLabelsAndSkipUnanswered() {
        val item = approvalItem(
            "dsh:s3|ask-01",
            kind = "input",
            replyOnly = true,
            questions = listOf(
                question("0", "先看哪份日志？", true, "relay.log", "tunnel.log"),
                question("1", "要不要顺手重启 relay？", false, "要", "不要"),
            ),
        )
        val key = busApprovalSelectionKey(item.key, "0")
        assertEquals("dsh:s3|ask-01|0", key)
        assertEquals(
            mapOf("0" to listOf("tunnel.log")),
            busApprovalAnswers(item, mapOf(key to listOf("tunnel.log"))),
        )
        // 两个问题都勾上：各自成一条答案
        assertEquals(
            mapOf("0" to listOf("relay.log", "tunnel.log"), "1" to listOf("要")),
            busApprovalAnswers(
                item,
                mapOf(
                    key to listOf("tunnel.log", "relay.log"),
                    busApprovalSelectionKey(item.key, "1") to listOf("要"),
                ),
            ),
        )
        // 一个都没选 -> 空 map（协议里 answers 整个键省略）
        assertEquals(emptyMap(), busApprovalAnswers(item, emptyMap()))
        // 别的卡片同名的勾选不能串过来
        assertEquals(
            emptyMap(),
            busApprovalAnswers(item, mapOf("other|0" to listOf("relay.log"))),
        )
    }

    @Test
    fun answersIgnoreSelectionsFromOtherCardsAndDirtyLabels() {
        val item = approvalItem(
            "a",
            kind = "command",
            questions = listOf(question("scope", "批准范围", false, "只这一次", "以后都允许")),
        )
        assertEquals(
            emptyMap(),
            busApprovalAnswers(item, mapOf(busApprovalSelectionKey("b", "scope") to listOf("以后都允许"))),
        )
        assertEquals(
            mapOf("scope" to listOf("以后都允许")),
            busApprovalAnswers(
                item,
                mapOf(busApprovalSelectionKey("a", "scope") to listOf("以后都允许", "乱来的值")),
            ),
        )
    }

    @Test
    fun hasSelectionFollowsAssembledAnswers() {
        val item = approvalItem(
            "a",
            kind = "command",
            questions = listOf(question("scope", "批准范围", false, "只这一次", "以后都允许")),
        )
        assertFalse(busApprovalHasSelection(item, emptyMap()))
        assertTrue(busApprovalHasSelection(item, mapOf(busApprovalSelectionKey("a", "scope") to listOf("只这一次"))))
        // 勾了又被电脑端撤掉的选项不算已选，主操作仍然是禁用态
        assertFalse(busApprovalHasSelection(item, mapOf(busApprovalSelectionKey("a", "scope") to listOf("gone"))))
        // 没有问题的卡片永远没有勾选
        assertFalse(busApprovalHasSelection(approvalItem("b", kind = "command"), mapOf("b|scope" to listOf("x"))))
    }

    // ---------- replyOnly 分流 ----------

    @Test
    fun quickSendOnlyForSingleSelectReplyQuestions() {
        val single = question("0", "先看哪份日志？", false, "relay.log", "tunnel.log")
        val multi = question("0", "先看哪份日志？", true, "relay.log", "tunnel.log")
        assertTrue(busApprovalQuickSend(approvalItem("a", kind = "input", replyOnly = true, questions = listOf(single))))
        assertFalse(busApprovalQuickSend(approvalItem("b", kind = "input", replyOnly = true, questions = listOf(multi))))
        assertFalse(
            busApprovalQuickSend(
                approvalItem("c", kind = "input", replyOnly = true, questions = listOf(single, single.copy(id = "1"))),
            ),
        )
        // 没问题可点，走「回复...」入口
        assertFalse(busApprovalQuickSend(approvalItem("d", kind = "input", replyOnly = true)))
        // 命令卡即使只有一个单选范围问题，也要显式点「批准」表态
        assertFalse(busApprovalQuickSend(approvalItem("e", kind = "command", questions = listOf(single))))
    }

    @Test
    fun primaryLabelDiffersForReplyCards() {
        assertEquals("批准", busApprovalPrimaryLabel(approvalItem("a", kind = "command")))
        assertEquals("批准", busApprovalPrimaryLabel(approvalItem("b", kind = "permission")))
        assertEquals("发送回复", busApprovalPrimaryLabel(approvalItem("c", kind = "input", replyOnly = true)))
    }

    // ---------- 接真实投影 ----------

    @Test
    fun fixtureItemsRouteIntoTabsAndRows() {
        val items = previewBusHomeView().approvalItems(emptySet())
        // s1 = permission（要表态）、s3 = input（只要回复）
        assertEquals(
            listOf(
                BusApprovalFilterTab(BUS_APPROVAL_FILTER_ALL, "全部", 2),
                BusApprovalFilterTab(BUS_APPROVAL_FILTER_DECISION, "待审批", 1),
                BusApprovalFilterTab(BUS_APPROVAL_FILTER_REPLY, "等待输入", 1),
            ),
            busApprovalFilterTabs(items, emptyMap()),
        )
        assertEquals(
            listOf("acp:claude-code:s1|toolu_01MergePr"),
            busApprovalRows(items, BUS_APPROVAL_FILTER_DECISION).filterIsInstance<BusApprovalRow.Card>()
                .map { it.item.key },
        )

        val ask = items.first { it.replyOnly }
        // s3 是多选，不能点一下就发；先勾选再「发送回复」
        assertFalse(busApprovalQuickSend(ask))
        // 回复类卡片没有命令块，正文仍是问题本身（不是 detail）
        assertEquals("", busApprovalCommandText(ask))
        assertEquals(listOf("先看哪份日志？"), busApprovalProseLines(ask))
        assertEquals(
            mapOf("0" to listOf("relay.log")),
            busApprovalAnswers(ask, mapOf(busApprovalSelectionKey(ask.key, "0") to listOf("relay.log"))),
        )

        val merge = items.first { !it.replyOnly }
        assertEquals("请求权限", busApprovalKindLabel(merge.kind))
        assertEquals("合并前端打包优化 PR", busApprovalTitle(merge, emptyMap()))
        // 命令块显示的是 detail 里的命令原文，不是 summary 那句标题
        assertEquals("git merge --no-ff origin/feature/packaging", busApprovalCommandText(merge))
        assertEquals(
            "合并前端打包优化 PR（已批准）",
            busApprovalTitle(merge, mapOf(merge.key to true)),
        )
        // 单选范围问题：勾「以后都允许」再批准，answers 只带这一条
        assertEquals(
            mapOf("scope" to listOf("以后都允许")),
            busApprovalAnswers(
                merge,
                mapOf(
                    busApprovalSelectionKey(merge.key, "scope") to
                        busApprovalToggleOption(
                            merge.questions.single(),
                            listOf("只这一次"),
                            "以后都允许",
                        ),
                ),
            ),
        )
    }

    private fun approvalItem(
        key: String,
        kind: String,
        title: String = "任务 $key",
        replyOnly: Boolean = kind !in busApprovalDecisionKinds,
        summary: String = "摘要 $key",
        detail: String = "",
        questions: List<BusApprovalQuestion> = emptyList(),
    ): BusApprovalItem = BusApprovalItem(
        key = key,
        taskId = key.substringBefore('|'),
        requestId = key.substringAfter('|', key),
        kind = kind,
        title = title,
        project = "agentbus",
        agentName = "DSH",
        time = "10-29 08:29",
        summary = summary,
        detail = detail,
        questions = questions,
        replyOnly = replyOnly,
    )

    private fun question(
        id: String,
        text: String,
        multiSelect: Boolean,
        vararg labels: String,
    ): BusApprovalQuestion = BusApprovalQuestion(
        id = id,
        question = text,
        options = labels.map { it to "" },
        multiSelect = multiSelect,
    )
}
