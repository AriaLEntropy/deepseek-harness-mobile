package com.example.dsh.ui.home

import com.example.dsh.base.setTimeout
import com.example.dsh.session.DshSession
import com.example.dsh.base.bridgeModule
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.example.dsh.base.setTimeout
import com.example.dsh.base.setTimeout
import com.tencent.kuikly.core.timer.setTimeout
import com.example.dsh.session.DSH_DRAWER_ORDER_MANUAL
import com.example.dsh.session.DSH_DRAWER_ORDER_UPDATED
import com.example.dsh.session.dshApplyOrder
import com.example.dsh.ui.session.DshWorkspacePickerScreen

internal fun DshHomePage.toggleWorkspaceExpanded(workspaceId: String) {
    workspaceExpandedIds = if (workspaceId in workspaceExpandedIds) workspaceExpandedIds - workspaceId
    else workspaceExpandedIds + workspaceId
}

/** 会话是否有待用户决策（审批/提问），供抽屉行状态点显示；本地模式恒 false。 */

internal fun DshHomePage.refreshWorkspaceGroups() {
    ensureDrawerOrdersLoaded()
    if (!isRemoteHost) {
        workspaceGroups = ObservableList()
        workspacePickerFolders.clear()
        return
    }
    val repository = remoteRepo ?: return
    val byId = sessions.associateBy { it.id }
    val groups = repository.workspaceGroups().map { group ->
        val resolved = group.sessions.map { byId[it.id] ?: it }
        val order = if (drawerViewOrderBy == DSH_DRAWER_ORDER_MANUAL) {
            sessionManualOrder[group.workspaceId].orEmpty()
        } else {
            emptyList()
        }
        val orderedSessions = when {
            order.isNotEmpty() -> dshApplyOrder(resolved, order) { it.id }
            drawerViewOrderBy == DSH_DRAWER_ORDER_UPDATED ->
                resolved.sortedWith(compareByDescending<DshSession> { it.updatedAt }.thenBy { it.id })
            else -> resolved
        }
        group.copy(sessions = orderedSessions)
    }
    val orderedGroups = if (workspaceManualOrder.isEmpty()) {
        groups
    } else {
        dshApplyOrder(groups, workspaceManualOrder) { it.workspaceId }
    }
    if (orderedGroups != workspaceGroups.toList()) workspaceGroups = ObservableList(orderedGroups.toMutableList())
    val folders = orderedGroups.filter { it.workspaceId.isNotEmpty() }
    workspacePickerFolders.clear()
    workspacePickerFolders.addAll(folders)
}

// ===== 会话抽屉拖拽排序 =====

/** 首次使用时按连接范围加载本地保存的自定义顺序。 */

internal fun DshHomePage.openWorkspacePicker() {
    if (!isRemoteHost) return
    if (!isBlankSession()) {
        bridgeModule.toast("会话已开始，工作区目录不可修改")
        return
    }
    dismissKeyboard()
    closeSessionDrawer()
    workspacePickerGeneration++
    workspacePickerVisible = true
    workspacePickerScreen = DshWorkspacePickerScreen.RECENT
    workspacePickerBusy = false
    workspacePickerError = ""
    workspaceAddNewName = ""
    refreshWorkspaceGroups()
}

internal fun DshHomePage.closeWorkspacePicker() {
    workspacePickerGeneration++
    workspacePickerVisible = false
    workspacePickerBusy = false
    workspaceAddBusy = false
    workspacePickerError = ""
    workspacePickerScreen = DshWorkspacePickerScreen.RECENT
}

/** 返回键/左上返回：ADD 界面回 RECENT，RECENT 界面关闭弹窗。 */

internal fun DshHomePage.onWorkspacePickerBack() {
    if (workspacePickerBusy || workspaceAddBusy) return
    if (workspacePickerScreen == DshWorkspacePickerScreen.ADD) {
        workspacePickerScreen = DshWorkspacePickerScreen.RECENT
        workspacePickerError = ""
    } else {
        closeWorkspacePicker()
    }
}

/**
 * 选中「最近文件夹」：为当前空白会话切换到该工作区并关闭弹窗。
 * 复用该工作区下已有的空白会话；没有则 `session.create` 新建后再选中。
 */

internal fun DshHomePage.switchWorkspaceTo(workspaceId: String) {
    val remote = remoteRepo ?: return
    if (workspacePickerBusy) return
    if (!isBlankSession()) {
        bridgeModule.toast("会话已开始，工作区不可修改")
        return
    }
    if (activeWorkspaceId() == workspaceId) {
        closeWorkspacePicker()
        return
    }
    val generation = ++workspacePickerGeneration
    val connection = activeConnectionId
    val sourceSession = activeSessionId
    workspacePickerBusy = true
    workspacePickerError = ""
    fun current() = pageAlive && workspacePickerVisible && generation == workspacePickerGeneration &&
        remote === this.repository && connection == activeConnectionId && sourceSession == activeSessionId
    fun fail(message: String) {
        if (!current()) return
        workspacePickerBusy = false
        workspacePickerError = message
    }
    fun openSession(sessionId: String) {
        remote.loadHistory(sessionId, { history ->
            if (!current()) return@loadHistory
            sessionMessageState(sessionId, loadFromDisk = false).diffUpdate(history) { old, new -> old == new }
            sessionMessageReady.add(sessionId)
            selectSession(sessionId)
            closeWorkspacePicker()
        }, { fail("无法读取目标会话：$it") })
    }
    val existing = remote.blankSessionInWorkspace(workspaceId)
    if (existing != null) {
        openSession(existing.id)
        return
    }
    remote.createSession(workspaceId, { sessionId ->
        if (!current()) return@createSession
        remote.loadSessionCatalog({ catalog ->
            if (!current()) return@loadSessionCatalog
            sessions = catalog.sessions
            reorderSessionsByUpdatedAt()
            refreshVisibleSessions()
            refreshWorkspaceGroups()
            preferBlankHomeOnNextLoad = false
            openSession(sessionId)
        }, { error -> fail("无法同步工作区：${error.message}") })
    }, { error -> fail("无法创建会话：$error") }, permission = permissionValue, agentPreset = agentModeValue)
    setTimeout(pagerId, 35_000) {
        if (current() && workspacePickerBusy) {
            fail("切换超时，请重试")
            workspacePickerGeneration++
        }
    }
}

internal fun DshHomePage.openWorkspaceAddFolder() {
    if (!isRemoteHost) return
    dismissKeyboard()
    workspacePickerScreen = DshWorkspacePickerScreen.ADD
    workspacePickerError = ""
    workspaceAddNewName = ""
    loadWorkspaceAddDirectory(null)
}

internal fun DshHomePage.loadWorkspaceAddDirectory(path: String?) {
    val remote = remoteRepo ?: return
    if (workspacePickerBusy) return
    val generation = ++workspacePickerGeneration
    workspaceAddBusy = true
    workspaceAddDirectoryLoaded = false
    workspacePickerError = ""
    remote.listDirectory(path) { listing, error ->
        postToUi {
            if (!pageAlive || !workspacePickerVisible || workspacePickerScreen != DshWorkspacePickerScreen.ADD ||
                generation != workspacePickerGeneration || remote !== this.repository
            ) return@postToUi
            workspaceAddBusy = false
            if (error != null || listing == null) {
                workspacePickerError = error?.message ?: "无法读取目录"
                return@postToUi
            }
            workspaceAddPath = listing.path
            workspaceAddHome = listing.home
            workspaceAddDirectoryLoaded = true
            workspaceAddEntries.clear()
            workspaceAddEntries.addAll(listing.entries.filterNot { it.hidden })
        }
    }
}

internal fun DshHomePage.createWorkspaceAddDirectory() {
    val remote = remoteRepo ?: return
    if (workspacePickerBusy || workspaceAddBusy) return
    val name = workspaceAddNewName.trim()
    if (workspaceAddPath.isEmpty() || name.isEmpty()) return
    val generation = workspacePickerGeneration
    workspaceAddBusy = true
    workspacePickerError = ""
    remote.createDirectory(workspaceAddPath, name) { createdPath, error ->
        postToUi {
            if (!pageAlive || !workspacePickerVisible || workspacePickerScreen != DshWorkspacePickerScreen.ADD ||
                generation != workspacePickerGeneration || remote !== this.repository
            ) return@postToUi
            workspaceAddBusy = false
            if (error != null || createdPath == null) {
                workspacePickerError = error?.message ?: "无法创建目录"
                return@postToUi
            }
            workspaceAddNewName = ""
            loadWorkspaceAddDirectory(createdPath)
        }
    }
}

/** 添加文件夹：注册 Host 目录为工作区，成功后直接切换过去并关闭弹窗。 */

internal fun DshHomePage.adoptWorkspaceAddDirectory() {
    val remote = remoteRepo ?: return
    val path = workspaceAddPath
    if (path.isEmpty() || workspacePickerBusy || workspaceAddBusy) return
    workspaceAddBusy = true
    workspacePickerError = ""
    remote.createWorkspace(path) { value, error ->
        postToUi {
            if (!pageAlive || !workspacePickerVisible || remote !== this.repository) return@postToUi
            if (error != null) {
                workspaceAddBusy = false
                workspacePickerError = error.message
                return@postToUi
            }
            // 重新拉取 workspace.list 基线，确保新注册工作区已进入本地投影后再解析其 id。
            remote.loadSessionCatalog({ catalog ->
                if (!pageAlive || !workspacePickerVisible || remote !== this.repository) return@loadSessionCatalog
                sessions = catalog.sessions
                reorderSessionsByUpdatedAt()
                refreshVisibleSessions()
                refreshWorkspaceGroups()
                val workspaceId = value?.optString("workspaceId")?.takeIf { it.isNotEmpty() }
                    ?: workspaceGroups.firstOrNull { it.path == path }?.workspaceId.orEmpty()
                if (workspaceId.isEmpty()) {
                    workspaceAddBusy = false
                    workspacePickerError = "Host 尚未返回该目录对应的工作区"
                    return@loadSessionCatalog
                }
                workspaceAddBusy = false
                workspacePickerScreen = DshWorkspacePickerScreen.RECENT
                switchWorkspaceTo(workspaceId)
            }, { syncError ->
                if (!pageAlive || !workspacePickerVisible) return@loadSessionCatalog
                workspaceAddBusy = false
                workspacePickerError = "无法同步工作区：${syncError.message}"
            })
        }
    }
}

internal fun DshHomePage.openWorkspaceRename(workspaceId: String, currentTitle: String) {
    dismissKeyboard()
    workspaceRenameTargetId = workspaceId
    workspaceRenameDraft = currentTitle
    workspaceActionError = ""
}

internal fun DshHomePage.saveWorkspaceRename() {
    val repository = remoteRepo ?: return
    val workspaceId = workspaceRenameTargetId
    val title = workspaceRenameDraft.trim()
    if (workspaceId.isEmpty() || title.isEmpty()) return
    workspaceActionBusy = true
    workspaceActionError = ""
    repository.renameWorkspace(workspaceId, title) { _, error ->
        postToUi {
            workspaceActionBusy = false
            if (error != null) {
                workspaceActionError = error.message
                return@postToUi
            }
            workspaceRenameTargetId = ""
            workspaceRenameDraft = ""
            refreshWorkspaceGroups()
        }
    }
}

internal fun DshHomePage.openWorkspaceDelete(workspaceId: String) {
    workspaceDeleteTargetId = workspaceId
    workspaceActionError = ""
}

internal fun DshHomePage.confirmWorkspaceDelete() {
    val repository = remoteRepo ?: return
    val workspaceId = workspaceDeleteTargetId
    if (workspaceId.isEmpty()) return
    workspaceActionBusy = true
    workspaceActionError = ""
    repository.deleteWorkspace(workspaceId) { _, error ->
        postToUi {
            workspaceActionBusy = false
            if (error != null) {
                workspaceActionError = error.message
                return@postToUi
            }
            workspaceDeleteTargetId = ""
            refreshWorkspaceGroups()
        }
    }
}

internal fun DshHomePage.moveWorkspace(workspaceId: String, delta: Int) {
    val repository = remoteRepo ?: return
    val ordered = workspaceGroups.filter { it.workspaceId.isNotEmpty() }
    val index = ordered.indexOfFirst { it.workspaceId == workspaceId }
    if (index < 0) return
    val targetIndex = index + delta
    if (targetIndex < 0 || targetIndex >= ordered.size) return
    val beforeWorkspaceId = if (targetIndex == ordered.lastIndex) {
        null
    } else {
        ordered[targetIndex].workspaceId
    }
    workspaceActionBusy = true
    workspaceActionError = ""
    repository.moveWorkspaceBefore(workspaceId, beforeWorkspaceId) { _, error ->
        postToUi {
            workspaceActionBusy = false
            if (error != null) {
                workspaceActionError = error.message
                return@postToUi
            }
            refreshWorkspaceGroups()
        }
    }
}
