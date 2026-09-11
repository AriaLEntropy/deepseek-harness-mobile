package com.example.dsh.home

import com.example.dsh.theme.DshThemeMode

import com.example.dsh.base.*
import com.example.dsh.chat.*
import com.example.dsh.connection.*
import com.example.dsh.conversation.*
import com.example.dsh.home.*
import com.example.dsh.infrastructure.*
import com.example.dsh.rendering.*
import com.example.dsh.storage.*
import com.example.dsh.web.*
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vforIndex
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.views.compose.Button

internal fun ViewContainer<*, *>.DshConnectionSettingsModal(
    sshMode: () -> Boolean,
    host: () -> String,
    user: () -> String,
    port: () -> String,
    dshPort: () -> String,
    keyLabel: () -> String,
    keyPassphrase: () -> String,
    busy: () -> Boolean,
    error: () -> String,
    onModeChange: (Boolean) -> Unit,
    onHostChange: (String) -> Unit,
    onUserChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onDshPortChange: (String) -> Unit,
    onPickKey: () -> Unit,
    onPassphraseChange: (String) -> Unit,
    onTrustFingerprint: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onOpenApiKey: () -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    Modal(inWindow = true) {
        attr { absolutePositionAllZero(); allCenter(); backgroundColor(Color(0x66000000)); padding(20f) }
        View {
            attr {
                width(pagerData.pageViewWidth - 40f)
                maxWidth(440f)
                flexDirectionColumn()
                padding(22f)
                borderRadius(16f)
                backgroundColor(colors().bgLayer1)
            }
            View {
                attr { height(32f); flexDirectionRow(); alignItemsCenter() }
                Text { attr { text("连接设置"); flex(1f); fontSize(20f); fontWeightBold(); color(colors().labelPrimary) } }
                View { attr { size(32f, 32f); allCenter() }; Image { attr { src(ImageUri.commonAssets("x.svg")); size(20f, 20f); tintColor(colors().labelSecondary) } }; DshHitButton { if (!busy()) onClose() } }
            }
            Text { attr { text("选择 Agent 运行位置"); marginTop(16f); fontSize(13f); color(colors().labelSecondary) } }
            View {
                attr { height(42f); marginTop(8f); flexDirectionRow(); borderRadius(8f); backgroundColor(colors().specificSelector); padding(4f) }
                View {
                    attr { flex(1f); height(34f); flexDirectionRow(); alignItemsCenter(); justifyContentCenter(); backgroundColor(if (!sshMode()) colors().bgLayer1 else Color(0x00FFFFFF)); borderRadius(6f) }
                    Text { attr { text("扫码连接"); fontSize(13f); color(if (!sshMode()) colors().stateBusinessPrimary else colors().labelSecondary) } }
                    event { click { onModeChange(false) } }
                }
                View {
                    attr { flex(1f); height(34f); flexDirectionRow(); alignItemsCenter(); justifyContentCenter(); backgroundColor(if (sshMode()) colors().bgLayer1 else Color(0x00FFFFFF)); borderRadius(6f) }
                    Text { attr { text("SSH 连接电脑"); fontSize(13f); color(if (sshMode()) colors().stateBusinessPrimary else colors().labelSecondary) } }
                    event { click { onModeChange(true) } }
                }
            }
            vif({ !sshMode() }) {
                Text { attr { text("扫码模式连接电脑上的 DSH。返回连接页可重新扫码或更换电脑。"); marginTop(16f); fontSize(14f); lineHeight(21f); color(colors().labelSecondary) } }
                View {
                    attr { height(40f); marginTop(16f); flexDirectionRow(); justifyContentFlexEnd() }
                    Button { attr { width(132f); height(40f); borderRadius(8f); backgroundColor(colors().stateBusinessPrimary); titleAttr { text("返回连接页"); fontSize(14f); color(Color.WHITE) } }; event { click { if (!busy()) onSave() } } }
                }
            }
            velse {
                DshConnectionInput("SSH 主机", host, "例如 100.86.12.34 或 computer.example.com", onHostChange, colors = colors)
                DshConnectionInput("SSH 用户名", user, "例如 alex", onUserChange, colors = colors)
                View { attr { flexDirectionRow(); marginTop(12f) }; DshConnectionInput("SSH 端口", port, "22", onPortChange, 0.5f, colors = colors); DshConnectionInput("远程 DSH 端口", dshPort, "3080", onDshPortChange, 0.5f, 10f, colors = colors) }
                View {
                    attr { height(44f); marginTop(12f); flexDirectionRow(); alignItemsCenter(); paddingLeft(12f); paddingRight(10f); borderRadius(8f); backgroundColor(colors().specificSelector) }
                    Text { attr { text(keyLabel()); flex(1f); fontSize(13f); color(colors().labelPrimary) } }
                    Text { attr { text(if (busy()) "导入中..." else "选择私钥"); fontSize(13f); color(colors().stateBusinessPrimary) }; event { click { if (!busy()) onPickKey() } } }
                }
                DshConnectionInput("私钥口令（如有）", keyPassphrase, "仅本次连接使用", onPassphraseChange, password = true, colors = colors)
                vif({ error().startsWith("首次连接需要确认主机指纹：") }) {
                    View {
                        attr { marginTop(10f); padding(10f); borderRadius(8f); backgroundColor(colors().stateWarnTertiary) }
                        Text { attr { text("请确认这是你电脑的 SSH 主机指纹。确认后会保存，指纹变化时连接将被拒绝。"); fontSize(12f); lineHeight(18f); color(colors().stateWarnPrimary) } }
                        Text { attr { text("信任此指纹并连接"); marginTop(8f); fontSize(13f); color(colors().stateBusinessPrimary) }; event { click { if (!busy()) onTrustFingerprint() } } }
                    }
                }
                vif({ error().isNotEmpty() && !error().startsWith("首次连接需要确认主机指纹：") }) {
                    Text { attr { text(error()); marginTop(8f); fontSize(12f); lineHeight(18f); color(colors().stateErrorPrimary) } }
                }
                View { attr { marginTop(18f); height(40f); flexDirectionRow(); justifyContentFlexEnd() }; Button { attr { width(132f); height(40f); borderRadius(8f); backgroundColor(if (busy()) colors().stateBusinessTertiary else colors().stateBusinessPrimary); titleAttr { text(if (busy()) "连接中..." else "保存并连接"); fontSize(14f); color(Color.WHITE) } }; event { click { if (!busy()) onSave() } } } }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshConnectionInput(
    title: String,
    value: () -> String,
    hint: String,
    onChange: (String) -> Unit,
    flexValue: Float = 1f,
    marginLeft: Float = 0f,
    password: Boolean = false,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    View {
        attr { flex(flexValue); marginLeft(marginLeft); flexDirectionColumn() }
        Text { attr { text(title); marginTop(10f); fontSize(12f); color(colors().labelSecondary) } }
        View {
            attr { height(40f); marginTop(5f); borderRadius(8f); border(Border(1f, BorderStyle.SOLID, colors().borderL2)); backgroundColor(colors().bgBase); paddingLeft(10f); paddingRight(10f) }
            Input {
                ref { it.view?.setText(value()) }
                attr { flex(1f); fontSize(14f); color(colors().labelPrimary); placeholder(hint); placeholderColor(colors().labelTertiary); returnKeyTypeDone(); if (password) keyboardTypePassword() }
                event { textDidChange { onChange(it.text) } }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshCredentialSetupModal(
    title: () -> String,
    busy: () -> Boolean,
    error: () -> String,
    inputRef: (ViewRef<InputView>) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            allCenter()
            paddingLeft(20f)
            paddingRight(20f)
            backgroundColor(Color(0x66000000))
        }
        View {
            attr {
                width(pagerData.pageViewWidth - 40f)
                maxWidth(420f)
                flexDirectionColumn()
                padding(24f)
                borderRadius(18f)
                backgroundColor(colors().bgLayer1)
            }
            View {
                attr {
                    height(32f)
                    flexDirectionRow()
                    alignItemsCenter()
                }
                Text {
                    attr {
                        text(title())
                        flex(1f)
                        fontSize(20f)
                        fontWeightBold()
                        color(colors().labelPrimary)
                    }
                }
                View {
                    attr {
                        size(32f, 32f)
                        allCenter()
                    }
                    Image {
                        attr {
                            src(ImageUri.commonAssets("x.svg"))
                            size(20f, 20f)
                            tintColor(colors().labelSecondary)
                        }
                    }
                    DshHitButton { if (!busy()) onClose() }
                }
            }
            Text {
                attr {
                    text(if (title().contains("电脑端")) "确认后将修改电脑端 DSH 的凭据。" else "配置 DeepSeek 官方模型，即可开始使用。")
                    marginTop(8f)
                    fontSize(14f)
                    lineHeight(21f)
                    color(colors().labelSecondary)
                }
            }
            Text {
                attr {
                    text("API Key")
                    marginTop(22f)
                    fontSize(13f)
                    fontWeightMedium()
                    color(colors().labelPrimary)
                }
            }
            View {
                attr {
                    height(46f)
                    marginTop(8f)
                    borderRadius(8f)
                    border(Border(1f, BorderStyle.SOLID, if (error().isEmpty()) colors().borderL2 else colors().stateErrorPrimary))
                    backgroundColor(colors().bgBase)
                    paddingLeft(12f)
                    paddingRight(12f)
                }
                Input {
                    ref { inputRef(it) }
                    attr {
                        flex(1f)
                        fontSize(15f)
                        color(colors().labelPrimary)
                        placeholder("输入 DeepSeek API Key")
                        placeholderColor(colors().labelTertiary)
                        keyboardTypePassword()
                        returnKeyTypeDone()
                        autofocus(true)
                        editable(!busy())
                    }
                    event {
                        textDidChange { onApiKeyChange(it.text) }
                        inputReturn { if (!busy()) onSave() }
                    }
                }
            }
            vif({ error().isNotEmpty() }) {
                Text {
                    attr {
                        text(error())
                        marginTop(8f)
                        fontSize(12f)
                        lineHeight(18f)
                        color(colors().stateErrorPrimary)
                    }
                }
            }
            View {
                attr {
                    marginTop(24f)
                    height(40f)
                    flexDirectionRow()
                    justifyContentFlexEnd()
                }
                Button {
                    attr {
                        width(132f)
                        height(40f)
                        borderRadius(8f)
                        backgroundColor(if (busy()) colors().stateBusinessTertiary else colors().stateBusinessPrimary)
                        titleAttr {
                            text(if (busy()) "保存中..." else "保存并继续")
                            fontSize(14f)
                            color(Color.WHITE)
                        }
                    }
                    event { click { if (!busy()) onSave() } }
                }
            }
        }
    }
}

private const val DSH_WORDMARK_RATIO = 143f / 23f

internal fun ViewContainer<*, *>.DshWordmark(
    height: Float = 22f,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    Image {
        attr {
            src(ImageUri.commonAssets("wordmark.svg"))
            width(height * DSH_WORDMARK_RATIO)
            height(height)
            resizeContain()
            tintColor(if (colors().isDark) Color.WHITE else null)
        }
    }
}

/**
 * 移动端会话抽屉（对齐 dsh 原版侧边栏）：
 * 顶部为品牌与新会话，中部为会话列表，底部固定「设置」入口。
 * 远程时间线（isWebTimeline）下，工作区渲染为内嵌菜单式文件夹：文件夹行带
 * 打开/关闭文件夹图标与旋转箭头（收起→右、展开→下，与原版一致），会话行
 * 缩进排列；每行会话带相对时间与 ⋯ 溢出按钮（复用主页面的 DshOverflowMenu）。
 */
internal fun ViewContainer<*, *>.DshSessionDrawer(
    sessions: () -> ObservableList<DshSession>,
    workspaceGroups: () -> ObservableList<DshWorkspaceGroup>,
    isWebTimeline: () -> Boolean,
    activeId: () -> String,
    animated: () -> Boolean,
    expandedGroupIds: () -> List<String>,
    onToggleGroup: (String) -> Unit,
    sessionPending: (String) -> Boolean,
    activeWorkspaceId: () -> String,
    overflowVisible: () -> Boolean,
    overflowActions: () -> ObservableList<DshOverflowAction>,
    onOverflowSelect: (String) -> Unit,
    onDismissOverflow: () -> Unit,
    onOpenOverflowFor: (String) -> Unit,
    statusBarHeight: Float,
    pageViewWidth: Float,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewSession: () -> Unit,
    onSelect: (String) -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            flexDirectionRow()
            backgroundColor(Color(0x00000000))
        }
        View {
            attr {
                width((pagerData.pageViewWidth - 44f).coerceAtMost(340f))
                height(pagerData.pageViewHeight)
                flexDirectionColumn()
                paddingTop(pagerData.statusBarHeight + 10f)
                paddingLeft(14f)
                paddingRight(14f)
                paddingBottom(18f)
                backgroundColor(colors().bgBase)
                transform(Translate(if (animated()) 0f else -1f, 0f))
                animation(Animation.easeOut(0.24f), animated())
            }
            View {
                attr {
                    height(48f)
                    flexDirectionRow()
                    alignItemsCenter()
                }
                DshWordmark(colors = { colors() })
                View { attr { flex(1f) } }
                View {
                    attr { size(38f, 38f); allCenter() }
                    Image { attr { src(ImageUri.commonAssets("x.svg")); size(22f, 22f); tintColor(colors().labelSecondary) } }
                    event { click { onClose() } }
                }
            }
            View {
                attr {
                    height(42f)
                    marginTop(8f)
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(12f)
                    paddingRight(12f)
                    borderRadius(9f)
                    backgroundColor(colors().specificSelector)
                }
                Image { attr { src(ImageUri.commonAssets("plus.svg")); size(20f, 20f); tintColor(colors().labelPrimary) } }
                Text {
                    attr {
                        text("新会话")
                        marginLeft(10f)
                        fontSize(14f)
                        fontWeightMedium()
                        color(colors().labelPrimary)
                    }
                }
                event { click { onNewSession() } }
            }
            Text {
                attr {
                    text(if (isWebTimeline()) "工作区" else "会话")
                    marginTop(16f)
                    marginBottom(6f)
                    fontSize(12f)
                    color(colors().labelTertiary)
                }
            }
            Scroller {
                attr { flex(1f) }
                vif({ !isWebTimeline() }) {
                    vfor({ sessions() }) { session ->
                        DshSessionDrawerRow(
                            title = session.title,
                            subtitle = session.workspace,
                            active = activeId() == session.id,
                            running = session.running,
                            updatedAt = session.updatedAt,
                            now = currentTimeMillis(),
                            indented = false,
                            pending = sessionPending(session.id),
                            onSelect = { onSelect(session.id) },
                            onOpenOverflow = { onOpenOverflowFor(session.id) },
                            colors = colors,
                        )
                    }
                }
                vif({ isWebTimeline() }) {
                    vfor({ workspaceGroups() }) { group ->
                        val groupKey = group.workspaceId
                        View {
                            attr {
                                marginTop(4f)
                                marginBottom(2f)
                                flexDirectionColumn()
                            }
                            // 文件夹行：内嵌菜单头（文件夹图标 + 标题 + 旋转箭头）
                            View {
                                attr {
                                    height(40f)
                                    flexDirectionRow()
                                    alignItemsCenter()
                                    paddingLeft(8f)
                                    paddingRight(8f)
                                    borderRadius(9f)
                                }
                                Image {
                                    attr {
                                        src(ImageUri.commonAssets(
                                            if (expandedGroupIds().contains(groupKey)) "folder.svg" else "folder-closed.svg",
                                        ))
                                        size(16f, 16f)
                                        tintColor(if (groupKey.isNotEmpty() &&
                                            expandedGroupIds().contains(groupKey) &&
                                            activeWorkspaceId() == groupKey
                                        ) colors().stateBusinessPrimary else colors().labelTertiary)
                                    }
                                }
                                View { attr { width(4f) } }
                                Text {
                                    attr {
                                        text(group.title)
                                        flex(1f)
                                        lines(1)
                                        fontSize(14f)
                                        fontWeightMedium()
                                        color(colors().labelPrimary)
                                    }
                                }
                                Image {
                                    attr {
                                        src(ImageUri.commonAssets("chevron-down.svg"))
                                        size(14f, 14f)
                                        transform(Rotate(if (expandedGroupIds().contains(groupKey)) 0f else -90f))
                                        tintColor(colors().labelTertiary)
                                    }
                                }
                                event { click { onToggleGroup(groupKey) } }
                            }
                            vif({ expandedGroupIds().contains(groupKey) }) {
                                // 展开即显示全部会话（与原版一致）；vif 条件翻转时重建列表。
                                group.sessions.forEach { session ->
                                    DshSessionDrawerRow(
                                        title = session.title,
                                        subtitle = "",
                                        active = activeId() == session.id,
                                        running = session.running,
                                        updatedAt = session.updatedAt,
                                        now = currentTimeMillis(),
                                        indented = true,
                                        pending = sessionPending(session.id),
                                        onSelect = { onSelect(session.id) },
                                        onOpenOverflow = { onOpenOverflowFor(session.id) },
                                        colors = colors,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // 底部固定「设置」入口（不随列表滚动），使用齿轮图标，与原版侧边栏一致
            View {
                attr {
                    height(1f)
                    marginTop(8f)
                    backgroundColor(colors().borderL1)
                }
            }
            View {
                attr {
                    height(44f)
                    marginTop(4f)
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(12f)
                    paddingRight(12f)
                    borderRadius(9f)
                }
                Image {
                    attr {
                        src(ImageUri.commonAssets("settings.svg"))
                        size(20f, 20f)
                        tintColor(colors().labelSecondary)
                    }
                }
                Text {
                    attr {
                        text("设置")
                        marginLeft(10f)
                        fontSize(14f)
                        fontWeightMedium()
                        color(colors().labelSecondary)
                    }
                }
                event { click { onOpenSettings() } }
            }
        }
        View {
            attr {
                flex(1f)
                height(pagerData.pageViewHeight)
            }
            event { click { onClose() } }
        }
        // 复用主页面的 overflow menu：挂在抽屉 Modal 最上层，位置沿用主页
        // topbar 下方的定位，仅当从抽屉会话行 ⋯ 打开时可见。
        DshOverflowMenu(
            visible = overflowVisible,
            actions = overflowActions,
            onSelect = onOverflowSelect,
            onDismiss = onDismissOverflow,
            statusBarHeight = statusBarHeight,
            pageViewWidth = pageViewWidth,
            colors = colors,
        )
    }
}

/**
 * 会话行相对时间标签，桶位与 dsh 原版一致（刚刚/N分钟/N小时/N天/N个月/N年）；
 * updatedAt 无效（<=0）时返回空串，调用方隐藏时间位。
 */
internal fun dshRelativeTimeLabel(updatedAt: Long, now: Long): String {
    if (updatedAt <= 0L) return ""
    val diff = (now - updatedAt).coerceAtLeast(0L)
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L}分钟"
        diff < 86_400_000L -> "${diff / 3_600_000L}小时"
        diff < 30L * 86_400_000L -> "${diff / 86_400_000L}天"
        diff < 365L * 86_400_000L -> "${diff / (30L * 86_400_000L)}个月"
        else -> "${diff / (365L * 86_400_000L)}年"
    }
}

internal fun ViewContainer<*, *>.DshSessionDrawerRow(
    title: String,
    subtitle: String,
    active: Boolean,
    running: Boolean,
    pending: Boolean,
    updatedAt: Long,
    now: Long,
    indented: Boolean,
    onSelect: () -> Unit,
    onOpenOverflow: () -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    View {
        attr {
            height(46f)
            marginBottom(2f)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(if (indented) 32f else 12f)
            paddingRight(4f)
            borderRadius(9f)
            backgroundColor(if (active) colors().specificSidebarNavItemActive else Color(0x00FFFFFF))
        }
        // 状态点不常驻：待用户决策（琥珀）或进行中/有新消息（蓝）才显示；
        // 无状态时不留占位，文字靠左（与 ds 移动端一致）。
        vif({ pending || running }) {
            View {
                attr {
                    width(7f)
                    allCenter()
                }
                vif({ pending || running }) {
                    View {
                        attr {
                            size(7f, 7f)
                            borderRadius(4f)
                            backgroundColor(if (pending) colors().stateWarnPrimary else colors().stateBusinessPrimary)
                        }
                    }
                }
            }
        }
        View {
            attr {
                flex(1f)
                marginLeft(10f)
                flexDirectionColumn()
                justifyContentCenter()
            }
            Text {
                attr {
                    text(title)
                    lines(1)
                    fontSize(14f)
                    color(colors().labelPrimary)
                }
            }
            vif({ subtitle.isNotEmpty() }) {
                Text {
                    attr {
                        text(subtitle)
                        lines(1)
                        marginTop(2f)
                        fontSize(10f)
                        color(colors().labelTertiary)
                    }
                }
            }
        }
        vif({ updatedAt > 0L && !active }) {
            Text {
                attr {
                    text(dshRelativeTimeLabel(updatedAt, now))
                    marginLeft(6f)
                    fontSize(11f)
                    color(colors().labelTertiary)
                }
            }
        }
        // 溢出按钮仅在选中行显示（ds 移动端交互：正常状态不露 ⋯）。
        vif({ active }) {
            View {
                attr { size(34f, 34f); marginLeft(2f); allCenter() }
                Image {
                    attr {
                        src(ImageUri.commonAssets("more.svg"))
                        size(18f, 18f)
                        tintColor(colors().labelTertiary)
                    }
                }
                DshHitButton { onOpenOverflow() }
            }
        }
        event { click { onSelect() } }
    }
}

internal fun ViewContainer<*, *>.DshModelPicker(
    options: () -> ObservableList<DshModelOption>,
    busy: () -> Boolean,
    error: () -> String,
    onClose: () -> Unit,
    onSelect: (DshModelOption) -> Unit,
    onSelectEffort: (String) -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    var showEfforts = false
    val selectedOpt: () -> DshModelOption? = { options().firstOrNull { it.selected } }
    val selectedEfforts: () -> ObservableList<DshReasoningEffort> = {
        val sel = selectedOpt()
        ObservableList<DshReasoningEffort>().apply { addAll(sel?.reasoningEfforts.orEmpty()) }
    }
    val selectedEffortName: () -> String = {
        val sel = selectedOpt()
        sel?.reasoningEfforts?.firstOrNull { it.id == sel.reasoningEffort }?.name
            ?: sel?.reasoningEffort ?: ""
    }
    val selectedSupportsEfforts: () -> Boolean = {
        (selectedOpt()?.reasoningEfforts?.isEmpty()) != true
    }
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            flexDirectionColumn()
            justifyContentFlexEnd()
            backgroundColor(Color(0x55000000))
        }
        View {
            attr { flex(1f) }
            event { click { onClose() } }
        }
        View {
            attr {
                height((pagerData.pageViewHeight * 0.62f).coerceAtMost(540f))
                flexDirectionColumn()
                padding(18f)
                borderRadius(20f)
                backgroundColor(colors().bgLayer1)
            }
            View {
                attr { height(40f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(if (showEfforts) "推理等级" else "选择模型")
                        fontSize(18f)
                        fontWeightBold()
                        color(colors().labelPrimary)
                    }
                }
                View { attr { flex(1f) } }
                View {
                    attr { size(36f, 36f); allCenter() }
                    Image { attr { src(ImageUri.commonAssets("x.svg")); size(21f, 21f); tintColor(colors().labelSecondary) } }
                    event { click { onClose() } }
                }
            }
            vif({ error().isNotEmpty() }) {
                Text {
                    attr {
                        text(error())
                        marginTop(6f)
                        marginBottom(6f)
                        fontSize(12f)
                        color(colors().stateErrorPrimary)
                    }
                }
            }
            if (showEfforts) {
                // 推理等级：顶部返回栏 + 当前模型名
                View {
                    attr {
                        height(36f)
                        marginTop(2f)
                        marginBottom(4f)
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    View {
                        attr { size(32f, 32f); allCenter() }
                        Image { attr { src(ImageUri.commonAssets("chevron-left.svg")); size(18f, 18f) } }
                        event { click { showEfforts = false } }
                    }
                    Text {
                        attr {
                            text(selectedOpt()?.name ?: "")
                            marginLeft(4f)
                            fontSize(13f)
                            color(colors().labelTertiary)
                        }
                    }
                }
                Scroller {
                    attr { flex(1f); marginTop(4f) }
                    vfor({ selectedEfforts() }) { effort ->
                        View {
                            attr {
                                minHeight(54f)
                                marginBottom(6f)
                                flexDirectionRow()
                                alignItemsCenter()
                                padding(10f, 12f, 10f, 12f)
                                borderRadius(10f)
                                backgroundColor(if (effort.id == selectedOpt()?.reasoningEffort) colors().specificSelector else colors().bgModulePlatform)
                            }
                            View {
                                attr { flex(1f); flexDirectionColumn() }
                                Text {
                                    attr {
                                        text(effort.name)
                                        fontSize(14f)
                                        fontWeightMedium()
                                        color(colors().labelPrimary)
                                    }
                                }
                                if (effort.description.isNotEmpty()) {
                                    Text {
                                        attr {
                                            text(effort.description)
                                            marginTop(3f)
                                            lines(1)
                                            fontSize(11f)
                                            color(colors().labelTertiary)
                                        }
                                    }
                                }
                            }
                            if (effort.id == selectedOpt()?.reasoningEffort) {
                                Image {
                                    attr {
                                        src(ImageUri.commonAssets("check.svg"))
                                        size(18f, 18f)
                                        tintColor(colors().stateBusinessPrimary)
                                    }
                                }
                            }
                            event { click { if (!busy()) onSelectEffort(effort.id) } }
                        }
                    }
                }
            } else {
                // 当前选中模型：若支持推理等级，显示"推理等级 ›"入口行
                vif({ selectedSupportsEfforts() }) {
                    View {
                        attr {
                            height(44f)
                            marginTop(6f)
                            marginBottom(2f)
                            paddingLeft(12f)
                            paddingRight(12f)
                            flexDirectionRow()
                            alignItemsCenter()
                            borderRadius(10f)
                            backgroundColor(colors().specificSelector)
                        }
                        Text {
                            attr {
                                text("推理等级")
                                fontSize(14f)
                                color(colors().labelPrimary)
                            }
                        }
                        View { attr { flex(1f) } }
                        Text {
                            attr {
                                text(selectedEffortName())
                                marginRight(4f)
                                fontSize(13f)
                                color(colors().labelTertiary)
                            }
                        }
                        Image { attr { src(ImageUri.commonAssets("chevron-right.svg")); size(14f, 14f); tintColor(colors().labelTertiary) } }
                        event { click { showEfforts = true } }
                    }
                }
                vif({ busy() && options().isEmpty() }) {
                    Text {
                        attr {
                            text("正在加载模型...")
                            marginTop(24f)
                            fontSize(14f)
                            color(colors().labelTertiary)
                        }
                    }
                }
                Scroller {
                    attr { flex(1f); marginTop(8f) }
                    vfor({ options() }) { option ->
                        View {
                            attr {
                                minHeight(58f)
                                marginBottom(6f)
                                flexDirectionRow()
                                alignItemsCenter()
                                padding(10f, 12f, 10f, 12f)
                                borderRadius(10f)
                                backgroundColor(if (option.selected) colors().stateBusinessTertiary else colors().bgBase)
                            }
                            View {
                                attr { flex(1f); flexDirectionColumn() }
                                Text {
                                    attr {
                                        text(option.name)
                                        fontSize(14f)
                                        fontWeightMedium()
                                        color(colors().labelPrimary)
                                    }
                                }
                                Text {
                                    attr {
                                        text(option.providerName + if (option.description.isEmpty()) "" else " · ${option.description}")
                                        marginTop(3f)
                                        lines(1)
                                        fontSize(11f)
                                        color(colors().labelTertiary)
                                    }
                                }
                            }
                            if (option.selected) {
                                Image {
                                    attr {
                                        src(ImageUri.commonAssets("check.svg"))
                                        size(18f, 18f)
                                        tintColor(colors().stateBusinessPrimary)
                                    }
                                }
                            }
                            event { click { if (!busy()) onSelect(option) } }
                        }
                    }
                }
            }
        }
    }
}

internal data class DshPermissionOption(
    val value: String,
    val label: String,
    val selected: Boolean = false,
)

// dsh 语义：权限态 → 盾牌图标（read-only=盾牌+对勾，workspace-write=盾牌+铅笔，full-access=盾牌+感叹号）
internal fun dshPermissionIcon(value: String): String = when (value) {
    "read-only" -> "permission-read.svg"
    "danger-full-access" -> "permission-danger.svg"
    "full-access" -> "permission-danger.svg"
    else -> "permission-write.svg"
}

// 权限选择弹窗（会话开始前，users 在底部工具栏点击权限 chip 打开）。
// 三个圆角卡片：盾牌 svg 在上、权限名在下，选中卡片蓝底高亮 + 右上对勾（cf412 样式）。
internal fun ViewContainer<*, *>.DshPermissionPicker(
    options: () -> ObservableList<DshPermissionOption>,
    onClose: () -> Unit,
    onSelect: (DshPermissionOption) -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            flexDirectionColumn()
            justifyContentFlexEnd()
            backgroundColor(Color(0x55000000))
        }
        View {
            attr { flex(1f) }
            event { click { onClose() } }
        }
        View {
            attr {
                flexDirectionColumn()
                padding(18f)
                backgroundColor(colors().bgLayer1)
                borderRadius(BorderRectRadius(20f, 20f, 0f, 0f))
            }
            View {
                attr { height(40f); flexDirectionRow(); alignItemsCenter(); }
                Text {
                    attr {
                        text("选择权限")
                        fontSize(18f)
                        fontWeightBold()
                        color(colors().labelPrimary)
                    }
                }
                View { attr { flex(1f) } }
                View {
                    attr { size(36f, 36f); allCenter() }
                    Image { attr { src(ImageUri.commonAssets("x.svg")); size(21f, 21f); tintColor(colors().labelSecondary) } }
                    event { click { onClose() } }
                }
            }
            // 三个圆角权限卡片：横向一行等宽，svg 在卡内上方、文字在下方
            View {
                attr {
                    marginTop(12f)
                    flexDirectionRow()
                }
                vforIndex({ options() }) { option, index, _ ->
                    View {
                        attr {
                            flex(1f)
                            height(84f)
                            if (index > 0) marginLeft(12f)
                            flexDirectionColumn()
                            alignItemsCenter()
                            justifyContentCenter()
                            borderRadius(14f)
                            border(Border(
                                1f,
                                BorderStyle.SOLID,
                                if (option.selected) {
                                    if (option.value == "danger-full-access") colors().stateErrorPrimary else colors().stateBusinessPrimary
                                } else colors().borderL2,
                            ))
                            backgroundColor(
                                if (option.selected) {
                                    if (option.value == "danger-full-access") colors().stateErrorSecondary else colors().stateBusinessTertiary
                                } else colors().bgBase,
                            )
                        }
                        View {
                            attr { size(22f, 22f); allCenter() }
                            Image {
                                attr {
                                    src(ImageUri.commonAssets(dshPermissionIcon(option.value)))
                                    size(20f, 20f)
                                }
                            }
                        }
                        Text {
                            attr {
                                text(option.label)
                                marginTop(6f)
                                fontSize(13f)
                                fontWeightMedium()
                                color(
                                    if (option.selected) {
                                        if (option.value == "danger-full-access") colors().stateErrorPrimary else colors().stateBusinessPrimary
                                    } else colors().labelPrimary
                                )
                            }
                        }
                        if (option.selected) {
                            View {
                                attr {
                                    absolutePosition(top = 8f, left = 8f)
                                    size(20f, 20f)
                                    allCenter()
                                    borderRadius(10f)
                                    backgroundColor(
                                        if (option.value == "danger-full-access") colors().stateErrorPrimary else colors().stateBusinessPrimary
                                    )
                                }
                                Image {
                                    attr {
                                        src(ImageUri.commonAssets("check.svg"))
                                        size(14f, 14f)
                                        tintColor(Color.WHITE)
                                    }
                                }
                            }
                        }
                        event { click { onSelect(option) } }
                    }
                }
            }
        }
    }
}

// Full access 风险确认弹窗：对齐 dsh 原版 RiskConfirmation（红色警示 icon + 风险文案 +
// 「我已了解风险」勾选 + 取消/启用双按钮），勾选前启用按钮不可点。
internal fun ViewContainer<*, *>.DshRiskConfirmationModal(
    title: String,
    description: String,
    acknowledgeLabel: String,
    cancelLabel: String,
    confirmLabel: String,
    acknowledged: () -> Boolean,
    busy: () -> Boolean,
    onAcknowledgedChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            allCenter()
            paddingLeft(20f)
            paddingRight(20f)
            backgroundColor(Color(0x66000000))
        }
        View {
            attr {
                width(pagerData.pageViewWidth - 40f)
                maxWidth(440f)
                flexDirectionColumn()
                padding(24f)
                borderRadius(18f)
                backgroundColor(colors().bgLayer1)
            }
            View {
                attr { height(32f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(title)
                        flex(1f)
                        fontSize(18f)
                        fontWeightBold()
                        color(colors().labelPrimary)
                    }
                }
                View {
                    attr { size(32f, 32f); allCenter() }
                    Image { attr { src(ImageUri.commonAssets("x.svg")); size(20f, 20f); tintColor(colors().labelSecondary) } }
                    DshHitButton { if (!busy()) onCancel() }
                }
            }
            View {
                attr {
                    marginTop(16f)
                    flexDirectionRow()
                    alignItemsFlexStart()
                }
                Image {
                    attr {
                        src(ImageUri.commonAssets("warning-outline.svg"))
                        size(18f, 18f)
                        marginTop(2f)
                        tintColor(colors().stateErrorPrimary)
                    }
                }
                Text {
                    attr {
                        text(description)
                        flex(1f)
                        marginLeft(10f)
                        fontSize(14f)
                        lineHeight(22f)
                        color(colors().labelSecondary)
                    }
                }
            }
            View {
                attr {
                    marginTop(20f)
                    flexDirectionRow()
                    alignItemsFlexStart()
                }
                View {
                    attr {
                        size(18f, 18f)
                        allCenter()
                        borderRadius(4f)
                        border(Border(
                            1.5f,
                            BorderStyle.SOLID,
                            if (acknowledged()) colors().buttonPrimaryFill else colors().borderL2,
                        ))
                        backgroundColor(if (acknowledged()) colors().buttonPrimaryFill else Color(0x00000000))
                    }
                    vif({ acknowledged() }) {
                        Image {
                            attr {
                                src(ImageUri.commonAssets("check.svg"))
                                size(12f, 12f)
                                tintColor(colors().labelPrimaryInverted)
                            }
                        }
                    }
                    DshHitButton { if (!busy()) onAcknowledgedChange(!acknowledged()) }
                }
                Text {
                    attr {
                        text(acknowledgeLabel)
                        flex(1f)
                        marginLeft(10f)
                        fontSize(14f)
                        lineHeight(22f)
                        color(colors().labelPrimary)
                    }
                }
                DshHitButton { if (!busy()) onAcknowledgedChange(!acknowledged()) }
            }
            View {
                attr {
                    marginTop(24f)
                    flexDirectionRow()
                    justifyContentFlexEnd()
                    alignItemsCenter()
                }
                View {
                    attr {
                        height(38f)
                        paddingLeft(16f)
                        paddingRight(16f)
                        allCenter()
                        borderRadius(8f)
                        border(Border(1f, BorderStyle.SOLID, colors().borderL2))
                        backgroundColor(colors().bgLayer1)
                    }
                    Text {
                        attr {
                            text(cancelLabel)
                            fontSize(14f)
                            fontWeightMedium()
                            color(colors().labelPrimary)
                        }
                    }
                    DshHitButton { if (!busy()) onCancel() }
                }
                View {
                    attr {
                        height(38f)
                        marginLeft(12f)
                        paddingLeft(16f)
                        paddingRight(16f)
                        allCenter()
                        borderRadius(8f)
                        backgroundColor(if (acknowledged()) colors().buttonPrimaryFill else colors().buttonPrimaryDimmed)
                    }
                    Text {
                        attr {
                            text(confirmLabel)
                            fontSize(14f)
                            fontWeightMedium()
                            color(if (acknowledged()) colors().labelPrimaryInverted else colors().labelSecondary)
                        }
                    }
                    DshHitButton {
                        if (!busy() && acknowledged()) onConfirm()
                    }
                }
            }
        }
    }
}

internal data class DshAgentModeOption(
    val value: String,
    val label: String,
    val description: String,
    val selected: Boolean = false,
)

// 模式选择弹窗（会话开始前，users 在 Hero 区点击模式 chip 打开）。
// 纵向 list：每项 模式名 + 多行描述，选中项右侧蓝勾（d888 样式）。
internal fun ViewContainer<*, *>.DshAgentModePicker(
    options: () -> ObservableList<DshAgentModeOption>,
    onClose: () -> Unit,
    onSelect: (DshAgentModeOption) -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            flexDirectionColumn()
            justifyContentFlexEnd()
            backgroundColor(Color(0x55000000))
        }
        View {
            attr { flex(1f) }
            event { click { onClose() } }
        }
        View {
            attr {
                height((pagerData.pageViewHeight * 0.62f).coerceAtMost(520f))
                flexDirectionColumn()
                padding(18f)
                borderRadius(20f)
                backgroundColor(colors().bgLayer1)
            }
            View {
                attr { height(40f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text("选择模式")
                        fontSize(18f)
                        fontWeightBold()
                        color(colors().labelPrimary)
                    }
                }
                View { attr { flex(1f) } }
                View {
                    attr { size(36f, 36f); allCenter() }
                    Image { attr { src(ImageUri.commonAssets("x.svg")); size(21f, 21f); tintColor(colors().labelSecondary) } }
                    event { click { onClose() } }
                }
            }
            Scroller {
                attr { flex(1f); marginTop(8f) }
                vfor({ options() }) { option ->
                    View {
                        attr {
                            minHeight(64f)
                            marginBottom(6f)
                            flexDirectionRow()
                            alignItemsCenter()
                            padding(10f, 12f, 10f, 12f)
                            borderRadius(10f)
                            backgroundColor(if (option.selected) colors().stateBusinessTertiary else colors().bgBase)
                        }
                        View {
                            attr { flex(1f); flexDirectionColumn() }
                            Text {
                                attr {
                                    text(option.label)
                                    fontSize(14f)
                                    fontWeightSemiBold()
                                    color(if (option.selected) colors().stateBusinessPrimary else colors().labelPrimary)
                                }
                            }
                            Text {
                                attr {
                                    text(option.description)
                                    marginTop(4f)
                                    fontSize(12f)
                                    lineHeight(18f)
                                    color(colors().labelTertiary)
                                }
                            }
                        }
                        if (option.selected) {
                            Image {
                                    attr {
                                        src(ImageUri.commonAssets("check.svg"))
                                        size(18f, 18f)
                                        tintColor(colors().stateBusinessPrimary)
                                    }
                                }
                        }
                        event { click { onSelect(option) } }
                    }
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshTopBar(
    title: () -> String,
    onOpenDrawer: () -> Unit,
    onOpenOverflow: () -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    View {
        attr {
            height(58f)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(12f)
            paddingRight(14f)
            backgroundColor(colors().bgLayer1)
            borderBottom(Border(1f, BorderStyle.SOLID, colors().borderL1))
        }
//        左侧菜单图标：点击打开会话抽屉
        View {
            attr { size(38f, 38f); allCenter() }
            Image {
                attr {
                    src(ImageUri.commonAssets("menu.svg"))
                    size(26f, 26f)
                    tintColor(colors().labelPrimary)
                }
            }
            DshHitButton(onOpenDrawer)
        }
//        会话标题：点击也打开会话抽屉
        Text {
            attr {
                text(title())
                marginLeft(10f)
                flex(1f)
                fontSize(17f)
                fontWeightMedium()
                color(colors().labelPrimary)
                lines(1)
            }
            event { click { onOpenDrawer() } }
        }
//        右上角 overflow menu：日志/重命名/归档/删除
        View {
            attr { size(38f, 38f); allCenter() }
            Image {
                attr {
                    src(ImageUri.commonAssets("more.svg"))
                    size(22f, 22f)
                    tintColor(colors().labelPrimary)
                }
            }
            DshHitButton(onOpenOverflow)
        }
    }
}

internal fun ViewContainer<*, *>.DshSessionRail(
    sessions: () -> ObservableList<DshSession>,
    activeId: () -> String,
    compact: Boolean,
    onSelect: (String) -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    View {
        attr {
            if (compact) {
                height(92f)
                flexDirectionRow()
            } else {
                width(236f)
                flexDirectionColumn()
            }
            backgroundColor(colors().bgLayer2)
            padding(14f)
        }
        Text {
            attr {
                text("会话")
                fontSize(13f)
                color(colors().labelSecondary)
                marginBottom(9f)
            }
        }
        if (compact) {
            Scroller {
                attr {
                    flex(1f)
                    flexDirectionRow()
                }
                vfor({ sessions() }) { session ->
                    DshSessionButton(session, activeId() == session.id, onSelect, colors = colors)
                }
            }
        } else {
            Scroller {
                attr { flex(1f) }
                vfor({ sessions() }) { session ->
                    DshSessionButton(session, activeId() == session.id, onSelect, colors = colors)
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshSessionButton(
    session: DshSession,
    active: Boolean,
    onSelect: (String) -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    Button {
        attr {
            height(48f)
            width(if (active) 220f else 220f)
            marginBottom(4f)
            borderRadius(7f)
            backgroundColor(if (active) colors().specificSidebarNavItemActive else Color(0x00000000))
            titleAttr {
                text(session.title)
                color(if (active) colors().stateBusinessPrimary else colors().labelPrimary)
                fontSize(13f)
            }
        }
        event { click { onSelect(session.id) } }
    }
}

internal fun ViewContainer<*, *>.DshSessionDetailsPanel(
    title: () -> String,
    cwd: () -> String,
    modelLabel: () -> String,
    agentPreset: () -> String,
    running: () -> Boolean,
    queueCount: () -> Int,
    jobCount: () -> Int,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    View {
        attr {
            width(280f)
            height(pagerData.pageViewHeight)
            flexDirectionColumn()
            padding(16f)
            backgroundColor(colors().bgBase)
            border(Border(1f, BorderStyle.SOLID, colors().borderL1))
        }
        Text {
            attr {
                text("Session")
                fontSize(12f)
                color(colors().labelTertiary)
            }
        }
        Text {
            attr {
                text(title())
                marginTop(6f)
                fontSize(17f)
                fontWeightSemiBold()
                color(colors().labelPrimary)
                lines(2)
            }
        }
        View {
            attr {
                height(1f)
                marginTop(14f)
                backgroundColor(colors().borderL1)
            }
        }
        DshDetailRow("状态", if (running()) "运行中" else "空闲", colors = colors)
        DshDetailRow("模型", modelLabel(), colors = colors)
        vif({ agentPreset().isNotEmpty() }) {
            DshDetailRow("Agent Preset", agentPreset(), colors = colors)
        }
        DshDetailRow("队列", "${queueCount()} 条", colors = colors)
        DshDetailRow("后台任务", "${jobCount()} 个", colors = colors)
        vif({ cwd().isNotEmpty() }) {
            DshDetailRow("目录", cwd(), colors = colors)
        }
    }
}

internal fun ViewContainer<*, *>.DshDetailRow(
    label: String,
    value: String,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    View {
        attr {
            minHeight(44f)
            marginTop(10f)
            flexDirectionColumn()
            justifyContentCenter()
        }
        Text {
            attr {
                text(label)
                fontSize(11f)
                color(colors().labelTertiary)
            }
        }
        Text {
            attr {
                text(value)
                marginTop(2f)
                fontSize(13f)
                color(colors().labelSecondary)
                lines(2)
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshWorkspaceBrowserModal(
    path: () -> String,
    home: () -> String,
    entries: () -> ObservableList<DshDirectoryEntry>,
    busy: () -> Boolean,
    error: () -> String,
    newName: () -> String,
    onDirectorySelect: (String) -> Unit,
    onNewNameChange: (String) -> Unit,
    onCreateDirectory: () -> Unit,
    onAdopt: () -> Unit,
    onClose: () -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            allCenter()
            paddingLeft(20f)
            paddingRight(20f)
            backgroundColor(Color(0x66000000))
        }
        View {
            attr {
                width(pagerData.pageViewWidth - 40f)
                maxWidth(560f)
                maxHeight(pagerData.pageViewHeight - 80f)
                flexDirectionColumn()
                padding(18f)
                borderRadius(16f)
                backgroundColor(colors().bgLayer1)
            }
            View {
                attr { height(36f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(if (path().isEmpty()) home() else path())
                        flex(1f)
                        lines(1)
                        fontSize(17f)
                        fontWeightBold()
                        color(colors().labelPrimary)
                    }
                }
                View { attr { size(32f, 32f); allCenter() }; Image { attr { src(ImageUri.commonAssets("x.svg")); size(20f, 20f) } }; DshHitButton { onClose() } }
            }
            Scroller {
                attr {
                    flex(1f)
                    marginTop(12f)
                    borderRadius(8f)
                    backgroundColor(colors().bgBase)
                }
                vfor({ entries() }) { entry ->
                    View {
                        attr {
                            height(42f)
                            flexDirectionRow()
                            alignItemsCenter()
                            paddingLeft(10f)
                            paddingRight(10f)
                        }
                        Text {
                            attr {
                                text(entry.name)
                                flex(1f)
                                lines(1)
                                fontSize(14f)
                                color(colors().labelSecondary)
                            }
                        }
                        event { click { if (!busy()) onDirectorySelect(entry.path) } }
                    }
                }
            }
            vif({ error().isNotEmpty() }) {
                Text { attr { text(error()); marginTop(8f); fontSize(12f); color(colors().stateErrorPrimary) } }
            }
            Input {
                attr {
                    height(38f)
                    marginTop(10f)
                    fontSize(14f)
                    placeholder("新目录名称")
                    placeholderColor(colors().labelTertiary)
                }
                event { textDidChange { onNewNameChange(it.text) } }
            }
            View {
                attr { height(42f); marginTop(12f); flexDirectionRow(); justifyContentFlexEnd() }
                Text {
                    attr {
                        text(if (busy()) "处理中..." else "新建目录")
                        width(88f)
                        height(38f)
                        textAlignCenter()
                        fontSize(13f)
                        color(colors().labelTertiary)
                    }
                    event { click { if (!busy()) onCreateDirectory() } }
                }
                Text {
                    attr {
                        text(if (busy()) "处理中..." else "使用此目录")
                        width(112f)
                        height(38f)
                        marginLeft(8f)
                        textAlignCenter()
                        fontSize(13f)
                        color(colors().stateBusinessPrimary)
                    }
                    event { click { if (!busy()) onAdopt() } }
                }
            }
        }
    }
}

// ===== 设置页（ds 风格：顶部居中标题 + 右上 ×，分组列表，行右侧当前值） =====
internal fun ViewContainer<*, *>.DshSettingsPage(
    loading: () -> Boolean,
    error: () -> String,
    snapshot: () -> DshSettingsSnapshot,
    isRemoteHost: () -> Boolean,
    connectionModeLabel: () -> String,
    apiKeyConfigured: () -> Boolean,
    hostVersion: () -> String,
    themeMode: () -> DshThemeMode,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onOpenConnection: () -> Unit,
    onOpenApiKey: () -> Unit,
    onPickPermission: () -> Unit,
    onPickLocale: () -> Unit,
    onPickTheme: () -> Unit,
    onPickDefaultModel: () -> Unit,
    onOpenDiagnosticLogs: () -> Unit,
    onDisconnect: () -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            backgroundColor(colors().bgBase)
        }
        View {
            attr {
                height(pagerData.statusBarHeight + 52f)
                paddingTop(pagerData.statusBarHeight)
                flexDirectionRow()
                alignItemsCenter()
                paddingLeft(12f)
                paddingRight(8f)
                backgroundColor(colors().bgBase)
            }
            View { attr { flex(1f) } }
            Text { attr { text("设置"); fontSize(17f); fontWeightBold(); color(colors().labelPrimary) } }
            View {
                attr { flex(1f); flexDirectionRow(); justifyContentFlexEnd(); alignItemsCenter() }
                View {
                    attr { size(36f, 36f); allCenter() }
                    Image { attr { src(ImageUri.commonAssets("x.svg")); size(20f, 20f) } }
                    event { click { onClose() } }
                }
            }
        }
        View {
            attr {
                height(1f)
                backgroundColor(colors().borderL1)
            }
        }
        Scroller {
            attr {
                flex(1f)
                width(pagerData.pageViewWidth)
                backgroundColor(colors().bgLayer2)
            }
            vif({ loading() }) {
                Text {
                    attr {
                        text("正在读取电脑端配置…")
                        marginTop(48f)
                        fontSize(13f)
                        textAlignCenter()
                        color(colors().labelTertiary)
                    }
                }
            }
            vif({ !loading() && error().isNotEmpty() }) {
                View {
                    attr {
                        margin(16f)
                        padding(12f)
                        borderRadius(10f)
                        backgroundColor(Color(0xFFFFF7E6))
                        flexDirectionColumn()
                    }
                    Text {
                        attr {
                            text("无法读取电脑端配置（可能未连接或版本过旧）")
                            fontSize(13f)
                            color(Color(0xFF7A5B16))
                        }
                    }
                    Text {
                        attr {
                            text("重试")
                            marginTop(6f)
                            fontSize(13f)
                            fontWeightMedium()
                            color(colors().stateBusinessPrimary)
                        }
                        event { click { onRetry() } }
                    }
                }
            }
            vif({ !loading() && error().isEmpty() && !isRemoteHost() }) {
                Text {
                    attr {
                        text("未连接电脑端 DSH，以下设置无法同步")
                        margin(16f)
                        fontSize(12f)
                        color(colors().labelTertiary)
                    }
                }
            }
            vif({ !loading() && error().isEmpty() && isRemoteHost() && !snapshot().writable }) {
                Text {
                    attr {
                        text("电脑端设置当前为只读，仅可查看")
                        margin(16f)
                        fontSize(12f)
                        color(colors().labelTertiary)
                    }
                }
            }

            // 账户
            DshSettingsGroupTitle("账户", colors = colors)
            DshSettingsRow("icon-link16.svg", "连接设置", connectionModeLabel, onOpenConnection, colors = colors)
            DshSettingsRow("icon-api14.svg", "API Key", { if (apiKeyConfigured()) "已配置" else "未配置" }, onOpenApiKey, colors = colors)

            // 权限
            DshSettingsGroupTitle("权限", colors = colors)
            DshSettingsRow("permission-write.svg", "工作区权限", { dshSettingsPermissionLabel(snapshot()) }, onPickPermission, colors = colors)

            // 应用
            DshSettingsGroupTitle("应用", colors = colors)
            DshSettingsRow("icon-globe14.svg", "语言", { dshSettingsLocaleLabel(snapshot()) }, onPickLocale, colors = colors)
            DshSettingsRow("icon-followsystem16.svg", "外观", { dshThemeModeLabel(themeMode()) }, onPickTheme, colors = colors)
            DshSettingsRow("icon-agentpreset16.svg", "默认模型", { dshSettingsDefaultModelLabel(snapshot()) }, onPickDefaultModel, colors = colors)
            DshSettingsRow("icon-refresh16.svg", "诊断日志", { "" }, onOpenDiagnosticLogs, colors = colors)

            // 关于
            DshSettingsGroupTitle("关于", colors = colors)
            DshSettingsRow("icon-refresh16.svg", "电脑端 DSH 版本", hostVersion, {}, colors = colors)
            View {
                attr {
                    height(52f)
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(16f)
                    paddingRight(12f)
                    backgroundColor(colors().bgLayer1)
                }
                Text {
                    attr {
                        text("断开连接")
                        fontSize(14f)
                        color(colors().stateErrorPrimary)
                    }
                    event { click { onDisconnect() } }
                }
            }
            Text {
                attr {
                    text("设置同步至电脑端 DSH（~/.dsh/settings.yaml）")
                    marginTop(20f)
                    marginBottom(28f)
                    fontSize(11f)
                    textAlignCenter()
                    color(colors().labelTertiary)
                }
            }
        }
    }
}

internal fun dshSettingsPermissionLabel(snapshot: DshSettingsSnapshot): String {
    val current = snapshot.permissionPreset
    if (current.isEmpty()) return "电脑端未提供"
    return snapshot.permissionChoices.firstOrNull { it.value == current }?.label?.ifEmpty { current } ?: current
}

internal fun dshSettingsLocaleLabel(snapshot: DshSettingsSnapshot): String = when (snapshot.localeValue) {
    "zh" -> "简体中文"
    "en" -> "English"
    else -> "跟随电脑端"
}

/** 由页面的 observable 主题模式驱动，颜色不变时也能更新外观文字。 */
internal fun dshThemeModeLabel(mode: DshThemeMode): String = when (mode) {
    DshThemeMode.LIGHT -> "浅色"
    DshThemeMode.DARK -> "深色"
    DshThemeMode.SYSTEM -> "跟随系统"
}


internal fun dshSettingsDefaultModelLabel(snapshot: DshSettingsSnapshot): String =
    snapshot.defaultModelLabel.ifEmpty { "未设置" }

internal fun ViewContainer<*, *>.DshSettingsGroupTitle(title: String, colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light }) {
    Text {
        attr {
            text(title)
            marginTop(20f)
            marginBottom(4f)
            marginLeft(16f)
            fontSize(13f)
            color(colors().labelTertiary)
        }
    }
}

internal fun ViewContainer<*, *>.DshSettingsRow(
    icon: String,
    title: String,
    value: () -> String,
    onClick: () -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    View {
        attr {
            height(52f)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(16f)
            paddingRight(12f)
            backgroundColor(colors().bgBase)
        }
        Image {
            attr {
                src(ImageUri.commonAssets(icon))
                size(20f, 20f)
                tintColor(colors().labelSecondary)
            }
        }
        Text {
            attr {
                text(title)
                flex(1f)
                marginLeft(12f)
                fontSize(14f)
                color(colors().labelPrimary)
            }
        }
        vif({ value().isNotEmpty() }) {
            Text {
                attr {
                    text(value())
                    fontSize(13f)
                    color(colors().labelTertiary)
                }
            }
        }
        Image {
            attr {
                src(ImageUri.commonAssets("chevron-right.svg"))
                size(16f, 16f)
                marginLeft(6f)
                tintColor(colors().labelTertiary)
            }
        }
        event { click { onClick() } }
    }
    View {
        attr {
            height(1f)
            marginLeft(16f)
            backgroundColor(colors().specificSelector)
        }
    }
}

// 通用选项选择器（设置页底部弹出：权限预设 / 语言 / 外观）
internal fun ViewContainer<*, *>.DshSettingsChoicePicker(
    title: String,
    options: () -> ObservableList<DshSettingsChoice>,
    selectedValue: () -> String,
    busy: () -> Boolean,
    onClose: () -> Unit,
    onSelect: (DshSettingsChoice) -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    Modal(inWindow = true) {
        attr {
            absolutePositionAllZero()
            flexDirectionColumn()
            justifyContentFlexEnd()
            backgroundColor(Color(0x55000000))
        }
        View {
            attr { flex(1f) }
            event { click { if (!busy()) onClose() } }
        }
        View {
            attr {
                flexDirectionColumn()
                padding(18f)
                backgroundColor(colors().bgLayer1)
                borderRadius(BorderRectRadius(20f, 20f, 0f, 0f))
            }
            View {
                attr { height(40f); flexDirectionRow(); alignItemsCenter() }
                Text {
                    attr {
                        text(title)
                        fontSize(17f)
                        fontWeightBold()
                        color(colors().labelPrimary)
                    }
                }
                View { attr { flex(1f) } }
                View {
                    attr { size(36f, 36f); allCenter() }
                    Image { attr { src(ImageUri.commonAssets("x.svg")); size(21f, 21f); tintColor(colors().labelSecondary) } }
                    event { click { if (!busy()) onClose() } }
                }
            }
            vfor({ options() }) { option ->
                View {
                    attr {
                        height(52f)
                        marginTop(4f)
                        flexDirectionRow()
                        alignItemsCenter()
                        paddingLeft(14f)
                        paddingRight(14f)
                        borderRadius(9f)
                        backgroundColor(if (option.value == selectedValue()) colors().stateBusinessTertiary else Color(0x00FFFFFF))
                    }
                    Text {
                        attr {
                            text(option.label.ifEmpty { option.value })
                            flex(1f)
                            fontSize(14f)
                            color(if (option.value == selectedValue()) colors().stateBusinessPrimary else colors().labelPrimary)
                        }
                    }
                    vif({ option.value == selectedValue() }) {
                        Image {
                            attr {
                                src(ImageUri.commonAssets("check.svg"))
                                size(18f, 18f)
                                tintColor(colors().stateBusinessPrimary)
                            }
                        }
                    }
                    event { click { if (!busy()) onSelect(option) } }
                }
            }
        }
    }
}
