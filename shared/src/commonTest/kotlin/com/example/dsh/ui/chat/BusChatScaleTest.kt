package com.example.dsh.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 390 设计稿的等比缩放断言
 *
 * 411dp 的机器（本机 1080×2400 / 420dpi）应该得到 1.054 倍，
 * 窄屏和宽屏夹在 0.90–1.14，避免小屏点不中、大屏字过大
 */
class BusChatScaleTest {

    @Test
    fun scaleFollowsPageWidth() {
        BusChatTokens.updateDesignScale(390f)
        assertEquals(14f, BusChatTokens.pageSide, 0.001f)
        assertEquals(44f, BusChatTokens.capsuleHeight, 0.001f)

        BusChatTokens.updateDesignScale(411f)
        assertEquals(14.75f, BusChatTokens.pageSide, 0.02f)
        assertEquals(46.36f, BusChatTokens.capsuleHeight, 0.02f)
    }

    @Test
    fun scaleIsClamped() {
        BusChatTokens.updateDesignScale(320f)
        assertEquals(12.6f, BusChatTokens.pageSide, 0.001f)

        BusChatTokens.updateDesignScale(800f)
        assertEquals(15.96f, BusChatTokens.pageSide, 0.001f)

        BusChatTokens.updateDesignScale(0f)
        assertEquals(14f, BusChatTokens.pageSide, 0.001f)

        // 全局系数在测试之间复位，避免污染其它断言
        BusChatTokens.updateDesignScale(390f)
    }
}
