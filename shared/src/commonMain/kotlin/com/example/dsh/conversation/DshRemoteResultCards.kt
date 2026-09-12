package com.example.dsh.conversation

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 结构化工具结果卡片：对齐电脑端 dsh 的 WebBlock / SearchBlock。
 *
 * Host 在工具结果的 `view` 里下发结构化元数据（sources / paths / files 等），
 * 这里做严格解析：字段缺失或类型不符时返回 null，渲染层回退到通用文本，
 * 避免把不完整数据渲染成空卡片。
 */
internal data class DshWebSource(
    val url: String,
    val title: String,
    val snippet: String,
    val publishedAt: String,
)

/** web_search（检索）或 web_fetch（抓取）的结构化视图。 */
internal data class DshWebCard(
    /** true = web_fetch 抓取；false = web_search 检索。 */
    val isFetch: Boolean,
    val url: String = "",
    val statusCode: Int = 0,
    val answer: String = "",
    val sources: List<DshWebSource> = emptyList(),
    val truncated: Boolean = false,
)

internal data class DshSearchMatch(
    val lineNumber: Int,
    val line: String,
)

internal data class DshSearchFile(
    val path: String,
    val matches: List<DshSearchMatch>,
)

/** grep（分组命中）或 glob（路径列表）的结构化视图。 */
internal data class DshSearchCard(
    /** true = glob 的路径列表；false = grep 的文件分组命中。 */
    val pathsOnly: Boolean,
    val paths: List<String> = emptyList(),
    val files: List<DshSearchFile> = emptyList(),
    val truncated: Boolean = false,
    val total: Int = 0,
) {
    /** 卡片中的结果行数（文件头也占一行），用于限高与展开判断。 */
    val rowCount: Int
        get() = if (pathsOnly) paths.size else files.sumOf { it.matches.size + 1 }
}

/** 解析 web_search / web_fetch 的结构化视图；不完整时返回 null。 */
internal fun dshParseWebCard(view: JSONObject?): DshWebCard? {
    if (view == null) return null
    if (view.optString("kind") == "fetch") {
        val url = view.optString("url")
        if (url.isEmpty()) return null
        return DshWebCard(
            isFetch = true,
            url = url,
            statusCode = view.optInt("statusCode", 0),
            truncated = view.optBoolean("truncated", false),
        )
    }
    val sourcesArr = view.optJSONArray("sources") ?: return null
    val answer = view.optString("answer")
    val sources = buildList {
        for (index in 0 until sourcesArr.length()) {
            val source = sourcesArr.optJSONObject(index) ?: continue
            val url = source.optString("url")
            if (url.isEmpty()) continue
            add(
                DshWebSource(
                    url = url,
                    title = source.optString("title"),
                    snippet = source.optString("snippet"),
                    publishedAt = source.optString("publishedAt"),
                ),
            )
        }
    }
    if (sources.isEmpty() && answer.isEmpty()) return null
    return DshWebCard(
        isFetch = false,
        answer = answer,
        sources = sources,
        truncated = view.optBoolean("truncated", false),
    )
}

/** 解析 grep / glob 的结构化视图；不完整时返回 null。 */
internal fun dshParseSearchCard(view: JSONObject?): DshSearchCard? {
    if (view == null) return null
    val truncated = view.optBoolean("truncated", false)
    val total = view.optInt("total", 0)
    if (view.optString("shape") == "paths") {
        val pathsArr = view.optJSONArray("paths") ?: return null
        val paths = buildList {
            for (index in 0 until pathsArr.length()) {
                val path = pathsArr.optString(index).orEmpty()
                if (path.isNotEmpty()) add(path)
            }
        }
        if (paths.isEmpty()) return null
        return DshSearchCard(pathsOnly = true, paths = paths, truncated = truncated, total = total)
    }
    val filesArr = view.optJSONArray("files") ?: return null
    val files = buildList {
        for (index in 0 until filesArr.length()) {
            val file = filesArr.optJSONObject(index) ?: continue
            val matchesArr = file.optJSONArray("matches") ?: JSONArray()
            val matches = buildList {
                for (matchIndex in 0 until matchesArr.length()) {
                    val match = matchesArr.optJSONObject(matchIndex) ?: continue
                    add(DshSearchMatch(match.optInt("lineNumber", 0), match.optString("line")))
                }
            }
            add(DshSearchFile(file.optString("path"), matches))
        }
    }
    if (files.isEmpty()) return null
    return DshSearchCard(pathsOnly = false, files = files, truncated = truncated, total = total)
}
