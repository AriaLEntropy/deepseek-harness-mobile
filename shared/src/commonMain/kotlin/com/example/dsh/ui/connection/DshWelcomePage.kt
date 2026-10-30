package com.example.dsh.ui.connection

import com.example.dsh.base.BasePager
import com.example.dsh.base.BridgeModule
import com.example.dsh.connection.DshEngineModule
import com.example.dsh.connection.DshRelayModule
import com.example.dsh.connection.DshRelayPhase
import com.example.dsh.connection.DshRelayState
import com.example.dsh.connection.DshSshConfig
import com.example.dsh.connection.DshSshPhase
import com.example.dsh.connection.supportsRelayBridge
import com.example.dsh.connection.supportsSshBridge
import com.example.dsh.host.DshConnectionMode
import com.example.dsh.host.DshHostClient
import com.example.dsh.host.DshHostConnection
import com.example.dsh.host.DshRemoteHostRepository
import com.example.dsh.host.DshWebSocketModule
import com.example.dsh.log.DshLogService
import com.example.dsh.log.DshStreamLog
import com.example.dsh.log.LogLevel
import com.example.dsh.session.DshRelayProfile
import com.example.dsh.session.DshRemoteProfile
import com.example.dsh.session.DshSessionScope
import com.example.dsh.storage.DshLocalStore
import com.example.dsh.storage.createDshLocalStore
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.setTimeout
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 启动入口页。
 *
 * - 没有本地电脑配置：展示登录页（无返回箭头），首次用户可以扫码或走 SSH。
 * - 已经连接过：首屏直接展示一键连接页；点“使用其他连接”切回登录页，
 *   此时登录页左上角才出现返回箭头，点箭头回到一键连接页。
 * - 启动时不再自动连接，避免先闪一下首页再跳旧首页。
 */
@Page("welcome")
internal class DshWelcomePage : BasePager() {
    private var busy by observable(false)
    private var error by observable("")
    private var fingerprintPending by observable("")
    private var typed by observable(0)
    private var cursorOn by observable(true)
    private var glowA by observable(false)
    private var glowB by observable(false)
    private var glowC by observable(false)
    private var glowD by observable(false)
    private var paired by observable(false)
    private var showQuickConnect by observable(false)
    private var canGoBack by observable(false)
    private var deviceName by observable("")
    private var store: DshLocalStore? = null
    private var relayProfile: DshRelayProfile? = null
    private var remoteProfile: DshRemoteProfile? = null
    private var pendingSshProfile: DshRemoteProfile? = null
    private var probeRepository: DshHostClient? = null
    private var databaseDir = ""
    private var alive = true
    private val tagline = "随时随地，掌控自如"

    override fun created() {
        super.created()
        getBackPressHandler().addCallback(object : BackPressCallback() {
            override fun handleOnBackPressed() {
                if (this@DshWelcomePage.canGoBack) {
                    this@DshWelcomePage.backToQuickConnect()
                } else {
                    acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
                }
            }
        })
        databaseDir = pageData.params.optString("databaseDir")
        runCatching { DshLogService.ensureStarted(databaseDir) }
        store = if (databaseDir.isEmpty()) {
            null
        } else {
            runCatching { createDshLocalStore("$databaseDir/dsh.db", null) }.getOrNull()
        }
        reloadProfiles()
        paired = relayProfile != null || remoteProfile != null
        showQuickConnect = paired
        canGoBack = false
        syncNavigationBarColor()
        startTypewriter()
        startCursorBlink()
        startGlowLoop({ glowA = !glowA }, 7000)
        startGlowLoop({ glowB = !glowB }, 9000)
        startGlowLoop({ glowC = !glowC }, 11000)
        startGlowLoop({ glowD = !glowD }, 13000)
    }

    private fun reloadProfiles() {
        relayProfile = runCatching { store?.loadRelayProfile() }.getOrNull()
        remoteProfile = runCatching { store?.loadRemoteProfile() }.getOrNull()
        val preferSsh = runCatching { store?.loadLastConnectionMode() }.getOrNull() == DshConnectionMode.SSH
        deviceName = when {
            preferSsh && remoteProfile != null -> remoteProfile?.host.orEmpty()
            relayProfile != null -> relayProfile?.hostName.orEmpty()
            else -> remoteProfile?.host.orEmpty()
        }
    }

    private fun syncNavigationBarColor() {
        val c = loginPalette(themeColors.isDark)
        runCatching {
            acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
                .setNavigationBarColor(c.bgArgb, themeColors.isDark)
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

    private fun startTypewriter() {
        typed = 0
        setTimeout(pagerId, 400) { typeNext() }
    }

    private fun typeNext() {
        if (!alive) return
        if (typed < tagline.length) {
            typed += 1
            setTimeout(pagerId, 220) { typeNext() }
        } else {
            setTimeout(pagerId, 1800) { deleteNext() }
        }
    }

    private fun deleteNext() {
        if (!alive) return
        if (typed > 0) {
            typed -= 1
            setTimeout(pagerId, 110) { deleteNext() }
        } else {
            setTimeout(pagerId, 600) { typeNext() }
        }
    }

    private fun startCursorBlink() {
        setTimeout(pagerId, 530) {
            if (!alive) return@setTimeout
            cursorOn = !cursorOn
            startCursorBlink()
        }
    }

    private fun startGlowLoop(toggle: () -> Unit, periodMs: Int) {
        setTimeout(pagerId, periodMs) {
            if (!alive) return@setTimeout
            toggle()
            startGlowLoop(toggle, periodMs)
        }
    }

    /** 从一键连接页进入“其他连接方式”：登录页出现返回箭头。 */
    private fun useOtherConnection() {
        showQuickConnect = false
        canGoBack = paired
        error = ""
        fingerprintPending = ""
    }

    /** 登录页左上角返回：回到一键连接页。 */
    private fun backToQuickConnect() {
        if (!paired) return
        showQuickConnect = true
        canGoBack = false
        error = ""
        fingerprintPending = ""
    }

    private fun openSshSetup() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            "ssh_setup",
            JSONObject().apply {
                put("pageName", "ssh_setup")
                put("databaseDir", pageData.params.optString("databaseDir"))
            },
        )
    }

    private fun quickConnect() {
        if (busy) return
        val relay = relayProfile
        val remote = remoteProfile
        error = ""
        fingerprintPending = ""
        val lastMode = runCatching { store?.loadLastConnectionMode() }.getOrNull()
        when {
            lastMode == DshConnectionMode.SSH && remote != null -> probeSsh(remote)
            relay != null -> probeRelay()
            remote != null -> probeSsh(remote)
            else -> {
                paired = false
                showQuickConnect = false
                canGoBack = false
                error = "还没有添加电脑，请选择一种连接方式"
            }
        }
    }

    /** 已配对电脑：建立 Relay 隧道，确认 Host 类型后进入对应首页。 */
    private fun probeRelay() {
        if (!pageData.supportsRelayBridge) {
            error = "扫码连接目前仅支持 Android、iOS 和 HarmonyOS"
            return
        }
        val module = acquireModule<DshRelayModule>(DshRelayModule.MODULE_NAME)
        var settled = false
        var hostCheckStarted = false

        fun fail(message: String) {
            if (settled) return
            settled = true
            DshStreamLog.log(LogLevel.ERROR, "connect.error", "mode=relay phase=probe $message")
            busy = false
            error = message
            probeRepository?.stop()
            probeRepository = null
            module.disconnect()
        }

        // 统一首页的数据来自 /bus/ws 快照，Host 类型 ready 不等于数据 ready。
        // 必须等 snapshot 真正到达再跳，否则会进入“未连接电脑”的空首页。
        fun openAgentHostHome() {
            if (settled) return
            fun awaitSnapshot(attempt: Int) {
                if (settled) return
                val json = runCatching { module.busHomeJson() }.getOrNull().orEmpty()
                if (json.isNotEmpty()) {
                    settled = true
                    setTimeout(pagerId, 0) {
                        busy = false
                        error = ""
                        openHomePage("bus_home", DshConnectionMode.RELAY)
                    }
                } else if (attempt >= SNAPSHOT_WAIT_ATTEMPTS) {
                    fail("已连上电脑，但 Agent Host 没有返回任务数据；请确认电脑端 Agent Host 仍在运行")
                } else {
                    setTimeout(pagerId, SNAPSHOT_POLL_MS) { awaitSnapshot(attempt + 1) }
                }
            }
            awaitSnapshot(0)
        }

        fun startDshProbe(state: DshRelayState) {
            if (settled || probeRepository != null) return
            val repository = DshRemoteHostRepository(
                network = acquireModule(NetworkModule.MODULE_NAME),
                webSocket = acquireModule(DshWebSocketModule.MODULE_NAME),
                connection = DshHostConnection("http://127.0.0.1:${state.localPort}", state.localToken),
                pagerId = pagerId,
            )
            probeRepository = repository
            repository.loadSessions({
                if (settled) return@loadSessions
                settled = true
                setTimeout(pagerId, 0) {
                    busy = false
                    error = ""
                    probeRepository?.stop()
                    probeRepository = null
                    module.disconnect()
                    openHomePage("home", DshConnectionMode.RELAY)
                }
            }, { message ->
                fail("远程 DSH 不可用：$message")
            })
        }

        fun awaitHostKind(state: DshRelayState, attempt: Int) {
            if (settled) return
            when (module.busHostKind()) {
                "agent-host" -> openAgentHostHome()
                "dsh-plugin", "unknown" -> startDshProbe(state)
                else -> {
                    if (attempt >= 15) startDshProbe(state)
                    else setTimeout(pagerId, 200) { awaitHostKind(state, attempt + 1) }
                }
            }
        }

        busy = true
        error = "正在连接已配对电脑"
        setTimeout(pagerId, RELAY_PROBE_TIMEOUT_MS) {
            fail("无法连接到已配对电脑，请确认电脑端 Relay 已启动")
        }
        module.connect { state ->
            if (settled) return@connect
            logConnectionProbe("relay", state.phase.name, state.message)
            when (state.phase) {
                DshRelayPhase.READY -> {
                    if (state.localPort <= 0 || state.localToken.isEmpty()) return@connect
                    if (probeRepository != null || hostCheckStarted) return@connect
                    hostCheckStarted = true
                    awaitHostKind(state, 0)
                }
                DshRelayPhase.ERROR, DshRelayPhase.STOPPED ->
                    fail(state.message.ifEmpty { "无法连接到已配对电脑" })
                else -> Unit
            }
        }
    }

    /** 已保存的 SSH 电脑：建隧道并检查远程 DSH。 */
    private fun probeSsh(profile: DshRemoteProfile) {
        if (!pageData.supportsSshBridge) {
            error = "远程 SSH 模式目前仅支持 Android 和 iOS"
            return
        }
        if (profile.host.isBlank() || profile.username.isBlank() || profile.keyId.isBlank()) {
            error = "SSH 配置不完整，请使用其他连接重新填写"
            return
        }
        pendingSshProfile = profile
        busy = true
        error = "正在连接 SSH 并检查远程 DSH"
        val module = acquireModule<DshEngineModule>(DshEngineModule.MODULE_NAME)
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
            logConnectionProbe("ssh", state.phase.name, state.message)
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
                        setTimeout(pagerId, 0) {
                            busy = false
                            error = ""
                            fingerprintPending = ""
                            probeRepository?.stop()
                            probeRepository = null
                            // 隧道交给主页复用，避免拆掉刚建好的连接再重连。
                            module.detachSsh()
                            openHomePage("home", DshConnectionMode.SSH)
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
        val profile = pendingSshProfile
        if (fingerprint.isBlank() || profile == null) return
        runCatching {
            acquireModule<DshEngineModule>(DshEngineModule.MODULE_NAME).trustSshFingerprint(fingerprint)
        }
        val updated = profile.copy(hostFingerprint = fingerprint)
        remoteProfile = updated
        runCatching { store?.saveRemoteProfile(updated) }
        fingerprintPending = ""
        error = ""
        probeSsh(updated)
    }

    private fun openHomePage(pageName: String, mode: DshConnectionMode) {
        val profileId = if (mode == DshConnectionMode.RELAY) {
            relayProfile?.hostId?.takeIf { it.isNotEmpty() } ?: DshSessionScope.DEFAULT_REMOTE_PROFILE_ID
        } else {
            DshSessionScope.DEFAULT_REMOTE_PROFILE_ID
        }
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(pageName, JSONObject().apply {
            put("pageName", pageName)
            put("connectionMode", if (mode == DshConnectionMode.SSH) "ssh" else "relay")
            put("profileId", profileId)
            put("databaseDir", databaseDir)
        })
    }

    private fun logConnectionProbe(mode: String, phase: String, message: String) {
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
            "mode=$mode phase=$phase $message",
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
                val profile = DshRelayProfile(
                    result.hostId,
                    result.hostName.ifEmpty { "电脑" },
                    result.relayOrigin,
                    result.pairedAt,
                )
                runCatching {
                    store?.saveRelayProfile(profile)
                    store?.saveLastConnectionMode(DshConnectionMode.RELAY)
                }
                relayProfile = profile
                paired = true
                deviceName = profile.hostName
                showQuickConnect = true
                canGoBack = false
                error = ""
            }
        }
    }

    override fun body(): ViewBuilder {
        val ctx = this
        val w = pagerData.pageViewWidth
        return {
            // 颜色在 vbind 内读取，切换主题时整页按新配色重建（此前只在 body 构建时读一次）。
            vbind({ ctx.themeColors }) {
                val c = loginPalette(ctx.themeColors.isDark)
                View {
                    attr {
                        size(w, pagerData.pageViewHeight)
                        backgroundColor(c.bg)
                    }

                    // 流光：4 个径向光斑，呼吸 + 缓慢位移
                    this.LoginGlow(c.glowTint, { ctx.glowA }, 7f, leftDp = -ctx.x(90f), topDp = -ctx.y(140f), sizeDp = ctx.x(420f), onOpacity = 0.6f, offOpacity = 0.3f, dx = 0.05f, dy = 0.03f)
                    this.LoginGlow(c.glowTint, { ctx.glowB }, 9f, rightDp = -ctx.x(100f), topDp = ctx.y(180f), sizeDp = ctx.x(360f), onOpacity = 0.5f, offOpacity = 0.25f, dx = 0.04f, dy = 0.03f)
                    this.LoginGlow(c.glowTint, { ctx.glowC }, 11f, leftDp = -ctx.x(80f), bottomDp = ctx.y(60f), sizeDp = ctx.x(320f), onOpacity = 0.45f, offOpacity = 0.2f, dx = 0.03f, dy = 0.04f)
                    this.LoginGlow(c.glowTint, { ctx.glowD }, 13f, rightDp = -ctx.x(60f), topDp = -ctx.y(80f), sizeDp = ctx.x(280f), onOpacity = 0.5f, offOpacity = 0.22f, dx = 0.05f, dy = 0.02f)

                    vif({ ctx.showQuickConnect }) {
                        ctx.quickConnectScreen(this, c)
                    }
                    vif({ !ctx.showQuickConnect }) {
                        ctx.loginScreen(this, c)
                    }
                }
            }
        }
    }

    private fun x(p: Float): Float = pagerData.pageViewWidth * p / 375f

    private fun y(p: Float): Float =
        (pagerData.pageViewHeight + pagerData.safeAreaInsets.bottom) * p / 812f

    /** 品牌区：icon + 名称 + 打字机宣传语。两种首屏共用。 */
    private fun brandBlock(parent: ViewContainer<*, *>, c: LoginPalette) {
        val ctx = this@DshWelcomePage
        fun x(p: Float) = ctx.x(p)
        fun y(p: Float) = ctx.y(p)
        parent.apply {
        val w = ctx.pagerData.pageViewWidth
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
        }
    }

    /** 已配对电脑的一键连接首屏。 */
    private fun quickConnectScreen(parent: ViewContainer<*, *>, c: LoginPalette) {
        val ctx = this@DshWelcomePage
        fun x(p: Float) = ctx.x(p)
        fun y(p: Float) = ctx.y(p)
        parent.apply {
        val w = ctx.pagerData.pageViewWidth
        ctx.brandBlock(this, c)

        // 电脑名称
        View {
            attr {
                positionAbsolute()
                left(0f)
                top(y(405f))
                width(w)
                height(y(24f))
                alignItemsCenter()
                justifyContentCenter()
            }
            Text {
                attr {
                    text(ctx.deviceName.ifEmpty { "已配对的电脑" })
                    fontSize(x(17f))
                    fontWeightBold()
                    color(c.text)
                }
            }
        }

        // 一键连接
        View {
            attr {
                positionAbsolute()
                left((w - x(335f)) / 2f)
                top(y(445f))
                width(x(335f))
                height(y(50f))
                borderRadius(x(16f))
                backgroundColor(c.primaryBg)
                opacity(if (ctx.busy) 0.72f else 1f)
                flexDirectionRow()
                alignItemsCenter()
                justifyContentCenter()
            }
            event { click { this@DshWelcomePage.quickConnect() } }
            Text {
                attr {
                    text(if (ctx.busy) "正在连接…" else "一键连接")
                    fontSize(x(16f))
                    fontWeightBold()
                    color(c.primaryText)
                }
            }
        }

        // 使用其他连接
        View {
            attr {
                positionAbsolute()
                left(0f)
                top(y(509f))
                width(w)
                height(y(20f))
                flexDirectionRow()
                alignItemsCenter()
                justifyContentCenter()
            }
            event { click { this@DshWelcomePage.useOtherConnection() } }
            Image {
                attr {
                    src(ImageUri.commonAssets("info.svg"))
                    size(x(14f), x(14f))
                    tintColor(c.quiet)
                }
            }
            Text {
                attr {
                    text("使用其他连接")
                    fontSize(x(13f))
                    color(c.quiet)
                    marginLeft(x(6f))
                }
            }
            Image {
                attr {
                    src(ImageUri.commonAssets("chevron-right.svg"))
                    size(x(12f), x(12f))
                    tintColor(c.quiet)
                    marginLeft(x(2f))
                }
            }
        }

        vif({ this@DshWelcomePage.error.isNotEmpty() }) {
            Text {
                attr {
                    positionAbsolute()
                    left(x(20f))
                    top(y(545f))
                    width(x(335f))
                    fontSize(x(12f))
                    color(Color(0xFFD0554F))
                    textAlignCenter()
                }
                event { click { } }
            }
        }
        vif({ this@DshWelcomePage.fingerprintPending.isNotEmpty() }) {
            Text {
                attr {
                    positionAbsolute()
                    left(x(20f))
                    top(y(575f))
                    width(x(335f))
                    fontSize(x(13f))
                    color(c.text)
                    textAlignCenter()
                }
                event { click { this@DshWelcomePage.trustFingerprint() } }
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
                    src(ImageUri.commonAssets("cloud-upload.svg"))
                    size(x(12f), x(12f))
                    tintColor(c.quiet)
                    marginRight(x(5f))
                }
            }
            Text {
                attr {
                    text("电脑不在线也可随时连接")
                    fontSize(x(11f))
                    color(c.quiet)
                }
            }
        }
        }
    }

    /** 登录页（首次进入没有返回箭头；从一键连接页进入才有）。 */
    private fun loginScreen(parent: ViewContainer<*, *>, c: LoginPalette) {
        val ctx = this@DshWelcomePage
        fun x(p: Float) = ctx.x(p)
        fun y(p: Float) = ctx.y(p)
        parent.apply {
        val w = ctx.pagerData.pageViewWidth
        vif({ this@DshWelcomePage.canGoBack }) {
            View {
                attr {
                    positionAbsolute()
                    // 与 SSH 页返回箭头保持一致：图标可见左缘对齐 24dp 文本线。
                    left(x(12f))
                    top(y(50f))
                    size(x(32f), x(32f))
                    alignItemsCenter()
                    justifyContentCenter()
                }
                event { click { this@DshWelcomePage.backToQuickConnect() } }
                Image {
                    attr {
                        src(ImageUri.commonAssets("chevron-left.svg"))
                        size(x(22f), x(22f))
                        tintColor(c.text)
                    }
                }
            }
        }

        ctx.brandBlock(this, c)

        // 扫码添加电脑
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
            event { click { this@DshWelcomePage.scan() } }
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

        // SSH
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
            event { click { this@DshWelcomePage.openSshSetup() } }
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
                    text("用 SSH 连接")
                    fontSize(x(14f))
                    fontWeightMedium()
                    color(c.text)
                }
            }
        }

        vif({ this@DshWelcomePage.error.isNotEmpty() }) {
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

    companion object {
        private const val RELAY_PROBE_TIMEOUT_MS = 12_000
        private const val SNAPSHOT_POLL_MS = 200
        private const val SNAPSHOT_WAIT_ATTEMPTS = 30
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
