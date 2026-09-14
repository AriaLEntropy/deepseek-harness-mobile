package com.example.dsh.export

import com.example.dsh.message.DshMessage
import com.example.dsh.message.DshShareGroup
import com.example.dsh.message.dshShareGroupForMessage
import com.example.dsh.message.dshShareGroups

/**
 * 导出多选态的纯逻辑（无页面状态依赖）。
 *
 * 选择单位是「对话组」（用户 Prompt + 该轮最终助手回复），因此勾选/统计/取正文都围绕
 * [DshShareGroup] 的 key 计算；页面只持有 `exportSelectedGroups` 这个 observable 集合。
 */
object DshExportSelection {

    fun groups(messages: List<DshMessage>): List<DshShareGroup> = dshShareGroups(messages)

    /** 当前需要打勾的消息 id；同一组的 Prompt 与回复同时勾选。 */
    fun selectedMessageIds(groups: List<DshShareGroup>, selectedKeys: Set<String>): Set<String> =
        groups.filter { it.key in selectedKeys }.flatMap { it.selectableIds }.toSet()

    /** 只统计当前列表仍存在的选中组，避免流式/历史刷新后残留 key 造成计数不一致。 */
    fun selectedCount(groups: List<DshShareGroup>, selectedKeys: Set<String>): Int {
        val keys = groups.map { it.key }.toSet()
        return selectedKeys.count { it in keys }
    }

    fun allSelected(groups: List<DshShareGroup>, selectedKeys: Set<String>): Boolean {
        val keys = groups.map { it.key }
        return keys.isNotEmpty() && keys.all { it in selectedKeys }
    }

    /** 全选/取消全选后的新选中集合。 */
    fun toggleAll(groups: List<DshShareGroup>, selectedKeys: Set<String>): Set<String> =
        if (allSelected(groups, selectedKeys)) emptySet() else groups.map { it.key }.toSet()

    /** 点击某条消息（属于哪个组就切换哪个组）；消息不在任何组时返回 null。 */
    fun toggledGroupKey(messages: List<DshMessage>, selectedKeys: Set<String>, messageId: String): Set<String>? {
        if (messageId.isBlank()) return null
        val group = dshShareGroupForMessage(messages, messageId) ?: return null
        return if (group.key in selectedKeys) selectedKeys - group.key else selectedKeys + group.key
    }

    /**
     * 按界面顺序取出所选对话组的正文：用户 Prompt + 该轮最终助手回复。
     * 只导出正文内容，不含工具调用、思考过程与上下文注入。
     */
    fun selectedMessages(messages: List<DshMessage>, selectedKeys: Set<String>): List<DshMessage> {
        val selectedIds = selectedMessageIds(groups(messages), selectedKeys)
        return messages.filter { it.id in selectedIds && !it.hidden }
    }
}
