package com.example.dsh.ui.home

import com.example.dsh.base.setTimeout
import com.example.dsh.export.DshExportFormat
import com.example.dsh.message.DshMessage
import com.example.dsh.export.DshReadableContent
import com.example.dsh.message.DshShareGroup
import com.example.dsh.message.dshShareGroupForMessage
import com.example.dsh.diagnostics.DshLogWork
import com.example.dsh.platform.currentTimeMillis
import com.example.dsh.log.publishReadableExport
import com.example.dsh.log.shareExportFile
import com.example.dsh.base.bridgeModule
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.example.dsh.base.setTimeout
import kotlinx.coroutines.cancel
import com.example.dsh.export.DshExportSelection
import com.example.dsh.base.setTimeout
import com.tencent.kuikly.core.timer.setTimeout
import com.example.dsh.ui.export.DshTextExportPhase
import com.example.dsh.ui.export.DshTextExportState

internal fun DshHomePage.cancelReadableExport() {
    readableExportVersion++
    readableExportWork?.cancel()
    readableExportWork = null
    if (readableExportBusy) readableExport = readableExport.copy(phase = DshTextExportPhase.CANCELLED, error = "")
}

internal fun DshHomePage.closeReadableExportDialog() {
    if (readableExportBusy) cancelReadableExport()
    readableExportDialogVisible = false
}

internal fun DshHomePage.failReadableExport(message: String) {
    readableExport = readableExport.copy(phase = DshTextExportPhase.FAILED, error = message)
}

// ===== 分享多选态操作流程 =====

/** 入口：从 overflow menu「分享消息」进入多选态，必要时先切到目标会话。 */

internal fun DshHomePage.beginExportSelection(sessionId: String, preselectId: String = "") {
    if (sessionId.isBlank() || readableExportBusy) return
    closeOverflowMenu()
    closeMessageActions()
    closeSelectTextModal()
    dismissKeyboard()
    if (sessionId != activeSessionId) {
        pendingExportSelectionSessionId = sessionId
        pendingExportSelectionPreselect = preselectId
        selectSession(sessionId)
    } else {
        enterExportSelection(sessionId, preselectId)
    }
}

internal fun DshHomePage.enterExportSelection(sessionId: String, preselectId: String = "") {
    if (sessionId.isBlank() || sessionId != activeSessionId) return
    exportSelectSessionId = sessionId
    // 默认选中点击消息所属的「对话组」：该条 AI 回复 + 触发它的用户 Prompt
    val group = if (preselectId.isNotEmpty()) {
        dshShareGroupForMessage(sessionMessageState(sessionId), preselectId)
    } else null
    exportSelectedGroups = group?.let { setOf(it.key) } ?: emptySet()
    exportFormat = DshExportFormat.HTML
    exportMoreShareVisible = false
    exportSelectMode = true
}

/** 会话切换完成后再进入多选态，避免在多选态中对未激活会话操作。 */

internal fun DshHomePage.maybeEnterPendingExportSelection() {
    val pending = pendingExportSelectionSessionId
    if (pending.isEmpty() || pending != activeSessionId) return
    val preselect = pendingExportSelectionPreselect
    pendingExportSelectionSessionId = ""
    pendingExportSelectionPreselect = ""
    enterExportSelection(pending, preselect)
}

internal fun DshHomePage.exportSelectionSessionId(): String = exportSelectSessionId.ifEmpty { activeSessionId }

internal fun DshHomePage.exportGroups(): List<DshShareGroup> =
    DshExportSelection.groups(sessionMessageState(exportSelectionSessionId()))

/** 当前多选态下需要在消息列表打勾的消息 id（同组 Prompt 与回复同时勾选）。 */

internal fun DshHomePage.exportSelectedMessageIds(): Set<String> =
    DshExportSelection.selectedMessageIds(exportGroups(), exportSelectedGroups)

internal fun DshHomePage.toggleExportMessage(messageId: String) {
    if (!exportSelectMode) return
    val next = DshExportSelection.toggledGroupKey(
        sessionMessageState(exportSelectionSessionId()),
        exportSelectedGroups,
        messageId,
    ) ?: return
    exportSelectedGroups = next
    if (exportSelectedGroups.isEmpty()) exportMoreShareVisible = false
}

internal fun DshHomePage.exportTotalCount(): Int = exportGroups().size

internal fun DshHomePage.exportSelectedCount(): Int =
    DshExportSelection.selectedCount(exportGroups(), exportSelectedGroups)

internal fun DshHomePage.exportAllSelected(): Boolean =
    DshExportSelection.allSelected(exportGroups(), exportSelectedGroups)

internal fun DshHomePage.toggleExportSelectAll() {
    exportSelectedGroups = DshExportSelection.toggleAll(exportGroups(), exportSelectedGroups)
    if (exportSelectedGroups.isEmpty()) exportMoreShareVisible = false
}

internal fun DshHomePage.cancelExportSelection() {
    exportSelectMode = false
    exportSelectSessionId = ""
    pendingExportSelectionSessionId = ""
    pendingExportSelectionPreselect = ""
    exportSelectedGroups = emptySet()
    exportMoreShareVisible = false
    exportPdfBusy = false
}

/** 流式助手正文优先使用实时内容；其余按已结算正文导出。 */

internal fun DshHomePage.exportContentFor(sessionId: String, message: DshMessage): String =
    if (streaming && activeSessionId == sessionId && streamingAssistantId == message.id &&
        streamingAssistantContent.isNotEmpty()
    ) {
        streamingAssistantContent
    } else {
        message.content
    }

/**
 * 按界面顺序取出所选对话组的正文：用户 Prompt + 该轮最终助手回复。
 * 只导出正文内容，不含工具调用、思考过程与上下文注入。
 */

internal fun DshHomePage.exportSelectedMessages(sessionId: String): List<DshMessage> =
    DshExportSelection.selectedMessages(sessionMessageState(sessionId), exportSelectedGroups)

/** 校验多选态并返回 (sessionId, 标题, 有序消息)；无有效选择时提示并返回 null。 */

internal fun DshHomePage.currentExportSelection(): Triple<String, String, List<DshMessage>>? {
    val sessionId = exportSelectionSessionId()
    if (sessionId.isBlank() || !exportSelectMode) return null
    if (exportSelectedGroups.isEmpty()) { bridgeModule.toast("请先选择要分享的对话"); return null }
    val ordered = exportSelectedMessages(sessionId)
    if (ordered.isEmpty()) { bridgeModule.toast("请先选择要分享的对话"); return null }
    val title = sessions.firstOrNull { it.id == sessionId }?.title ?: sessionId
    return Triple(sessionId, title, ordered)
}

/** 更多分享：按所选格式（默认 HTML）生成文件并打开系统分享。 */

internal fun DshHomePage.confirmExportSelection() {
    if (readableExportBusy) { bridgeModule.toast("正在分享，请稍候"); return }
    val selection = currentExportSelection() ?: return
    val (sessionId, title, ordered) = selection
    val format = exportFormat
    val connection = activeConnectionId
    val text = DshReadableContent.selection(title, sessionId, ordered, format) { exportContentFor(sessionId, it) }
    cancelExportSelection()
    closeSessionDrawer()
    readableExportSourceText = text
    readableExportExtension = format.extension
    readableExport = DshTextExportState(sessionId, connection, title, DshTextExportPhase.WRITING)
    readableExportDialogVisible = true
    writeReadableExport(++readableExportVersion) { text }
}

/** 复制内容：按可读文本复制所选对话组到剪贴板。 */

internal fun DshHomePage.copyExportSelection() {
    val selection = currentExportSelection() ?: return
    val (sessionId, title, ordered) = selection
    val count = exportSelectedCount()
    val text = DshReadableContent.selection(title, sessionId, ordered, DshExportFormat.TXT) {
        exportContentFor(sessionId, it)
    }
    bridgeModule.copyToPasteboard(text)
    bridgeModule.toast(if (count > 0) "已复制 $count 组对话" else "已复制所选对话")
}

/** 更多分享：展开/收起格式选择行。 */

internal fun DshHomePage.toggleExportMoreShare() {
    if (exportSelectedGroups.isEmpty()) { bridgeModule.toast("请先选择要分享的对话"); return }
    exportMoreShareVisible = !exportMoreShareVisible
    if (exportMoreShareVisible) exportFormat = DshExportFormat.HTML
}

/** 生成 PDF：Android 走原生 WebView 打印，其他端暂不支持。 */

internal fun DshHomePage.exportSelectionAsPdf() {
    if (exportPdfBusy) { bridgeModule.toast("正在生成 PDF，请稍候"); return }
    if (!pageData.isAndroid) { bridgeModule.toast("当前平台暂不支持生成 PDF"); return }
    val selection = currentExportSelection() ?: return
    val (sessionId, title, ordered) = selection
    val html = DshReadableContent.selection(title, sessionId, ordered, DshExportFormat.HTML) {
        exportContentFor(sessionId, it)
    }
    exportPdfBusy = true
    val filename = "dsh-session-${currentTimeMillis()}.pdf"
    bridgeModule.htmlToPdf(html, filename) { ok, path, message ->
        exportPdfBusy = false
        if (!pageAlive) return@htmlToPdf
        if (!ok) {
            bridgeModule.toast(message.ifEmpty { "生成 PDF 失败，请重试" })
            return@htmlToPdf
        }
        cancelExportSelection()
        closeSessionDrawer()
        if (path.isEmpty()) {
            // Android 走系统打印（另存为 PDF），无本地路径可直接分享
            bridgeModule.toast(message.ifEmpty { "已打开系统打印，可选择「另存为 PDF」" })
        } else {
            bridgeModule.shareExportFile(path, "application/pdf") { shared, shareMessage ->
                if (!shared) bridgeModule.toast(shareMessage.ifEmpty { "PDF 已生成，分享失败" })
            }
        }
    }
}

internal fun DshHomePage.exportReadableSession(sessionId: String) {
    if (sessionId.isBlank() || readableExportBusy) return
    val version = ++readableExportVersion
    val connection = activeConnectionId
    val title = sessions.firstOrNull { it.id == sessionId }?.title ?: sessionId
    readableExportSourceText = null
    readableExportExtension = "txt"
    readableExport = DshTextExportState(sessionId, connection, title, DshTextExportPhase.READING)
    closeSessionDrawer()
    readableExportDialogVisible = true
    val remote = remoteRepo
    if (remote == null || !remote.isProductReady()) { failReadableExport("请先连接 Host 后重试"); return }
    fun current() = pageAlive && version == readableExportVersion && repository === remote && activeConnectionId == connection
    remote.loadCompleteHistory(sessionId, { events ->
        if (!current()) return@loadCompleteHistory
        val raw = events.toString()
        writeReadableExport(version) { DshReadableContent.session(title, sessionId, JSONArray(raw)) }
    }, { error ->
        if (current()) failReadableExport(error)
    }, ::current)
    setTimeout(pagerId, 35_000) {
        if (current() && readableExport.phase == DshTextExportPhase.READING) {
            readableExportVersion++
            failReadableExport("读取完整会话超时，请重试")
        }
    }
}

/** 消息「分享」：进入统一的多选分享态，并预选当前消息。 */

internal fun DshHomePage.shareMessageSelection(message: DshMessage) {
    closeMessageActions()
    if (readableExportBusy) { bridgeModule.toast("正在分享，请稍候"); return }
    beginExportSelection(activeSessionId, preselectId = message.id)
}

internal fun DshHomePage.retryReadableExport() {
    if (readableExportBusy) return
    val text = readableExportSourceText
    if (text == null) exportReadableSession(readableExport.sessionId)
    else writeReadableExport(++readableExportVersion) { text }
}

internal fun DshHomePage.writeReadableExport(version: Int, content: () -> String) {
    readableExport = readableExport.copy(phase = DshTextExportPhase.WRITING, path = "", error = "")
    val dir = exportDir
    val filename = "dsh-session-${currentTimeMillis()}-$version.$readableExportExtension"
    val work = DshLogWork(localReadScope) { cancelled -> publishReadableExport(dir, filename, content(), cancelled) }
    readableExportWork = work
    fun receive() {
        if (!pageAlive || version != readableExportVersion || readableExportWork !== work) return
        val result = work.take()
        if (result == null) { setTimeout(50) { receive() }; return }
        readableExportWork = null
        result.onSuccess {
            readableExport = readableExport.copy(phase = DshTextExportPhase.READY, path = it)
            shareReadableExport()
        }.onFailure { failReadableExport(it.message ?: "无法生成文件，请重试") }
    }
    setTimeout(50) { receive() }
}

internal fun DshHomePage.shareReadableExport() {
    if (readableExport.path.isEmpty() || readableExportBusy) return
    closeSessionDrawer()
    readableExportDialogVisible = true
    val version = readableExportVersion
    val path = readableExport.path
    readableExport = readableExport.copy(phase = DshTextExportPhase.SHARING, error = "")
    bridgeModule.shareExportFile(path) { ok, message ->
        if (!pageAlive || version != readableExportVersion || path != readableExport.path) return@shareExportFile
        if (ok) readableExport = readableExport.copy(phase = DshTextExportPhase.READY)
        else failReadableExport(message.ifEmpty { "无法打开系统分享，请重试" })
    }
    setTimeout(pagerId, 35_000) {
        if (pageAlive && version == readableExportVersion && readableExport.phase == DshTextExportPhase.SHARING) {
            readableExportVersion++
            failReadableExport("系统分享未响应，可重新分享已生成的文件")
        }
    }
}

internal fun DshHomePage.exportActiveSession() {
    val repository = remoteRepo ?: return
    val url = repository.sessionExportUrl(activeSessionId)
    acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
        "link_view",
        JSONObject().apply {
            put("pageName", "link_view")
            put("url", url)
        },
    )
}

/** 打开工作区选择：仅远程模式且当前会话尚未开始（blank）时可改工作区。 */
