package com.example.dsh.base

import com.example.dsh.connection.*
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme
import com.example.dsh.theme.DshThemeManager
import com.example.dsh.theme.DshThemeMode
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.*

internal abstract class BasePager : Pager() {
    private var nightModel: Boolean? by observable(null)

    /** 页面级主题色镜像：DshThemeManager.currentColors 的响应式副本，attr 内读取可注册依赖 */
    protected var themeColors by observable<DshColorTokens>(DshDefaultTheme.light)
    /** 页面级主题模式镜像：随全局广播同步，设置页外观行等文字可响应式显示 */
    protected var themeMode by observable<DshThemeMode>(DshThemeMode.LIGHT)

    override fun createExternalModules(): Map<String, Module>? {
        val externalModules = hashMapOf<String, Module>()
        externalModules[BridgeModule.MODULE_NAME] = BridgeModule()
        externalModules[DshBlurModule.MODULE_NAME] = DshBlurModule()
        externalModules[DshEngineModule.MODULE_NAME] = DshEngineModule()
        externalModules[DshRelayModule.MODULE_NAME] = DshRelayModule()
        externalModules[DshSseModule.MODULE_NAME] = DshSseModule()
        externalModules[DshWebSocketModule.MODULE_NAME] = DshWebSocketModule()
        return externalModules
    }

    override fun created() {
        super.created()
        // 恢复移动端本地持久化的外观偏好（无记录则保持跟随系统）；host 的 ui-theme 偏好不再自动覆盖移动端
        val saved = runCatching {
            acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME).getItem(DshThemeManager.PREF_KEY_THEME_MODE)
        }.getOrNull().orEmpty()
        if (saved.isNotEmpty()) DshThemeManager.applyPreference(saved)
        DshThemeManager.systemDark = isNightMode()
        DshThemeManager.addListener(::syncThemeColors)
        syncThemeColors()
    }

    override fun themeDidChanged(data: JSONObject) {
        super.themeDidChanged(data)
        nightModel = data.optBoolean(IS_NIGHT_MODE_KEY)
        DshThemeManager.systemDark = isNightMode()
        DshThemeManager.notifyChanged()
    }

    /** 把全局主题色写入页面级 observable（值未变则跳过，避免无谓重绘） */
    protected fun syncThemeColors() {
        setTimeout(0) {
            val next = DshThemeManager.currentColors
            if (themeColors !== next) themeColors = next
            if (themeMode !== DshThemeManager.mode) themeMode = DshThemeManager.mode
        }
    }

    // 是否为夜间模式
    override fun isNightMode(): Boolean {
        if (nightModel == null) {
            nightModel = pageData.params.optBoolean(IS_NIGHT_MODE_KEY)
        }
        return nightModel!!
    }

    override fun pageWillDestroy() {
        DshThemeManager.removeListener(::syncThemeColors)
        super.pageWillDestroy()
    }

    // 不开启调试UI模式
    override fun debugUIInspector(): Boolean {
        return false
    }

    companion object {
        const val IS_NIGHT_MODE_KEY = "isNightMode"
    }

}
