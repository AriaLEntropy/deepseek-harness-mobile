package com.example.dsh.models

fun selectedReasoningEffortName(option: DshModelOption): String =
    option.reasoningEfforts.firstOrNull { it.id == option.reasoningEffort }?.name
        ?: option.reasoningEffort
        ?: ""

/** 自定义 Provider 表单校验；通过返回 null，否则返回首条可读错误。 */
fun dshValidateCustomProvider(
    route: String,
    baseUrl: String,
    protocol: String,
    models: List<DshProviderModel>,
    takenRoutes: List<String>,
): String? {
    val routeError = dshCustomRouteError(route, takenRoutes)
    if (route.isEmpty() || routeError.isNotEmpty()) return routeError.ifEmpty { "请填写 Provider ID。" }
    if (baseUrl.isEmpty()) return "自定义提供方需要填写 API 地址。"
    if (protocol.isEmpty()) return "请选择 API 协议。"
    if (models.isEmpty() || models.any { it.id.trim().isEmpty() }) return "自定义提供方至少需要一个模型，且模型 ID 不能为空。"
    val ids = models.map { it.id.trim() }
    if (ids.size != ids.toSet().size) return "模型 ID 不能重复。"
    return null
}

/** Provider 编辑草稿校验；通过返回 null，否则返回首条可读错误。 */
fun dshValidateProviderDraft(models: List<DshProviderModel>): String? {
    if (models.any { it.id.trim().isEmpty() }) return "模型 ID 不能为空。"
    val ids = models.map { it.id.trim() }
    if (ids.size != ids.toSet().size) return "模型 ID 不能重复。"
    return null
}
