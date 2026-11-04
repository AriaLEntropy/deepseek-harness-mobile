package com.example.dsh.ui.chat

import com.example.dsh.message.DshMessage
import com.example.dsh.message.DshMessageRole
import com.example.dsh.theme.DshDefaultDarkTokens
import com.example.dsh.theme.DshDefaultLightTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 对话页纯函数断言：模型芯片、工具摘要、正文分块、审批文案与深浅色映射
 *
 * 这些都决定屏幕上「字写成什么、卡片落在哪一档灰」，样式回归先在这里拦住
 */
class BusChatModelTest {

    private fun tool(name: String, content: String, failed: Boolean = false) = DshMessage(
        id = "tool-$name",
        role = DshMessageRole.TOOL,
        content = content,
        toolName = name,
        toolError = failed,
    )

    @Test
    fun modelChipKeepsSuffixAndEffort() {
        assertEquals("V4-Flash · High", busChatModelChipLabel("DeepSeek-V4-Flash", "m", "high"))
        assertEquals("Claude Opus 4.1", busChatModelChipLabel("Claude Opus 4.1", "m", ""))
        assertEquals("v4-pro · Medium", busChatModelChipLabel("", "deepseek-v4-pro", "medium"))
        assertEquals("默认模型", busChatModelChipLabel("", "", ""))
    }

    @Test
    fun modelSuffixStripsOnlyVendorPrefix() {
        assertEquals("V4-Flash", busChatModelSuffix("DeepSeek-V4-Flash"))
        assertEquals("5.1", busChatModelSuffix("gpt-5.1"))
        assertEquals("Claude Opus 4.1", busChatModelSuffix("Claude Opus 4.1"))
    }

    @Test
    fun agentSubtitleUsesProviderThenLocal() {
        assertEquals("OpenAI · 本机", busChatAgentSubtitle("codex"))
        assertEquals("Anthropic · 本机", busChatAgentSubtitle("claude"))
        assertEquals("DeepSeek · 本机", busChatAgentSubtitle("dsh"))
        assertEquals("通用 ACP 连接器", busChatAgentSubtitle("acp"))
        assertEquals("本机 Agent", busChatAgentSubtitle("other"))
    }

    @Test
    fun effortLabelCapitalisesUnknownTier() {
        assertEquals("High", busChatEffortLabel("high"))
        assertEquals("XHigh", busChatEffortLabel("xhigh"))
        assertEquals("", busChatEffortLabel("  "))
    }

    @Test
    fun blocksSplitParagraphHeadingListAndCode() {
        val blocks = busChatBlocks(
            """
            # 结论
            第一段
            第二行

            - 条目一
            - 条目二

            1. 读文件
            2. 改文件

            ```
            npm run build
            ```
            """.trimIndent(),
        )
        assertEquals(5, blocks.size)
        assertEquals(BusChatBlock.Heading("结论"), blocks[0])
        assertEquals(BusChatBlock.Paragraph("第一段\n第二行"), blocks[1])
        assertEquals(BusChatBlock.Bullets(listOf("条目一", "条目二")), blocks[2])
        assertEquals(BusChatBlock.Numbered(listOf("读文件", "改文件")), blocks[3])
        assertEquals(BusChatBlock.Code("npm run build"), blocks[4])
    }

    @Test
    fun inlineMarkdownIsStripped() {
        assertEquals("看这里 完整说明", busChatPlainInline("**看这里** [完整说明](https://example.com)"))
        assertEquals("code 不换字体", busChatPlainInline("`code` 不换字体"))
        val block = busChatBlocks("正文里有 `npm run build` 和 **加粗**").single()
        assertEquals(BusChatBlock.Paragraph("正文里有 npm run build 和 加粗"), block)
    }

    @Test
    fun toolSummaryCountsWritesAndEdits() {
        val tools = listOf(
            tool("read_file", "读取 a.kt"),
            tool("write_file", "写入 b.kt"),
            tool("apply_patch", "编辑 c.kt"),
            tool("bash", "ls -lh"),
        )
        assertEquals("查看 4 个步骤，写入 1 个文件，编辑 1 个文件", busChatToolSummary(tools))
        assertEquals("查看 1 个步骤", busChatToolSummary(listOf(tool("bash", "ls"))))
        val parts = busChatToolSummaryParts(tools)
        assertEquals("查看 4 个步骤", parts.head)
        assertEquals("，写入 1 个文件，编辑 1 个文件", parts.tail)
        assertEquals("查看 1 个步骤", busChatToolSummaryParts(listOf(tool("bash", "ls"))).head)
    }

    @Test
    fun expandedToolGroupHeadSwapsToCollapse() {
        val head = busChatToolSummaryParts(listOf(tool("bash", "ls"))).head
        assertEquals("查看 1 个步骤", head)
        assertEquals("收起 1 个步骤", busChatToolSummaryHead(head, true))
        assertEquals(head, busChatToolSummaryHead(head, false))
        // 补充段不参与替换，展开后仍是「，写入 N 个文件」
        val group = busChatToolSummaryParts(listOf(tool("write", "a.ts"), tool("bash", "ls")))
        assertEquals("收起 2 个步骤", busChatToolSummaryHead(group.head, true))
        assertEquals("，写入 1 个文件", group.tail)
    }

    @Test
    fun stepRowsCarryLabelSummaryAndFailure() {
        val step = busChatSteps(listOf(tool("bash", "ls -lh /tmp\n", failed = true))).single()
        assertEquals("执行命令", step.label)
        assertEquals("ls -lh /tmp", step.summary)
        assertTrue(step.failed)
    }

    @Test
    fun approvalTitleAndCodeLines() {
        assertEquals("需要批准：写入 src/payment/refund.ts", busChatApprovalTitle("fileChange", "写入 src/payment/refund.ts"))
        assertEquals("需要回复：选哪一项", busChatApprovalTitle("input", "选哪一项"))
        assertEquals(
            listOf("- old", "+ new"),
            busChatApprovalCodeLines("- old\n\n+ new\n+ extra"),
        )
        assertTrue(busChatApprovalCodeLines(" ").isEmpty())
    }

    @Test
    fun autoApproveRequiresSupportAndProject() {
        assertTrue(busChatCanShowAutoApprove(canAutoApprove = true, hasProject = true))
        assertFalse(busChatCanShowAutoApprove(canAutoApprove = true, hasProject = false))
        assertFalse(busChatCanShowAutoApprove(canAutoApprove = false, hasProject = true))
    }

    @Test
    fun rowGapFollowsTurnRules() {
        val user = BusChatRow.Message(DshMessage("u", DshMessageRole.USER, "hi"))
        val assistant = BusChatRow.Message(DshMessage("a", DshMessageRole.ASSISTANT, "yo"))
        val group = BusChatRow.ToolGroup("g", listOf(tool("bash", "ls")))
        assertEquals(0f, busChatRowGap(null, user))
        assertEquals(24f, busChatRowGap(assistant, user))
        assertEquals(8f, busChatRowGap(user, group))
        assertEquals(14f, busChatRowGap(group, assistant))
        assertEquals(8f, busChatRowGap(user, assistant))
    }

    @Test
    fun lightPaletteKeepsMockValues() {
        val p = busChatPalette(DshDefaultLightTokens)
        assertFalse(p.isDark)
        assertEquals(0xFFF4F4F6L, p.bgBase.hexColor)
        assertEquals(0xFF6E5FA3L, p.kuik.hexColor)
        assertEquals(0xFF1D1B20L, p.solid.hexColor)
        assertEquals(0xFFF3F3F6L, p.glassBase.hexColor)
    }

    @Test
    fun darkPaletteFollowsThemeTokensAndDropsGlow() {
        val colors = DshDefaultDarkTokens
        val p = busChatPalette(colors)
        assertTrue(p.isDark)
        assertEquals(colors.bgCanvas.hexColor, p.bgBase.hexColor)
        assertEquals(colors.bgModulePlatform.hexColor, p.surface.hexColor)
        assertEquals(colors.labelPrimary.hexColor, p.ink.hexColor)
        assertEquals(colors.labelPrimaryForeground.hexColor, p.onSolid.hexColor)
        assertEquals(0x00000000L, p.glowTop.hexColor)
        assertEquals(0x00000000L, p.glowClear.hexColor)
        // 玻璃必须跟着页面底色走，不能残留浅色的 #F3F3F6
        assertTrue(p.glassBase.hexColor != 0xFFF3F3F6L)
    }
}
