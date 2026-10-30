package com.example.dsh.ui.connection

import com.example.dsh.base.BasePager
import com.example.dsh.base.BridgeModule
import com.example.dsh.base.bridgeModule
import com.example.dsh.connection.DshEngineModule
import com.example.dsh.connection.DshSshConfig
import com.example.dsh.connection.DshSshPhase
import com.example.dsh.connection.supportsSshBridge
import com.example.dsh.host.DshConnectionMode
import com.example.dsh.host.DshHostClient
import com.example.dsh.host.DshHostConnection
import com.example.dsh.host.DshRemoteHostRepository
import com.example.dsh.host.DshWebSocketModule
import com.example.dsh.log.DshLogService
import com.example.dsh.log.DshStreamLog
import com.example.dsh.log.LogLevel
import com.example.dsh.session.DshRemoteProfile
import com.example.dsh.session.DshSessionScope
import com.example.dsh.storage.DshLocalStore
import com.example.dsh.storage.createDshLocalStore
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * SSH 连接配置页（对齐 M2 SSH 连接配置页 v5/v4）。
 *
 * 从登录页的“用 SSH 连接”进入；左上角裸返回箭头复用 chevron-left.svg，
 * 与后续连接页保持同一套返回语言。
 */
@Page("ssh_setup")
internal class DshSshSetupPage : BasePager() {
    private var host by observable("")
    private var user by observable("")
    private var sshPort by observable("22")
    private var dshPort by observable("3080")
    private var keyId by observable("")
    private var keyLabel by observable("未导入 SSH 私钥")
    private var busy by observable(false)
    private var error by observable("")
    private var fingerprintPending by observable("")
    private var hostFingerprint = ""
    private var store: DshLocalStore? = null
    private var probeRepository: DshHostClient? = null
    private var databaseDir = ""
    private var alive = true

    override fun created() {
        super.created()
        getBackPressHandler().addCallback(object : BackPressCallback() {
            override fun handleOnBackPressed() {
                acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
            }
        })
        databaseDir = pageData.params.optString("databaseDir")
        runCatching { DshLogService.ensureStarted(databaseDir) }
        store = if (databaseDir.isEmpty()) {
            null
        } else {
            runCatching { createDshLocalStore("$databaseDir/dsh.db", null) }.getOrNull()
        }
        val profile = runCatching { store?.loadRemoteProfile() }.getOrNull()
        host = profile?.host.orEmpty()
        user = profile?.username.orEmpty()
        sshPort = profile?.sshPort?.toString() ?: "22"
        dshPort = profile?.remoteDshPort?.toString() ?: "3080"
        keyId = profile?.keyId.orEmpty()
        hostFingerprint = profile?.hostFingerprint.orEmpty()
        keyLabel = if (keyId.isEmpty()) "未导入 SSH 私钥" else "已导入 SSH 私钥"
        syncNavigationBarColor()
    }

    private fun syncNavigationBarColor() {
        val p = sshPalette(themeColors.isDark)
        runCatching {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
                .setNavigationBarColor(p.bgArgb, themeColors.isDark)
        }
    }

    override fun onThemeSynced() {
        syncNavigationBarColor()
    }

    override fun pageWillDestroy() {
        alive = false
        runCatching { probeRepository?.stop() }
        probeRepository = null
        runCatching {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
                .setNavigationBarColor(0x00000000L, themeColors.isDark)
        }
        super.pageWillDestroy()
    }

    private fun pickKey() {
        if (busy) return
        busy = true
        bridgeModule.pickSshKey { uri ->
            if (uri.isEmpty()) {
                busy = false
                return@pickSshKey
            }
            bridgeModule.importSshKey(uri) { imported ->
                setTimeout(pagerId, 0) {
                    busy = false
                    if (imported.isEmpty()) {
                        error = "无法导入 SSH 私钥"
                    } else {
                        keyId = imported
                        keyLabel = "已导入 SSH 私钥"
                        error = ""
                    }
                }
            }
        }
    }

    private fun connect() {
        if (busy) return
        fingerprintPending = ""
        if (!pageData.supportsSshBridge) {
            error = "远程 SSH 模式目前仅支持 Android 和 iOS"
            return
        }
        val ssh = sshPort.toIntOrNull()
        val dsh = dshPort.toIntOrNull()
        when {
            host.isBlank() -> error = "请输入 SSH 主机地址"
            user.isBlank() -> error = "请输入 SSH 用户名"
            ssh == null || ssh !in 1..65535 -> error = "SSH 端口无效"
            dsh == null || dsh !in 1..65535 -> error = "远程 DSH 端口无效"
            keyId.isBlank() -> error = "请先导入 SSH 私钥"
            else -> {
                busy = true
                error = ""
                bridgeModule.validateSshKey(keyId) { valid ->
                    setTimeout(pagerId, 0) {
                        if (!valid) {
                            busy = false
                            error = "SSH 私钥不存在或格式无法识别"
                            return@setTimeout
                        }
                        val profile = currentProfile(ssh, dsh)
                        runCatching {
                            store?.saveRemoteProfile(profile)
                            store?.saveLastConnectionMode(DshConnectionMode.SSH)
                        }
                        probeRemote(profile)
                    }
                }
            }
        }
    }

    private fun currentProfile(ssh: Int, dsh: Int) = DshRemoteProfile(
        host = host.trim(),
        sshPort = ssh,
        username = user.trim(),
        remoteDshPort = dsh,
        keyId = keyId,
        hostFingerprint = hostFingerprint,
    )

    private fun probeRemote(profile: DshRemoteProfile) {
        val module = acquireModule<DshEngineModule>(DshEngineModule.MODULE_NAME)
        busy = true
        error = "正在连接 SSH 并检查远程 DSH"
        module.startSsh(
            DshSshConfig(
                host = profile.host,
                port = profile.sshPort,
                username = profile.username,
                remoteDshPort = profile.remoteDshPort,
                keyId = profile.keyId,
                hostFingerprint = profile.hostFingerprint,
            ),
        ) { state ->
            logConnectionProbe(state.phase.name, state.message)
            when (state.phase) {
                DshSshPhase.FINGERPRINT_REQUIRED -> {
                    busy = false
                    fingerprintPending = state.message
                    error = "首次连接需要确认主机指纹：${state.message}"
                }
                DshSshPhase.READY -> {
                    val repository = DshRemoteHostRepository(
                        network = acquireModule(NetworkModule.MODULE_NAME),
                        webSocket = acquireModule(DshWebSocketModule.MODULE_NAME),
                        connection = DshHostConnection("http://127.0.0.1:${state.localPort}"),
                        pagerId = pagerId,
                    )
                    probeRepository = repository
                    repository.loadSessions({
                        if (!alive) return@loadSessions
                        setTimeout(pagerId, 0) {
                            busy = false
                            error = ""
                            fingerprintPending = ""
                            probeRepository?.stop()
                            probeRepository = null
                            // 保留 SSH 隧道交给主页复用。
                            module.detachSsh()
                            openHome()
                        }
                    }, { message ->
                        setTimeout(pagerId, 0) {
                            busy = false
                            error = "远程 DSH 不可用：$message"
                            probeRepository?.stop()
                            probeRepository = null
                            module.stopSsh()
                        }
                    })
                }
                DshSshPhase.ERROR -> {
                    busy = false
                    error = state.message.ifEmpty { "SSH 连接失败" }
                    module.stopSsh()
                }
                else -> error = state.message.ifEmpty { "正在连接 SSH" }
            }
        }
    }

    private fun trustFingerprint() {
        val fingerprint = fingerprintPending
        if (fingerprint.isBlank()) return
        val ssh = sshPort.toIntOrNull() ?: 22
        val dsh = dshPort.toIntOrNull() ?: 3080
        hostFingerprint = fingerprint
        runCatching {
            acquireModule<DshEngineModule>(DshEngineModule.MODULE_NAME).trustSshFingerprint(fingerprint)
        }
        val profile = currentProfile(ssh, dsh)
        runCatching { store?.saveRemoteProfile(profile) }
        fingerprintPending = ""
        error = ""
        probeRemote(profile)
    }

    private fun openHome() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage("home", JSONObject().apply {
            put("pageName", "home")
            put("connectionMode", "ssh")
            put("profileId", DshSessionScope.DEFAULT_REMOTE_PROFILE_ID)
            put("databaseDir", databaseDir)
        })
    }

    private fun logConnectionProbe(phase: String, message: String) {
        val type = when (phase) {
            "ERROR" -> "connect.error"
            "READY" -> "connect.ready"
            "RECONNECTING" -> "connect.reconnecting"
            "STOPPED" -> "connect.stopped"
            else -> "connect.connecting"
        }
        DshStreamLog.log(
            if (phase == "ERROR") LogLevel.ERROR else LogLevel.INFO,
            type,
            "mode=ssh phase=$phase $message",
        )
    }

    override fun body(): ViewBuilder {
        val ctx = this
        val p = sshPalette(themeColors.isDark)
        val w = pagerData.pageViewWidth
        return {
            View {
                attr {
                    size(w, pagerData.pageViewHeight)
                    backgroundLinearGradient(
                        Direction.TO_BOTTOM,
                        ColorStop(p.bgTop, 0f),
                        ColorStop(p.bgMid, 0.48f),
                        ColorStop(p.bgBottom, 1f),
                    )
                }

                // 流光光斑
                Image {
                    attr {
                        src(ImageUri.commonAssets("glow.svg"))
                        positionAbsolute()
                        left(w - ctx.x(160f))
                        top(-ctx.y(90f))
                        size(ctx.x(280f), ctx.x(280f))
                        tintColor(p.glowA)
                        opacity(p.glowAOpacity)
                    }
                }
                Image {
                    attr {
                        src(ImageUri.commonAssets("glow.svg"))
                        positionAbsolute()
                        left(-ctx.x(90f))
                        top(ctx.y(540f))
                        size(ctx.x(300f), ctx.x(300f))
                        tintColor(p.glowB)
                        opacity(p.glowBOpacity)
                    }
                }

                // 返回箭头
                View {
                    attr {
                        positionAbsolute()
                        // 让 chevron 的可见左缘与下方标题/表单的 24dp 起始线对齐；
                        // chevron-left 的路径在 22dp 图标内有约 12dp 视觉留白。
                        left(ctx.x(12f))
                        top(ctx.y(50f))
                        size(ctx.x(32f), ctx.x(32f))
                        alignItemsCenter()
                        justifyContentCenter()
                    }
                    event { click { this@DshSshSetupPage.acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage() } }
                    Image {
                        attr {
                            src(ImageUri.commonAssets("chevron-left.svg"))
                            size(ctx.x(22f), ctx.x(22f))
                            tintColor(p.text)
                        }
                    }
                }

                // 标题
                Text {
                    attr {
                        positionAbsolute()
                        left(ctx.x(24f))
                        top(ctx.y(100f))
                        width(w - ctx.x(48f))
                        text("SSH 连接")
                        fontSize(ctx.x(28f))
                        fontWeightBold()
                        color(p.text)
                    }
                }
                Text {
                    attr {
                        positionAbsolute()
                        left(ctx.x(24f))
                        top(ctx.y(140f))
                        width(w - ctx.x(48f))
                        text("填写设备配置，建立安全连接")
                        fontSize(ctx.x(13f))
                        color(p.secondary)
                    }
                }

                // 表单
                View {
                    attr {
                        positionAbsolute()
                        left(ctx.x(24f))
                        top(ctx.y(185f))
                        width(w - ctx.x(48f))
                        flexDirectionColumn()
                    }
                    SshUnderlineInput(
                        value = { ctx.host },
                        hint = "SSH 主机（例如 Tailscale IP 或域名）",
                        p = p,
                        onChange = { ctx.host = it; ctx.error = "" },
                    )
                    View { attr { height(ctx.y(22f)) } }
                    SshUnderlineInput(
                        value = { ctx.user },
                        hint = "SSH 用户名（例如 alex）",
                        p = p,
                        onChange = { ctx.user = it; ctx.error = "" },
                    )
                    View { attr { height(ctx.y(22f)) } }
                    View {
                        attr { flexDirectionRow() }
                        View {
                            attr {
                                flex(1f)
                                marginRight(ctx.x(28f))
                                flexDirectionColumn()
                            }
                            SshUnderlineInput(
                                value = { ctx.sshPort },
                                hint = "22",
                                p = p,
                                onChange = { ctx.sshPort = it; ctx.error = "" },
                            )
                            Text {
                                attr {
                                    text("SSH 端口")
                                    marginTop(ctx.x(6f))
                                    fontSize(ctx.x(11f))
                                    fontWeightMedium()
                                    color(p.tertiary)
                                }
                            }
                        }
                        View {
                            attr { flex(1f); flexDirectionColumn() }
                            SshUnderlineInput(
                                value = { ctx.dshPort },
                                hint = "3080",
                                p = p,
                                onChange = { ctx.dshPort = it; ctx.error = "" },
                            )
                            Text {
                                attr {
                                    text("远程 DSH 端口")
                                    marginTop(ctx.x(6f))
                                    fontSize(ctx.x(11f))
                                    fontWeightMedium()
                                    color(p.tertiary)
                                }
                            }
                        }
                    }
                    View { attr { height(ctx.y(24f)) } }

                    // 私钥行
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            paddingTop(ctx.x(12f))
                            paddingBottom(ctx.x(12f))
                            borderTop(Border(1f, BorderStyle.SOLID, p.divider))
                        }
                        View {
                            attr {
                                flex(1f)
                                flexDirectionRow()
                                alignItemsCenter()
                            }
                            Image {
                                attr {
                                    src(ImageUri.commonAssets("lock.svg"))
                                    size(ctx.x(17f), ctx.x(17f))
                                    tintColor(p.accentIcon)
                                }
                            }
                            Text {
                                attr {
                                    text(ctx.keyLabel)
                                    fontSize(ctx.x(13f))
                                    fontWeightMedium()
                                    color(p.secondary)
                                    marginLeft(ctx.x(8f))
                                }
                            }
                        }
                        View {
                            attr {
                                height(ctx.x(32f))
                                paddingLeft(ctx.x(18f))
                                paddingRight(ctx.x(18f))
                                borderRadius(ctx.x(16f))
                                backgroundColor(p.primaryButtonBg)
                                alignItemsCenter()
                                justifyContentCenter()
                            }
                            event { click { ctx.pickKey() } }
                            Text {
                                attr {
                                    text(if (ctx.busy) "导入中..." else "导入私钥")
                                    fontSize(ctx.x(12f))
                                    fontWeightBold()
                                    color(p.primaryButtonText)
                                }
                            }
                        }
                    }
                }

                // 连接按钮
                View {
                    attr {
                        positionAbsolute()
                        left(ctx.x(24f))
                        top(ctx.y(507f))
                        width(w - ctx.x(48f))
                        height(ctx.x(48f))
                        borderRadius(ctx.x(16f))
                        backgroundColor(p.primaryButtonBg)
                        opacity(if (ctx.busy) 0.72f else 1f)
                        alignItemsCenter()
                        justifyContentCenter()
                    }
                    event { click { ctx.connect() } }
                    Text {
                        attr {
                            text(if (ctx.busy) "正在连接…" else "连接")
                            fontSize(ctx.x(16f))
                            fontWeightBold()
                            color(p.primaryButtonText)
                        }
                    }
                }

                vif({ ctx.error.isNotEmpty() }) {
                    Text {
                        attr {
                            positionAbsolute()
                            left(ctx.x(24f))
                            top(ctx.y(567f))
                            width(w - ctx.x(48f))
                            text(ctx.error)
                            fontSize(ctx.x(12f))
                            color(Color(0xFFD0554F))
                            textAlignCenter()
                        }
                    }
                }
                vif({ ctx.fingerprintPending.isNotEmpty() }) {
                    Text {
                        attr {
                            positionAbsolute()
                            left(ctx.x(24f))
                            top(ctx.y(597f))
                            width(w - ctx.x(48f))
                            text("确认并继续使用此 SSH 主机指纹")
                            fontSize(ctx.x(13f))
                            color(p.accent)
                            textAlignCenter()
                        }
                        event { click { ctx.trustFingerprint() } }
                    }
                }
            }
        }
    }

    private fun x(p: Float): Float = pagerData.pageViewWidth * p / 375f

    private fun y(p: Float): Float =
        (pagerData.pageViewHeight + pagerData.safeAreaInsets.bottom) * p / 812f
}

private fun ViewContainer<*, *>.SshUnderlineInput(
    value: () -> String,
    hint: String,
    p: SshPalette,
    onChange: (String) -> Unit,
) {
    View {
        attr { flexDirectionColumn() }
        View {
            attr {
                height(p.inputHeight)
                flexDirectionRow()
            }
            Input {
                ref { it.view?.setText(value()) }
                attr {
                    flex(1f)
                    fontSize(p.inputFontSize)
                    color(p.text)
                    placeholder(hint)
                    placeholderColor(p.tertiary)
                    returnKeyTypeDone()
                }
                event { textDidChange { onChange(it.text) } }
            }
        }
        View {
            attr {
                height(1f)
                backgroundColor(p.underline)
            }
        }
    }
}

private class SshPalette(
    val bgArgb: Long,
    val bgTop: Color,
    val bgMid: Color,
    val bgBottom: Color,
    val text: Color,
    val secondary: Color,
    val tertiary: Color,
    val underline: Color,
    val divider: Color,
    val accent: Color,
    val accentIcon: Color,
    val primaryButtonBg: Color,
    val primaryButtonText: Color,
    val glowA: Color,
    val glowB: Color,
    val glowAOpacity: Float,
    val glowBOpacity: Float,
    val inputHeight: Float,
    val inputFontSize: Float,
)

private fun sshPalette(isDark: Boolean): SshPalette = if (isDark) {
    SshPalette(
        bgArgb = 0xFF0E1119,
        bgTop = Color(0xFF0E1119),
        bgMid = Color(0xFF1A1F30),
        bgBottom = Color(0xFF242B45),
        text = Color(0xFFFFFFFF),
        secondary = Color(0xB8FFFFFF),
        tertiary = Color(0x66FFFFFF),
        underline = Color(0x33FFFFFF),
        divider = Color(0x14FFFFFF),
        accent = Color(0xFF6B5BFF),
        accentIcon = Color(0xFFB39BFF),
        primaryButtonBg = Color(0xFFFFFFFF),
        primaryButtonText = Color(0xFF241D4A),
        glowA = Color(0xFF9B5CFF),
        glowB = Color(0xFF6B5BFF),
        glowAOpacity = 0.22f,
        glowBOpacity = 0.14f,
        inputHeight = 52f,
        inputFontSize = 15f,
    )
} else {
    SshPalette(
        bgArgb = 0xFFF6F6FA,
        bgTop = Color(0xFFF6F6FA),
        bgMid = Color(0xFFFAFAFA),
        bgBottom = Color(0xFFFFFFFF),
        text = Color(0xFF1D1B20),
        secondary = Color(0xFF55555E),
        tertiary = Color(0xFFA9AAB3),
        underline = Color(0xFFECECF0),
        divider = Color(0xFFF2F2F5),
        accent = Color(0xFF6B5BFF),
        accentIcon = Color(0xFF6B5BFF),
        primaryButtonBg = Color(0xFF1D1B20),
        primaryButtonText = Color(0xFFFFFFFF),
        glowA = Color(0xFF6B5BFF),
        glowB = Color(0xFF9B5CFF),
        glowAOpacity = 0.16f,
        glowBOpacity = 0.10f,
        inputHeight = 52f,
        inputFontSize = 15f,
    )
}
