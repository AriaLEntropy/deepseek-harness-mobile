package com.example.dsh.conversation

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * 设置页「模型」板块（对齐电脑端 DSH settings.models）：
 * llm.providers（可配置提供方目录）× settings.describe（脱敏后的分层配置）
 * × credentials.describe（密钥状态）三面联接后的移动端视图。
 */
internal data class DshProviderCredential(
    val configured: Boolean = false,
    val writable: Boolean = false,
    val source: String = "",
)

/** 一个提供方 profile 下的模型目录条目；raw 保留原有字段以便保存时不丢数据。 */
internal data class DshProviderModel(
    val id: String = "",
    val name: String = "",
    val raw: JSONObject? = null,
)

internal data class DshProviderConfig(
    val provider: String,
    val displayName: String,
    val settingsNs: String,
    val settingsPath: List<String>,
    val active: Boolean,
    val configured: Boolean,
    val removable: Boolean,
    val apiKeyEnv: String,
    val credential: DshProviderCredential?,
    val baseUrl: String,
    val models: List<DshProviderModel>,
    val modelsOverridden: Boolean,
    val revision: Int,
)

internal data class DshModelsSettings(
    val writable: Boolean = false,
    val providers: List<DshProviderConfig> = emptyList(),
)

/** 移动端按 <ROUTE>_API_KEY 派生凭据名，与电脑端 deriveKeyRef 一致。 */
internal fun dshDeriveKeyRef(provider: String): String {
    val stem = buildString {
        provider.forEach { ch ->
            when {
                ch in 'a'..'z' -> append(ch.uppercaseChar())
                ch in 'A'..'Z' || ch in '0'..'9' -> append(ch)
                else -> append('_')
            }
        }
    }
    return stem + "_API_KEY"
}

private fun dshJsonAtPath(root: Any?, path: List<String>): Any? {
    var current: Any? = root
    for (segment in path) {
        val obj = current as? JSONObject ?: return null
        current = obj.opt(segment)
    }
    return current
}

private fun dshJsonObjectAtPath(root: Any?, path: List<String>): JSONObject? =
    dshJsonAtPath(root, path) as? JSONObject

private fun dshJsonStringList(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    val result = mutableListOf<String>()
    for (index in 0 until array.length()) {
        array.optString(index)?.takeIf { it.isNotEmpty() }?.let { result += it }
    }
    return result
}

private fun dshParseProviderModels(array: JSONArray?): List<DshProviderModel> {
    if (array == null) return emptyList()
    val result = mutableListOf<DshProviderModel>()
    for (index in 0 until array.length()) {
        val entry = array.optJSONObject(index) ?: continue
        result += DshProviderModel(
            id = entry.optString("id"),
            name = entry.optString("name"),
            raw = entry,
        )
    }
    return result
}

private fun dshNamespaceMap(settingsValue: JSONObject): Map<String, JSONObject> {
    val result = mutableMapOf<String, JSONObject>()
    val namespaces = settingsValue.optJSONArray("namespaces") ?: JSONArray()
    for (index in 0 until namespaces.length()) {
        val namespace = namespaces.optJSONObject(index) ?: continue
        val ns = namespace.optString("ns")
        if (ns.isNotEmpty()) result[ns] = namespace
    }
    return result
}

internal fun dshParseModelsSettings(
    providersValue: JSONObject,
    settingsValue: JSONObject,
    credentialsValue: JSONObject,
): DshModelsSettings {
    val namespaces = dshNamespaceMap(settingsValue)
    val credentials = credentialsValue.optJSONObject("credentials") ?: JSONObject()
    val providers = providersValue.optJSONArray("providers") ?: JSONArray()
    val rows = mutableListOf<DshProviderConfig>()
    for (index in 0 until providers.length()) {
        val provider = providers.optJSONObject(index) ?: continue
        val providerId = provider.optString("provider")
        val settingsNs = provider.optString("settingsNs")
        // 没有 settings 地址的路由无处配置，与电脑端一致地不渲染。
        if (providerId.isEmpty() || settingsNs.isEmpty()) continue
        val path = dshJsonStringList(provider.optJSONArray("settingsPath"))
        val namespace = namespaces[settingsNs]
        val profile = dshJsonObjectAtPath(namespace?.opt("value"), path)
        val apiKeyEnv = profile?.optString("apiKeyEnv").orEmpty()
        val credential = if (apiKeyEnv.isEmpty()) {
            null
        } else {
            credentials.optJSONObject(apiKeyEnv)?.let {
                DshProviderCredential(
                    configured = it.optBoolean("configured"),
                    writable = it.optBoolean("writable"),
                    source = it.optString("source"),
                )
            }
        }
        val userAtPath = dshJsonAtPath(namespace?.opt("user"), path)
        val baseAtPath = dshJsonAtPath(namespace?.opt("base"), path)
        rows += DshProviderConfig(
            provider = providerId,
            displayName = provider.optString("displayName").ifEmpty { providerId },
            settingsNs = settingsNs,
            settingsPath = path,
            active = provider.optBoolean("active"),
            configured = profile != null,
            removable = userAtPath is JSONObject && baseAtPath == null,
            apiKeyEnv = apiKeyEnv,
            credential = credential,
            baseUrl = profile?.optString("baseURL").orEmpty(),
            models = dshParseProviderModels(profile?.optJSONArray("models")),
            modelsOverridden = dshJsonAtPath(namespace?.opt("user"), path + "models") != null,
            revision = namespace?.optInt("revision") ?: 0,
        )
    }
    return DshModelsSettings(
        writable = settingsValue.optBoolean("writable"),
        providers = rows,
    )
}

private fun dshCollectCredentialRefs(providersValue: JSONObject, settingsValue: JSONObject): JSONArray {
    val namespaces = dshNamespaceMap(settingsValue)
    val refs = linkedSetOf<String>()
    val providers = providersValue.optJSONArray("providers") ?: JSONArray()
    for (index in 0 until providers.length()) {
        val provider = providers.optJSONObject(index) ?: continue
        val settingsNs = provider.optString("settingsNs")
        val namespace = namespaces[settingsNs] ?: continue
        val path = dshJsonStringList(provider.optJSONArray("settingsPath"))
        val profile = dshJsonObjectAtPath(namespace.opt("value"), path) ?: continue
        profile.optString("apiKeyEnv").takeIf { it.isNotEmpty() }?.let { refs += it }
    }
    return JSONArray().apply { refs.forEach { put(it) } }
}

/**
 * 依次读取目录、设置与凭据并联接。凭据读取失败不阻塞目录展示。
 * call 由各 repository 适配为自身的 RPC 通道。
 */
internal fun dshLoadModelsSettings(
    call: (String, JSONObject, (JSONObject?, String?) -> Unit) -> Unit,
    onSuccess: (DshModelsSettings) -> Unit,
    onError: (String) -> Unit,
) {
    call(DshHostProtocol.LLM_PROVIDERS, JSONObject()) { providers, providersError ->
        if (providersError != null || providers == null) {
            onError(providersError ?: "llm.providers 返回为空")
            return@call
        }
        call(DshHostProtocol.SETTINGS_DESCRIBE, JSONObject()) { settings, settingsError ->
            if (settingsError != null || settings == null) {
                onError(settingsError ?: "settings.describe 返回为空")
                return@call
            }
            val emptyCredentials = JSONObject()
            call(DshHostProtocol.CREDENTIALS_DESCRIBE, JSONObject().apply {
                put("refs", dshCollectCredentialRefs(providers, settings))
            }) { credentials, credentialsError ->
                val available = if (credentialsError == null && credentials != null) credentials else emptyCredentials
                onSuccess(dshParseModelsSettings(providers, settings, available))
            }
        }
    }
}

private fun dshSetOp(path: List<String>, value: Any): JSONObject = JSONObject().apply {
    put("op", "set")
    put("path", JSONArray().apply { path.forEach { put(it) } })
    put("value", value)
}

private fun dshUnsetOp(path: List<String>): JSONObject = JSONObject().apply {
    put("op", "unset")
    put("path", JSONArray().apply { path.forEach { put(it) } })
}

private fun dshModelsToJson(models: List<DshProviderModel>): JSONArray = JSONArray().apply {
    models.forEach { model ->
        val obj = JSONObject()
        val raw = model.raw
        if (raw != null) {
            val keys = raw.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == "id" || key == "name") continue
                raw.opt(key)?.let { obj.put(key, it) }
            }
        }
        obj.put("id", model.id.trim())
        if (model.name.isNotEmpty()) obj.put("name", model.name)
        put(obj)
    }
}

/**
 * 保存一个提供方的配置：先合并 settings 路径 op，再写入密钥。
 * 空 apiKey 表示保持已存密钥；baseURL 清空写入 unset。
 */
internal fun dshSaveProviderProfile(
    repo: DshRepository,
    provider: DshProviderConfig,
    apiKey: String,
    baseUrl: String,
    models: List<DshProviderModel>,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    val ops = JSONArray()
    val nextBaseUrl = baseUrl.trim()
    if (nextBaseUrl != provider.baseUrl.trim()) {
        if (nextBaseUrl.isEmpty()) {
            ops.put(dshUnsetOp(provider.settingsPath + "baseURL"))
        } else {
            ops.put(dshSetOp(provider.settingsPath + "baseURL", nextBaseUrl))
        }
    }
    val originalPairs = provider.models.map { it.id.trim() to it.name }
    val nextPairs = models.map { it.id.trim() to it.name }
    if (nextPairs != originalPairs) {
        ops.put(dshSetOp(provider.settingsPath + "models", dshModelsToJson(models)))
    }
    val ref = provider.apiKeyEnv.ifEmpty { dshDeriveKeyRef(provider.provider) }
    if (apiKey.isNotEmpty() && provider.apiKeyEnv.isEmpty()) {
        ops.put(dshSetOp(provider.settingsPath + "apiKeyEnv", ref))
    }
    fun writeCredential() {
        if (apiKey.isEmpty()) {
            onSuccess()
        } else {
            repo.setCredential(ref, apiKey, onSuccess, onError)
        }
    }
    if (ops.length() == 0) {
        writeCredential()
    } else {
        repo.mutateSetting(provider.settingsNs, ops, provider.revision, { writeCredential() }, onError)
    }
}

/** 删除用户层承载的提供方 profile；命中页面派生的凭据时一并清除。 */
internal fun dshRemoveProviderProfile(
    repo: DshRepository,
    provider: DshProviderConfig,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    val derived = dshDeriveKeyRef(provider.provider)
    val credential = provider.credential
    val shouldClearCredential = provider.apiKeyEnv == derived &&
        credential?.configured == true && credential.writable
    fun removeProfile() {
        repo.mutateSetting(
            provider.settingsNs,
            JSONArray().apply { put(dshUnsetOp(provider.settingsPath)) },
            provider.revision,
            onSuccess,
            onError,
        )
    }
    if (shouldClearCredential) {
        repo.unsetCredential(derived, { removeProfile() }, onError)
    } else {
        removeProfile()
    }
}
