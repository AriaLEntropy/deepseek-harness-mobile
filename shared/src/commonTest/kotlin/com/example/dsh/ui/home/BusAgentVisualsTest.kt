package com.example.dsh.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals

class BusAgentVisualsTest {
    @Test
    fun knownSourcesMapToOfficialMarks() {
        assertEquals(BusAgentVisuals.CODEX, BusAgentVisuals.assetFor("codex"))
        assertEquals(BusAgentVisuals.CLAUDE, BusAgentVisuals.assetFor("claude"))
        assertEquals(BusAgentVisuals.DEEPSEEK, BusAgentVisuals.assetFor("dsh"))
        assertEquals(BusAgentVisuals.HERMES, BusAgentVisuals.assetFor("hermes"))
        assertEquals(BusAgentVisuals.PI, BusAgentVisuals.assetFor("pi"))
        assertEquals(BusAgentVisuals.OPENCLAW, BusAgentVisuals.assetFor("openclaw"))
        assertEquals("agent-codex.jpg", BusAgentVisuals.assetFor("codex"))
        assertEquals("agent-claude.jpg", BusAgentVisuals.assetFor("claude"))
        assertEquals("agent-deepseek.png", BusAgentVisuals.assetFor("dsh"))
    }

    @Test
    fun acpConnectorIdDecidesTheMark() {
        assertEquals(BusAgentVisuals.OPENCODE, BusAgentVisuals.assetFor("acp", "opencode"))
        assertEquals(BusAgentVisuals.CLAUDE, BusAgentVisuals.assetFor("acp", "claude-code"))
        assertEquals(BusAgentVisuals.CODEX, BusAgentVisuals.assetFor("acp", "codex-cli"))
    }

    @Test
    fun connectorIdWinsOverSource() {
        assertEquals(BusAgentVisuals.OPENCODE, BusAgentVisuals.assetFor("claude", "opencode"))
        assertEquals(BusAgentVisuals.DEEPSEEK, BusAgentVisuals.assetFor("acp", "deepseek-harness"))
    }

    @Test
    fun idsAreCaseAndSeparatorInsensitive() {
        assertEquals(BusAgentVisuals.CLAUDE, BusAgentVisuals.assetFor("Claude-Code"))
        assertEquals(BusAgentVisuals.OPENCODE, BusAgentVisuals.assetFor("Open Code"))
        assertEquals(BusAgentVisuals.OPENCLAW, BusAgentVisuals.assetFor("OpenClaw"))
        assertEquals(BusAgentVisuals.PI, BusAgentVisuals.assetFor(" PI "))
    }

    @Test
    fun unknownConnectorsFallBackToNeutralMark() {
        assertEquals(BusAgentVisuals.FALLBACK, BusAgentVisuals.assetFor(null))
        assertEquals(BusAgentVisuals.FALLBACK, BusAgentVisuals.assetFor(""))
        assertEquals(BusAgentVisuals.FALLBACK, BusAgentVisuals.assetFor("acp", null))
        assertEquals(BusAgentVisuals.FALLBACK, BusAgentVisuals.assetFor("acp", "some-manifest-id"))
        assertEquals(BusAgentVisuals.FALLBACK, BusAgentVisuals.assetFor("unknown"))
        assertEquals("agent-default.svg", BusAgentVisuals.FALLBACK)
    }

    @Test
    fun genericSourceStillFindsDedicatedMarkByAgentKeyOrName() {
        assertEquals(BusAgentVisuals.CODEX, BusAgentVisuals.assetFor("builtin", null, "codex"))
        assertEquals(BusAgentVisuals.CODEX, BusAgentVisuals.assetFor("", null, null, "Codex"))
        assertEquals(BusAgentVisuals.CLAUDE, BusAgentVisuals.assetFor("builtin", null, "claude"))
    }

    @Test
    fun acpDoesNotInferDedicatedMarkFromGenericName() {
        // 通用 ACP 连接器哪怕显示名带 Codex，也仍然是通用头像
        assertEquals(
            BusAgentVisuals.FALLBACK,
            BusAgentVisuals.assetFor("acp", "some-manifest-id", "acp:some", "Codex"),
        )
    }

    @Test
    fun piDoesNotSwallowUnrelatedIds() {
        assertEquals(BusAgentVisuals.FALLBACK, BusAgentVisuals.assetFor("pilot"))
        assertEquals(BusAgentVisuals.FALLBACK, BusAgentVisuals.assetFor("acp", "pipeline"))
    }
}
