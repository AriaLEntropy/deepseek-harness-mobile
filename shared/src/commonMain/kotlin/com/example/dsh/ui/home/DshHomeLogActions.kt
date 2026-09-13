package com.example.dsh.ui.home

import com.example.dsh.base.setTimeout
import com.example.dsh.diagnostics.DshCrashMarker
import com.example.dsh.diagnostics.DshLogWork
import com.example.dsh.diagnostics.DshLogPageContract
import com.example.dsh.log.DshLogService
import com.example.dsh.log.DshLogWriteBehind
import com.example.dsh.log.LogEvent
import com.example.dsh.log.LogLevel
import com.example.dsh.log.LogSanitizer
import com.example.dsh.platform.currentTimeMillis
import com.example.dsh.base.bridgeModule
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.module.NotifyModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.example.dsh.base.setTimeout
import com.example.dsh.base.setTimeout
import com.tencent.kuikly.core.timer.setTimeout

internal fun DshHomePage.openSessionLogs() {
    closeSessionDrawer()
    openLogPage(overflowTargetId())
}

internal fun DshHomePage.openDiagnosticLogs() {
    closeSessionDrawer()
    openLogPage("")
}

/** 打开统一日志页；logSessionId 仅作为可修改、可清除的初始会话筛选。 */

internal fun DshHomePage.openLogPage(logSessionId: String) {
    dismissKeyboard()
    if (pagerData.platform == "ohos") reportLastCrashIfAny()
    acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
        "dsh_log",
        JSONObject().apply {
            put("pageName", "dsh_log")
            put("sessionId", logSessionId)
            put("exportDir", exportDir)
            put("connectionMode", connectionModeLabel())
            put(DshLogPageContract.KEY_OWNER, pagerId)
            put(DshLogPageContract.KEY_SESSION_TITLES, JSONArray().apply {
                sessions.forEach { session ->
                    put(JSONObject().apply {
                        put(DshLogPageContract.KEY_SESSION_ID, session.id)
                        put(DshLogPageContract.KEY_SESSION_TITLE, session.title)
                    })
                }
            })
        },
    )
}

internal fun DshHomePage.registerLogPageNotifications() {
    val notify = acquireModule<NotifyModule>(NotifyModule.MODULE_NAME)
    logJumpNotifyRef = notify.addNotify(DshLogPageContract.EVENT_JUMP_TO_SESSION) { data ->
        if (data?.optString(DshLogPageContract.KEY_OWNER) != pagerId) return@addNotify
        val sessionId = data?.optString(DshLogPageContract.KEY_SESSION_ID).orEmpty()
        val requestId = data?.optString(DshLogPageContract.KEY_REQUEST).orEmpty()
        jumpToSession(sessionId) { ok, message ->
            notify.postNotify(DshLogPageContract.EVENT_JUMP_RESULT, JSONObject().apply {
                put(DshLogPageContract.KEY_REQUEST, requestId)
                put("ok", ok); put("message", message)
            })
        }
    }
}

internal fun DshHomePage.unregisterLogPageNotifications() {
    val notify = acquireModule<NotifyModule>(NotifyModule.MODULE_NAME)
    logJumpNotifyRef?.let { notify.removeNotify(DshLogPageContract.EVENT_JUMP_TO_SESSION, it) }
    logJumpNotifyRef = null
}

internal fun DshHomePage.jumpToSession(sessionId: String, isCurrent: () -> Boolean = { true }, onResult: (Boolean, String) -> Unit) {
    val remote = remoteRepo
    if (sessionId.isBlank() || remote == null) { onResult(false, "当前未连接到 Host"); return }
    val expectedConnection = activeConnectionId
    remote.loadSessions({ available ->
        if (!pageAlive || !isCurrent()) return@loadSessions
        if (repository !== remote || activeConnectionId != expectedConnection) { onResult(false, "连接已切换，请重新打开日志页"); return@loadSessions }
        val target = available.firstOrNull { it.id == sessionId }
        if (target == null) { onResult(false, "Host 中已找不到该会话，可能已删除"); return@loadSessions }
        // Includes archived sessions from session.list. Opening does not unarchive or alter the Host ledger.
        remote.loadHistory(sessionId, { loaded ->
            if (!pageAlive || !isCurrent()) return@loadHistory
            if (repository !== remote || activeConnectionId != expectedConnection) { onResult(false, "连接已切换"); return@loadHistory }
            if (sessions.none { it.id == sessionId }) sessions = sessions + target
            val targetState = sessionMessageState(sessionId, loadFromDisk = false)
            targetState.diffUpdate(loaded) { old, new -> old == new }
            sessionMessageReady.add(sessionId)
            closeSettingsPage()
            closeSessionDrawer()
            if (activeSessionId == sessionId) {
                loadWebTimeline(sessionId, forceReplace = true)
            } else {
                selectMountedSession(sessionId)
            }
            onResult(true, "")
        }, { message -> if (pageAlive && isCurrent()) onResult(false, "无法读取该会话：$message") })
    }, { message -> if (pageAlive && isCurrent()) onResult(false, "无法确认会话：$message") })
}

/** 读取上次崩溃；日志与稳定 ID 在同一 SQLite 事务提交，跨主页/重启幂等。 */

internal fun DshHomePage.reportLastCrashIfAny() {
    val source = DshLogService.current ?: return
    val epoch = source.clearVersion()
    if (pagerData.platform == "ohos") {
        bridgeModule.readLastCrashAsync { raw -> reportCrashRecord(raw, source, epoch) }
    } else {
        reportCrashRecord(runCatching { bridgeModule.readLastCrash() }.getOrDefault(""), source, epoch)
    }
}

internal fun DshHomePage.reportCrashRecord(raw: String, source: DshLogWriteBehind, epoch: Long) {
    if (!pageAlive || raw.isEmpty() || crashImportWork != null) return
    val id = DshCrashMarker.idOf(raw)
    val text = "上次异常退出：${LogSanitizer.sanitize(raw).take(16000)}"
    val event = LogEvent(0, currentTimeMillis(), LogLevel.ERROR, "crash", null, null, text, text.length)
    val work = DshLogWork(localReadScope) { cancelled ->
        if (cancelled()) false else source.importCrash(id, event, epoch)
    }
    crashImportWork = work
    fun receive() {
        if (!pageAlive || crashImportWork !== work) return
        val result = work.take()
        if (result == null) { setTimeout(50) { receive() }; return }
        crashImportWork = null
        result.onSuccess { if (it) bridgeModule.toast("检测到上次异常退出，崩溃栈已保存到日志") }
            .onFailure { setTimeout(5000) { if (pageAlive && source.clearVersion() == epoch) reportLastCrashIfAny() } }
    }
    setTimeout(50) { receive() }
}



// ===== 重命名会话 =====
