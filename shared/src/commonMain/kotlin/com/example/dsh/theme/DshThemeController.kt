package com.example.dsh.theme

import com.tencent.kuikly.core.reactive.handler.observable

/**
 * 主题控制器：管理当前主题包、主题模式、系统暗色状态，解析当前生效的语义化颜色。
 *
 * 对标 dsh 原版：
 * - preference = light/dark/system（持久化，默认 system）
 * - system 模式下读取系统 prefers-color-scheme
 * - 解析结果对应 body 是否带 data-ds-dark-theme 属性
 *
 * 组件通过 colors: () -> DshColorTokens 参数引用当前颜色，
 * mode / systemDark 变化时自动触发重新渲染。
 */
class DshThemeController {
    /** 当前主题包，可替换为社区主题实现 */
    var theme: DshTheme = DshDefaultTheme

    /** 用户选择的主题模式 */
    var mode by observable(DshThemeMode.SYSTEM)

    /** 系统是否处于暗色模式（由 BasePager.isNightMode() 注入） */
    var systemDark by observable(false)

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

    fun cycleMode() {
        mode = when (mode) {
            DshThemeMode.LIGHT -> DshThemeMode.DARK
            DshThemeMode.DARK -> DshThemeMode.SYSTEM
            DshThemeMode.SYSTEM -> DshThemeMode.LIGHT
        }
    }
}
