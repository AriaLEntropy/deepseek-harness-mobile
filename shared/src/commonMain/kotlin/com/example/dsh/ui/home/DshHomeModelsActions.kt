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
    val configured = modelsProviders.count { it.configured }
    return if (configured > 0) "已配置 $configured 个" else ""
}

internal fun DshHomePage.openModelsPage() {
    dismissKeyboard()
    modelsEditingProvider = ""
    modelsSaveError = ""
    modelsDeleteTarget = null
    modelsAdding = false
    modelsPickerVisible = false
    modelsCustomAdding = false
    modelsEditorAdvanced = false
    modelsSavedNotice = ""
    modelsPageVisible = true
    reloadModelsSettings()
}

internal fun DshHomePage.closeModelsPage() {
    modelsPageVisible = false
    modelsEditingProvider = ""
    modelsDraftApiKey = ""
    modelsSaveError = ""
    modelsDeleteTarget = null
    modelsAdding = false
    modelsPickerVisible = false
    modelsCustomAdding = false
    modelsEditorAdvanced = false
    modelsSavedNotice = ""
}

internal fun DshHomePage.reloadModelsSettings(showLoading: Boolean = true) {
    if (showLoading) {
        modelsLoading = true
        modelsError = ""
    }
    val repo = repository
    if (repo == null) {
        modelsLoading = false
        if (showLoading) modelsError = "未连接电脑端"
        return
    }
    repo.loadModelsSettings({
        modelsWritable = it.writable
        modelsProviders.clear()
        modelsProviders.addAll(it.providers)
        modelsConfiguredProviders.clear()
        modelsConfiguredProviders.addAll(it.providers.filter { p -> p.configured })
        modelsAddableProviders.clear()
        modelsAddableProviders.addAll(it.providers.filter { p -> !p.configured && p.settingsNs.isNotEmpty() })
        modelsProtocols = it.protocols
        modelsCustomRevision = it.customRevision
        if (modelsCustomProtocol.isEmpty()) modelsCustomProtocol = it.protocols.firstOrNull() ?: ""
        modelsError = ""
        modelsLoading = false
    }, {
        modelsLoading = false
        if (showLoading) modelsError = it else bridgeModule.toast("模型设置刷新失败：$it")
    })
}

internal fun DshHomePage.openProviderEditor(provider: DshProviderConfig) {
    if (modelsEditingProvider == provider.provider && !modelsAdding) {
        modelsEditingProvider = ""
        return
    }
    modelsAdding = false
    modelsCustomAdding = false
    modelsEditingProvider = provider.provider
    modelsEditorAdvanced = false
    modelsDraftBaseUrl = provider.baseUrl
    modelsDraftApiKey = ""
    modelsSaveError = ""
    modelsDraftModels.clear()
    // 保留 raw，保存时才能带出未在编辑器展示的字段（如容量）。
    modelsDraftModels.addAll(provider.models.map { it.copy() })
}

internal fun DshHomePage.cancelAddProvider() {
    modelsAdding = false
    modelsEditingProvider = ""
    modelsSaveError = ""
}

/** 选择要添加的已有提供方：以空草稿打开其编辑器。 */

internal fun DshHomePage.selectAddableProvider(provider: DshProviderConfig) {
    modelsPickerVisible = false
    modelsCustomAdding = false
    modelsAdding = true
    modelsEditingProvider = provider.provider
    modelsEditorAdvanced = false
    modelsDraftBaseUrl = provider.baseUrl
    modelsDraftApiKey = ""
    modelsSaveError = ""
    modelsSavedNotice = ""
    modelsDraftModels.clear()
    modelsDraftModels.addAll(provider.models.map { it.copy() })
}

internal fun DshHomePage.openCustomProvider() {
    modelsAdding = false
    modelsEditingProvider = ""
    modelsCustomAdding = true
    modelsSavedNotice = ""
    modelsCustomError = ""
    modelsCustomRoute = ""
    modelsCustomName = ""
    modelsCustomBaseUrl = ""
    modelsCustomApiKey = ""
    modelsCustomProtocol = modelsProtocols.firstOrNull() ?: ""
    modelsCustomModels.clear()
    modelsCustomModels.add(DshProviderModel())
}

internal fun DshHomePage.cancelCustomProvider() {
    modelsCustomAdding = false
    modelsCustomError = ""
}

internal fun DshHomePage.updateCustomModel(index: Int, field: String, value: String) {
    if (index !in 0 until modelsCustomModels.size) return
    val current = modelsCustomModels[index]
    modelsCustomModels[index] = when (field) {
        "id" -> current.copy(id = value)
        "name" -> current.copy(name = value)
        else -> current
    }
}

internal fun DshHomePage.applyCustomProvider() {
    if (modelsCustomBusy) return
    val route = modelsCustomRoute.trim()
    val taken = modelsConfiguredProviders.map { it.provider } + modelsAddableProviders.map { it.provider }
    val baseUrl = modelsCustomBaseUrl.trim()
    val validationError = dshValidateCustomProvider(
        route = route,
        baseUrl = baseUrl,
        protocol = modelsCustomProtocol,
        models = modelsCustomModels,
        takenRoutes = taken,
    )
    if (validationError != null) {
        modelsCustomError = validationError
        return
    }
    val repo = repository ?: run { modelsCustomError = "未连接电脑端"; return }
    modelsCustomBusy = true
    modelsCustomError = ""
    dshCreateCustomProvider(
        repo = repo,
        route = route,
        displayName = modelsCustomName,
        baseUrl = baseUrl,
        protocol = modelsCustomProtocol,
        apiKey = modelsCustomApiKey.trim(),
        models = modelsCustomModels.toList(),
        revision = modelsCustomRevision,
        onSuccess = {
            modelsCustomBusy = false
            modelsCustomAdding = false
            modelsSavedNotice = "已保存 ${modelsCustomName.trim().ifEmpty { route }}。"
            reloadModelsSettings(showLoading = false)
            reloadSettings(showLoading = false)
        },
        onError = {
            modelsCustomBusy = false
            modelsCustomError = it
        },
    )
}

internal fun DshHomePage.updateDraftModel(index: Int, field: String, value: String) {
    if (index !in 0 until modelsDraftModels.size) return
    val current = modelsDraftModels[index]
    modelsDraftModels[index] = when (field) {
        "id" -> current.copy(id = value)
        "name" -> current.copy(name = value)
        else -> current
    }
}

internal fun DshHomePage.addDraftModel() {
    modelsDraftModels.add(DshProviderModel())
}

internal fun DshHomePage.removeDraftModel(index: Int) {
    if (index in 0 until modelsDraftModels.size) modelsDraftModels.removeAt(index)
}

internal fun DshHomePage.applyProviderEditor(provider: DshProviderConfig) {
    if (modelsSaving) return
    val draftError = dshValidateProviderDraft(modelsDraftModels)
    if (draftError != null) {
        modelsSaveError = draftError
        return
    }
    val repo = repository
    if (repo == null) {
        modelsSaveError = "未连接电脑端"
        return
    }
    modelsSaving = true
    modelsSaveError = ""
    dshSaveProviderProfile(
        repo = repo,
        provider = provider,
        apiKey = modelsDraftApiKey.trim(),
        baseUrl = modelsDraftBaseUrl,
        models = modelsDraftModels.toList(),
        onSuccess = {
            modelsSaving = false
            modelsEditingProvider = ""
            modelsAdding = false
            modelsDraftApiKey = ""
            modelsSavedNotice = "已保存 ${provider.displayName}。"
            reloadModelsSettings(showLoading = false)
            reloadSettings(showLoading = false)
        },
        onError = {
            modelsSaving = false
            modelsSaveError = it
        },
    )
}

internal fun DshHomePage.requestRemoveProvider(provider: DshProviderConfig) {
    modelsSaveError = ""
    modelsDeleteTarget = provider
}

internal fun DshHomePage.confirmRemoveProvider() {
    val provider = modelsDeleteTarget ?: return
    if (modelsDeleting) return
    val repo = repository
    if (repo == null) {
        bridgeModule.toast("未连接电脑端")
        return
    }
    modelsDeleting = true
    dshRemoveProviderProfile(repo, provider, {
        modelsDeleting = false
        modelsDeleteTarget = null
        if (modelsEditingProvider == provider.provider) {
            modelsEditingProvider = ""
            modelsAdding = false
        }
        reloadModelsSettings(showLoading = false)
    }, {
        modelsDeleting = false
        bridgeModule.toast("删除提供方失败：$it")
    })
}
