package com.example.dsh.ui.home

import com.example.dsh.base.setTimeout
import com.example.dsh.ui.message.DshMessageActionsMenu
import com.example.dsh.ui.chat.DshSelectTextModal
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.example.dsh.base.setTimeout
import com.example.dsh.base.setTimeout
import com.tencent.kuikly.core.timer.setTimeout
import com.example.dsh.ui.session.DshArchivedSessions
import com.example.dsh.ui.export.DshTextExportDialog
import com.example.dsh.ui.interaction.DshAgentModeOption
import com.example.dsh.ui.interaction.DshAgentModePicker
import com.example.dsh.ui.models.DshModelPicker
import com.example.dsh.ui.interaction.DshPermissionOption
import com.example.dsh.ui.interaction.DshPermissionPicker
import com.example.dsh.ui.interaction.DshRiskConfirmationModal
import com.example.dsh.ui.session.DshSessionArchiveDialog
import com.example.dsh.ui.session.DshSessionDeleteDialog
import com.example.dsh.ui.session.DshSessionRenameDialog
import com.example.dsh.ui.search.DshSessionSearchOverlay

internal fun DshHomePage.bodySessionSearchAndArchive(): ViewBuilder {
    val ctx = this
    return {
        // ===== 独立全屏搜索界面 =====
        // 从抽屉搜索入口打开，顶部返回箭头 + 输入框，下方为命中结果。
        vif({ ctx.ui.sessionSearchVisible }) {
            DshSessionSearchOverlay(
                input = { ctx.sessionSearchInput },
                hasInput = { ctx.ui.sessionSearchActive },
                results = { ctx.ui.sessionSearchHits },
                activeId = { ctx.ui.activeSessionId },
                onInput = { ctx.onSessionSearch(it) },
                onClear = { ctx.clearSessionSearch() },
                onBack = { ctx.closeSessionSearch() },
                onSelect = { id ->
                    ctx.closeSessionSearch()
                    ctx.closeSessionDrawerImmediately()
                    setTimeout(ctx.pagerId, 0) {
                        ctx.selectSession(id)
                    }
                },
                onInputRef = { ctx.sessionSearchInputView = it.view },
                colors = { ctx.themeColors },
            )
        }

        vif({ ctx.ui.archiveListVisible }) {
            DshArchivedSessions(
                groups = { ctx.ui.archiveGroups },
                projectOptions = { ctx.ui.archiveProjectOptions },
                selectedProject = { ctx.ui.archiveProjectFilter },
                sort = { ctx.ui.archiveSort },
                openMenu = { ctx.ui.archiveMenu },
                search = { ctx.ui.archiveSearch },
                loading = { ctx.ui.archiveListLoading },
                error = { ctx.ui.archiveListError },
                notice = { ctx.ui.archiveNotice },
                busy = { ctx.ui.archiveBusy },
                openingId = { ctx.ui.archiveOpeningId },
                confirm = { ctx.ui.archiveConfirm },
                overflowVisible = { ctx.ui.archiveOverflowVisible },
                overflowActions = { ctx.archiveOverflowActions() },
                overflowAnchorX = { ctx.ui.archiveOverflowAnchorX },
                overflowAnchorY = { ctx.ui.archiveOverflowAnchorY },
                filterAnchorX = { ctx.ui.archiveFilterX },
                filterAnchorY = { ctx.ui.archiveFilterY },
                statusBarHeight = ctx.pagerData.statusBarHeight,
                pageViewWidth = ctx.pagerData.pageViewWidth,
                pageViewHeight = ctx.pagerData.pageViewHeight,
                onSearch = { ctx.onArchiveSearch(it) },
                onToggleMenu = { menu, x, y -> ctx.onArchiveToggleMenu(menu, x, y) },
                onPickProject = { ctx.onArchivePickProject(it) },
                onPickSort = { ctx.onArchivePickSort(it) },
                onClose = { ctx.closeArchiveList() },
                onOpen = { ctx.openArchivedSession(it) },
                onOpenOverflow = { id, x, y -> ctx.openArchiveOverflowFor(id, x, y) },
                onOverflowSelect = { ctx.onArchiveOverflowAction(it) },
                onDismissOverflow = { ctx.dismissArchiveOverflow() },
                onRequestDeleteProject = { ctx.requestArchiveDeleteProject(it) },
                onRequestDeleteAll = { ctx.requestArchiveDeleteAll() },
                onConfirmDelete = { ctx.confirmArchiveDelete() },
                onCancelConfirm = { ctx.cancelArchiveConfirm() },
                formatDate = { ctx.archiveDateLabel(it) },
                colors = { ctx.themeColors },
            )
        }

    }
}

internal fun DshHomePage.bodyPickers(): ViewBuilder {
    val ctx = this
    return {
        // ===== 模型选择弹窗 =====
        // 选择当前会话使用的模型。
        vif({ ctx.ui.modelPickerVisible }) {
            DshModelPicker(
                options = { ctx.ui.modelOptions },
                busy = { ctx.ui.modelPickerBusy },
                error = { ctx.ui.modelPickerError },
                showEfforts = { ctx.ui.modelEffortsVisible },
                onShowEfforts = { ctx.ui.modelEffortsVisible = it },
                onClose = { ctx.ui.modelPickerVisible = false },
                onSelect = { option ->
                    ctx.selectModel(option)
                },
                onSelectEffort = { ctx.selectModelEffort(it) },
                colors = { ctx.themeColors },
            )
        }

        // ===== 权限选择弹窗 =====
        // 选择「默认权限预设」：选项优先取 host settings 动态枚举（与设置页一致），
        // 未连接/不支持时回退本地三项。选择动作走 settings.update 全局设置通道，
        // 与设置页「工作区权限」同一入口，影响之后新建的会话。
        vif({ ctx.ui.permissionPickerVisible }) {
            DshPermissionPicker(
                options = {
                    ObservableList<DshPermissionOption>().apply {
                        val choices = ctx.ui.settingsSnapshot.permissionChoices
                        if (choices.isNotEmpty()) {
                            addAll(choices.map {
                                DshPermissionOption(
                                    it.value,
                                    it.label,
                                    it.value == ctx.ui.settingsSnapshot.permissionPreset,
                                )
                            })
                        } else {
                            addAll(listOf(
                                DshPermissionOption("read-only", "只读", ctx.ui.permissionValue == "read-only"),
                                DshPermissionOption("workspace-write", "工作区写入", ctx.ui.permissionValue == "workspace-write"),
                                DshPermissionOption("danger-full-access", "完全访问", ctx.ui.permissionValue == "danger-full-access"),
                            ))
                        }
                    }
                },
                onClose = { ctx.ui.permissionPickerVisible = false },
                onSelect = { option ->
                    if (option.value == "danger-full-access") {
                        ctx.pendingDangerPermission = option
                        ctx.ui.riskAcknowledged = false
                        ctx.ui.riskConfirmVisible = true
                    } else {
                        ctx.ui.permissionValue = option.value
                        ctx.ui.permissionLabel = option.label
                        ctx.ui.permissionPickerVisible = false
                        ctx.applyPermissionPreset(option)
                    }
                },
                colors = { ctx.themeColors },
            )
        }

        // ===== Full access 风险确认弹窗（对齐 dsh 原版 RiskConfirmation）=====
        vif({ ctx.ui.riskConfirmVisible }) {
            DshRiskConfirmationModal(
                title = "确认启用 Full access？",
                description = "启用 Full access 后，agent 将减少确认步骤，并且可以直接执行更多操作，包括敏感操作、文件修改或外部命令。仅建议在你信任当前任务时使用。",
                acknowledgeLabel = "我已了解风险，并愿意继续",
                cancelLabel = "取消",
                confirmLabel = "启用 Full access",
                acknowledged = { ctx.ui.riskAcknowledged },
                busy = { ctx.ui.settingsChoiceBusy },
                onAcknowledgedChange = { ctx.ui.riskAcknowledged = it },
                onCancel = { ctx.ui.riskConfirmVisible = false },
                onConfirm = {
                    val option = ctx.pendingDangerPermission
                    if (option != null) {
                        ctx.ui.riskConfirmVisible = false
                        ctx.pendingDangerPermission = null
                        ctx.ui.permissionPickerVisible = false
                        ctx.ui.permissionValue = option.value
                        ctx.ui.permissionLabel = option.label
                        ctx.applyPermissionPreset(option)
                    }
                },
                colors = { ctx.themeColors },
            )
        }

        // ===== 模式选择弹窗 =====
        // 会话开始前选择 Agent 模式（agentPreset）。host 有 agentPreset.list，
        // 当前以本地预设为兜底；选中值存本地，未来在创建会话时随参数下发。
        vif({ ctx.ui.agentModePickerVisible }) {
            DshAgentModePicker(
                title = { ctx.ui.agentModePickerTitle },
                options = {
                    ObservableList<DshAgentModeOption>().apply {
                        if (ctx.ui.agentPresetOptions.isEmpty()) {
                            addAll(listOf(
                                DshAgentModeOption(
                                    "standard", "标准模式",
                                    "功能完整的编码 Agent，支持文件编辑、Shell、文件与网页检索、Skills、计划、目标、子代理和工作流",
                                    ctx.ui.agentModeValue == "standard",
                                ),
                                DshAgentModeOption(
                                    "ptc", "PTC 模式",
                                    "具备标准模式的全部能力，并通过 Code Mode SDK 呈现工具，让模型用一个 TypeScript 程序组合多步操作",
                                    ctx.ui.agentModeValue == "ptc",
                                ),
                                DshAgentModeOption(
                                    "minimal", "极简模式",
                                    "仅提供持久 bash 与 str_replace_editor 的双工具编码 Agent",
                                    ctx.ui.agentModeValue == "minimal",
                                ),
                                DshAgentModeOption(
                                    "creator", "创造模式",
                                    "用于创建自定义 Agent preset：具备标准模式的全部能力，并提供运行时检查、插件实验和 preset 创作指导",
                                    ctx.ui.agentModeValue == "creator",
                                ),
                            ))
                        } else {
                            for (preset in ctx.ui.agentPresetOptions) {
                                add(DshAgentModeOption(
                                    preset.id,
                                    preset.name,
                                    preset.description,
                                    ctx.ui.agentModeValue == preset.id,
                                ))
                            }
                        }
                    }
                },
                onClose = { ctx.ui.agentModePickerVisible = false },
                onSelect = { option ->
                    ctx.ui.agentModeValue = option.value
                    ctx.ui.agentModeLabel = option.label
                    ctx.ui.agentModePickerVisible = false
                },
                colors = { ctx.themeColors },
            )
        }

    }
}

internal fun DshHomePage.bodyMessageOverlays(): ViewBuilder {
    val ctx = this
    return {
        // ===== 消息长按操作菜单 =====
        // 长按 AI 输出消息时弹出的底部操作菜单（复制/选择文本/反馈/分支/分享）。
        DshMessageActionsMenu(
            visible = { ctx.ui.messageActionsMessage != null },
            items = { ctx.messageActionsItems() },
            blurUri = { ctx.ui.menuBlurUri },
            x = { ctx.ui.messageActionsX },
            y = { ctx.ui.messageActionsY },
            onDismiss = { ctx.closeMessageActions() },
            colors = { ctx.themeColors },
        )
        // ===== 选择文本弹窗 =====
        // 「选择文本」以单个可选中文本节点承载完整正文，供原生选区复制。
        DshSelectTextModal(
            visible = { ctx.ui.selectTextModalVisible },
            content = { ctx.ui.selectTextModalContent },
            onClose = { ctx.closeSelectTextModal() },
            colors = { ctx.themeColors },
        )
        // ===== 会话管理动作 =====
        // overflow menu 由会话抽屉（DshSessionDrawer）内实例渲染，锚定到点击的会话行 ⋯，
        // 这里不再重复渲染，避免同时弹出两个菜单。
        DshSessionRenameDialog(
            visible = { ctx.ui.sessionRenameVisible },
            draft = { ctx.ui.sessionRenameDraft },
            busy = { ctx.ui.sessionRenameBusy },
            error = { ctx.ui.sessionRenameError },
            onDraftChange = { ctx.ui.sessionRenameDraft = it },
            onCancel = { ctx.cancelSessionRename() },
            onSave = { ctx.saveSessionRename() },
            pageViewWidth = ctx.pagerData.pageViewWidth,
            colors = { ctx.themeColors },
        )
        vif({ ctx.ui.readableExportDialogVisible }) {
            DshTextExportDialog(
                state = { ctx.ui.readableExport },
                onClose = { ctx.closeReadableExportDialog() },
                onRetry = { ctx.retryReadableExport() },
                onShare = { ctx.shareReadableExport() },
                colors = { ctx.themeColors },
            )
        }
        DshSessionArchiveDialog(
            visible = { ctx.ui.sessionArchiveVisible },
            busy = { ctx.ui.sessionArchiveBusy },
            error = { ctx.ui.sessionArchiveError },
            onCancel = { if (!ctx.ui.sessionArchiveBusy) { ctx.ui.sessionArchiveVisible = false; ctx.ui.sessionArchiveError = "" } },
            onConfirm = { ctx.confirmSessionArchive() },
            pageViewWidth = ctx.pagerData.pageViewWidth,
            colors = { ctx.themeColors },
        )
        DshSessionDeleteDialog(
            visible = { ctx.ui.sessionDeleteVisible },
            busy = { ctx.ui.sessionDeleteBusy },
            error = { ctx.ui.sessionDeleteError },
            onCancel = { if (!ctx.ui.sessionDeleteBusy) { ctx.ui.sessionDeleteVisible = false; ctx.ui.sessionDeleteError = "" } },
            onConfirm = { ctx.confirmSessionDelete() },
            pageViewWidth = ctx.pagerData.pageViewWidth,
            colors = { ctx.themeColors },
        )
    }
}
