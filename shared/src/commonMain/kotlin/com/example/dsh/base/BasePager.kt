package com.example.dsh.base

import com.example.dsh.connection.DshEngineModule
import com.example.dsh.connection.DshRelayModule
import com.example.dsh.connection.DshSseModule
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshThemeManager
import com.example.dsh.log.cachedLocalTimezoneOffsetMillis
import com.example.dsh.theme.DshThemeMode
import com.tencent.kuikly.core.pager.Pager
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.*
import com.example.dsh.host.DshWebSocketModule
import com.tencent.kuikly.core.timer.setTimeout

internal abstract class BasePager : Pager() {
    private var nightModel: Boolean? by observable(null)

    /** 页面级主题色镜像：DshThemeManager.currentColors 的响应式副本，attr 内读取可注册依赖 */
    protected var themeColors by observable<DshColorTokens>(DshThemeManager.currentColors)
    /** 页面级主题模式镜像：随全局广播同步，设置页外观行等文字可响应式显示 */
    protected var themeMode by observable<DshThemeMode>(DshThemeManager.mode)
    /** 日出日落模式下下一次切换的定时器引用；非空表示已排程。 */
    private var sunriseSunsetTimer: String? = null

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
        DshThemeManager.systemDark = isNightMode()
        // JS 运行时（QuickJS）无系统时区数据，从原生侧同步查询一次并缓存，供日志时间格式化使用
        runCatching {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME).timezoneOffsetMillis()
        }.getOrNull()?.let { cachedLocalTimezoneOffsetMillis = it }
        // 恢复移动端本地持久化的外观偏好（无记录则保持跟随系统）；host 的 ui-theme 偏好不再自动覆盖移动端
        val saved = runCatching {
            acquireModule<SharedPreferencesModule>(SharedPreferencesModule.MODULE_NAME).getItem(DshThemeManager.PREF_KEY_THEME_MODE)
        }.getOrNull().orEmpty()
        if (saved.isNotEmpty()) DshThemeManager.applyPreference(saved)
        DshThemeManager.addListener(::syncThemeColors)
        syncThemeColors()
    }

    override fun themeDidChanged(data: JSONObject) {
        super.themeDidChanged(data)
        nightModel = data.optBoolean(IS_NIGHT_MODE_KEY)
        DshThemeManager.systemDark = isNightMode()
        DshThemeManager.notifyChanged()
    }

    /** 把全局主题色写入页面级 observable（值未变则跳过，避免无谓重绘）。 */
    protected fun syncThemeColors() {
        val next = DshThemeManager.currentColors
        if (themeColors !== next) themeColors = next
        if (themeMode !== DshThemeManager.mode) themeMode = DshThemeManager.mode
        ensureSunriseSunsetTimer()
    }

    /**
     * 日出日落主题：在下一个切换时刻（06:00 / 18:00）重算主题并继续排程。
     * 仅该模式生效；切换到其他模式后，已在途的定时器到点自行退出，不会触发重绘。
     */
    private fun ensureSunriseSunsetTimer() {
        if (DshThemeManager.mode != DshThemeMode.SUNRISE_SUNSET) return
        if (sunriseSunsetTimer != null) return
        val delay = DshThemeManager.millisUntilNextSwitch()
            .coerceIn(SUNRISE_SUNSET_MIN_DELAY_MS, SUNRISE_SUNSET_MAX_DELAY_MS)
        sunriseSunsetTimer = setTimeout(pagerId, delay.toInt()) {
            sunriseSunsetTimer = null
            if (DshThemeManager.mode != DshThemeMode.SUNRISE_SUNSET) return@setTimeout
            DshThemeManager.notifyChanged()
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
        private const val SUNRISE_SUNSET_MIN_DELAY_MS = 1_000L
        private const val SUNRISE_SUNSET_MAX_DELAY_MS = 12L * 60L * 60L * 1000L
    }

}
