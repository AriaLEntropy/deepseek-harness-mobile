package com.example.dsh.search

/**
 * 搜索命中行：一条会话内匹配的消息（标题命中时退化为标题行）。
 * 同一会话有多条内容命中时拆成多行，用 [matchBadge]（如「1/2」）标注序号。
 * 摘要拆成 before/match/after 三段，渲染时把 match 加粗。
 */
data class DshSessionSearchHit(
    val sessionId: String,
    val title: String,
    val dateLabel: String,
    val snippetBefore: String = "",
    val snippetMatch: String = "",
    val snippetAfter: String = "",
    val matchBadge: String = "",
)

data class DshSessionSearchSnippet(
    val before: String,
    val match: String,
    val after: String,
)

private const val DSH_SEARCH_SNIPPET_BEFORE = 12
private const val DSH_SEARCH_SNIPPET_AFTER = 26
private const val DSH_SEARCH_SNIPPET_MAX = DSH_SEARCH_SNIPPET_BEFORE + DSH_SEARCH_SNIPPET_AFTER

private val DSH_SEARCH_FENCE = Regex("```[\\s\\S]*?```")
private val DSH_SEARCH_IMAGE = Regex("!\\[[^\\]]*\\]\\([^)]*\\)")
private val DSH_SEARCH_LINK = Regex("\\[([^\\]]*)\\]\\([^)]*\\)")
private val DSH_SEARCH_HTML = Regex("<[^>]+>")
private val DSH_SEARCH_HEADING = Regex("(?m)^\\s*#{1,6}\\s*")
private val DSH_SEARCH_QUOTE = Regex("(?m)^\\s*>\\s?")
private val DSH_SEARCH_LIST = Regex("(?m)^\\s*(?:[-*+]|\\d+[.)])\\s+")
private val DSH_SEARCH_INLINE_CODE = Regex("`([^`]*)`")
private val DSH_SEARCH_EMPHASIS = Regex("(\\*\\*|__|\\*|_|~~)")
private val DSH_SEARCH_WHITESPACE = Regex("\\s+")

/** 去掉 Markdown/HTML 语法（代码块、图片、链接、标题、列表、强调、标签），压成单行纯文本。 */
fun dshSearchPlainText(content: String): String {
    var text = content
    text = DSH_SEARCH_FENCE.replace(text, " ")
    text = DSH_SEARCH_IMAGE.replace(text, " ")
    text = DSH_SEARCH_LINK.replace(text) { it.groupValues[1] }
    text = DSH_SEARCH_HTML.replace(text, " ")
    text = DSH_SEARCH_HEADING.replace(text, "")
    text = DSH_SEARCH_QUOTE.replace(text, "")
    text = DSH_SEARCH_LIST.replace(text, "")
    text = DSH_SEARCH_INLINE_CODE.replace(text) { it.groupValues[1] }
    text = DSH_SEARCH_EMPHASIS.replace(text, "")
    return DSH_SEARCH_WHITESPACE.replace(text, " ").trim()
}

/**
 * 生成单行摘要：以匹配词为中心取上下文，两端按需补省略号；
 * 匹配段单独返回，供结果行加粗显示。
 */
fun dshSearchSnippet(content: String, needle: String): DshSessionSearchSnippet {
    val plain = dshSearchPlainText(content)
    val index = plain.lowercase().indexOf(needle.lowercase())
    if (index < 0) return DshSessionSearchSnippet(plain.take(DSH_SEARCH_SNIPPET_MAX), "", "")
    val start = (index - DSH_SEARCH_SNIPPET_BEFORE).coerceAtLeast(0)
    val matchEnd = (index + needle.length).coerceAtMost(plain.length)
    val end = (matchEnd + DSH_SEARCH_SNIPPET_AFTER).coerceAtMost(plain.length)
    val before = (if (start > 0) "…" else "") + plain.substring(start, index)
    val match = plain.substring(index, matchEnd)
    val after = plain.substring(matchEnd, end) + (if (end < plain.length) "…" else "")
    return DshSessionSearchSnippet(before, match, after)
}
