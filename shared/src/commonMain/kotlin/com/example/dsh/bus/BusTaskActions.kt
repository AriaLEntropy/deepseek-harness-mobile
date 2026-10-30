package com.example.dsh.bus

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * 协议 3.8 的列表删除能力。
 *
 * - `deleteTask` 只对报了 `ConnectorInfo.canDeleteTasks` 且已停止的会话发送；
 * - `removeProject` 只对报了 `AgentInfo.canRemoveProjects` 的电脑发送。
 *
 * 判定和文案都放在纯函数/常量里，页面与单测共用，避免两边各写一份。
 */
val busStoppedTaskStatuses = setOf("completed", "failed", "interrupted", "idle")

fun busTaskIsStopped(status: String): Boolean = status in busStoppedTaskStatuses

/** 只有“连接器声明支持 + 会话已停止”时，页面才显示永久删除入口。 */
fun busCanDeleteTask(status: String, canDeleteTasks: Boolean): Boolean =
    canDeleteTasks && busTaskIsStopped(status)

const val BUS_DELETE_TASK_CONFIRM_TITLE = "永久删除这个会话？"
const val BUS_DELETE_TASK_CONFIRM_MESSAGE =
    "删除后无法恢复；Codex 会同时删除派生的子会话；不会删除项目文件。"

const val BUS_REMOVE_PROJECT_CONFIRM_TITLE = "从列表移除项目？"
const val BUS_REMOVE_PROJECT_CONFIRM_MESSAGE =
    "只是从列表里隐藏，不删除目录、代码和会话记录；之后有新活动时会重新出现。"

/** recentResults 里的一条命令结果；[Pending] 表示电脑还没回。 */
sealed class BusCommandOutcome {
    data object Pending : BusCommandOutcome()
    data class Success(val taskId: String = "") : BusCommandOutcome()
    data class Failure(val error: String) : BusCommandOutcome()
}

/**
 * 从快照 JSON 的 recentResults 中读某条命令的结果。
 * 协议用 `error == "pending"` 表示仍在执行；找不到记录同样按未完成处理。
 */
fun busCommandOutcome(snapshotJson: String, commandId: String): BusCommandOutcome {
    val root = runCatching { busParse(snapshotJson).jsonObject }.getOrNull()
        ?: return BusCommandOutcome.Pending
    val results = root["recentResults"] as? JsonArray ?: return BusCommandOutcome.Pending
    for (item in results) {
        val result = item as? JsonObject ?: continue
        if (result.string("commandId") != commandId) continue
        if (result.string("error") == "pending") return BusCommandOutcome.Pending
        val ok = (result["ok"] as? JsonPrimitive)?.booleanOrNull == true
        return if (ok) {
            BusCommandOutcome.Success(result.string("taskId").orEmpty())
        } else {
            BusCommandOutcome.Failure(result.string("error").orEmpty().ifBlank { "操作失败" })
        }
    }
    return BusCommandOutcome.Pending
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull
