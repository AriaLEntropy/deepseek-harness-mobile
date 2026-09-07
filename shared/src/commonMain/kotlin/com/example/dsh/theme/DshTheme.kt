package com.example.dsh.theme

import com.tencent.kuikly.core.base.Color

/**
 * 主题模式：亮色 / 暗色 / 跟随系统
 * 对标 dsh 原版 THEME_PREFERENCES = ['light', 'dark', 'system']
 */
enum class DshThemeMode {
    LIGHT, DARK, SYSTEM;

    companion object {
        fun from(name: String): DshThemeMode = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: SYSTEM
    }
}

/**
 * 静态色板（对应原版 --dsw-static-*）
 * 亮色和暗色完全相同，是原始色值，不直接被组件引用。
 * 社区主题可替换此层来改变整体色相。
 */
interface DshStaticPalette {
    // Amber
    val amber100: Color
    val amber400: Color
    val amber500: Color
    val amber600: Color
    val amber900: Color
    // Blue
    val blue50: Color
    val blue75: Color
    val blue100: Color
    val blue300: Color
    val blue400: Color
    val blue450: Color
    val blue500: Color
    val blue600: Color
    val blue800: Color
    val blue900: Color
    val blue950: Color
    // DeepSeek brand
    val deepseek50: Color
    val deepseek100: Color
    val deepseek200: Color
    val deepseek300: Color
    val deepseek400: Color
    val deepseek450: Color
    val deepseek500: Color
    val deepseek600: Color
    val deepseek800: Color
    val deepseek900: Color
    // Green
    val green100: Color
    val green400: Color
    val green500: Color
    val green900: Color
    // Neutral (pure gray)
    val neutral00: Color
    val neutral50: Color
    val neutral100: Color
    val neutral150: Color
    val neutral200: Color
    val neutral250: Color
    val neutral300: Color
    val neutral400: Color
    val neutral500: Color
    val neutral550: Color
    val neutral600: Color
    val neutral700: Color
    val neutral800: Color
    val neutral850: Color
    val neutral900: Color
    val neutral1000: Color
    // Neutral-bluish (slightly cool gray, primary surface palette)
    val nb00: Color
    val nb50: Color
    val nb60: Color
    val nb75: Color
    val nb100: Color
    val nb150: Color
    val nb200: Color
    val nb300: Color
    val nb400: Color
    val nb500: Color
    val nb600: Color
    val nb700: Color
    val nb750: Color
    val nb800: Color
    val nb850: Color
    val nb875: Color
    val nb900: Color
    val nb950: Color
    val nb1000: Color
    // Red
    val red50: Color
    val red100: Color
    val red400: Color
    val red500: Color
    val red600: Color
    val red900: Color
}

/**
 * dsh 默认静态色板，逐值翻译自 design-platform.css body 段。
 */
object DshDefaultPalette : DshStaticPalette {
    override val amber100 = Color(0xFFFEF5E7)
    override val amber400 = Color(0xFFF7AD31)
    override val amber500 = Color(0xFFF59E0B)
    override val amber600 = Color(0xFFDD8629)
    override val amber900 = Color(0xFF27241F)
    override val blue50 = Color(0xFFEFF6FF)
    override val blue75 = Color(0xFFE5F0FF)
    override val blue100 = Color(0xFFDBEAFE)
    override val blue300 = Color(0xFF93C5FD)
    override val blue400 = Color(0xFF60A5FA)
    override val blue450 = Color(0xFF4D93F8)
    override val blue500 = Color(0xFF3B82F6)
    override val blue600 = Color(0xFF2563EB)
    override val blue800 = Color(0xFF1E40AF)
    override val blue900 = Color(0xFF0E3074)
    override val blue950 = Color(0xFF172554)
    override val deepseek50 = Color(0xFFEDF3FE)
    override val deepseek100 = Color(0xFFE4EDFD)
    override val deepseek200 = Color(0xFFD3E2FF)
    override val deepseek300 = Color(0xFFB7C8FE)
    override val deepseek400 = Color(0xFF679EFE)
    override val deepseek450 = Color(0xFF5686FE)
    override val deepseek500 = Color(0xFF4176E6)
    override val deepseek600 = Color(0xFF4868B2)
    override val deepseek800 = Color(0xFF34415B)
    override val deepseek900 = Color(0xFF283142)
    override val green100 = Color(0xFFE6FAED)
    override val green400 = Color(0xFF4ED17E)
    override val green500 = Color(0xFF22C55E)
    override val green900 = Color(0xFF233C2C)
    override val neutral00 = Color(0xFFFFFFFF)
    override val neutral50 = Color(0xFFFAFAFA)
    override val neutral100 = Color(0xFFF5F5F5)
    override val neutral150 = Color(0xFFEDEDED)
    override val neutral200 = Color(0xFFE5E5E5)
    override val neutral250 = Color(0xFFDCDCDC)
    override val neutral300 = Color(0xFFD4D4D4)
    override val neutral400 = Color(0xFFA2A4A6)
    override val neutral500 = Color(0xFF7F8287)
    override val neutral550 = Color(0xFF65676B)
    override val neutral600 = Color(0xFF545557)
    override val neutral700 = Color(0xFF3C3C3D)
    override val neutral800 = Color(0xFF292929)
    override val neutral850 = Color(0xFF212123)
    override val neutral900 = Color(0xFF0F0F0F)
    override val neutral1000 = Color(0xFF000000)
    override val nb00 = Color(0xFFFFFFFF)
    override val nb50 = Color(0xFFF9FAFB)
    override val nb60 = Color(0xFFF5F6F7)
    override val nb75 = Color(0xFFF1F3F5)
    override val nb100 = Color(0xFFEBEEF2)
    override val nb150 = Color(0xFFE9ECF2)
    override val nb200 = Color(0xFFE1E5EE)
    override val nb300 = Color(0xFFCFD3D6)
    override val nb400 = Color(0xFFADB2B8)
    override val nb500 = Color(0xFF979DA6)
    override val nb600 = Color(0xFF81858C)
    override val nb700 = Color(0xFF61666B)
    override val nb750 = Color(0xFF43454A)
    override val nb800 = Color(0xFF353638)
    override val nb850 = Color(0xFF2C2C2E)
    override val nb875 = Color(0xFF232324)
    override val nb900 = Color(0xFF1B1B1C)
    override val nb950 = Color(0xFF151517)
    override val nb1000 = Color(0xFF0F1115)
    override val red50 = Color(0xFFFEF2F2)
    override val red100 = Color(0xFFFEE2E2)
    override val red400 = Color(0xFFF25A5A)
    override val red500 = Color(0xFFEF4444)
    override val red600 = Color(0xFFEC1313)
    override val red900 = Color(0xFF570C0C)
}

/**
 * 语义化颜色别名（对应原版 --dsw-alias-* / --dsw-specific-*）
 * 组件只引用此接口属性，不碰静态色板。
 * 亮色和暗色各有一套实现，换主题只需替换实现对象。
 */
interface DshColorTokens {
    /** 是否为暗色主题，用于需要特殊处理的组件（如 Markdown 代码高亮） */
    val isDark: Boolean
    // Background
    val bgBase: Color
    val bgLayer1: Color
    val bgLayer2: Color
    val bgLayer3: Color
    val bgModulePlatform: Color
    val bgOverlay: Color
    val bgSkeleton: Color
    // Border
    val borderL1: Color
    val borderL2: Color
    val borderL3: Color
    val borderL4: Color
    val borderInverted: Color
    // Brand
    val brandPrimary: Color
    val brandPrimaryInvert: Color
    val brandText: Color
    // Button
    val buttonPrimaryFill: Color
    val buttonPrimaryHover: Color
    val buttonPrimaryDimmed: Color
    val buttonInfoFill: Color
    val buttonInfoHover: Color
    val buttonGhostActiveFill: Color
    val buttonGhostActiveHover: Color
    val buttonElevatedFill: Color
    val buttonFloatingFill: Color
    val buttonFloatingHover: Color
    // Interactive
    val interactiveBgHover: Color
    val interactiveBgActive: Color
    val interactiveBgHoverSolid: Color
    val interactiveBgHoverAccent: Color
    val interactiveBgHoverDanger: Color
    // Label (text)
    val labelPrimary: Color
    val labelSecondary: Color
    val labelTertiary: Color
    val labelCaption: Color
    val labelDimmed: Color
    val labelPrimaryInverted: Color
    val labelPrimaryForeground: Color
    val labelPrimaryBluish: Color
    // Markdown
    val markdownCodeBlock: Color
    val markdownCodeBlockBanner: Color
    val markdownInlineCode: Color
    val markdownTag: Color
    val markdownPlaceholder: Color
    val markdownCitation: Color
    // Scrollbar
    val scrollbarBgL1: Color
    val scrollbarBgL2: Color
    val scrollbarHoverL1: Color
    val scrollbarHoverL2: Color
    // State
    val stateErrorPrimary: Color
    val stateErrorSecondary: Color
    val stateSuccessPrimary: Color
    val stateSuccessSecondary: Color
    val stateSuccessTertiary: Color
    val stateWarnPrimary: Color
    val stateWarnSecondary: Color
    val stateWarnTertiary: Color
    val stateWarnLabel: Color
    val stateBusinessPrimary: Color
    val stateBusinessTertiary: Color
    // Toast / Tooltip
    val toastBg: Color
    val tooltipBg: Color
    // Specific components
    val specificBubble: Color
    val specificBubbleHighlight: Color
    val specificInputMajor: Color
    val specificMenu: Color
    val specificSelector: Color
    val specificSidebarFill: Color
    val specificSidebarNavItemActive: Color
    val specificSidebarNavItemActiveAccent: Color
    val specificSidebarNavItemHover: Color
    val specificTip: Color
}

/**
 * dsh 默认亮色别名，逐值翻译自 design-platform.css body 段。
 */
object DshDefaultLightTokens : DshColorTokens {
    private val p = DshDefaultPalette
    override val isDark = false
    override val bgBase = p.nb00
    override val bgLayer1 = p.nb00
    override val bgLayer2 = p.nb00
    override val bgLayer3 = p.nb00
    override val bgModulePlatform = p.nb60
    override val bgOverlay = p.nb150
    override val bgSkeleton = Color(0x0A000000)
    override val borderL1 = Color(0x0A000000)
    override val borderL2 = Color(0x1A000000)
    override val borderL3 = Color(0x1F000000)
    override val borderL4 = Color(0x29000000)
    override val borderInverted = Color(0x00000000)
    override val brandPrimary = p.nb1000
    override val brandPrimaryInvert = p.nb1000
    override val brandText = p.nb1000
    override val buttonPrimaryFill = p.nb1000
    override val buttonPrimaryHover = p.nb750
    override val buttonPrimaryDimmed = p.nb100
    override val buttonInfoFill = p.deepseek500
    override val buttonInfoHover = p.deepseek400
    override val buttonGhostActiveFill = p.nb100
    override val buttonGhostActiveHover = p.nb150
    override val buttonElevatedFill = p.nb00
    override val buttonFloatingFill = p.nb00
    override val buttonFloatingHover = p.nb75
    override val interactiveBgHover = Color(0x0F263148)
    override val interactiveBgActive = Color(0x1A263148)
    override val interactiveBgHoverSolid = p.nb75
    override val interactiveBgHoverAccent = Color(0x24263148)
    override val interactiveBgHoverDanger = Color(0x0DEC1313)
    override val labelPrimary = p.nb1000
    override val labelSecondary = p.nb700
    override val labelTertiary = p.nb600
    override val labelCaption = p.nb400
    override val labelDimmed = p.nb200
    override val labelPrimaryInverted = p.nb00
    override val labelPrimaryForeground = p.nb00
    override val labelPrimaryBluish = p.blue900
    override val markdownCodeBlock = p.nb50
    override val markdownCodeBlockBanner = p.nb50
    override val markdownInlineCode = p.nb100
    override val markdownTag = p.nb75
    override val markdownPlaceholder = p.nb60
    override val markdownCitation = p.nb100
    override val scrollbarBgL1 = p.neutral200
    override val scrollbarBgL2 = p.neutral200
    override val scrollbarHoverL1 = p.neutral300
    override val scrollbarHoverL2 = p.neutral300
    override val stateErrorPrimary = p.red600
    override val stateErrorSecondary = p.red400
    override val stateSuccessPrimary = p.green500
    override val stateSuccessSecondary = p.green400
    override val stateSuccessTertiary = p.green100
    override val stateWarnPrimary = p.amber500
    override val stateWarnSecondary = p.amber400
    override val stateWarnTertiary = p.amber100
    override val stateWarnLabel = p.amber600
    override val stateBusinessPrimary = p.deepseek500
    override val stateBusinessTertiary = p.deepseek100
    override val toastBg = p.nb800
    override val tooltipBg = p.nb850
    override val specificBubble = p.deepseek50
    override val specificBubbleHighlight = p.deepseek200
    override val specificInputMajor = p.nb00
    override val specificMenu = p.nb00
    override val specificSelector = p.nb60
    override val specificSidebarFill = p.nb50
    override val specificSidebarNavItemActive = p.nb100
    override val specificSidebarNavItemActiveAccent = p.deepseek100
    override val specificSidebarNavItemHover = p.nb75
    override val specificTip = p.nb60
}

/**
 * dsh 默认暗色别名，逐值翻译自 design-platform.css body[data-ds-dark-theme] 段。
 */
object DshDefaultDarkTokens : DshColorTokens {
    private val p = DshDefaultPalette
    override val isDark = true
    override val bgBase = p.nb950
    override val bgLayer1 = p.nb875
    override val bgLayer2 = p.nb850
    override val bgLayer3 = p.nb800
    override val bgModulePlatform = p.nb800
    override val bgOverlay = p.nb700
    override val bgSkeleton = Color(0x14FFFFFF)
    override val borderL1 = Color(0x0FFFFFFF)
    override val borderL2 = Color(0x1FFFFFFF)
    override val borderL3 = Color(0x29FFFFFF)
    override val borderL4 = Color(0x33FFFFFF)
    override val borderInverted = Color(0x0FFFFFFF)
    override val brandPrimary = p.nb50
    override val brandPrimaryInvert = p.nb50
    override val brandText = p.nb50
    override val buttonPrimaryFill = p.nb50
    override val buttonPrimaryHover = p.nb100
    override val buttonPrimaryDimmed = p.nb750
    override val buttonInfoFill = p.deepseek400
    override val buttonInfoHover = p.deepseek500
    override val buttonGhostActiveFill = p.nb750
    override val buttonGhostActiveHover = p.nb700
    override val buttonElevatedFill = p.nb750
    override val buttonFloatingFill = p.nb850
    override val buttonFloatingHover = p.nb800
    override val interactiveBgHover = Color(0x14FFFFFF)
    override val interactiveBgActive = Color(0x24FFFFFF)
    override val interactiveBgHoverSolid = p.nb800
    override val interactiveBgHoverAccent = Color(0x3DFFFFFF)
    override val interactiveBgHoverDanger = Color(0x26F25A5A)
    override val labelPrimary = p.nb50
    override val labelSecondary = p.nb300
    override val labelTertiary = p.nb400
    override val labelCaption = p.nb600
    override val labelDimmed = p.nb750
    override val labelPrimaryInverted = p.nb800
    override val labelPrimaryForeground = p.nb1000
    override val labelPrimaryBluish = p.nb50
    override val markdownCodeBlock = p.nb900
    override val markdownCodeBlockBanner = p.nb850
    override val markdownInlineCode = p.nb850
    override val markdownTag = p.nb850
    override val markdownPlaceholder = p.nb850
    override val markdownCitation = p.nb800
    override val scrollbarBgL1 = p.neutral700
    override val scrollbarBgL2 = p.neutral600
    override val scrollbarHoverL1 = p.neutral600
    override val scrollbarHoverL2 = p.neutral550
    override val stateErrorPrimary = p.red400
    override val stateErrorSecondary = p.red400
    override val stateSuccessPrimary = p.green500
    override val stateSuccessSecondary = p.green400
    override val stateSuccessTertiary = p.green900
    override val stateWarnPrimary = p.amber500
    override val stateWarnSecondary = p.amber400
    override val stateWarnTertiary = p.amber900
    override val stateWarnLabel = p.amber600
    override val stateBusinessPrimary = p.deepseek400
    override val stateBusinessTertiary = p.deepseek800
    override val toastBg = p.nb750
    override val tooltipBg = p.nb750
    override val specificBubble = p.nb850
    override val specificBubbleHighlight = p.nb750
    override val specificInputMajor = p.nb850
    override val specificMenu = p.nb800
    override val specificSelector = p.nb800
    override val specificSidebarFill = p.nb900
    override val specificSidebarNavItemActive = p.nb750
    override val specificSidebarNavItemActiveAccent = p.nb800
    override val specificSidebarNavItemHover = p.nb850
    override val specificTip = p.nb800
}

/**
 * 主题包：静态色板 + 亮色别名 + 暗色别名 + 名称。
 * 社区主题实现此接口即可整体替换，组件代码无需改动。
 */
interface DshTheme {
    val name: String
    val palette: DshStaticPalette
    val light: DshColorTokens
    val dark: DshColorTokens
}

/** dsh 默认主题 */
object DshDefaultTheme : DshTheme {
    override val name = "dsh-default"
    override val palette = DshDefaultPalette
    override val light = DshDefaultLightTokens
    override val dark = DshDefaultDarkTokens
}
