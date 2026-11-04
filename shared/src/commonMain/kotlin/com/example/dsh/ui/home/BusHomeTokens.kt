package com.example.dsh.ui.home

import com.tencent.kuikly.core.base.Color

/**
 * 首页视觉基线（对照 Agent-host-docs 里定稿的首页/审批面板设计稿与截图）
 *
 * 只收敛定稿设计用到的色值、圆角、间距与字号，不改写 [com.example.dsh.theme.DshColorTokens]
 * 这套全局主题，也不强制旧页面迁移。改版页面直接引用这里的语义名，
 * 避免各文件重复写十六进制、rgba 和魔法数字
 *
 * 命名约定：bg 背景、text 文字、line 描边、fill 填充、state 状态、
 * radius 圆角、space 与 gap 间距、fontSize 字号
 */
internal object BusHomeTokens {

    /**
     * 首页设计稿画布宽 375；运行时按页面宽度等比缩放，夹在 0.90–1.14
     *
     * 与对话页 BusChatTokens 同一套做法（见 docs/kuikly-web-to-kuikly-pitfalls.md §20），
     * 只是画布宽不同：首页那份稿子是 375×812，对话页那份是 390×844
     */
    private const val HOME_DESIGN_WIDTH = 375f

    /** 当前缩放系数；单测里没人调 update，保持 1f */
    internal var homeDesignScale = 1f
        private set

    internal fun updateHomeDesignScale(pageWidth: Float) {
        homeDesignScale = if (pageWidth > 0f) {
            (pageWidth / HOME_DESIGN_WIDTH).coerceIn(0.90f, 1.14f)
        } else {
            1f
        }
    }
    // ---------- 背景 ----------

    /** 页面底色 #F4F4F6 */
    val bgBase: Color = Color(0xFFF4F4F6)

    /** 顶部渐变起始色 #F1F0F4（头部淡紫区） */
    val bgTop: Color = Color(0xFFF1F0F4)

    /** 顶部渐变中段 #F3F2F5 */
    val bgTopMid: Color = Color(0xFFF3F2F5)

    /** 头部淡紫：径向渐变在首屏的实际强度（rgba(211,203,238,.31)），不是 CSS 中心值 .58 */
    val headerLavender: Color = Color(0x4FD3CBEE)

    /** 头部淡紫中段 rgba(211,203,238,.16) */
    val headerLavenderSoft: Color = Color(0x29D3CBEE)

    /** 头部淡紫完全透明端 rgba(211,203,238,0) */
    val headerLavenderClear: Color = Color(0x00D3CBEE)

    /** 卡片/悬浮面板底色 rgba(255,255,255,.94) */
    val surface: Color = Color(0xF0FFFFFF)

    /** 卡片高光描边 rgba(255,255,255,.72) */
    val surfaceBorder: Color = Color(0xB8FFFFFF)

    /** Bottom Sheet 底色 rgba(245,245,247,.98) */
    val sheet: Color = Color(0xFFF5F5F7)

    /** 遮罩层 rgba(17,17,26,.30) */
    val scrim: Color = Color(0x4D11111A)

    // ---------- 文字 ----------

    /** 主文字 #1D1B20 */
    val textPrimary: Color = Color(0xFF1D1B20)

    /** 次级文字 #57534E */
    val textSecondary: Color = Color(0xFF57534E)

    /** 三级文字 #8E8E93 */
    val textTertiary: Color = Color(0xFF8E8E93)

    /** 弱化文字/占位 #C4C4CC */
    val textQuiet: Color = Color(0xFFC4C4CC)

    // ---------- 描边与填充 ----------

    /** 发丝线/分隔线 rgba(29,27,32,.05) */
    val hairline: Color = Color(0x0D1D1B20)

    /** 输入框/图标底盘 #F5F5F7 */
    val field: Color = Color(0xFFF5F5F7)

    /** 分段控件未选中底 #EFEFF2 */
    val chipFill: Color = Color(0xFFEFEFF2)

    /** 项目/场景标签底 #EFEFEA */
    val tagFill: Color = Color(0xFFEFEFEA)

    /** 胶囊角标底 #E5484D */
    val badgeFill: Color = Color(0xFFE5484D)

    // ---------- 状态 ----------

    /** 选中态底色 #1D1B20 */
    val selectedFill: Color = Color(0xFF1D1B20)

    /** 选中态前景 #FFFFFF */
    val selectedForeground: Color = Color(0xFFFFFFFF)

    /** 待审批文字 #9A6A1F */
    val pendingText: Color = Color(0xFF9A6A1F)

    /** 待审批底色 #FBF1DF */
    val pendingFill: Color = Color(0xFFFBF1DF)

    /** 成功/在线 #557A63 */
    val success: Color = Color(0xFF557A63)

    /** 错误文字 #D92D20 */
    val error: Color = Color(0xFFD92D20)

    /** 错误底色 #FEE4E4 */
    val errorFill: Color = Color(0xFFFEE4E4)

    // ---------- 圆角 ----------

    /** 任务卡片 18 */
    val radiusCard: Float get() = busHomePx(18f)

    /** Bottom Sheet 顶部 24 */
    val radiusSheet: Float get() = busHomePx(24f)

    /** 胶囊按钮 26 */
    val radiusCapsule: Float get() = busHomePx(26f)

    /** 普通按钮 10 */
    val radiusButton: Float get() = busHomePx(10f)

    /** Chip/分段块 9 */
    val radiusChip: Float get() = busHomePx(9f)

    /** 设备入口、卡片内小面板 16 */
    val radiusNavEntry: Float get() = busHomePx(16f)

    /** 分段控件外框 12 */
    val radiusSegmented: Float get() = busHomePx(12f)

    /** 底部胶囊外壳 31 */
    val radiusCapsuleShell: Float get() = busHomePx(31f)

    // ---------- 间距 ----------

    /** 页面左右安全边距 20 */
    val spacePage: Float get() = busHomePx(20f)

    /** 卡片内边距 16 */
    val spaceCard: Float get() = busHomePx(16f)

    /** 任务行间距 12 */
    val gapRow: Float get() = busHomePx(12f)

    /** 同行控件间距 10 */
    val gapControl: Float get() = busHomePx(10f)

    /** 头部区与任务列表之间的上间距 16 */
    val spaceListTop: Float get() = busHomePx(16f)

    /** Bottom Sheet 左右内边距 20 */
    val spaceSheet: Float get() = busHomePx(20f)

    // ---------- 字号 ----------

    /** 标题/任务名 15 */
    val fontSizeTitle: Float get() = busHomePx(15f)

    /** 正文/设备名/分段 13 */
    val fontSizeBody: Float get() = busHomePx(13f)

    /** 辅助信息/Agent 名 12 */
    val fontSizeMeta: Float get() = busHomePx(12f)

    /** 时间/状态 11 */
    val fontSizeCaption: Float get() = busHomePx(11f)

    /** 角标 10 */
    val fontSizeBadge: Float get() = busHomePx(10f)

    // ---------- 顶部渐隐（v78/v84：任务列表滚过头部后出现的实心渐隐层） ----------

    /** `#task-top-fade` 高度 56，用负 margin 叠在列表上不占位 */
    val topFadeHeight: Float get() = busHomePx(56f)

    /** 顶部渐隐起始色 #F2F1F4 */
    val topFadeStart: Color = Color(0xFFF2F1F4)

    /** 顶部渐隐 24% 处 #F3F2F5 */
    val topFadeMiddle: Color = Color(0xFFF3F2F5)

    /** 顶部渐隐 46% 处 rgba(244,244,246,.94) */
    val topFadeBody: Color = Color(0xF0F4F4F6)

    /** 顶部渐隐完全透明端 rgba(244,244,246,0) */
    val topFadeClear: Color = Color(0x00F4F4F6)

    /**
     * 深色主题的同一组色标：v86 只有浅色，深色是我们自己桥接的，
     * 照搬浅色那四条 #F2F1F4/#F3F2F5 会在 #151517 的页面上刷出一道白条
     * （实测深色截图 y=580~640 全是 RGB(242,241,244)）。
     * 桥法与浅色同幅：浅色是页面底 #F4F4F6 往下压 2/3/2 阶，
     * 深色就从 bgCanvas #151517 往下压同样 2/3/2 阶，中段与透明端回到页面底色本身
     */
    val topFadeStartDark: Color = Color(0xFF131215)
    val topFadeMiddleDark: Color = Color(0xFF141316)
    val topFadeBodyDark: Color = Color(0xF0151517)
    val topFadeClearDark: Color = Color(0x00151517)

    /** 顶部渐隐分段位置：24% */
    const val topFadeStopMiddle: Float = 0.24f

    /** 顶部渐隐分段位置：46% */
    const val topFadeStopBody: Float = 0.46f

    /** 底部渐隐遮罩高度 160 */
    val bottomFadeHeight: Float get() = busHomePx(160f)

    /** 底部渐隐中段 rgba(244,244,246,.55) */
    val bottomFadeMid: Color = Color(0x8CF4F4F6)

    // ---------- 角标（胶囊上的待处理计数） ----------

    /** 角标高度 16，min-width 同为 16 */
    val badgeHeight: Float get() = busHomePx(16f)

    /** 角标最小宽度 16（超出按 padding 撑开） */
    val badgeMinWidth: Float get() = busHomePx(16f)

    /** 角标圆角 8 */
    val radiusBadge: Float get() = busHomePx(8f)

    /** 角标左右内边距 4 */
    val spaceBadge: Float get() = busHomePx(4f)

    /** 角标相对胶囊的定位：上 7 */
    val badgeTop: Float get() = busHomePx(7f)

    /** 角标相对胶囊的定位：右 12 */
    val badgeEnd: Float get() = busHomePx(12f)

    /** 角标文字 #FFFFFF */
    val badgeText: Color = Color(0xFFFFFFFF)

    // ---------- 底部胶囊（列表 / 审批互斥切换） ----------

    /** 单个胶囊按钮宽 84 */
    val capsuleWidth: Float get() = busHomePx(84f)

    /** 单个胶囊按钮高 52 */
    val capsuleHeight: Float get() = busHomePx(52f)

    /** 胶囊外壳内边距 5 */
    val capsuleShellPadding: Float get() = busHomePx(5f)

    /** 胶囊图标 24 */
    val capsuleIconSize: Float get() = busHomePx(24f)

    /** 胶囊外壳描边 #EFEFF2 */
    val capsuleShellBorder: Color = Color(0xFFEFEFF2)

    /** 胶囊外壳投影 rgba(17,17,26,.06) */
    val capsuleShadow: Color = Color(0x0F11111A)

    /** 胶囊外壳离底部 36 */
    val capsuleBottomInset: Float get() = busHomePx(36f)

    /** 新建任务圆钮 56×56 */
    val fabSize: Float get() = busHomePx(56f)

    /** 新建任务圆钮投影 rgba(17,17,26,.14) */
    val fabShadow: Color = Color(0x2411111A)

    // ---------- 面板与卡片 ----------

    /** 卡片/审批卡/Agent 行的实心底色 #FFFFFF */
    val cardFill: Color = Color(0xFFFFFFFF)

    /** 卡片投影 rgba(17,17,26,.04) */
    val cardShadow: Color = Color(0x0A11111A)

    /** Bottom Sheet 顶部描边 rgba(255,255,255,.85) */
    val sheetBorder: Color = Color(0xD9FFFFFF)

    /** Bottom Sheet 上抛投影 rgba(30,30,45,.12) */
    val sheetShadow: Color = Color(0x1F1E1E2D)

    /** Bottom Sheet 顶边比例：设计稿 432 / 812，面板高度 = 页高 × (1 - 比例) */
    const val sheetTopRatio: Float = 432f / 812f

    /** 卡片描边 #E4E4E7（拒绝按钮等白底描边控件） */
    val lineCard: Color = Color(0xFFE4E4E7)

    /** 虚线描边（自定义回复入口） #D1D1D6 */
    val lineDashed: Color = Color(0xFFD1D1D6)

    /** 卡片/审批卡圆角 16 */
    val radiusPanel: Float get() = busHomePx(16f)

    /** 分段控件内边距 3 */
    val segmentedPadding: Float get() = busHomePx(3f)

    /** 分段块上下内边距 6 */
    val segmentedItemVertical: Float get() = busHomePx(6f)

    /** 分段块左右内边距 16 */
    val segmentedItemHorizontal: Float get() = busHomePx(16f)

    /** 筛选行最小高度 39 */
    val listControlsHeight: Float get() = busHomePx(39f)

    /** Agent 头像 26 */
    val avatarSize: Float get() = busHomePx(26f)

    /** Agent 头像叠放重叠量 8 */
    val avatarOverlap: Float get() = busHomePx(8f)

    /** Agent 头像描边 1.5 */
    val avatarBorder: Float get() = busHomePx(1.5f)

    /** 头像投影 rgba(17,17,26,.18) */
    val avatarShadow: Color = Color(0x2E11111A)

    /** 页面宽度基准 375（设计稿宽度，等比换算用） */
    const val designPageWidth: Float = 375f

    /** 页面高度基准 812 */
    const val designPageHeight: Float = 812f
}

/**
 * 配色的一组语义打包，方便面板/卡片一次传参，避免 UI 里逐条点名
 *
 * 目前只有浅色一套（设计基线只给了浅色）；深色主题沿用
 * [com.example.dsh.theme.DshColorTokens]，这里用 [isDark] 让调用方区分两条链路
 */
internal data class BusHomePalette(
    val bgBase: Color,
    val surface: Color,
    val sheet: Color,
    val scrim: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val chipFill: Color,
    val selectedFill: Color,
    /** 选中态（墨底/白底）上的前景色；深色主题下 selectedFill 翻白，文字必须翻黑 */
    val onSelected: Color,
    val error: Color,
    val errorFill: Color,
    /** 列表顶部渐隐四条色标；浅色取 v86 原值，深色按当前页面底色同幅压暗 */
    val topFadeStart: Color,
    val topFadeMiddle: Color,
    val topFadeBody: Color,
    val topFadeClear: Color,
    val isDark: Boolean,
)

private val busHomeLightPalette = BusHomePalette(
    bgBase = BusHomeTokens.bgBase,
    surface = BusHomeTokens.surface,
    sheet = BusHomeTokens.sheet,
    scrim = BusHomeTokens.scrim,
    textPrimary = BusHomeTokens.textPrimary,
    textSecondary = BusHomeTokens.textSecondary,
    textTertiary = BusHomeTokens.textTertiary,
    chipFill = BusHomeTokens.chipFill,
    selectedFill = BusHomeTokens.selectedFill,
    onSelected = BusHomeTokens.selectedForeground,
    error = BusHomeTokens.error,
    errorFill = BusHomeTokens.errorFill,
    topFadeStart = BusHomeTokens.topFadeStart,
    topFadeMiddle = BusHomeTokens.topFadeMiddle,
    topFadeBody = BusHomeTokens.topFadeBody,
    topFadeClear = BusHomeTokens.topFadeClear,
    isDark = false,
)

/** 浅色调色板（单例，可安全用于响应式比较）。 */
internal val BusHomeTokens.light: BusHomePalette
    get() = busHomeLightPalette

/** 首页设计 px → 当前设备 dp；1px 发丝线、比例、透明度不要走这里 */
internal fun busHomePx(value: Float): Float = value * BusHomeTokens.homeDesignScale
