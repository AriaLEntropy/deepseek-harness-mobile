package com.example.dsh.home

import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.views.compose.Button
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme

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
 * 会话抽屉右缘内阴影层：由宽到窄、由浅到深，多层叠加后叠加出平滑衰减。
 * width 为从右缘向内扩散的宽度，alpha 为边缘处的黑色透明度（0..255）。
 */
internal val DSH_DRAWER_EDGE_SHADOW_LAYERS = listOf(
    72f to 0x06,
    56f to 0x08,
    40f to 0x0A,
    26f to 0x0C,
    14f to 0x10,
)

/**
 * 会话抽屉右缘内阴影：叠加多层由右向左的线性渐变，模拟阴影从右缘向侧栏内部扩散，
 * 越靠右越深、向左平滑衰减并融入侧栏底色，同时让侧栏与右侧内容的分界线保持清晰。
 */
internal fun ViewContainer<*, *>.DshDrawerEdgeShadow() {
    DSH_DRAWER_EDGE_SHADOW_LAYERS.forEach { (width, alpha) ->
        View {
            attr {
                positionAbsolute()
                top(0f)
                right(0f)
                bottom(0f)
                width(width)
                backgroundLinearGradient(
                    Direction.TO_RIGHT,
                    ColorStop(Color.TRANSPARENT, 0f),
                    ColorStop(Color(alpha shl 24), 1f),
                )
            }
        }
    }
}

/**
 * 移动端会话抽屉（对齐 dsh 原版侧边栏）：
 * 顶部为品牌与搜索入口，中部为会话/工作区列表，底部固定「设置」入口。
 * 搜索入口点击后进入独立的 [DshSessionSearchOverlay] 全屏界面。
 * 远程时间线（isWebTimeline）下，工作区渲染为内嵌菜单式文件夹：文件夹行带
 * 打开/关闭文件夹图标与旋转箭头（收起→右、展开→下，与原版一致），会话行
 * 缩进排列；每行会话带相对时间与 ⋯ 溢出按钮（复用主页面的 DshOverflowMenu）。
 */
