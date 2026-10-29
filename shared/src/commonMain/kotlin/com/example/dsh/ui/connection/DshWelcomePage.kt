package com.example.dsh.ui.connection

import com.example.dsh.base.BasePager
import com.example.dsh.base.BridgeModule
import com.example.dsh.connection.DshRelayModule
import com.example.dsh.connection.supportsRelayBridge
import com.example.dsh.host.DshConnectionMode
import com.example.dsh.session.DshRelayProfile
import com.example.dsh.storage.DshLocalStore
import com.example.dsh.storage.createDshLocalStore
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 首次启动/未配对时的欢迎页。
 *
 * 已配对用户不在这里停留：created() 发现本地有 Relay/SSH profile 后直接进入连接页，
 * 由连接页完成自动探测；未配对用户看到品牌页，扫码或走 SSH。
 */
@Page("welcome")
internal class DshWelcomePage : BasePager() {
    private var busy by observable(false)
    private var error by observable("")
    private var typed by observable(0)
    private var cursorOn by observable(true)
    private var glowA by observable(false)
    private var glowB by observable(false)
    private var glowC by observable(false)
    private var glowD by observable(false)
    private var store: DshLocalStore? = null
    private val tagline = "随时随地，掌控自如"

    override fun created() {
        super.created()
        val databaseDir = pageData.params.optString("databaseDir")
        store = if (databaseDir.isEmpty()) {
            null
        } else {
            runCatching { createDshLocalStore("$databaseDir/dsh.db", null) }.getOrNull()
        }
        val paired = runCatching {
            store?.loadRelayProfile() != null || store?.loadRemoteProfile() != null
        }.getOrDefault(false)
        if (paired) {
            setTimeout(pagerId, 50) { openConnectionSetup() }
        } else {
            setTimeout(pagerId, 50) { syncNavigationBarColor() }
            setTimeout(pagerId, 1200) { startTypewriter() }
            startCursorBlink()
            startGlowLoop({ glowA = !glowA }, 7000)
            startGlowLoop({ glowB = !glowB }, 9000)
            startGlowLoop({ glowC = !glowC }, 11000)
            startGlowLoop({ glowD = !glowD }, 13000)
        }
    }

    private fun syncNavigationBarColor() {
        val c = loginPalette(themeColors.isDark)
        runCatching {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
                .setNavigationBarColor(c.bgArgb, themeColors.isDark)
        }
    }

    override fun pageWillDestroy() {
        runCatching {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
                .setNavigationBarColor(0x00000000L, themeColors.isDark)
        }
        super.pageWillDestroy()
    }

    private fun startTypewriter() {
        typed = 0
        setTimeout(pagerId, 400) { typeNext() }
    }

    private fun typeNext() {
        if (typed < tagline.length) {
            typed += 1
            setTimeout(pagerId, 220) { typeNext() }
        } else {
            setTimeout(pagerId, 1800) { deleteNext() }
        }
    }

    private fun deleteNext() {
        if (typed > 0) {
            typed -= 1
            setTimeout(pagerId, 110) { deleteNext() }
        } else {
            setTimeout(pagerId, 600) { typeNext() }
        }
    }

    private fun startCursorBlink() {
        setTimeout(pagerId, 530) {
            cursorOn = !cursorOn
            startCursorBlink()
        }
    }

    private fun startGlowLoop(toggle: () -> Unit, periodMs: Int) {
        setTimeout(pagerId, periodMs) {
            toggle()
            startGlowLoop(toggle, periodMs)
        }
    }

    private fun openConnectionSetup(initialMode: String? = null, skipAutoConnect: Boolean = false) {
        runCatching {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
                .setNavigationBarColor(0x00000000L, themeColors.isDark)
        }
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            "connection_setup",
            JSONObject().apply {
                put("pageName", "connection_setup")
                put("databaseDir", pageData.params.optString("databaseDir"))
                initialMode?.let { put("initialMode", it) }
                if (skipAutoConnect) put("skipAutoConnect", true)
            },
        )
    }

    private fun scan() {
        if (busy) return
        if (!pageData.supportsRelayBridge) {
            error = "扫码连接目前仅支持 Android、iOS 和 HarmonyOS"
            return
        }
        busy = true
        error = ""
        acquireModule<DshRelayModule>(DshRelayModule.MODULE_NAME).scanAndPair { result ->
            setTimeout(pagerId, 0) {
                busy = false
                if (!result.ok) {
                    error = result.message.ifEmpty { "扫码配对失败" }
                    return@setTimeout
                }
                runCatching {
                    store?.saveRelayProfile(
                        DshRelayProfile(
                            result.hostId,
                            result.hostName.ifEmpty { "电脑" },
                            result.relayOrigin,
                            result.pairedAt,
                        ),
                    )
                    store?.saveLastConnectionMode(DshConnectionMode.RELAY)
                }
                openConnectionSetup()
            }
        }
    }

    override fun body(): ViewBuilder {
        val ctx = this
        val c = loginPalette(themeColors.isDark)
        val w = pagerData.pageViewWidth
        val h = pagerData.pageViewHeight + pagerData.safeAreaInsets.bottom
        fun x(p: Float) = w * p / 375f
        fun y(p: Float) = h * p / 812f
        return {
            View {
                attr {
                    size(w, pagerData.pageViewHeight)
                    backgroundColor(c.bg)
                }

                // 流光：4 个径向光斑，呼吸 + 缓慢位移
                LoginGlow(c.glowTint, { ctx.glowA }, 7f, leftDp = -x(90f), topDp = -y(140f), sizeDp = x(420f), onOpacity = 0.6f, offOpacity = 0.3f, dx = 0.05f, dy = 0.03f)
                LoginGlow(c.glowTint, { ctx.glowB }, 9f, rightDp = -x(100f), topDp = y(180f), sizeDp = x(360f), onOpacity = 0.5f, offOpacity = 0.25f, dx = 0.04f, dy = 0.03f)
                LoginGlow(c.glowTint, { ctx.glowC }, 11f, leftDp = -x(80f), bottomDp = y(60f), sizeDp = x(320f), onOpacity = 0.45f, offOpacity = 0.2f, dx = 0.03f, dy = 0.04f)
                LoginGlow(c.glowTint, { ctx.glowD }, 13f, rightDp = -x(60f), topDp = -y(80f), sizeDp = x(280f), onOpacity = 0.5f, offOpacity = 0.22f, dx = 0.05f, dy = 0.02f)

                // 品牌区：按 HTML 375×812 的比例绝对定位
                Image {
                    attr {
                        src(ImageUri.commonAssets("kuik-logo.svg"))
                        positionAbsolute()
                        left((w - x(60f)) / 2f)
                        top(y(224f))
                        size(x(60f), x(60f))
                        tintColor(c.text)
                    }
                }
                View {
                    attr {
                        positionAbsolute()
                        left(0f)
                        top(y(316f))
                        width(w)
                        height(y(35f))
                        alignItemsCenter()
                        justifyContentCenter()
                    }
                    Text {
                        attr {
                            text("KuikAgentBus")
                            fontSize(x(24f))
                            fontWeightBold()
                            color(c.text)
                        }
                    }
                }
                View {
                    attr {
                        positionAbsolute()
                        left(0f)
                        top(y(361f))
                        width(w)
                        height(y(20f))
                        flexDirectionRow()
                        alignItemsCenter()
                        justifyContentCenter()
                    }
                    Text {
                        attr {
                            text(ctx.tagline.take(ctx.typed))
                            fontSize(x(14f))
                            color(c.muted)
                        }
                    }
                    View {
                        attr {
                            width(x(2f))
                            height(x(15f))
                            marginLeft(x(3f))
                            backgroundColor(c.text)
                            opacity(if (ctx.cursorOn) 1f else 0f)
                        }
                    }
                }

                // 主按钮
                View {
                    attr {
                        positionAbsolute()
                        left((w - x(335f)) / 2f)
                        top(y(409f))
                        width(x(335f))
                        height(y(50f))
                        borderRadius(x(16f))
                        backgroundColor(c.primaryBg)
                        flexDirectionRow()
                        alignItemsCenter()
                        justifyContentCenter()
                    }
                    event { click { ctx.scan() } }
                    Image {
                        attr {
                            src(ImageUri.commonAssets("camera.svg"))
                            size(x(19f), x(19f))
                            tintColor(c.primaryText)
                            marginRight(x(8f))
                        }
                    }
                    Text {
                        attr {
                            text(if (ctx.busy) "正在扫码..." else "扫码添加电脑")
                            fontSize(x(16f))
                            fontWeightBold()
                            color(c.primaryText)
                        }
                    }
                }

                // 次按钮
                View {
                    attr {
                        positionAbsolute()
                        left((w - x(335f)) / 2f)
                        top(y(471f))
                        width(x(335f))
                        height(y(46f))
                        borderRadius(x(16f))
                        backgroundColor(c.secondaryBg)
                        border(Border(1f, BorderStyle.SOLID, c.secondaryBorder))
                        flexDirectionRow()
                        alignItemsCenter()
                        justifyContentCenter()
                    }
                    event { click { ctx.openConnectionSetup("ssh", skipAutoConnect = true) } }
                    Image {
                        attr {
                            src(ImageUri.commonAssets("tool-terminal.svg"))
                            size(x(16f), x(16f))
                            tintColor(c.text)
                            marginRight(x(7f))
                        }
                    }
                    Text {
                        attr {
                            text("通过 SSH 连接")
                            fontSize(x(14f))
                            fontWeightMedium()
                            color(c.text)
                        }
                    }
                }

                vif({ ctx.error.isNotEmpty() }) {
                    Text {
                        attr {
                            positionAbsolute()
                            left(x(20f))
                            top(y(560f))
                            width(x(335f))
                            fontSize(x(12f))
                            color(Color(0xFFD0554F))
                        }
                    }
                }

                // 查看引导
                View {
                    attr {
                        positionAbsolute()
                        left(0f)
                        top(y(529f))
                        width(w)
                        alignItemsCenter()
                    }
                    Text {
                        attr {
                            text("查看引导")
                            fontSize(x(13f))
                            color(c.quiet)
                        }
                    }
                }

                // 底部安全行
                View {
                    attr {
                        positionAbsolute()
                        left(0f)
                        top(y(736f))
                        width(w)
                        height(y(52f))
                        flexDirectionRow()
                        alignItemsCenter()
                        justifyContentCenter()
                    }
                    Image {
                        attr {
                            src(ImageUri.commonAssets("shield.svg"))
                            size(x(12f), x(12f))
                            tintColor(c.quiet)
                            marginRight(x(5f))
                        }
                    }
                    Text {
                        attr {
                            text("数据端到端加密")
                            fontSize(x(11f))
                            color(c.quiet)
                            marginRight(x(16f))
                        }
                    }
                    Image {
                        attr {
                            src(ImageUri.commonAssets("icon-link16.svg"))
                            size(x(12f), x(12f))
                            tintColor(c.quiet)
                            marginRight(x(5f))
                        }
                    }
                    Text {
                        attr {
                            text("只有你的手机和电脑能解密")
                            fontSize(x(11f))
                            color(c.quiet)
                        }
                    }
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.LoginGlow(
    tint: Color,
    flag: () -> Boolean,
    duration: Float,
    leftDp: Float? = null,
    rightDp: Float? = null,
    topDp: Float? = null,
    bottomDp: Float? = null,
    sizeDp: Float,
    onOpacity: Float,
    offOpacity: Float,
    dx: Float,
    dy: Float,
) {
    View {
        attr {
            positionAbsolute()
            leftDp?.let { left(it) }
            rightDp?.let { right(it) }
            topDp?.let { top(it) }
            bottomDp?.let { bottom(it) }
            size(sizeDp, sizeDp)
            opacity(if (flag()) onOpacity else offOpacity)
            transform(translate = Translate(if (flag()) dx else -dx, if (flag()) dy else -dy))
            animate(Animation.easeInOut(duration), flag())
        }
        Image {
            attr {
                src(ImageUri.commonAssets("glow.svg"))
                size(sizeDp, sizeDp)
                tintColor(tint)
            }
        }
    }
}

private class LoginPalette(
    val bgArgb: Long,
    val bg: Color,
    val glowTint: Color,
    val text: Color,
    val muted: Color,
    val primaryBg: Color,
    val primaryText: Color,
    val secondaryBg: Color,
    val secondaryBorder: Color,
    val quiet: Color,
)

private fun loginPalette(isDark: Boolean): LoginPalette = if (isDark) {
    LoginPalette(
        bgArgb = 0xFF16122E,
        bg = Color(0xFF16122E),
        glowTint = Color(0xFF7A8CFF),
        text = Color(0xFFFFFFFF),
        muted = Color(0xEBFFFFFF),
        primaryBg = Color(0xFFFFFFFF),
        primaryText = Color(0xFF241D4A),
        secondaryBg = Color(0x1AFFFFFF),
        secondaryBorder = Color(0x29FFFFFF),
        quiet = Color(0x9EFFFFFF),
    )
} else {
    LoginPalette(
        bgArgb = 0xFFF5F3FB,
        bg = Color(0xFFF5F3FB),
        glowTint = Color(0xFFC9C4F0),
        text = Color(0xFF1D1B20),
        muted = Color(0xC71D1B20),
        primaryBg = Color(0xFF1D1B20),
        primaryText = Color(0xFFFFFFFF),
        secondaryBg = Color(0x0B1D1B20),
        secondaryBorder = Color(0x241D1B20),
        quiet = Color(0xFF8A8A90),
    )
}
