package com.example.dsh.ui.chat

import com.example.dsh.theme.DshColorTokens
import com.tencent.kuikly.core.base.Color

/**
 * 对话页视觉基线（对照 .artifacts/agentbus-chat-detail.html）
 *
 * 只收敛这一页用到的色值、圆角、间距与字号；不改写全局 [DshColorTokens]
 * 也不影响首页与旧页面。浅色值直接取自设计稿，深色值按同一层级映射到
 * 主题的暗色别名，保证切主题时整页一起变，而不是只有背景变
 */
internal object BusChatTokens {
    /** 设计稿画布宽 390；运行时按页面宽度等比缩放，夹在 0.90–1.14 之间 */
    private const val DESIGN_WIDTH = 390f
    private var designScale = 1f

    /** 每个页面在 body 里先同步一次；同设备宽度下多页共用同一个系数 */
    internal fun updateDesignScale(pageWidth: Float) {
        designScale = if (pageWidth > 0f) {
            (pageWidth / DESIGN_WIDTH).coerceIn(0.90f, 1.14f)
        } else {
            1f
        }
    }

    /** 设计稿 px → 当前设备 dp */
    internal fun px(value: Float): Float = value * designScale

    // ---------- 背景 ----------

    /** 页面底色 #F4F4F6 */
    val bgBase: Color = Color(0xFFF4F4F6)

    /** 淡紫氛围起始 rgba(211,203,238,.31)，与首页头部同一强度 */
    val glowTop: Color = Color(0x4FD3CBEE)

    /** 淡紫氛围中段 rgba(211,203,238,.16) */
    val glowMid: Color = Color(0x29D3CBEE)

    /** 淡紫氛围透明端 */
    val glowClear: Color = Color(0x00D3CBEE)

    /** 卡片/气泡/浮层底色 rgba(255,255,255,.94) */
    val surface: Color = Color(0xF0FFFFFF)

    /** 卡片高光描边 rgba(255,255,255,.85) */
    val surfaceBorder: Color = Color(0xD9FFFFFF)

    /** 底部面板底色 #FAFAFB；Kuikly 没有 backdrop blur，改成不透明避免透出输入条 */
    val sheet: Color = Color(0xFFFAFAFB)

    /** 遮罩 rgba(29,27,32,.20) */
    val scrim: Color = Color(0x331D1B20)

    // ---------- 文字 ----------

    /** 主文字 #1D1B20 */
    val ink: Color = Color(0xFF1D1B20)

    /** 次级文字 #3A3A40 */
    val ink2: Color = Color(0xFF3A3A40)

    /** 深灰 #57534E */
    val deep: Color = Color(0xFF57534E)

    /** 弱化文字 #8E8E93 */
    val mut: Color = Color(0xFF8E8E93)

    /** 最弱文字/占位 #C4C4CC */
    val faint: Color = Color(0xFFC4C4CC)

    // ---------- 描边与填充 ----------

    /** 发丝线 #EFEFF2 */
    val line: Color = Color(0xFFEFEFF2)

    /** 输入框/图标底盘 #F5F5F7 */
    val field: Color = Color(0xFFF5F5F7)

    /** 关闭态圆键/停止键禁用底 #E7E7EC */
    val chip: Color = Color(0xFFE7E7EC)

    /** 选中/发送键墨底 #1D1B20 */
    val solid: Color = Color(0xFF1D1B20)

    /** 墨底上的前景 #FFFFFF */
    val onSolid: Color = Color(0xFFFFFFFF)

    // ---------- 状态 ----------

    /** 完成 #557A63 */
    val ok: Color = Color(0xFF557A63)

    /** 待处理 #9A6A1F */
    val warn: Color = Color(0xFF9A6A1F)

    /** 失败 #E5484D */
    val bad: Color = Color(0xFFE5484D)

    /** 字标唯一彩色 #6E5FA3 */
    val kuik: Color = Color(0xFF6E5FA3)

    // ---------- 顶部玻璃 ----------

    /**
     * 顶栏玻璃板的底色：稿子是 `rgba(243,243,246, α)`，α 由「渐变 × mask」合成
     *
     * 原来这里存的是五档合成前的渐变值（.90/.72/.42/.16/0），少乘了稿子那条
     * `mask-image`，尾巴比设计硬；底色单独存一个，α 在渲染处按合成表给
     */
    val glassBase: Color = Color(0xFFF3F3F6)

    // ---------- 顶部遮罩（正文化下去，至少四档） ----------

    val fade0: Color = Color(0x00F4F4F6)
    val fade14: Color = Color(0x24F4F4F6)
    val fade38: Color = Color(0x61F4F4F6)
    val fade64: Color = Color(0xA3F4F4F6)
    val fade86: Color = Color(0xDBF4F4F6)
    val fade100: Color = Color(0xFFF4F4F6)

    // ---------- 投影 ----------

    /** 卡片主投影 rgba(40,40,55,.06) */
    val cardShadow: Color = Color(0x0F282837)

    /** 浮层主投影 rgba(40,40,55,.16) */
    val overlayShadow: Color = Color(0x29282837)

    /** 按钮投影 rgba(40,40,55,.07) */
    val buttonShadow: Color = Color(0x12282837)

    /** 输入条/悬浮卡主投影：软影，比卡片影重一点，但不加接触硬边 */
    val composerShadow: Color = Color(0x24282837)

    // ---------- 尺寸 ----------

    /** 页面左右边距，同时是正文左基线 14 */
    val pageSide: Float get() = px(14f)

    /** 顶栏上边距 4 */
    val navTop: Float get() = px(4f)

    /** 玻璃圆钮 38 */
    val iconButton: Float get() = px(38f)

    /** 玻璃圆钮半径 13 */
    val iconButtonRadius: Float get() = px(13f)

    /** 元信息行图标 12 */
    val metaIcon: Float get() = px(12f)

    /** Agent 专属头像 20 */
    val agentAvatar: Float get() = px(20f)

    /** 元信息胶囊内头像 15 */
    val badgeAvatar: Float get() = px(15f)

    /** 用户气泡最大宽度占比 82% */
    const val bubbleMaxRatio: Float = 0.82f

    /** 用户气泡圆角 18，右下 6 */
    val bubbleRadius: Float get() = px(18f)
    val bubbleTail: Float get() = px(6f)

    /** 用户气泡内边距 9 14 */
    val bubblePaddingV: Float get() = px(9f)
    val bubblePaddingH: Float get() = px(14f)

    /** 正文 15 / 1.65 */
    val bodySize: Float get() = px(15f)
    val bodyLine: Float get() = px(24.75f)

    /** 正文段间距 9 */
    val paragraphGap: Float get() = px(9f)

    /** 列表方点 4 */
    val bulletDot: Float get() = px(4f)

    /** 换轮 24 / 同轮 8 / 卡片 12 */
    val gapTurn: Float get() = px(24f)
    val gapWithinTurn: Float get() = px(8f)
    val gapCard: Float get() = px(12f)

    /** 工具行摘要 13 / 12.5 */
    val toolTitle: Float get() = px(13f)
    val toolSummary: Float get() = px(12.5f)

    /** 流式行 13，计数 11 */
    val streamLabel: Float get() = px(13f)
    val streamCount: Float get() = px(11f)

    /** 审批卡圆角 16、内边距 12 */
    val approvalRadius: Float get() = px(16f)
    val approvalPadding: Float get() = px(12f)

    /** 审批卡图标井 28 */
    val well: Float get() = px(28f)

    /** 代码井 10，内边距 8 10 */
    val codeRadius: Float get() = px(10f)
    val codeSize: Float get() = px(12.5f)

    /** 输入胶囊高 44、半径 24、内缩 7 */
    val capsuleHeight: Float get() = px(44f)
    val capsuleRadius: Float get() = px(24f)
    val capsuleInset: Float get() = px(7f)

    /** 圆键 28，字形框 16 */
    val roundKey: Float get() = px(28f)
    val glyph: Float get() = px(16f)

    /** 停止方块实心 12 */
    val stopSquare: Float get() = px(12f)

    /** 模型芯片 11.5，箭头 9 */
    val modelChip: Float get() = px(11.5f)
    val modelChevron: Float get() = px(9f)

    /** 上方芯片行高 20，字 10.5 */
    val chipHeight: Float get() = px(20f)
    val chipFont: Float get() = px(10.5f)

    /** 输入区左右 14，底部 26（从屏幕物理底边算，整屏沉浸之后直接用这个值） */
    val composerSide: Float get() = px(14f)
    val composerBottom: Float get() = px(26f)

    /** 底部面板圆角 22 */
    val sheetRadius: Float get() = px(22f)

    /** 抓握条 34×4 */
    val grabWidth: Float get() = px(34f)
    val grabHeight: Float get() = px(4f)

    /** 面板标题行 15 / 600 */
    val sheetTitle: Float get() = px(15f)

    /** 面板左右 16 */
    val sheetSide: Float get() = px(16f)

    /** 技能列表限高 168 */
    val skillListMax: Float get() = px(168f)

    /** 面板最大高度占比 74% */
    const val sheetMaxRatio: Float = 0.74f

    /**
     * 顶栏玻璃板高度 94 = 顶栏 42 + 稿子的 `padding-bottom: 52`
     *
     * 中间那版收到 24 是矫枉过正：24 只盖到顶栏的一半，正文滚到顶栏下沿时
     * 既没有被压住也没有被化开，是一条硬切 + 一小段很陡的雾，看起来更突兀。
     * 按稿子回到 94，并把「渐变 × mask」合成成一条真正柔和的尾巴（见
     * BusChatGlassBackdrop 的分段表）
     */
    val glassHeight: Float get() = px(94f)

    /** 底部淡出 44：对齐 HTML 的 mask 长度，配合列表 44 的底部留白 */
    val bottomFade: Float get() = px(44f)

    /** 空状态字标 29 / 1.15 */
    val wordmark: Float get() = px(29f)

    /** 空状态副文案 14 */
    val emptyTag: Float get() = px(14f)
}

/** 对话页里直接写的设计稿数值统一走这里，避免漏掉缩放 */
internal fun busChatPx(value: Float): Float = BusChatTokens.px(value)

/**
 * 对话页配色打包
 *
 * 深色主题下没有淡紫氛围与实心渐隐，玻璃换成页面底色；
 * 选中/发送键由墨底翻成近白底，保证深色下仍是最亮的一颗键
 */
internal data class BusChatPalette(
    val bgBase: Color,
    val glowTop: Color,
    val glowMid: Color,
    val glowClear: Color,
    val surface: Color,
    val surfaceBorder: Color,
    val sheet: Color,
    val scrim: Color,
    val ink: Color,
    val ink2: Color,
    val deep: Color,
    val mut: Color,
    val faint: Color,
    val line: Color,
    val field: Color,
    val chip: Color,
    val solid: Color,
    val onSolid: Color,
    val ok: Color,
    val warn: Color,
    val bad: Color,
    val kuik: Color,
    val glassBase: Color,
    val fade0: Color,
    val fade14: Color,
    val fade38: Color,
    val fade64: Color,
    val fade86: Color,
    val fade100: Color,
    val isDark: Boolean,
)

private val busChatLightPalette = BusChatPalette(
    bgBase = BusChatTokens.bgBase,
    glowTop = BusChatTokens.glowTop,
    glowMid = BusChatTokens.glowMid,
    glowClear = BusChatTokens.glowClear,
    surface = BusChatTokens.surface,
    surfaceBorder = BusChatTokens.surfaceBorder,
    sheet = BusChatTokens.sheet,
    scrim = BusChatTokens.scrim,
    ink = BusChatTokens.ink,
    ink2 = BusChatTokens.ink2,
    deep = BusChatTokens.deep,
    mut = BusChatTokens.mut,
    faint = BusChatTokens.faint,
    line = BusChatTokens.line,
    field = BusChatTokens.field,
    chip = BusChatTokens.chip,
    solid = BusChatTokens.solid,
    onSolid = BusChatTokens.onSolid,
    ok = BusChatTokens.ok,
    warn = BusChatTokens.warn,
    bad = BusChatTokens.bad,
    kuik = BusChatTokens.kuik,
    glassBase = BusChatTokens.glassBase,
    fade0 = BusChatTokens.fade0,
    fade14 = BusChatTokens.fade14,
    fade38 = BusChatTokens.fade38,
    fade64 = BusChatTokens.fade64,
    fade86 = BusChatTokens.fade86,
    fade100 = BusChatTokens.fade100,
    isDark = false,
)

/** 浅色基线（单例，可安全用于响应式比较） */
internal val BusChatTokens.light: BusChatPalette
    get() = busChatLightPalette

/**
 * 主题色 → 对话页配色
 *
 * 深色下卡片用 bgModulePlatform、输入底盘用 bgLayer1，和首页卡片/面板同一层级；
 * 淡紫氛围只在浅色出现，否则深色页会多一层发灰的色块
 */
internal fun busChatPalette(colors: DshColorTokens): BusChatPalette {
    if (!colors.isDark) return BusChatTokens.light
    val bg = colors.bgCanvas
    return BusChatPalette(
        bgBase = bg,
        glowTop = Color(0x00000000),
        glowMid = Color(0x00000000),
        glowClear = Color(0x00000000),
        surface = colors.bgModulePlatform,
        surfaceBorder = colors.borderL2,
        sheet = colors.bgModulePlatform,
        scrim = Color(0x6611111A),
        ink = colors.labelPrimary,
        ink2 = colors.labelSecondary,
        deep = colors.labelSecondary,
        mut = colors.labelTertiary,
        faint = colors.labelCaption,
        line = colors.borderL1,
        field = colors.bgLayer1,
        chip = colors.bgLayer2,
        solid = colors.labelPrimary,
        onSolid = colors.labelPrimaryForeground,
        ok = colors.stateSuccessPrimary,
        warn = colors.stateWarnPrimary,
        bad = colors.stateErrorSecondary,
        kuik = Color(0xFFB3A6E1),
        glassBase = bg,
        fade0 = bg.opacity(0f),
        fade14 = bg.opacity(0.14f),
        fade38 = bg.opacity(0.38f),
        fade64 = bg.opacity(0.64f),
        fade86 = bg.opacity(0.86f),
        fade100 = bg.opacity(1f),
        isDark = true,
    )
}
