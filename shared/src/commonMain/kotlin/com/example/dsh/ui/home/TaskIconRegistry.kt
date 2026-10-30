package com.example.dsh.ui.home

/**
 * 任务类型 → 图标资产。
 *
 * 协议里的 taskType 是开放字符串：已知类型从注册表取图标，
 * 未知类型或字段缺失时回退到通用会话图标，保证旧 host 也能显示。
 */
internal object TaskIconRegistry {
    const val FALLBACK: String = "session.svg"

    private val icons: Map<String, String> = mapOf(
        "code" to "task-code.svg",
        "diagnose" to "task-diagnose.svg",
        "doc" to "task-doc.svg",
        "note" to "task-note.svg",
        "data" to "task-data.svg",
        "design" to "task-design.svg",
        "deploy" to "task-deploy.svg",
        "maintenance" to "task-maintenance.svg",
        "research" to "task-research.svg",
    )

    fun iconFor(taskType: String?): String = icons[taskType] ?: FALLBACK
}
