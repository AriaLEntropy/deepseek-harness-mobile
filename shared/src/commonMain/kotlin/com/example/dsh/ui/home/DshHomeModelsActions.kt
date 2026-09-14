package com.example.dsh.ui.home

import com.example.dsh.models.DshProviderConfig
import com.example.dsh.models.DshProviderModel
import com.example.dsh.models.dshCreateCustomProvider
import com.example.dsh.models.dshRemoveProviderProfile
import com.example.dsh.models.dshSaveProviderProfile
import com.example.dsh.base.bridgeModule
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.reactive.handler.*
import com.example.dsh.models.dshValidateCustomProvider
import com.example.dsh.models.dshValidateProviderDraft

internal fun DshHomePage.modelsSummary(): String {
    val configured = ui.modelsProviders.count { it.configured }
    return if (configured > 0) "已配置 $configured 个" else ""
}

internal fun DshHomePage.openModelsPage() {
    dismissKeyboard()
    ui.modelsEditingProvider = ""
    ui.modelsSaveError = ""
    ui.modelsDeleteTarget = null
    ui.modelsAdding = false
    ui.modelsPickerVisible = false
    ui.modelsCustomAdding = false
    ui.modelsEditorAdvanced = false
    ui.modelsSavedNotice = ""
    ui.modelsPageVisible = true
    reloadModelsSettings()
}

internal fun DshHomePage.closeModelsPage() {
    ui.modelsPageVisible = false
    ui.modelsEditingProvider = ""
    ui.modelsDraftApiKey = ""
    ui.modelsSaveError = ""
    ui.modelsDeleteTarget = null
    ui.modelsAdding = false
    ui.modelsPickerVisible = false
    ui.modelsCustomAdding = false
    ui.modelsEditorAdvanced = false
    ui.modelsSavedNotice = ""
}

internal fun DshHomePage.reloadModelsSettings(showLoading: Boolean = true) {
    if (showLoading) {
        ui.modelsLoading = true
        ui.modelsError = ""
    }
    val repo = repository
    if (repo == null) {
        ui.modelsLoading = false
        if (showLoading) ui.modelsError = "未连接电脑端"
        return
    }
    repo.loadModelsSettings({
        ui.modelsWritable = it.writable
        ui.modelsProviders.clear()
        ui.modelsProviders.addAll(it.providers)
        ui.modelsConfiguredProviders.clear()
        ui.modelsConfiguredProviders.addAll(it.providers.filter { p -> p.configured })
        ui.modelsAddableProviders.clear()
        ui.modelsAddableProviders.addAll(it.providers.filter { p -> !p.configured && p.settingsNs.isNotEmpty() })
        ui.modelsProtocols = it.protocols
        ui.modelsCustomRevision = it.customRevision
        if (ui.modelsCustomProtocol.isEmpty()) ui.modelsCustomProtocol = it.protocols.firstOrNull() ?: ""
        ui.modelsError = ""
        ui.modelsLoading = false
    }, {
        ui.modelsLoading = false
        if (showLoading) ui.modelsError = it else bridgeModule.toast("模型设置刷新失败：$it")
    })
}

internal fun DshHomePage.openProviderEditor(provider: DshProviderConfig) {
    if (ui.modelsEditingProvider == provider.provider && !ui.modelsAdding) {
        ui.modelsEditingProvider = ""
        return
    }
    ui.modelsAdding = false
    ui.modelsCustomAdding = false
    ui.modelsEditingProvider = provider.provider
    ui.modelsEditorAdvanced = false
    ui.modelsDraftBaseUrl = provider.baseUrl
    ui.modelsDraftApiKey = ""
    ui.modelsSaveError = ""
    ui.modelsDraftModels.clear()
    // 保留 raw，保存时才能带出未在编辑器展示的字段（如容量）。
    ui.modelsDraftModels.addAll(provider.models.map { it.copy() })
}

internal fun DshHomePage.cancelAddProvider() {
    ui.modelsAdding = false
    ui.modelsEditingProvider = ""
    ui.modelsSaveError = ""
}

/** 选择要添加的已有提供方：以空草稿打开其编辑器。 */

internal fun DshHomePage.selectAddableProvider(provider: DshProviderConfig) {
    ui.modelsPickerVisible = false
    ui.modelsCustomAdding = false
    ui.modelsAdding = true
    ui.modelsEditingProvider = provider.provider
    ui.modelsEditorAdvanced = false
    ui.modelsDraftBaseUrl = provider.baseUrl
    ui.modelsDraftApiKey = ""
    ui.modelsSaveError = ""
    ui.modelsSavedNotice = ""
    ui.modelsDraftModels.clear()
    ui.modelsDraftModels.addAll(provider.models.map { it.copy() })
}

internal fun DshHomePage.openCustomProvider() {
    ui.modelsAdding = false
    ui.modelsEditingProvider = ""
    ui.modelsCustomAdding = true
    ui.modelsSavedNotice = ""
    ui.modelsCustomError = ""
    ui.modelsCustomRoute = ""
    ui.modelsCustomName = ""
    ui.modelsCustomBaseUrl = ""
    ui.modelsCustomApiKey = ""
    ui.modelsCustomProtocol = ui.modelsProtocols.firstOrNull() ?: ""
    ui.modelsCustomModels.clear()
    ui.modelsCustomModels.add(DshProviderModel())
}

internal fun DshHomePage.cancelCustomProvider() {
    ui.modelsCustomAdding = false
    ui.modelsCustomError = ""
}

internal fun DshHomePage.updateCustomModel(index: Int, field: String, value: String) {
    if (index !in 0 until ui.modelsCustomModels.size) return
    val current = ui.modelsCustomModels[index]
    ui.modelsCustomModels[index] = when (field) {
        "id" -> current.copy(id = value)
        "name" -> current.copy(name = value)
        else -> current
    }
}

internal fun DshHomePage.applyCustomProvider() {
    if (ui.modelsCustomBusy) return
    val route = ui.modelsCustomRoute.trim()
    val taken = ui.modelsConfiguredProviders.map { it.provider } + ui.modelsAddableProviders.map { it.provider }
    val baseUrl = ui.modelsCustomBaseUrl.trim()
    val validationError = dshValidateCustomProvider(
        route = route,
        baseUrl = baseUrl,
        protocol = ui.modelsCustomProtocol,
        models = ui.modelsCustomModels,
        takenRoutes = taken,
    )
    if (validationError != null) {
        ui.modelsCustomError = validationError
        return
    }
    val repo = repository ?: run { ui.modelsCustomError = "未连接电脑端"; return }
    ui.modelsCustomBusy = true
    ui.modelsCustomError = ""
    dshCreateCustomProvider(
        repo = repo,
        route = route,
        displayName = ui.modelsCustomName,
        baseUrl = baseUrl,
        protocol = ui.modelsCustomProtocol,
        apiKey = ui.modelsCustomApiKey.trim(),
        models = ui.modelsCustomModels.toList(),
        revision = ui.modelsCustomRevision,
        onSuccess = {
            ui.modelsCustomBusy = false
            ui.modelsCustomAdding = false
            ui.modelsSavedNotice = "已保存 ${ui.modelsCustomName.trim().ifEmpty { route }}。"
            reloadModelsSettings(showLoading = false)
            reloadSettings(showLoading = false)
        },
        onError = {
            ui.modelsCustomBusy = false
            ui.modelsCustomError = it
        },
    )
}

internal fun DshHomePage.updateDraftModel(index: Int, field: String, value: String) {
    if (index !in 0 until ui.modelsDraftModels.size) return
    val current = ui.modelsDraftModels[index]
    ui.modelsDraftModels[index] = when (field) {
        "id" -> current.copy(id = value)
        "name" -> current.copy(name = value)
        else -> current
    }
}

internal fun DshHomePage.addDraftModel() {
    ui.modelsDraftModels.add(DshProviderModel())
}

internal fun DshHomePage.removeDraftModel(index: Int) {
    if (index in 0 until ui.modelsDraftModels.size) ui.modelsDraftModels.removeAt(index)
}

internal fun DshHomePage.applyProviderEditor(provider: DshProviderConfig) {
    if (ui.modelsSaving) return
    val draftError = dshValidateProviderDraft(ui.modelsDraftModels)
    if (draftError != null) {
        ui.modelsSaveError = draftError
        return
    }
    val repo = repository
    if (repo == null) {
        ui.modelsSaveError = "未连接电脑端"
        return
    }
    ui.modelsSaving = true
    ui.modelsSaveError = ""
    dshSaveProviderProfile(
        repo = repo,
        provider = provider,
        apiKey = ui.modelsDraftApiKey.trim(),
        baseUrl = ui.modelsDraftBaseUrl,
        models = ui.modelsDraftModels.toList(),
        onSuccess = {
            ui.modelsSaving = false
            ui.modelsEditingProvider = ""
            ui.modelsAdding = false
            ui.modelsDraftApiKey = ""
            ui.modelsSavedNotice = "已保存 ${provider.displayName}。"
            reloadModelsSettings(showLoading = false)
            reloadSettings(showLoading = false)
        },
        onError = {
            ui.modelsSaving = false
            ui.modelsSaveError = it
        },
    )
}

internal fun DshHomePage.requestRemoveProvider(provider: DshProviderConfig) {
    ui.modelsSaveError = ""
    ui.modelsDeleteTarget = provider
}

internal fun DshHomePage.confirmRemoveProvider() {
    val provider = ui.modelsDeleteTarget ?: return
    if (ui.modelsDeleting) return
    val repo = repository
    if (repo == null) {
        bridgeModule.toast("未连接电脑端")
        return
    }
    ui.modelsDeleting = true
    dshRemoveProviderProfile(repo, provider, {
        ui.modelsDeleting = false
        ui.modelsDeleteTarget = null
        if (ui.modelsEditingProvider == provider.provider) {
            ui.modelsEditingProvider = ""
            ui.modelsAdding = false
        }
        reloadModelsSettings(showLoading = false)
    }, {
        ui.modelsDeleting = false
        bridgeModule.toast("删除提供方失败：$it")
    })
}
