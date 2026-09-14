package com.example.dsh.search

import com.example.dsh.message.DshMessage
import com.example.dsh.session.DshSession

/**
 * 本地会话搜索（纯逻辑，无页面状态依赖）。
 *
 * 在会话标题与已缓存的消息正文中查找关键词：命中消息按会话内顺序拆成多行（带 n/total 角标），
 * 仅标题命中时退化为一行；摘要只取单行纯文本（剥离 Markdown/HTML），并标出命中词。
 *
 * 依赖（会话列表、消息取数、日期格式化）全部通过参数注入，页面只负责把结果灌进 observable 列表。
 */
object DshSessionSearch {

    fun build(
        sessions: List<DshSession>,
        messagesFor: (String) -> List<DshMessage>?,
        rawQuery: String,
        hitLimit: Int,
        dateLabel: (Long) -> String,
    ): List<DshSessionSearchHit> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return emptyList()
        val needle = query.lowercase()
        val hits = mutableListOf<DshSessionSearchHit>()
        sessions.forEach { session ->
            if (hits.size >= hitLimit) return@forEach
            val matches = mutableListOf<String>()
            messagesFor(session.id)?.forEach { message ->
                if (message.hidden || message.isReasoning || message.isContextInjection) return@forEach
                val content = message.content
                if (content.isNotEmpty() && dshSearchPlainText(content).lowercase().contains(needle)) matches.add(content)
            }
            if (matches.isEmpty()) {
                if (session.title.lowercase().contains(needle)) {
                    val fallback = messagesFor(session.id)
                        ?.lastOrNull { !it.hidden && !it.isReasoning && !it.isContextInjection && it.content.isNotEmpty() }
                        ?.content
                    val snippet = fallback?.let { dshSearchSnippet(it, query) }
                    hits.add(
                        DshSessionSearchHit(
                            sessionId = session.id,
                            title = session.title,
                            dateLabel = dateLabel(session.updatedAt),
                            snippetBefore = snippet?.before.orEmpty(),
                            snippetMatch = snippet?.match.orEmpty(),
                            snippetAfter = snippet?.after.orEmpty(),
                        ),
                    )
                }
                return@forEach
            }
            matches.forEachIndexed { index, content ->
                if (hits.size >= hitLimit) return@forEachIndexed
                val snippet = dshSearchSnippet(content, query)
                hits.add(
                    DshSessionSearchHit(
                        sessionId = session.id,
                        title = session.title,
                        dateLabel = dateLabel(session.updatedAt),
                        snippetBefore = snippet.before,
                        snippetMatch = snippet.match,
                        snippetAfter = snippet.after,
                        matchBadge = if (matches.size > 1) "${index + 1}/${matches.size}" else "",
                    ),
                )
            }
        }
        return hits
    }
}
