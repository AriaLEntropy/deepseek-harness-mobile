package com.example.dsh.log

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogSanitizerTest {

    @Test
    fun leavesPlainTextUntouched() {
        assertEquals("hello world", LogSanitizer.sanitize("hello world"))
        assertEquals("", LogSanitizer.sanitize(""))
    }

    @Test
    fun redactsQueryTokens() {
        val out = LogSanitizer.sanitize("https://host/path?token=abc123&x=1")
        assertFalse(out.contains("abc123"), out)
        assertTrue(out.contains("token=***"), out)
    }

    @Test
    fun redactsNamedSecrets() {
        assertTrue(LogSanitizer.sanitize("apiKey=foo").contains("apiKey=***"))
        assertTrue(LogSanitizer.sanitize("password=hunter2").contains("password=***"))
    }

    @Test
    fun redactsBearerAndBasicAuth() {
        assertTrue(LogSanitizer.sanitize("Authorization: Bearer abc123").contains("Bearer ***"))
        assertFalse(LogSanitizer.sanitize("Authorization: Bearer abc123").contains("abc123"))
        val basic = LogSanitizer.sanitize("Authorization: Basic dXNlcjpwYXNz")
        assertFalse(basic.contains("dXNlcjpwYXNz"), basic)
    }

    @Test
    fun redactsOpenAiStyleKeys() {
        val out = LogSanitizer.sanitize("key=sk-abcdefgh1234")
        assertFalse(out.contains("sk-abcdefgh1234"), out)
    }

    @Test
    fun redactsJsonSecretValues() {
        val out = LogSanitizer.sanitize("""{"apiKey":"secretvalue","x":1}""")
        assertFalse(out.contains("secretvalue"), out)
        assertTrue(out.contains(""""apiKey":"***""""), out)
    }

    @Test
    fun omitsAttachmentPayloads() {
        assertEquals("[attachment omitted]", LogSanitizer.sanitize("data:image/png;base64,iVBORw0KGgo="))
    }

    @Test
    fun redactsLongBase64Blobs() {
        val blob = "A".repeat(70)
        assertEquals("[base64:70chars]", LogSanitizer.sanitize(blob))
    }
}
