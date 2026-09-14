package com.example.dsh.ui.home

import com.example.dsh.base.setTimeout
import com.example.dsh.message.DshMessage
import com.example.dsh.message.DshMessageRole
import com.example.dsh.host.DshRemoteRepository
import com.example.dsh.session.DshSession
import com.example.dsh.log.DshStreamLog
import com.example.dsh.log.LogLevel
import com.example.dsh.platform.currentTimeMillis
import com.example.dsh.base.bridgeModule
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.example.dsh.base.setTimeout
import com.example.dsh.search.DshSessionSearch
import com.example.dsh.base.setTimeout
import com.tencent.kuikly.core.timer.setTimeout
import com.example.dsh.session.DSH_DRAWER_GROUP_FLAT
import com.example.dsh.session.DSH_DRAWER_GROUP_WORKSPACE
import com.example.dsh.session.DSH_DRAWER_ORDER_MANUAL
import com.example.dsh.session.DSH_DRAWER_ORDER_UPDATED
import com.example.dsh.session.DSH_DRAWER_ROW_HEIGHT
import com.example.dsh.session.DSH_WORKSPACE_DRAG_SCOPE
import com.example.dsh.session.dshApplyOrder
import com.example.dsh.session.dshDecodeOrder
import com.example.dsh.session.dshDecodeSessionOrder
import com.example.dsh.session.DshDrawerDrag
import com.example.dsh.session.DshDrawerDragKind
import com.example.dsh.session.dshDrawerGroupByKey
import com.example.dsh.session.dshDrawerOrderByKey
import com.example.dsh.session.dshDropIndex
import com.example.dsh.session.dshEncodeOrder
import com.example.dsh.session.dshEncodeSessionOrder
import com.example.dsh.session.dshMoveItem
import com.example.dsh.session.dshSessionOrderKey
import com.example.dsh.session.dshWorkspaceOrderKey
import com.example.dsh.ui.session.DshOverflowAction

internal fun DshHomePage.openSessionDrawer() {
    if (ui.sessionDrawerVisible) return
    dismissKeyboard()
    if (isConnectionReadyLabel(connectionLabel)) {
        ui.connectionCapsuleVisible = false
        ui.connectionCapsuleFadeOut = false
    }
    // 抽屉是独立 Modal 窗口，菜单的透明捕获层够不着它；打开抽屉前先关闭长按菜单，
    // 否则切换会话后菜单仍会残留。
    closeMessageActions()
    closeSelectTextModal()
    // 重置上次可能残留的拖拽状态。
    ui.drawerDrag = DshDrawerDrag()
    ui.drawerViewOptionsVisible = false
    // Mount transparent first, then start drawer on the same frame.
    ui.sessionDrawerAnimated = false
    ui.sessionDrawerVisible = true
    setTimeout(pagerId, 16) {
        ui.sessionDrawerAnimated = true
    }
    // 首次打开且未手动展开过任何文件夹时，自动展开当前会话所在工作区（与原版一致）
    if (isRemoteHost && ui.workspaceExpandedIds.isEmpty()) {
        val group = ui.workspaceGroups.firstOrNull { it.sessions.any { s -> s.id == ui.activeSessionId } }
            ?: ui.workspaceGroups.firstOrNull()
        group?.let { ui.workspaceExpandedIds = ui.workspaceExpandedIds + it.workspaceId }
    }
    setTimeout(pagerId, DshHomePage.ANIMATION_DURATION_MS) {
        warmRecentSessionCache(scrollToEndAfterLoad = false)
    }
}

internal fun DshHomePage.closeSessionDrawer() {
    if (!ui.sessionDrawerVisible) return
    // 关闭抽屉时清掉会话行溢出菜单的目标会话，避免残留指向已关闭的会话
    ui.overflowTargetSessionId = ""
    ui.drawerViewOptionsVisible = false
    // Reverse the opening transition: drawer slides back out.
    ui.sessionDrawerAnimated = false
    setTimeout(pagerId, DshHomePage.ANIMATION_DURATION_MS) {
        ui.sessionDrawerVisible = false
    }
}

internal fun DshHomePage.closeSessionDrawerImmediately() {
    if (!ui.sessionDrawerVisible) return
    ui.overflowTargetSessionId = ""
    ui.sessionDrawerAnimated = false
    ui.sessionDrawerVisible = false
}

/** 从抽屉搜索入口进入独立搜索界面，挂载后聚焦输入框。 */

internal fun DshHomePage.openSessionSearch() {
    dismissKeyboard()
    sessionSearchInput = ""
    if (ui.sessionSearchHits.isNotEmpty()) ui.sessionSearchHits.clear()
    ui.sessionSearchActive = false
    ui.sessionSearchVisible = true
    postToUi {
        if (pageAlive && ui.sessionSearchVisible) sessionSearchInputView?.focus()
    }
}

/** 退出搜索界面：清空输入与结果并收起键盘。 */

internal fun DshHomePage.closeSessionSearch() {
    sessionSearchInput = ""
    sessionSearchInputView?.setText("")
    sessionSearchInputView?.blur()
    if (ui.sessionSearchHits.isNotEmpty()) ui.sessionSearchHits.clear()
    ui.sessionSearchActive = false
    ui.sessionSearchVisible = false
}

/**
 * 搜索输入变化：仅用可观察的命中列表驱动 UI，原生文本单独保存，
 * 避免每次按键重设 Input 文本导致光标跳动。
 */

internal fun DshHomePage.onSessionSearch(value: String) {
    sessionSearchInput = value
    rebuildSessionSearch(value)
}

/** 清空搜索框：保留搜索界面，仅重置输入与结果。 */

internal fun DshHomePage.clearSessionSearch() {
    sessionSearchInput = ""
    sessionSearchInputView?.setText("")
    if (ui.sessionSearchHits.isNotEmpty()) ui.sessionSearchHits.clear()
    ui.sessionSearchActive = false
}

internal fun DshHomePage.sessionSearchDateLabel(timestamp: Long): String =
    if (timestamp <= 0L) "" else bridgeModule.dateFormatter(timestamp, "yyyy年M月d日")

/**
 * 本地搜索：命中计算委托给纯函数 [DshSessionSearch.build]，
 * 这里只负责读取内存缓存并把结果灌进 observable 列表（先收集再一次 diffUpdate）。
 */

internal fun DshHomePage.rebuildSessionSearch(rawQuery: String) {
    if (rawQuery.isBlank()) {
        if (ui.sessionSearchHits.isNotEmpty()) ui.sessionSearchHits.clear()
        ui.sessionSearchActive = false
        return
    }
    sessionSearchInput = rawQuery
    val hits = DshSessionSearch.build(
        sessions = ui.visibleSessions,
        messagesFor = { sessionMessageStates[it] },
        rawQuery = rawQuery,
        hitLimit = SESSION_SEARCH_HIT_LIMIT,
        dateLabel = { sessionSearchDateLabel(it) },
    )
    ui.sessionSearchHits.diffUpdate(hits)
    ui.sessionSearchActive = true
    // 远端：正文来自消息缓存；未缓存会话按需拉历史（限量 + 去重），拉到后重算。
    // 避免每敲一个字就对全部会话发起请求。
    if (isRemoteHost) {
        ui.visibleSessions.asSequence()
            .filter { (sessionMessageStates[it.id]?.size ?: 0) == 0 }
            .take(SESSION_SEARCH_HISTORY_LIMIT)
            .forEach { loadSearchHistory(it.id) }
    }
}

private const val SESSION_SEARCH_HISTORY_LIMIT = 24

/** 按需拉取单会话历史补充搜索缓存；同会话在途时直接跳过（去重防重复请求）。 */
internal fun DshHomePage.loadSearchHistory(sessionId: String) {
    if (!sessionSearchHistoryLoading.add(sessionId)) return
    val hostRepository = repository ?: run { sessionSearchHistoryLoading.remove(sessionId); return }
    hostRepository.loadHistory(sessionId, { loaded ->
        sessionSearchHistoryLoading.remove(sessionId)
        if (!pageAlive) return@loadHistory
        if (loaded.isNotEmpty()) {
            val state = sessionMessageStates.getOrPut(sessionId) { ObservableList() }
            if (state.isEmpty()) state.addAll(loaded)
        }
        sessionMessageReady.add(sessionId)
        if (ui.sessionSearchActive && sessionSearchInput.isNotBlank()) rebuildSessionSearch(sessionSearchInput)
    }, { _ ->
        sessionSearchHistoryLoading.remove(sessionId)
    })
}

internal fun DshHomePage.sessionPending(sessionId: String): Boolean {
    return sessionId in ui.pendingSessionIds
}

/** 当前会话所在工作区；不在任何真实工作区时返回未分组键。 */

internal fun DshHomePage.activeWorkspaceId(): String {
    val id = ui.activeSessionId
    if (id.isEmpty()) return DshHomePage.NO_ACTIVE_WORKSPACE
    return ui.workspaceGroups
        .firstOrNull { it.sessions.any { s -> s.id == id } }
        ?.workspaceId
        ?: ""
}

internal fun DshHomePage.refreshVisibleSessions() {
    ensureDrawerOrdersLoaded()
    val archived = (remoteRepo)?.archivedSessionIds.orEmpty()
    val visible = ui.sessions.filterNot { it.blank || it.id in archived }
    val order = if (ui.drawerViewOrderBy == DSH_DRAWER_ORDER_MANUAL) ui.sessionManualOrder[""].orEmpty() else emptyList()
    val ordered = when {
        order.isNotEmpty() -> dshApplyOrder(visible, order) { it.id }
        ui.drawerViewOrderBy == DSH_DRAWER_ORDER_UPDATED ->
            visible.sortedWith(compareByDescending<DshSession> { it.updatedAt }.thenBy { it.id })
        else -> visible
    }
    syncVisibleSessions(ordered, ui.visibleSessions, archived)
    refreshPendingSessionIds()
}

internal fun DshHomePage.updateSessionMetadata(sessionId: String, update: (DshSession) -> DshSession) {
    val next = ui.sessions.map { if (it.id == sessionId) update(it) else it }
    if (next == ui.sessions) return
    ui.sessions = next
    refreshVisibleSessions()
    refreshWorkspaceGroups()
    ui.archivedSessions.diffUpdate(ui.archivedSessions.map { if (it.id == sessionId) update(it) else it })
    runCatching { localStore?.replaceSessions(activeConnectionId, ui.sessions) }
}

internal fun DshHomePage.createSession() {
    val hostRepository = repository ?: run {
        if (isRemoteHost) {
            closeSessionDrawer()
            bridgeModule.toast("未连接到远程 DSH")
        } else if (pendingApiKey.isEmpty()) {
            connectionLabel = "请先配置 API Key"
            openCredentialSettings()
        } else {
            closeSessionDrawer()
            connectionLabel = "本地 DSH 尚未就绪"
        }
        return
    }
    dismissKeyboard()
    closeSessionDrawer()
    val remoteRepository = hostRepository as? DshRemoteRepository
    // 当前工作区：优先当前会话所在的工作区分组，其次用会话 cwd 反查工作区路径，
    // 最后回退 Host 工作区基线。保证「新会话」留在当前工作区；
    // 只有工作区选择器里的「添加文件夹」才会创建新工作区。
    val activeSession = ui.sessions.firstOrNull { it.id == ui.activeSessionId }
    val currentWorkspaceId = if (isRemoteHost) {
        // 注意：activeWorkspaceId() 在无活动会话时返回 UI 分组哨兵 __none__，
        // 不能当作真实 workspaceId 发给 Host（会被判定为未知工作区而创建失败）。
        activeWorkspaceId()
            .takeIf { it.isNotEmpty() && it != DshHomePage.NO_ACTIVE_WORKSPACE }
            ?: activeSession?.cwd?.takeIf { it.isNotEmpty() }
                ?.let { cwd -> ui.workspaceGroups.firstOrNull { it.path == cwd }?.workspaceId }
            ?: remoteRepository?.workspaceIdForSession(ui.activeSessionId)
    } else {
        null
    }
    val blankSession = if (isRemoteHost) {
        remoteRepository?.blankSessionInWorkspace(currentWorkspaceId)
    } else {
        ui.sessions.firstOrNull { it.blank }
    }
    if (blankSession != null) {
        if (blankSession.id != ui.activeSessionId) {
            selectSession(blankSession.id)
        } else {
            applyActiveSessionChrome()
        }
        loadSkills(blankSession.id)
        postToUi { loadModels(blankSession.id) }
        return
    }
    hostRepository.createSession(currentWorkspaceId, { sessionId ->
        val created = DshSession(
            id = sessionId,
            title = "新会话",
            workspace = "Host",
            updatedLabel = "",
            blank = true,
            permission = ui.permissionValue,
            agentPreset = ui.agentModeValue,
        )
        // Keep the existing sessions when creating a new one. Clearing
        // this list also rewrites SQLite with only the newly created row.
        if (ui.sessions.none { it.id == created.id }) {
            ui.sessions = ui.sessions + created
            reorderSessionsByUpdatedAt()
            refreshVisibleSessions()
        }
        runCatching { localStore?.replaceSessions(activeConnectionId, ui.sessions.toList()) }
        ui.activeSessionId = sessionId
        ui.messages = ObservableList()
        sessionMessageStates[sessionId] = ui.messages
        sessionMessageReady.add(sessionId)
        ensureConversationPanel(sessionId)
        ui.draft = ""
        inputView?.setText("")
        applyActiveSessionChrome()
        // 远程模式：同步 Host 会话目录，让新会话归入当前工作区分组并带上 cwd，
        // 避免新会话落入「未分组」导致工作区看似被清除。
        if (isRemoteHost) {
            remoteRepository?.loadSessionCatalog({ catalog ->
                ui.sessions = catalog.sessions
                reorderSessionsByUpdatedAt()
                refreshVisibleSessions()
                refreshWorkspaceGroups()
                applyActiveSessionChrome()
            }, { /* 忽略：会话已创建，本地已保留 */ })
        }
        postToUi {
            if (ui.activeSessionId == sessionId) {
                loadSkills(sessionId)
                loadModels(sessionId)
            }
        }
    }, { error ->
        connectionLabel = "新会话创建失败"
        ui.messages.add(DshMessage("session-create-error-${ui.messages.size}", DshMessageRole.ERROR, error))
    }, permission = ui.permissionValue, agentPreset = ui.agentModeValue)
}

internal fun DshHomePage.ensureDrawerOrdersLoaded() {
    val prefs = prefsModule ?: return
    val scope = activeConnectionId
    if (scope == drawerOrderScope) return
    drawerOrderScope = scope
    ui.sessionManualOrder = dshDecodeSessionOrder(runCatching { prefs.getItem(dshSessionOrderKey(scope)) }.getOrNull())
    ui.workspaceManualOrder = dshDecodeOrder(runCatching { prefs.getItem(dshWorkspaceOrderKey(scope)) }.getOrNull())
    ui.drawerViewGroupBy = runCatching { prefs.getItem(dshDrawerGroupByKey(scope)) }.getOrNull()
        ?.takeIf { it == DSH_DRAWER_GROUP_WORKSPACE || it == DSH_DRAWER_GROUP_FLAT }
        ?: DSH_DRAWER_GROUP_WORKSPACE
    ui.drawerViewOrderBy = runCatching { prefs.getItem(dshDrawerOrderByKey(scope)) }.getOrNull()
        ?.takeIf { it == DSH_DRAWER_ORDER_MANUAL || it == DSH_DRAWER_ORDER_UPDATED }
        ?: DSH_DRAWER_ORDER_UPDATED
}

internal fun DshHomePage.persistSessionManualOrder() {
    val prefs = prefsModule ?: return
    runCatching { prefs.setItem(dshSessionOrderKey(activeConnectionId), dshEncodeSessionOrder(ui.sessionManualOrder)) }
}

internal fun DshHomePage.persistWorkspaceManualOrder() {
    val prefs = prefsModule ?: return
    runCatching { prefs.setItem(dshWorkspaceOrderKey(activeConnectionId), dshEncodeOrder(ui.workspaceManualOrder)) }
}

internal fun DshHomePage.persistDrawerViewOptions() {
    val prefs = prefsModule ?: return
    runCatching { prefs.setItem(dshDrawerGroupByKey(activeConnectionId), ui.drawerViewGroupBy) }
    runCatching { prefs.setItem(dshDrawerOrderByKey(activeConnectionId), ui.drawerViewOrderBy) }
}

/** 打开抽屉「视图选项」菜单，锚定到点击的按钮坐标（对齐电脑端 ViewOptionsMenu）。 */

internal fun DshHomePage.openDrawerViewOptions(pageX: Float, pageY: Float) {
    ui.drawerViewOptionsX = pageX
    ui.drawerViewOptionsY = pageY
    ui.drawerViewOptionsVisible = true
}

internal fun DshHomePage.closeDrawerViewOptions() {
    ui.drawerViewOptionsVisible = false
}

/** 视图选项选择：workspace/flat 切换分组，manual/updated 切换排序。 */

internal fun DshHomePage.selectDrawerViewOption(id: String) {
    when (id) {
        DSH_DRAWER_GROUP_WORKSPACE, DSH_DRAWER_GROUP_FLAT -> ui.drawerViewGroupBy = id
        DSH_DRAWER_ORDER_MANUAL, DSH_DRAWER_ORDER_UPDATED -> ui.drawerViewOrderBy = id
        else -> return
    }
    ui.drawerViewOptionsVisible = false
    persistDrawerViewOptions()
    refreshVisibleSessions()
    refreshWorkspaceGroups()
}

/** 长按会话行开始拖拽；groupKey 为空表示本地（非工作区分组）的扁平列表。 */

internal fun DshHomePage.beginSessionDrag(groupKey: String, sessionId: String, index: Int, pageY: Float) {
    dragOriginPageY = pageY
    ui.drawerDrag = DshDrawerDrag(
        kind = DshDrawerDragKind.SESSION,
        key = sessionId,
        groupKey = groupKey,
        startIndex = index,
        targetIndex = index,
        offsetY = 0f,
        itemHeight = DSH_DRAWER_ROW_HEIGHT,
    )
}

/** 工作区文件夹行拖拽开始。 */

internal fun DshHomePage.beginWorkspaceDrag(workspaceId: String, index: Int, itemHeight: Float, pageY: Float) {
    dragOriginPageY = pageY
    ui.drawerDrag = DshDrawerDrag(
        kind = DshDrawerDragKind.WORKSPACE,
        key = workspaceId,
        groupKey = DSH_WORKSPACE_DRAG_SCOPE,
        startIndex = index,
        targetIndex = index,
        offsetY = 0f,
        itemHeight = itemHeight,
    )
}

/** 拖拽过程中更新位移与落点；heights 为当前列表各项高度。 */

internal fun DshHomePage.updateDrawerDrag(pageY: Float, heights: List<Float>) {
    val state = ui.drawerDrag
    if (state.kind == DshDrawerDragKind.NONE) return
    val offset = pageY - dragOriginPageY
    val target = if (heights.isEmpty()) state.startIndex else dshDropIndex(offset, state.startIndex, heights)
    ui.drawerDrag = state.copy(offsetY = offset, targetIndex = target)
}

/** 拖拽结束：按落点提交顺序并持久化。 */

internal fun DshHomePage.endDrawerDrag() {
    val state = ui.drawerDrag
    ui.drawerDrag = DshDrawerDrag()
    if (state.kind == DshDrawerDragKind.NONE) return
    if (state.startIndex < 0 || state.targetIndex < 0 || state.startIndex == state.targetIndex) return
    when (state.kind) {
        DshDrawerDragKind.SESSION -> commitSessionOrder(state.groupKey, state.startIndex, state.targetIndex)
        DshDrawerDragKind.WORKSPACE -> commitWorkspaceOrder(state.startIndex, state.targetIndex)
        DshDrawerDragKind.NONE -> Unit
    }
}

/** 拖拽被系统取消：丢弃本次排序，不提交。 */

internal fun DshHomePage.cancelDrawerDrag() {
    ui.drawerDrag = DshDrawerDrag()
}

internal fun DshHomePage.commitSessionOrder(groupKey: String, from: Int, to: Int) {
    if (groupKey.isEmpty()) {
        val ids = ui.visibleSessions.map { it.id }
        val moved = dshMoveItem(ids, from, to)
        if (moved == ids) return
        ui.sessionManualOrder = ui.sessionManualOrder + ("" to moved)
        persistSessionManualOrder()
        markDrawerOrderManual()
        refreshVisibleSessions()
        return
    }
    val group = ui.workspaceGroups.firstOrNull { it.workspaceId == groupKey } ?: return
    val ids = group.sessions.map { it.id }
    val moved = dshMoveItem(ids, from, to)
    if (moved == ids) return
    ui.sessionManualOrder = ui.sessionManualOrder + (groupKey to moved)
    persistSessionManualOrder()
    markDrawerOrderManual()
    refreshWorkspaceGroups()
}

/** 手动拖拽后固定为「手动排序」，否则最近更新排序会立即覆盖用户编排。 */

internal fun DshHomePage.markDrawerOrderManual() {
    if (ui.drawerViewOrderBy == DSH_DRAWER_ORDER_MANUAL) return
    ui.drawerViewOrderBy = DSH_DRAWER_ORDER_MANUAL
    persistDrawerViewOptions()
}

internal fun DshHomePage.commitWorkspaceOrder(from: Int, to: Int) {
    val ids = ui.workspaceGroups.filter { it.workspaceId.isNotEmpty() }.map { it.workspaceId }
    val moved = dshMoveItem(ids, from, to)
    if (moved == ids) return
    ui.workspaceManualOrder = moved
    persistWorkspaceManualOrder()
    refreshWorkspaceGroups()
}

internal fun DshHomePage.renameActiveSession() {
    val repository = remoteRepo ?: return
    val current = ui.sessions.firstOrNull { it.id == ui.activeSessionId } ?: return
    val title = current.title.takeIf { it != "尚无标题" && it != "新会话" } ?: ""
    if (title.isBlank()) return
    repository.renameSession(ui.activeSessionId, title) { _, _ ->
        postToUi { loadRepository(preferredSessionId = ui.activeSessionId) }
    }
}

internal fun DshHomePage.reorderSessionsByUpdatedAt() {
    ui.sessions = ui.sessions.sortedByDescending { it.updatedAt }
}

/** 会话产生新消息时刷新 updatedAt（消息时间 = 当前时刻），并按新到旧重排主列表与抽屉分组。 */

internal fun DshHomePage.touchSessionActivity(sessionId: String) {
    val idx = ui.sessions.indexOfFirst { it.id == sessionId }
    if (idx < 0) return
    val now = currentTimeMillis()
    if (ui.sessions[idx].updatedAt >= now) return
    ui.sessions = ui.sessions.map { if (it.id == sessionId) it.copy(updatedAt = now) else it }
    (remoteRepo)?.touchSessionActivity(sessionId, now)
    reorderSessionsByUpdatedAt()
    refreshWorkspaceGroups()
    refreshVisibleSessions()
    runCatching { localStore?.replaceSessions(activeConnectionId, ui.sessions.toList()) }
}

// ===== 会话 overflow menu：日志 / 重命名 / 归档 / 删除 =====

/** 从会话抽屉某行的 ⋯ 打开 overflow menu：锁定目标会话并锚定到点击位置，抽屉保持开启。 */

internal fun DshHomePage.openOverflowMenuFor(sessionId: String, anchorX: Float = -1f, anchorY: Float = -1f) {
    if (ui.overflowMenuVisible) return
    closeMessageActions()
    closeSelectTextModal()
    ui.overflowTargetSessionId = sessionId
    ui.overflowAnchorX = anchorX
    ui.overflowAnchorY = anchorY
    ui.overflowMenuVisible = true
}

internal fun DshHomePage.closeOverflowMenu() {
    ui.overflowMenuVisible = false
}

/** overflow menu 的目标会话：抽屉行打开时指向该行会话，否则回退到当前会话。 */

internal fun DshHomePage.overflowTargetId(): String = ui.overflowTargetSessionId.ifEmpty { ui.activeSessionId }

internal fun DshHomePage.overflowActions(): ObservableList<DshOverflowAction> {
    val result = ObservableList<DshOverflowAction>()
    result.add(DshOverflowAction("log", "日志", "log.svg"))
    val session = ui.sessions.firstOrNull { it.id == overflowTargetId() }
    if (readableExportBusy) result.add(DshOverflowAction("export-status", "查看分享进度", "share.svg"))
    else if (session != null && !session.blank) result.add(DshOverflowAction("export-text", "分享消息", "share.svg"))
    if (ui.readableExport.canShare(overflowTargetId(), activeConnectionId)) {
        result.add(DshOverflowAction("share-text", "重新分享上次内容", "share.svg"))
    }
    if (session != null && !session.blank) {
        result.add(DshOverflowAction("rename", "重命名", "rename.svg"))
        result.add(DshOverflowAction("archive", "归档", "archive.svg"))
    }
    return result
}

internal fun DshHomePage.onOverflowAction(id: String) {
    ui.sessionActionTargetId = overflowTargetId()
    closeOverflowMenu()
    when (id) {
        "log" -> openSessionLogs()
        "export-text" -> beginExportSelection(ui.sessionActionTargetId)
        "export-status" -> { closeSessionDrawer(); ui.readableExportDialogVisible = true }
        "share-text" -> shareReadableExport()
        "rename" -> openSessionRenameDialog()
        "archive" -> { ui.sessionArchiveError = ""; ui.sessionArchiveVisible = true }
        "delete" -> { ui.sessionDeleteError = ""; ui.sessionDeleteVisible = true }
    }
}

// ===== 会话日志（独立路由页 DshLogPage） =====

internal fun DshHomePage.openSessionRenameDialog() {
    val session = ui.sessions.firstOrNull { it.id == ui.sessionActionTargetId } ?: return
    dismissKeyboard()
    ui.sessionRenameDraft = session.title.takeIf { it != "尚无标题" && it != "新会话" } ?: ""
    ui.sessionRenameError = ""
    ui.sessionRenameVisible = true
}

internal fun DshHomePage.cancelSessionRename() {
    if (ui.sessionRenameBusy) return
    bridgeModule.closeKeyboard()
    ui.sessionRenameVisible = false
    ui.sessionRenameError = ""
}

internal fun DshHomePage.saveSessionRename() {
    if (ui.sessionRenameBusy) return
    val targetId = ui.sessionActionTargetId
    if (targetId.isEmpty()) return
    val repository = remoteRepo ?: run {
        ui.sessionRenameError = "当前连接不支持重命名会话"
        return
    }
    val title = ui.sessionRenameDraft.trim()
    if (title.isEmpty()) {
        ui.sessionRenameError = "名称不能为空"
        return
    }
    ui.sessionRenameBusy = true
    ui.sessionRenameError = ""
    bridgeModule.closeKeyboard()
    val connection = activeConnectionId
    repository.renameSession(targetId, title) { _, error ->
        postToUi {
            if (!pageAlive || this.repository !== repository || activeConnectionId != connection) return@postToUi
            ui.sessionRenameBusy = false
            if (error != null) {
                ui.sessionRenameError = when (error.code) {
                    "title-invalid" -> "会话名称无效（不能为空或只包含空白字符），请修改后重试"
                    "session-not-found" -> "会话不存在，可能已被删除，请刷新列表"
                    else -> error.message
                }
                return@postToUi
            }
            ui.sessionRenameVisible = false
            catalogRequestGeneration++
            updateSessionMetadata(targetId) { it.copy(title = title) }
            bridgeModule.toast("会话已重命名")
        }
    }
}

// ===== 归档会话 =====

internal fun DshHomePage.selectSession(id: String) {
    dismissKeyboard()
    if (id == ui.activeSessionId) {
        return
    }
    if (!sessionMessageReady.contains(id)) {
        pendingSessionSelections.add(id)
        return
    }
    if (!ui.conversationPanelIds.contains(id)) {
        ensureConversationPanel(id)
        addTaskWhenPagerUpdateLayoutFinish {
            if (ui.activeSessionId != id) selectSession(id)
        }
        return
    }
    selectMountedSession(id)
}

internal fun DshHomePage.selectMountedSession(id: String) {
    if (id == ui.activeSessionId) return
    // 离开多选态目标会话时先退出多选态，避免勾选状态挂到别的会话上
    if (ui.exportSelectMode && id != exportSelectSessionId && id != pendingExportSelectionSessionId) {
        cancelExportSelection()
    }
    // 切换会话时兜底关闭长按菜单，覆盖所有切换路径（抽屉/会话栏/新建会话等）。
    closeMessageActions()
    closeSelectTextModal()
    refreshSessionRenderTree(id)
    cancelStreamingForSessionSwitch()
    sessionMessageStates[ui.activeSessionId] = ui.messages
    val nextMessages = sessionMessageState(id, loadFromDisk = false)
    ensureConversationPanel(id)
    ui.messages = nextMessages
    ui.activeSessionId = id
    DshStreamLog.log(LogLevel.INFO, "app.session.selected", "ui.messages=${ui.messages.size}", id)
    scrollMessagesToEnd()
    addTaskWhenPagerUpdateLayoutFinish {
        refreshSessionRenderTree(id)
        if (ui.activeSessionId == id) scrollMessagesToEnd()
    }
    // Invalidate any in-flight request for the previous session before
    // starting the new one, so an old response cannot repaint this view.
    historyRequestGeneration++
    // 清理旧会话的附件加载状态，防止 pendingAttachmentReads 泄漏导致新会话附件被跳过
    pendingAttachmentReads.clear()
    loadMessagesFromDisk(id)
    fetchHostHistory(id)
    postToUi {
        if (ui.activeSessionId == id) loadModels(id)
    }
    ui.draft = ""
    inputView?.setText("")
    applyActiveSessionChrome()
    maybeEnterPendingExportSelection()
}
