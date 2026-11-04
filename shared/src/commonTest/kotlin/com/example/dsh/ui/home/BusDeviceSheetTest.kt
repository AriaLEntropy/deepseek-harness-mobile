package com.example.dsh.ui.home

import com.example.dsh.theme.DshDefaultDarkTokens
import com.example.dsh.theme.DshDefaultLightTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 面板外壳与设备面板的几何/文案判定单测
 *
 * 面板顶边按设计稿 432/812 的比例换算，行宽、标签种类与副行文案也在设计基线里写死，
 * 这里把它们钉住，避免改样式时把面板比例、343 行宽、标签语义顺手改掉
 */
class BusDeviceSheetTest {

    // ---------- 面板几何（视觉规范 16.8） ----------

    @Test
    fun sheetHeightFollowsDesignTopRatio() {
        // 设计画布 812：顶边 432 -> 面板高 380
        assertEquals(380f, busSheetHeight(812f), 0.01f)
        // 更高的屏幕按同一比例下移，而不是固定 432：1000 × (1 - 432/812)
        assertEquals(467.98f, busSheetHeight(1000f), 0.01f)
        // 模拟器实际可用高度 890：顶边约 473.5，面板约 416.5
        assertEquals(416.5f, busSheetHeight(890f), 0.01f)
    }

    @Test
    fun sheetHeightFallsBackToRatioOnShortPageAndNeverOverflows() {
        // 比例高度已高于 45% 兜底；矮屏仍按 45% 页高保底，且不超过页高、不会给负数
        assertEquals(280.79f, busSheetHeight(600f), 0.01f)
        assertEquals(187.19f, busSheetHeight(400f), 0.01f)
        assertEquals(0f, busSheetHeight(0f), 0.01f)
        assertTrue(busSheetHeight(300f) <= 300f)
        assertTrue(busSheetHeight(-100f) >= 0f)
    }

    @Test
    fun rowWidthKeepsScrollAreaPadding() {
        // 滚动区左右 padding 16：375 - 32 = 343
        assertEquals(343f, busSheetRowWidth(375f), 0.001f)
        assertEquals(288f, busSheetRowWidth(320f), 0.001f)
        assertEquals(0f, busSheetRowWidth(10f), 0.001f)
    }

    // ---------- 设备行标签：只有当前 / 在线 / 离线 ----------

    @Test
    fun currentDeviceWinsEvenWhenOffline() {
        assertEquals(BusDeviceTagKind.Current, busDeviceRowTag(online = false, isCurrent = true))
        assertEquals(BusDeviceTagKind.Online, busDeviceRowTag(online = true, isCurrent = false))
        assertEquals(BusDeviceTagKind.Offline, busDeviceRowTag(online = false, isCurrent = false))
    }

    @Test
    fun tagLabelsAreExactlyThreeWords() {
        assertEquals("当前", busDeviceTagLabel(BusDeviceTagKind.Current))
        assertEquals("在线", busDeviceTagLabel(BusDeviceTagKind.Online))
        assertEquals("离线", busDeviceTagLabel(BusDeviceTagKind.Offline))
    }

    @Test
    fun offlineTagIsMutedAndNotEmphasized() {
        val palette = BusHomeTokens.light
        // 当前/在线 = 主文字 on #EFEFEA；离线 = 三级文字 on #EFEFF2
        assertEquals(palette.textPrimary to BusHomeTokens.tagFill, busDeviceTagColors(BusDeviceTagKind.Current, palette))
        assertEquals(palette.textPrimary to BusHomeTokens.tagFill, busDeviceTagColors(BusDeviceTagKind.Online, palette))
        assertEquals(palette.textTertiary to palette.chipFill, busDeviceTagColors(BusDeviceTagKind.Offline, palette))
        assertTrue(busDeviceTagIsEmphasized(BusDeviceTagKind.Current))
        assertTrue(busDeviceTagIsEmphasized(BusDeviceTagKind.Online))
        assertFalse(busDeviceTagIsEmphasized(BusDeviceTagKind.Offline))
    }

    // ---------- 设备行副行文案 ----------

    @Test
    fun subtitleCountsWhenOnlineAndLastSeenWhenOffline() {
        assertEquals("待处理 3 · 运行中 2", busDeviceRowSubtitle(device(online = true), pendingCount = 3, runningCount = 2))
        assertEquals(
            "离线 · 上次同步 10-29 08:30",
            busDeviceRowSubtitle(device(online = false), pendingCount = 1, runningCount = 0),
        )
        assertEquals("在设置里添加电脑", busDeviceRowSubtitle(null, pendingCount = 0, runningCount = 0))
    }

    @Test
    fun onlyPairedDeviceRowIsSelectable() {
        assertTrue(busDeviceRowIsSelectable(device(online = true)))
        assertFalse(busDeviceRowIsSelectable(null))
    }

    // ---------- 主题到面板配色的映射 ----------

    @Test
    fun lightThemeUsesBaselinePaletteAsIs() {
        assertSame(BusHomeTokens.light, busSheetPalette(DshDefaultLightTokens))
        assertEquals(BusHomeTokens.selectedForeground, BusHomeTokens.light.onSelected)
    }

    @Test
    fun darkThemeKeepsStructureAndMarksIsDark() {
        val dark = busSheetPalette(DshDefaultDarkTokens)
        assertTrue(dark.isDark)
        assertEquals(DshDefaultDarkTokens.labelPrimary, dark.textPrimary)
        assertEquals(DshDefaultDarkTokens.labelTertiary, dark.textTertiary)
        // 深色下面板底色取模块底，不会把 v86 的浅色底直接盖上去
        assertEquals(DshDefaultDarkTokens.bgModulePlatform, dark.sheet)
        // 深色 selectedFill 翻白，前景必须翻黑，避免白底白字
        assertEquals(DshDefaultDarkTokens.labelPrimaryInverted, dark.onSelected)
        assertNotEquals(dark.selectedFill, dark.onSelected)
    }

    @Test
    fun rowFillSwitchesBetweenBaselineWhiteAndSurface() {
        val light = BusHomeTokens.light
        val dark = busSheetPalette(DshDefaultDarkTokens)
        // 浅色就是基线的纯白行卡；深色退到面板 surface，不贴纯白
        assertEquals(BusHomeTokens.cardFill, busSheetRowFill(light))
        assertEquals(dark.surface, busSheetRowFill(dark))
        assertEquals(BusHomeTokens.field, busSheetIconDiscFill(light))
        assertEquals(dark.chipFill, busSheetIconDiscFill(dark))
    }

    private fun device(online: Boolean): BusHomeDevice = BusHomeDevice(
        agentId = "preview-host",
        name = "Mac mini",
        platform = "macos",
        online = online,
        platformAsset = "platform-apple.svg",
        lastSeenAt = "2026-10-29T08:30:00Z",
        appVersion = "1.2.0",
    )
}
