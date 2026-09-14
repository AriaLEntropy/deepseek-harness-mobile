package com.example.dsh.ui.home

import com.example.dsh.models.DshModelOption
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.example.dsh.models.selectedReasoningEffortName

internal fun DshHomePage.removeCustomModel(index: Int) {
    if (index in 0 until ui.modelsCustomModels.size) ui.modelsCustomModels.removeAt(index)
}

internal fun DshHomePage.composerFolderLabel(): String {
    val session = ui.sessions.firstOrNull { it.id == ui.activeSessionId }
    val cwd = session?.cwd
    if (cwd.isNullOrEmpty()) return "文件夹（可选）"
    // 只显示绝对路径最后一段（兼容 Windows 反斜杠与 Unix 斜杠，过滤空段），
    // 完整路径仍在右侧「会话详情面板」展示，不丢失信息。
    val leaf = cwd.split("\\", "/").lastOrNull { it.isNotBlank() } ?: return cwd
    return leaf
}

internal fun DshHomePage.archiveActiveSession() {
    val repository = remoteRepo ?: return
    repository.archiveSession(ui.activeSessionId) { _, _ ->
        postToUi {
            loadRepository(preferredSessionId = null)
            refreshWorkspaceGroups()
        }
    }
}

/** 会话列表按消息时间（updatedAt）从新到旧重排，供加载完成与增量插入后统一调用。 */

internal fun DshHomePage.loadModels(sessionId: String) {
    val hostRepository = repository ?: return
    val version = ++modelRequestVersion
    hostRepository.loadModels(sessionId, { loaded ->
        if (!pageAlive || repository !== hostRepository || ui.activeSessionId != sessionId || version != modelRequestVersion) return@loadModels
        ui.selectedModelLabel = loaded.current.name
        ui.selectedEffortLabel = selectedReasoningEffortName(loaded.current)
        ui.modelOptions = ObservableList(loaded.options.toMutableList())
        ui.modelPickerBusy = false
        ui.modelPickerError = if (loaded.routable) "" else "当前模型不可用，请选择其他模型。"
    }, { error ->
        if (!pageAlive || repository !== hostRepository || ui.activeSessionId != sessionId || version != modelRequestVersion) return@loadModels
        ui.modelPickerBusy = false
        ui.modelPickerError = error
    })
}

internal fun DshHomePage.openModelPicker() {
    if (ui.sessions.isEmpty()) return
    dismissKeyboard()
    ui.commandSheetVisible = false
    ui.modelEffortsVisible = false
    ui.modelPickerVisible = true
    ui.modelPickerBusy = true
    ui.modelPickerError = ""
    loadModels(ui.activeSessionId)
}

internal fun DshHomePage.selectModel(option: DshModelOption) {
    val hostRepository = repository ?: return
    if (ui.modelPickerBusy) return
    val sessionId = ui.activeSessionId
    val version = ++modelRequestVersion
    ui.modelPickerBusy = true
    ui.modelPickerError = ""
    hostRepository.selectModel(sessionId, option, { selected ->
        if (!pageAlive || repository !== hostRepository || ui.activeSessionId != sessionId || version != modelRequestVersion) return@selectModel
        ui.selectedModelLabel = selected.name
        ui.selectedEffortLabel = selectedReasoningEffortName(selected)
        ui.modelPickerBusy = false
        ui.modelPickerVisible = false
        ui.modelOptions = ObservableList(ui.modelOptions.map {
            if (it.provider == selected.provider && it.model == selected.model) {
                it.copy(selected = true, reasoningEffort = selected.reasoningEffort)
            } else {
                it.copy(selected = false)
            }
        }.toMutableList())
    }, { error ->
        if (!pageAlive || repository !== hostRepository || ui.activeSessionId != sessionId || version != modelRequestVersion) return@selectModel
        ui.modelPickerBusy = false
        ui.modelPickerError = error
    })
}

// 提交推理等级变更到 host：复用 selectModel 更新 effort。

internal fun DshHomePage.selectModelEffort(effortId: String) {
    val hostRepository = repository ?: return
    if (ui.modelPickerBusy) return
    val sessionId = ui.activeSessionId
    val version = ++modelRequestVersion
    val current = ui.modelOptions.firstOrNull { it.selected } ?: return
    ui.modelPickerBusy = true
    ui.modelPickerError = ""
    hostRepository.selectModel(sessionId, current.copy(reasoningEffort = effortId), { selected ->
        if (!pageAlive || repository !== hostRepository || ui.activeSessionId != sessionId || version != modelRequestVersion) return@selectModel
        ui.selectedModelLabel = selected.name
        ui.selectedEffortLabel = selectedReasoningEffortName(selected)
        ui.modelPickerBusy = false
        ui.modelOptions = ObservableList(ui.modelOptions.map {
            if (it.provider == selected.provider && it.model == selected.model) {
                it.copy(selected = true, reasoningEffort = selected.reasoningEffort)
            } else {
                it.copy(selected = false)
            }
        }.toMutableList())
    }, { error ->
        if (!pageAlive || repository !== hostRepository || ui.activeSessionId != sessionId || version != modelRequestVersion) return@selectModel
        ui.modelPickerBusy = false
        ui.modelPickerError = error
    })
}

/** 切换文字 / 语音输入模式（右下角语音↔键盘按钮）。 */
