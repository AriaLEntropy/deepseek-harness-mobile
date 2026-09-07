package com.example.dsh.theme

/**
 * 全局主题管理器（App 级）：所有页面共享同一份主题状态与语义色解析。
 *
 * 生命周期与 App 一致，由 BasePager 接线：
 * - 页面 created 时通过 [addListener] 注册同步回调（把 currentColors 写入页面级 observable），
 *   系统外观变化（themeDidChanged）时通过 [notifyChanged] 广播，所有页面响应式重绘。
 * - 首次打开 App 默认浅色（LIGHT）；跟随系统为可选模式，由设置页切换并本地持久化。
 *
 * 对标 dsh 原版 body 的 data-ds-dark-theme 属性：mode 解析结果即当前生效主题包。
 */
object DshThemeManager {
    /** 移动端本地持久化的外观偏好 key（SharedPreferences） */
    const val PREF_KEY_THEME_MODE = "theme_mode"
    /** 当前主题包，可替换为社区主题实现 */
    var theme: DshTheme = DshDefaultTheme

    /** 用户主题模式：默认浅色（跟随系统为可选模式） */
    var mode: DshThemeMode = DshThemeMode.LIGHT

    /** 系统是否处于暗色模式（由 BasePager.isNightMode() 注入，themeDidChanged 时更新） */
    var systemDark: Boolean = false

    /** host 持久化偏好值（light / dark / system），与设置 UI 一致 */
    val preferenceValue: String
        get() = when (mode) {
            DshThemeMode.LIGHT -> "light"
            DshThemeMode.DARK -> "dark"
            DshThemeMode.SYSTEM -> "system"
        }

    /** 当前解析出的语义化颜色集 */
    val currentColors: DshColorTokens
        get() = when (mode) {
            DshThemeMode.LIGHT -> theme.light
            DshThemeMode.DARK -> theme.dark
            DshThemeMode.SYSTEM -> if (systemDark) theme.dark else theme.light
        }

    /** 当前是否为暗色（用于需要特殊处理的组件，如 Markdown 代码高亮） */
    val isDark: Boolean
        get() = currentColors === theme.dark

    private val listeners = mutableListOf<() -> Unit>()

    /** 页面注册主题同步回调（页面销毁时必须注销） */
    fun addListener(listener: () -> Unit) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    /** 页面注销主题同步回调 */
    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    /** 广播主题变化：所有已注册页面把 currentColors 写入自己的 observable 触发重绘 */
    fun notifyChanged() {
        listeners.toList().forEach { it() }
    }

    /** 按 host 持久化的外观偏好应用主题模式（light / dark / 其他→跟随系统），并广播 */
    fun applyPreference(themeValue: String) {
        mode = when (themeValue) {
            "light" -> DshThemeMode.LIGHT
            "dark" -> DshThemeMode.DARK
            else -> DshThemeMode.SYSTEM
        }
        notifyChanged()
    }
}
