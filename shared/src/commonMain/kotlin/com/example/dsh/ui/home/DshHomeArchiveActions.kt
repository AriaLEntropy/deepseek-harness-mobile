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
import com.example.dsh.ui.export.DshTextExportState

internal fun DshHomePage.openArchiveList() {
    dismissKeyboard()
    closeMessageActions()
    closeSessionDrawerImmediately()
    archivedSessions.clear()
    archiveGroups.clear()
    archiveProjectOptions.clear()
    archiveSearch = ""
    archiveProjectFilter = ""
    archiveSort = DshArchiveSort.UPDATED
    archiveMenu = ""
    archiveNotice = ""
    archiveConfirm = null
    archiveBusy = false
    archiveListVisible = true
    archiveOpeningId = ""
    refreshArchiveList()
}

internal fun DshHomePage.closeArchiveList() {
    archiveRequestGeneration++
    archiveListVisible = false
    archiveListLoading = false
    archiveOpeningId = ""
    archiveListError = ""
    archiveBusy = false
    archiveConfirm = null
    archiveMenu = ""
    archivedSessions.clear()
    archiveGroups.clear()
    archiveProjectOptions.clear()
}

internal fun DshHomePage.refreshArchiveList() {
    if (!archiveListVisible || archiveOpeningId.isNotEmpty()) return
    val remote = remoteRepo
    if (remote == null || !remote.isProductReady()) {
        archiveListLoading = false
        archiveListError = "未连接到 Host，请连接后刷新"
        return
    }
    val expected = ++archiveRequestGeneration
    val connection = activeConnectionId
    fun current() = pageAlive && archiveListVisible && expected == archiveRequestGeneration &&
        repository === remote && activeConnectionId == connection
    archiveListLoading = true
    archiveListError = ""
    // 先读 meta（createdAt/cwd），失败也继续，只影响「创建时间」排序。
    remote.loadSessionMeta({ meta ->
        if (!current()) return@loadSessionMeta
        sessionCreatedAt = meta.associate { it.sessionId to it.createdAt }
        loadArchiveCatalog(remote, ::current)
    }, {
        if (!current()) return@loadSessionMeta
        sessionCreatedAt = emptyMap()
        loadArchiveCatalog(remote, ::current)
    })
    setTimeout(pagerId, 35_000) {
        if (current() && archiveListLoading) {
            archiveRequestGeneration++
            archiveListLoading = false
            archiveListError = "归档列表加载超时，请刷新重试"
        }
    }
}

internal fun DshHomePage.loadArchiveCatalog(remote: DshRemoteRepository, current: () -> Boolean) {
    remote.loadSessionCatalog({ catalog ->
        if (!current()) return@loadSessionCatalog
        archiveListLoading = false
        archivedSessions.diffUpdate(catalog.archived) { old, new -> old == new }
        refreshVisibleSessions()
        refreshWorkspaceGroups()
        rebuildArchiveGroups()
    }, { error ->
        if (!current()) return@loadSessionCatalog
        archiveListLoading = false
        archiveListError = "归档列表未更新：${error.message}。请点击刷新重试。"
    })
}

internal fun DshHomePage.rebuildArchiveGroups() {
    val remote = remoteRepo ?: return
    val all = remote.archivedWorkspaceGroups()
    // 项目筛选是「按工作区/文件夹」入口：列出全部工作区，而不是仅有归档会话的工作区。
    archiveProjectOptions.diffUpdate(
        remote.workspaceGroups()
            .filter { it.workspaceId.isNotEmpty() }
            .map { DshArchiveProjectOption(it.workspaceId, it.title) },
    ) { old, new -> old == new }
    var groups = all
    if (archiveProjectFilter.isNotEmpty()) groups = groups.filter { it.workspaceId == archiveProjectFilter }
    val query = archiveSearch.trim()
    if (query.isNotEmpty()) {
        groups = groups.mapNotNull { group ->
            val matched = group.sessions.filter { it.title.contains(query, ignoreCase = true) }
            if (matched.isEmpty()) null else group.copy(sessions = matched)
        }
    }
    val comparator = when (archiveSort) {
        DshArchiveSort.UPDATED -> compareByDescending<DshSession> { it.updatedAt }
        DshArchiveSort.CREATED -> compareByDescending<DshSession> { it.createdAt }
        DshArchiveSort.NAME -> compareBy { it.title.lowercase() }
    }
    val enriched = groups.map { group ->
        group.copy(
            sessions = group.sessions
                .map { session -> sessionCreatedAt[session.id]?.let { session.copy(createdAt = it) } ?: session }
                .sortedWith(comparator),
        )
    }
    archiveGroups.diffUpdate(enriched) { old, new -> old == new }
}

internal fun DshHomePage.onArchiveSearch(value: String) { archiveSearch = value; rebuildArchiveGroups() }

internal fun DshHomePage.onArchiveToggleMenu(menu: String) { archiveMenu = menu }

internal fun DshHomePage.onArchivePickProject(id: String) { archiveProjectFilter = id; archiveMenu = ""; rebuildArchiveGroups() }

internal fun DshHomePage.onArchivePickSort(value: DshArchiveSort) { archiveSort = value; archiveMenu = ""; rebuildArchiveGroups() }

internal fun DshHomePage.requestArchiveDeleteSession(sessionId: String) {
    val session = archivedSessions.firstOrNull { it.id == sessionId } ?: return
    archiveConfirm = DshArchiveConfirm(DshArchiveConfirmKind.SESSION, sessionId, session.title, 1)
}

/** 取消归档：走 host-plugin unarchive；成功后该会话回到主列表与工作区分组。 */

internal fun DshHomePage.unarchiveArchivedSession(sessionId: String) {
    if (archiveBusy) return
    val remote = remoteRepo ?: run {
        archiveListError = "未连接 Host"
        return
    }
    val connection = activeConnectionId
    archiveBusy = true
    archiveListError = ""
    archiveNotice = ""
    remote.unarchiveSession(sessionId) { _, error ->
        postToUi {
            if (!pageAlive || this.repository !== remote || activeConnectionId != connection) return@postToUi
            archiveBusy = false
            if (error != null) {
                archiveListError = "取消归档失败：${error.message}"
                return@postToUi
            }
            archiveNotice = "已取消归档"
            archivedSessions.diffUpdate(archivedSessions.filterNot { it.id == sessionId }) { old, new -> old == new }
            refreshVisibleSessions()
            refreshWorkspaceGroups()
            rebuildArchiveGroups()
        }
    }
    setTimeout(pagerId, 35_000) {
        if (pageAlive && archiveBusy) {
            archiveBusy = false
            archiveListError = "取消归档超时，请刷新确认结果"
        }
    }
}

internal fun DshHomePage.requestArchiveDeleteProject(group: DshWorkspaceGroup) {
    archiveConfirm = DshArchiveConfirm(DshArchiveConfirmKind.PROJECT, group.workspaceId, group.title, group.sessions.size)
}

internal fun DshHomePage.requestArchiveDeleteAll() {
    archiveConfirm = DshArchiveConfirm(DshArchiveConfirmKind.ALL, "", "", archivedSessions.size)
}

internal fun DshHomePage.cancelArchiveConfirm() { archiveConfirm = null }

internal fun DshHomePage.archiveDateLabel(timestamp: Long): String =
    if (timestamp <= 0) "" else bridgeModule.dateFormatter(timestamp, "yyyy年M月d日, HH:mm")

internal fun DshHomePage.confirmArchiveDelete() {
    val target = archiveConfirm ?: return
    if (archiveBusy) return
    val remote = remoteRepo ?: run { archiveListError = "未连接 Host"; return }
    val ids = when (target.kind) {
        DshArchiveConfirmKind.SESSION -> listOf(target.id)
        DshArchiveConfirmKind.PROJECT -> remote.archivedWorkspaceGroups()
            .firstOrNull { it.workspaceId == target.id }?.sessions?.map { it.id } ?: emptyList()
        DshArchiveConfirmKind.ALL -> archivedSessions.map { it.id }
    }
    if (ids.isEmpty()) { archiveConfirm = null; return }
    archiveBusy = true
    archiveConfirm = null
    archiveNotice = ""
    remote.deleteSessions(ids) { deleted, failed ->
        archiveBusy = false
        archiveNotice = if (failed.isEmpty()) "已删除 ${deleted.size} 条会话"
        else "已删除 ${deleted.size} 条，${failed.size} 条失败：${failed.first().second}"
        refreshArchiveList()
    }
    setTimeout(pagerId, 35_000) {
        if (pageAlive && archiveBusy) {
            archiveBusy = false
            archiveListError = "删除超时，请刷新确认结果"
        }
    }
}

internal fun DshHomePage.openArchivedSession(sessionId: String) {
    if (archiveListLoading || archiveOpeningId.isNotEmpty()) return
    val expected = ++archiveRequestGeneration
    val previousSession = activeSessionId
    archiveOpeningId = sessionId
    archiveListError = ""
    fun current() = archiveListVisible && expected == archiveRequestGeneration && activeSessionId == previousSession
    jumpToSession(sessionId, isCurrent = ::current) { ok, message ->
        if (!archiveListVisible || expected != archiveRequestGeneration) return@jumpToSession
        if (ok) closeArchiveList() else {
            archiveOpeningId = ""
            archiveListError = message
        }
    }
    setTimeout(pagerId, 35_000) {
        if (pageAlive && current() && archiveOpeningId.isNotEmpty()) {
            archiveRequestGeneration++
            archiveOpeningId = ""
            archiveListError = "读取历史超时，请重试"
        }
    }
}

internal fun DshHomePage.confirmSessionArchive() {
    if (sessionArchiveBusy) return
    val targetId = sessionActionTargetId
    if (targetId.isEmpty()) return
    val repository = remoteRepo ?: run {
        sessionArchiveError = "当前连接不支持归档会话"
        return
    }
    sessionArchiveBusy = true
    sessionArchiveError = ""
    val expectedConnection = activeConnectionId
    repository.archiveSession(targetId) { _, error ->
        postToUi {
            if (!pageAlive || this.repository !== repository || activeConnectionId != expectedConnection) return@postToUi
            sessionArchiveBusy = false
            if (error != null) {
                sessionArchiveError = error.message
                return@postToUi
            }
            sessionArchiveVisible = false
            refreshVisibleSessions()
            refreshWorkspaceGroups()
            val catalog = repository.sessionCatalog(sessions.toList())
            val next = catalog.nextActive(activeSessionId.takeUnless { it == targetId })
            if (next != null) {
                selectMountedSession(next.id)
                loadRepository(preferredSessionId = next.id, restoreOnError = false)
            } else {
                cancelStreamingForSessionSwitch()
                activeSessionId = ""
                messages = ObservableList()
                createSession()
            }
        }
    }
}

// ===== 删除会话（dsh-session-manager 插件 /delete）=====

internal fun DshHomePage.confirmSessionDelete() {
    if (sessionDeleteBusy) return
    val targetId = sessionActionTargetId
    if (targetId.isEmpty()) return
    val repository = remoteRepo ?: run {
        sessionDeleteError = "当前连接不支持删除会话"
        return
    }
    sessionDeleteBusy = true
    sessionDeleteError = ""
    val connection = activeConnectionId
    repository.callPlugin("delete", JSONObject().apply { put("sessionId", targetId) }) { _, error ->
        postToUi {
            if (!pageAlive || this.repository !== repository || activeConnectionId != connection) return@postToUi
            sessionDeleteBusy = false
            if (error != null) {
                sessionDeleteError = error.message
                return@postToUi
            }
            sessionDeleteVisible = false
            val remaining = sessions.toList().filterNot { it.id == targetId }
            sessions = remaining
            repository.removeSession(targetId)
            sessionMessageStates.remove(targetId)
            sessionCacheStates.remove(targetId)
            sessionMessageReady.remove(targetId)
            conversationPanelIds.remove(targetId)
            refreshVisibleSessions()
            runCatching { localStore?.deleteSession(activeConnectionId, targetId) }
            refreshWorkspaceGroups()
            if (activeSessionId != targetId) return@postToUi
            val next = repository.sessionCatalog(sessions).nextActive(null)
            if (next == null) {
                createSession()
            } else {
                selectSession(next.id)
            }
        }
    }
}

internal fun DshHomePage.resetSessionActions() {
    sessionRenameVisible = false; sessionRenameBusy = false; sessionRenameError = ""
    sessionArchiveVisible = false; sessionArchiveBusy = false; sessionArchiveError = ""
    sessionDeleteVisible = false; sessionDeleteBusy = false; sessionDeleteError = ""
    sessionActionTargetId = ""
    pendingSessionIds = emptySet()
    cancelReadableExport()
    cancelExportSelection()
    readableExport = DshTextExportState()
    readableExportSourceText = null
    readableExportDialogVisible = false
}
