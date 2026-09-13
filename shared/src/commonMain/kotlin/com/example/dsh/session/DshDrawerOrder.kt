package com.example.dsh.session

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/** 抽屉拖拽排序的对象类型。 */
internal enum class DshDrawerDragKind { NONE, SESSION, WORKSPACE }

/** 工作区拖拽使用的伪分组键，避免与会话的分组键（workspaceId）混淆。 */
internal const val DSH_WORKSPACE_DRAG_SCOPE = "__dsh_workspaces__"

/** 会话行固定高度：内容 46dp + 底部间距 2dp，用于拖拽落点估算。 */
internal const val DSH_DRAWER_ROW_HEIGHT = 48f

/** 工作区文件夹行（含间距）高度，用于拖拽落点估算。 */
internal const val DSH_DRAWER_WORKSPACE_HEADER_HEIGHT = 46f

/** 抽屉视图选项：分组方式，对齐电脑端 WorkspaceBrowser 的 groupBy。 */
internal const val DSH_DRAWER_GROUP_WORKSPACE = "workspace"
internal const val DSH_DRAWER_GROUP_FLAT = "flat"

/** 抽屉视图选项：排序方式，对齐电脑端 WorkspaceBrowser 的 orderBy。 */
internal const val DSH_DRAWER_ORDER_MANUAL = "manual"
internal const val DSH_DRAWER_ORDER_UPDATED = "updated"

internal const val DSH_PREF_DRAWER_SESSION_ORDER = "dsh.drawer.session.order"
internal const val DSH_PREF_DRAWER_WORKSPACE_ORDER = "dsh.drawer.workspace.order"
internal const val DSH_PREF_DRAWER_GROUP_BY = "dsh.drawer.group.by"
internal const val DSH_PREF_DRAWER_ORDER_BY = "dsh.drawer.order.by"

/** 当前正在进行的拖拽状态；`kind == NONE` 表示空闲。 */
internal data class DshDrawerDrag(
    val kind: DshDrawerDragKind = DshDrawerDragKind.NONE,
    val key: String = "",
    val groupKey: String = "",
    val startIndex: Int = -1,
    val targetIndex: Int = -1,
    val offsetY: Float = 0f,
    val itemHeight: Float = DSH_DRAWER_ROW_HEIGHT,
)

internal fun dshSessionOrderKey(scope: String): String = "$DSH_PREF_DRAWER_SESSION_ORDER.$scope"

internal fun dshWorkspaceOrderKey(scope: String): String = "$DSH_PREF_DRAWER_WORKSPACE_ORDER.$scope"

internal fun dshDrawerGroupByKey(scope: String): String = "$DSH_PREF_DRAWER_GROUP_BY.$scope"

internal fun dshDrawerOrderByKey(scope: String): String = "$DSH_PREF_DRAWER_ORDER_BY.$scope"

/** 按持久化的 id 顺序稳定重排；不在顺序中的元素保持原有相对顺序并排到最后。 */
internal fun <T> dshApplyOrder(items: List<T>, order: List<String>, idOf: (T) -> String): List<T> {
    if (order.isEmpty()) return items
    val ranks = order.withIndex().associate { (index, id) -> id to index }
    return items.sortedWith(compareBy { ranks[idOf(it)] ?: Int.MAX_VALUE })
}

/**
 * 计算拖拽行相对其静止位置的纵向位移（dp）。
 * 拖拽中的行跟随手指；其跨越过的行让出位置，视觉上形成空档。
 */
internal fun dshRowDragOffset(state: DshDrawerDrag, scope: String, id: String, index: Int): Float {
    if (state.kind == DshDrawerDragKind.NONE) return 0f
    if (state.groupKey != scope) return 0f
    if (index < 0 || state.startIndex < 0 || state.targetIndex < 0) return 0f
    return when {
        state.key == id -> state.offsetY
        state.startIndex < state.targetIndex && index in (state.startIndex + 1)..state.targetIndex -> -state.itemHeight
        state.startIndex > state.targetIndex && index in state.targetIndex until state.startIndex -> state.itemHeight
        else -> 0f
    }
}

/** 以各项高度中心为基准，返回拖拽位移后最接近的落点索引。 */
internal fun dshDropIndex(offset: Float, startIndex: Int, heights: List<Float>): Int {
    if (heights.isEmpty()) return -1
    val start = startIndex.coerceIn(0, heights.lastIndex)
    val centers = FloatArray(heights.size)
    var acc = 0f
    heights.forEachIndexed { index, height ->
        centers[index] = acc + height / 2f
        acc += height
    }
    val current = centers[start] + offset
    var best = start
    var bestDistance = Float.MAX_VALUE
    centers.forEachIndexed { index, center ->
        val distance = kotlin.math.abs(center - current)
        if (distance < bestDistance) {
            bestDistance = distance
            best = index
        }
    }
    return best
}

/** 将指定的 id 从原位置移动到目标位置，返回新列表。 */
internal fun <T> dshMoveItem(items: List<T>, from: Int, to: Int): List<T> {
    if (from !in items.indices || to !in items.indices || from == to) return items
    val next = items.toMutableList()
    val moved = next.removeAt(from)
    next.add(to, moved)
    return next
}

internal fun dshEncodeOrder(ids: List<String>): String {
    val array = JSONArray()
    ids.forEach { array.put(it) }
    return array.toString()
}

internal fun dshDecodeOrder(raw: String?): List<String> {
    if (raw.isNullOrEmpty()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        List(array.length()) { index -> array.optString(index).orEmpty() }.filter { it.isNotEmpty() }
    }.getOrDefault(emptyList())
}

internal fun dshEncodeSessionOrder(order: Map<String, List<String>>): String {
    val obj = JSONObject()
    order.forEach { (key, ids) ->
        val array = JSONArray()
        ids.forEach { array.put(it) }
        obj.put(key, array)
    }
    return obj.toString()
}

internal fun dshDecodeSessionOrder(raw: String?): Map<String, List<String>> {
    if (raw.isNullOrEmpty()) return emptyMap()
    return runCatching {
        val obj = JSONObject(raw)
        val result = linkedMapOf<String, List<String>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val array = obj.optJSONArray(key) ?: continue
            result[key] = List(array.length()) { index -> array.optString(index).orEmpty() }.filter { it.isNotEmpty() }
        }
        result
    }.getOrDefault(emptyMap())
}
