package com.example.dsh.ui.home

import com.example.dsh.base.setTimeout
import com.example.dsh.host.DshRemoteRepository
import com.example.dsh.session.DshSession
import com.example.dsh.session.DshWorkspaceGroup
import com.example.dsh.base.bridgeModule
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.example.dsh.base.setTimeout
import com.example.dsh.base.setTimeout
import com.tencent.kuikly.core.timer.setTimeout
import com.example.dsh.ui.session.DshArchiveConfirm
import com.example.dsh.ui.session.DshArchiveConfirmKind
import com.example.dsh.ui.session.DshArchiveProjectOption
import com.example.dsh.ui.session.DshArchiveSort
import com.example.dsh.ui.session.DshOverflowAction
import com.example.dsh.ui.export.DshTextExportState

internal fun DshHomePage.openArchiveList() {
    dismissKeyboard()
    closeMessageActions()
    closeSessionDrawerImmediately()
    ui.archivedSessions.clear()
    ui.archiveGroups.clear()
    ui.archiveProjectOptions.clear()
    ui.archiveSearch = ""
    ui.archiveProjectFilter = ""
    ui.archiveSort = DshArchiveSort.UPDATED
    ui.archiveMenu = ""
    ui.archiveNotice = ""
    ui.archiveConfirm = null
    ui.archiveOverflowVisible = false
    ui.archiveOverflowTargetId = ""
    ui.archiveBusy = false
    ui.archiveListVisible = true
    ui.archiveOpeningId = ""
    refreshArchiveList()
}

internal fun DshHomePage.closeArchiveList() {
    archiveRequestGeneration++
    ui.archiveListVisible = false
    ui.archiveListLoading = false
    ui.archiveOpeningId = ""
    ui.archiveListError = ""
    ui.archiveBusy = false
    ui.archiveConfirm = null
    ui.archiveOverflowVisible = false
    ui.archiveOverflowTargetId = ""
    ui.archiveMenu = ""
    ui.archivedSessions.clear()
    ui.archiveGroups.clear()
    ui.archiveProjectOptions.clear()
}

internal fun DshHomePage.refreshArchiveList() {
    if (!ui.archiveListVisible || ui.archiveOpeningId.isNotEmpty()) return
    val remote = remoteRepo
    if (remote == null || !remote.isProductReady()) {
        ui.archiveListLoading = false
        ui.archiveListError = "未连接到 Host，请连接后刷新"
        return
    }
    val expected = ++archiveRequestGeneration
    val connection = activeConnectionId
    fun current() = pageAlive && ui.archiveListVisible && expected == archiveRequestGeneration &&
        repository === remote && activeConnectionId == connection
    ui.archiveListLoading = true
    ui.archiveListError = ""
    // 先读 meta（createdAt/cwd），失败也继续，只影响「创建时间」排序。
    remote.loadSessionMeta({ meta ->
        if (!current()) return@loadSessionMeta
        ui.sessionCreatedAt = meta.associate { it.sessionId to it.createdAt }
        loadArchiveCatalog(remote, ::current)
    }, {
        if (!current()) return@loadSessionMeta
        ui.sessionCreatedAt = emptyMap()
        loadArchiveCatalog(remote, ::current)
    })
    setTimeout(pagerId, 35_000) {
        if (current() && ui.archiveListLoading) {
            archiveRequestGeneration++
            ui.archiveListLoading = false
            ui.archiveListError = "归档列表加载超时，请刷新重试"
        }
    }
}

internal fun DshHomePage.loadArchiveCatalog(remote: DshRemoteRepository, current: () -> Boolean) {
    remote.loadSessionCatalog({ catalog ->
        if (!current()) return@loadSessionCatalog
        ui.archiveListLoading = false
        ui.archivedSessions.diffUpdate(catalog.archived) { old, new -> old == new }
        refreshVisibleSessions()
        refreshWorkspaceGroups()
        rebuildArchiveGroups()
    }, { error ->
        if (!current()) return@loadSessionCatalog
        ui.archiveListLoading = false
        ui.archiveListError = "归档列表未更新：${error.message}。请点击刷新重试。"
    })
}

internal fun DshHomePage.rebuildArchiveGroups() {
    val remote = remoteRepo ?: return
    val all = remote.archivedWorkspaceGroups()
    // 项目筛选是「按工作区/文件夹」入口：列出全部工作区，而不是仅有归档会话的工作区。
    ui.archiveProjectOptions.diffUpdate(
        remote.workspaceGroups()
            .filter { it.workspaceId.isNotEmpty() }
            .map { DshArchiveProjectOption(it.workspaceId, it.title) },
    ) { old, new -> old == new }
    var groups = all
    if (ui.archiveProjectFilter.isNotEmpty()) groups = groups.filter { it.workspaceId == ui.archiveProjectFilter }
    val query = ui.archiveSearch.trim()
    if (query.isNotEmpty()) {
        groups = groups.mapNotNull { group ->
            val matched = group.sessions.filter { it.title.contains(query, ignoreCase = true) }
            if (matched.isEmpty()) null else group.copy(sessions = matched)
        }
    }
    val comparator = when (ui.archiveSort) {
        DshArchiveSort.UPDATED -> compareByDescending<DshSession> { it.updatedAt }
        DshArchiveSort.CREATED -> compareByDescending<DshSession> { it.createdAt }
        DshArchiveSort.NAME -> compareBy { it.title.lowercase() }
    }
    val enriched = groups.map { group ->
        group.copy(
            sessions = group.sessions
                .map { session -> ui.sessionCreatedAt[session.id]?.let { session.copy(createdAt = it) } ?: session }
                .sortedWith(comparator),
        )
    }
    ui.archiveGroups.diffUpdate(enriched) { old, new -> old == new }
}

internal fun DshHomePage.onArchiveSearch(value: String) { ui.archiveSearch = value; rebuildArchiveGroups() }

internal fun DshHomePage.onArchiveToggleMenu(menu: String, anchorX: Float = -1f, anchorY: Float = -1f) {
    ui.archiveMenu = menu
    ui.archiveFilterX = anchorX
    ui.archiveFilterY = anchorY
}

internal fun DshHomePage.onArchivePickProject(id: String) { ui.archiveProjectFilter = id; ui.archiveMenu = ""; rebuildArchiveGroups() }

internal fun DshHomePage.onArchivePickSort(value: DshArchiveSort) { ui.archiveSort = value; ui.archiveMenu = ""; rebuildArchiveGroups() }

internal fun DshHomePage.requestArchiveDeleteSession(sessionId: String) {
    val session = ui.archivedSessions.firstOrNull { it.id == sessionId } ?: return
    ui.archiveConfirm = DshArchiveConfirm(DshArchiveConfirmKind.SESSION, sessionId, session.title, 1)
}

/** 归档行 ⋯：复用主页面的 overflow menu，锚定到点击位置，菜单项为取消归档 / 删除。 */
internal fun DshHomePage.openArchiveOverflowFor(sessionId: String, anchorX: Float = -1f, anchorY: Float = -1f) {
    if (ui.archiveBusy || ui.archiveOpeningId.isNotEmpty()) return
    if (ui.archivedSessions.none { it.id == sessionId }) return
    ui.archiveOverflowTargetId = sessionId
    ui.archiveOverflowAnchorX = anchorX
    ui.archiveOverflowAnchorY = anchorY
    ui.archiveOverflowVisible = true
}

internal fun DshHomePage.dismissArchiveOverflow() {
    ui.archiveOverflowVisible = false
}

internal fun DshHomePage.archiveOverflowActions(): ObservableList<DshOverflowAction> {
    val result = ObservableList<DshOverflowAction>()
    if (ui.archivedSessions.none { it.id == ui.archiveOverflowTargetId }) return result
    result.add(DshOverflowAction("unarchive", "取消归档", "archive.svg"))
    result.add(DshOverflowAction("delete", "删除", "delete.svg", danger = true))
    return result
}

internal fun DshHomePage.onArchiveOverflowAction(id: String) {
    val targetId = ui.archiveOverflowTargetId
    dismissArchiveOverflow()
    when (id) {
        "unarchive" -> unarchiveArchivedSession(targetId)
        "delete" -> requestArchiveDeleteSession(targetId)
    }
}

/** 取消归档：走 host-plugin unarchive；成功后该会话回到主列表与工作区分组。 */

internal fun DshHomePage.unarchiveArchivedSession(sessionId: String) {
    if (ui.archiveBusy) return
    val remote = remoteRepo ?: run {
        ui.archiveListError = "未连接 Host"
        return
    }
    val connection = activeConnectionId
    ui.archiveBusy = true
    ui.archiveListError = ""
    ui.archiveNotice = ""
    remote.unarchiveSession(sessionId) { _, error ->
        postToUi {
            if (!pageAlive || this.repository !== remote || activeConnectionId != connection) return@postToUi
            ui.archiveBusy = false
            if (error != null) {
                ui.archiveListError = "取消归档失败：${error.message}"
                return@postToUi
            }
            ui.archiveNotice = "已取消归档"
            ui.archivedSessions.diffUpdate(ui.archivedSessions.filterNot { it.id == sessionId }) { old, new -> old == new }
            refreshVisibleSessions()
            refreshWorkspaceGroups()
            rebuildArchiveGroups()
        }
    }
    setTimeout(pagerId, 35_000) {
        if (pageAlive && ui.archiveBusy) {
            ui.archiveBusy = false
            ui.archiveListError = "取消归档超时，请刷新确认结果"
        }
    }
}

internal fun DshHomePage.requestArchiveDeleteProject(group: DshWorkspaceGroup) {
    ui.archiveConfirm = DshArchiveConfirm(DshArchiveConfirmKind.PROJECT, group.workspaceId, group.title, group.sessions.size)
}

internal fun DshHomePage.requestArchiveDeleteAll() {
    ui.archiveConfirm = DshArchiveConfirm(DshArchiveConfirmKind.ALL, "", "", ui.archivedSessions.size)
}

internal fun DshHomePage.cancelArchiveConfirm() { ui.archiveConfirm = null }

internal fun DshHomePage.archiveDateLabel(timestamp: Long): String =
    if (timestamp <= 0) "" else bridgeModule.dateFormatter(timestamp, "yyyy年M月d日, HH:mm")

internal fun DshHomePage.confirmArchiveDelete() {
    val target = ui.archiveConfirm ?: return
    if (ui.archiveBusy) return
    val remote = remoteRepo ?: run { ui.archiveListError = "未连接 Host"; return }
    val ids = when (target.kind) {
        DshArchiveConfirmKind.SESSION -> listOf(target.id)
        DshArchiveConfirmKind.PROJECT -> remote.archivedWorkspaceGroups()
            .firstOrNull { it.workspaceId == target.id }?.sessions?.map { it.id } ?: emptyList()
        DshArchiveConfirmKind.ALL -> ui.archivedSessions.map { it.id }
    }
    if (ids.isEmpty()) { ui.archiveConfirm = null; return }
    ui.archiveBusy = true
    ui.archiveConfirm = null
    ui.archiveNotice = ""
    remote.deleteSessions(ids) { deleted, failed ->
        ui.archiveBusy = false
        ui.archiveNotice = if (failed.isEmpty()) "已删除 ${deleted.size} 条会话"
        else "已删除 ${deleted.size} 条，${failed.size} 条失败：${failed.first().second}"
        refreshArchiveList()
    }
    setTimeout(pagerId, 35_000) {
        if (pageAlive && ui.archiveBusy) {
            ui.archiveBusy = false
            ui.archiveListError = "删除超时，请刷新确认结果"
        }
    }
}

internal fun DshHomePage.openArchivedSession(sessionId: String) {
    if (ui.archiveListLoading || ui.archiveOpeningId.isNotEmpty()) return
    val expected = ++archiveRequestGeneration
    val previousSession = ui.activeSessionId
    ui.archiveOpeningId = sessionId
    ui.archiveListError = ""
    fun current() = ui.archiveListVisible && expected == archiveRequestGeneration && ui.activeSessionId == previousSession
    jumpToSession(sessionId, isCurrent = ::current) { ok, message ->
        if (!ui.archiveListVisible || expected != archiveRequestGeneration) return@jumpToSession
        if (ok) closeArchiveList() else {
            ui.archiveOpeningId = ""
            ui.archiveListError = message
        }
    }
    setTimeout(pagerId, 35_000) {
        if (pageAlive && current() && ui.archiveOpeningId.isNotEmpty()) {
            archiveRequestGeneration++
            ui.archiveOpeningId = ""
            ui.archiveListError = "读取历史超时，请重试"
        }
    }
}

internal fun DshHomePage.confirmSessionArchive() {
    if (ui.sessionArchiveBusy) return
    val targetId = ui.sessionActionTargetId
    if (targetId.isEmpty()) return
    val repository = remoteRepo ?: run {
        ui.sessionArchiveError = "当前连接不支持归档会话"
        return
    }
    ui.sessionArchiveBusy = true
    ui.sessionArchiveError = ""
    val expectedConnection = activeConnectionId
    repository.archiveSession(targetId) { _, error ->
        postToUi {
            if (!pageAlive || this.repository !== repository || activeConnectionId != expectedConnection) return@postToUi
            ui.sessionArchiveBusy = false
            if (error != null) {
                ui.sessionArchiveError = error.message
                return@postToUi
            }
            ui.sessionArchiveVisible = false
            refreshVisibleSessions()
            refreshWorkspaceGroups()
            val catalog = repository.sessionCatalog(ui.sessions.toList())
            val next = catalog.nextActive(ui.activeSessionId.takeUnless { it == targetId })
            if (next != null) {
                selectMountedSession(next.id)
                loadRepository(preferredSessionId = next.id, restoreOnError = false)
            } else {
                cancelStreamingForSessionSwitch()
                ui.activeSessionId = ""
                ui.messages = ObservableList()
                createSession()
            }
        }
    }
}

// ===== 删除会话（dsh-session-manager 插件 /delete）=====

internal fun DshHomePage.confirmSessionDelete() {
    if (ui.sessionDeleteBusy) return
    val targetId = ui.sessionActionTargetId
    if (targetId.isEmpty()) return
    val repository = remoteRepo ?: run {
        ui.sessionDeleteError = "当前连接不支持删除会话"
        return
    }
    ui.sessionDeleteBusy = true
    ui.sessionDeleteError = ""
    val connection = activeConnectionId
    repository.callPlugin("delete", JSONObject().apply { put("sessionId", targetId) }) { _, error ->
        postToUi {
            if (!pageAlive || this.repository !== repository || activeConnectionId != connection) return@postToUi
            ui.sessionDeleteBusy = false
            if (error != null) {
                ui.sessionDeleteError = error.message
                return@postToUi
            }
            ui.sessionDeleteVisible = false
            val remaining = ui.sessions.toList().filterNot { it.id == targetId }
            ui.sessions = remaining
            repository.removeSession(targetId)
            sessionMessageStates.remove(targetId)
            sessionCacheStates.remove(targetId)
            sessionMessageReady.remove(targetId)
            ui.conversationPanelIds.remove(targetId)
            refreshVisibleSessions()
            runCatching { localStore?.deleteSession(activeConnectionId, targetId) }
            refreshWorkspaceGroups()
            if (ui.activeSessionId != targetId) return@postToUi
            val next = repository.sessionCatalog(ui.sessions).nextActive(null)
            if (next == null) {
                createSession()
            } else {
                selectSession(next.id)
            }
        }
    }
}

internal fun DshHomePage.resetSessionActions() {
    ui.sessionRenameVisible = false; ui.sessionRenameBusy = false; ui.sessionRenameError = ""
    ui.sessionArchiveVisible = false; ui.sessionArchiveBusy = false; ui.sessionArchiveError = ""
    ui.sessionDeleteVisible = false; ui.sessionDeleteBusy = false; ui.sessionDeleteError = ""
    ui.sessionActionTargetId = ""
    ui.pendingSessionIds = emptySet()
    cancelReadableExport()
    cancelExportSelection()
    ui.readableExport = DshTextExportState()
    readableExportSourceText = null
    ui.readableExportDialogVisible = false
}
