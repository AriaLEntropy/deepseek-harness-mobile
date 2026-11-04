package com.example.dsh.ui.chat

import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vbind
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.Text
import com.example.dsh.ui.home.busAgentIconIsPhoto
import com.tencent.kuikly.core.views.View

/**
 * 对话页输入条（对照 .artifacts/agentbus-chat-detail.html 的 .comp/.crow/.bar2）
 *
 * 结构从上到下：只读说明行 → 芯片行（工作区 + 自动批准）→ 单行输入胶囊
 * 运行状态只由胶囊右侧那颗键表达（方块=可中断，箭头=可发送，浅灰=禁用），
 * 组件自己不持有状态，所有显隐与开关都由调用方通过 lambda 传入
 */

/** 芯片文字上限：工作区路径可能很长，省略后仍要给模型芯片留位置 */
private val BUS_CHAT_CHIP_LABEL_MAX: Float get() = busChatPx(150f)

/** 模型芯片文字上限，超长省略 */
private val BUS_CHAT_MODEL_LABEL_MAX: Float get() = busChatPx(110f)

/** 透明底：Kuikly 没有 transparent 常量，Input 需要把宿主底色露出来 */
private val busChatComposerTransparent: Color = Color(0x00000000)

/** 发送键的三种形态：上箭头发送、方块中断、浅灰禁用 */
private enum class BusChatSendState { Send, Stop, Disabled }

/** 发送键当前形态；sending 只影响可点性与透明度，不改形态 */
private data class BusChatSendVisual(val state: BusChatSendState, val busy: Boolean)

/**
 * 发送键形态推导
 *
 * 只读优先于运行中：只读会话连停止都不该点；空闲且草稿为空时降级为禁用，
 * 这样空输入不会亮起一颗假的实心键
 */
private fun busChatSendVisual(
    running: Boolean,
    readOnly: Boolean,
    sending: Boolean,
): BusChatSendVisual {
    val state = when {
        readOnly -> BusChatSendState.Disabled
        running -> BusChatSendState.Stop
        else -> BusChatSendState.Send
    }
    return BusChatSendVisual(state, sending)
}

/**
 * 输入条主体
 *
 * 键盘高度直接顶起整条输入区（marginBottom 跟 keyboardHeight 同值并加动画），
 * 不再自己叠一层底部安全区，否则键盘收起后会多出一截空白
 */
internal fun ViewContainer<*, *>.BusChatComposer(
    palette: BusChatPalette,
    draft: () -> String,
    placeholder: () -> String,
    running: () -> Boolean,
    readOnly: () -> Boolean,
    sending: () -> Boolean,
    modelLabel: () -> String,
    workspaceLabel: () -> String,
    agentName: () -> String,
    agentAvatar: String,
    draftMode: () -> Boolean,
    keyboardUp: () -> Boolean,
    showAutoApprove: () -> Boolean,
    autoApprove: () -> Boolean,
    onDraftChange: (String) -> Unit,
    onPlus: () -> Unit,
    onModel: () -> Unit,
    onAutoApprove: () -> Unit,
    onWorkspace: () -> Unit,
    onAgentPick: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    keyboardHeight: () -> Float,
    onKeyboardHeightChange: (Float) -> Unit,
    inputRef: (InputView) -> Unit,
) {
    View {
        attr {
            flexDirectionColumn()
            backgroundColor(palette.bgBase)
            paddingLeft(BusChatTokens.composerSide)
            paddingRight(BusChatTokens.composerSide)
            paddingTop(busChatPx(10f))
            // 整屏沉浸之后内容画到物理底边：没有虚拟导航栏时就是设计稿的 26，
            // 有三键/手势条时取两者较大值，让输入条自己挤到栏上方，而不是永远空一格
            paddingBottom(
                if (keyboardUp()) {
                    busChatPx(8f)
                } else {
                    maxOf(BusChatTokens.composerBottom, pagerData.safeAreaInsets.bottom)
                },
            )
            marginBottom(keyboardHeight())
            animation(Animation.easeInOut(0.24f), keyboardHeight())
        }
        // 只读说明：贴在芯片行上方，一行灰字，不带图标底盘
        vif({ readOnly() }) {
            View {
                attr {
                    flexDirectionRow()
                    alignItemsCenter()
                    marginBottom(busChatPx(7f))
                }
                Image {
                    attr {
                        src(ImageUri.commonAssets("warning-outline.svg"))
                        size(busChatPx(13f), busChatPx(13f))
                        tintColor(palette.mut)
                    }
                }
                Text {
                    attr {
                        text(BUS_CHAT_READONLY_HINT)
                        fontSize(busChatPx(11.5f))
                        lineHeight(busChatPx(11.5f))
                        color(palette.mut)
                        marginLeft(busChatPx(5f))
                        flex(1f)
                        lines(1)
                        textOverFlowTail()
                    }
                }
            }
        }
        // 草稿态：键盘弹起时从输入条上方长出「选择工作区 / 选择 Agent」两行
        // 键盘收起或已创建会话时高度收回 0，不占位
        View {
            attr {
                val show = draftMode() && keyboardUp() && !readOnly()
                val h = if (show) busChatPx(62f) else 0f
                height(h)
                overflow(true)
                opacity(if (show) 1f else 0f)
                animation(Animation.easeOut(0.16f), h)
                flexDirectionColumn()
                marginBottom(if (show) busChatPx(6f) else 0f)
            }
            vbind({ workspaceLabel().isBlank() }) {
                BusChatDraftChoice(
                    palette = palette,
                    asset = "folder.svg",
                    label = workspaceLabel().ifBlank { "选择工作区" },
                    avatar = "",
                    picked = workspaceLabel().isNotBlank(),
                    onClick = onWorkspace,
                )
            }
            vbind({ agentName().isBlank() }) {
                BusChatDraftChoice(
                    palette = palette,
                    asset = if (agentName().isBlank()) "agent-default.svg" else "",
                    label = agentName().ifBlank { "选择 Agent" },
                    avatar = if (agentName().isBlank()) "" else agentAvatar,
                    picked = agentName().isNotBlank(),
                    onClick = onAgentPick,
                )
            }
        }
        // 自动批准仍在胶囊上方单独一行；没有时整行不占位
        vif({ showAutoApprove() }) {
            View {
                attr {
                    height(BusChatTokens.chipHeight)
                    flexDirectionRow()
                    alignItemsCenter()
                    // .crow 与 .bar2 之间的 6
                    marginBottom(busChatPx(6f))
                }
                vbind({ autoApprove() }) {
                    BusChatComposerChip(
                        palette = palette,
                        asset = if (autoApprove()) "check.svg" else "",
                        label = "自动批准",
                        active = autoApprove(),
                        maxLabelWidth = BUS_CHAT_CHIP_LABEL_MAX,
                    ) { onAutoApprove() }
                }
            }
        }
        // 只读时整条胶囊压到 .55 并锁住加号与发送键，所以按只读态重建这一层
        vbind({ readOnly() }) {
            val dimmed = readOnly()
            View {
                attr {
                    height(BusChatTokens.capsuleHeight)
                    opacity(if (dimmed) 0.55f else 1f)
                }
                // Kuikly 一个节点只支持一层 boxShadow。设计稿的 0 1 2 接触影
                // 在真机上会变成一条硬边，这里只保留 0 12 30 的软影
                View {
                    attr {
                        height(BusChatTokens.capsuleHeight)
                        flexDirectionRow()
                        alignItemsCenter()
                        // 44 定高里上下各让 7，28 的圆键居中；横向同样 7
                        paddingLeft(BusChatTokens.capsuleInset)
                        paddingRight(BusChatTokens.capsuleInset)
                        borderRadius(BusChatTokens.capsuleRadius)
                        backgroundColor(palette.surface)
                        border(Border(1f, BorderStyle.SOLID, palette.surfaceBorder))
                        // 设计稿是 0 1 2 + 0 12 30 两层影，Kuikly 只留一层软影；
                        // 页面下沿只留 26 设计 px，向下投影会被裁成硬边；用 0 0 10 的均匀柔光，靠范围出体积感
                        // 没有 backdrop blur：Kuikly 不支持实时背景模糊，用 94% 的白压一层代替
                        boxShadow(BoxShadow(0f, 0f, 10f, BusChatTokens.composerShadow))
                    }
                // 加号：只读时变浅灰且不响应
                View {
                    attr {
                        size(BusChatTokens.roundKey, BusChatTokens.roundKey)
                        borderRadius(BusChatTokens.roundKey / 2f)
                        backgroundColor(palette.field)
                        border(Border(1f, BorderStyle.SOLID, palette.line))
                        alignItemsCenter()
                        justifyContentCenter()
                    }
                    event { click { if (!dimmed) onPlus() } }
                    Image {
                        attr {
                            src(ImageUri.commonAssets("plus.svg"))
                            size(BusChatTokens.glyph, BusChatTokens.glyph)
                            tintColor(if (dimmed) palette.faint else palette.deep)
                        }
                    }
                }
                // 输入：占位文案在 attr 里读，运行态切换只更新属性，不重建输入框
                Input {
                    ref { viewRef -> viewRef.view?.let(inputRef) }
                    attr {
                        flex(1f)
                        height(busChatPx(28f))
                        marginLeft(busChatPx(7f))
                        marginRight(busChatPx(7f))
                        fontSize(busChatPx(14.5f))
                        color(palette.ink)
                        placeholder(placeholder())
                        placeholderColor(palette.faint)
                        backgroundColor(busChatComposerTransparent)
                    }
                    event {
                        textDidChange { onDraftChange(it.text) }
                        keyboardHeightChange { onKeyboardHeightChange(it.height) }
                        inputBlur { onKeyboardHeightChange(0f) }
                    }
                }
                // 模型芯片：文案 + 右侧小箭头，只读时点了没反应
                vbind({ modelLabel() }) {
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            marginRight(busChatPx(7f))
                        }
                        event { click { if (!dimmed) onModel() } }
                        Text {
                            attr {
                                text(modelLabel())
                                fontSize(BusChatTokens.modelChip)
                                lineHeight(BusChatTokens.modelChip)
                                fontWeightSemiBold()
                                color(palette.ink2)
                                letterSpacing(busChatPx(-0.1f))
                                maxWidth(BUS_CHAT_MODEL_LABEL_MAX)
                                lines(1)
                                textOverFlowTail()
                            }
                        }
                        Image {
                            attr {
                                src(ImageUri.commonAssets("chevron-down.svg"))
                                size(BusChatTokens.modelChevron, BusChatTokens.modelChevron)
                                tintColor(palette.mut)
                                marginLeft(busChatPx(2f))
                            }
                        }
                    }
                }
                // 发送键：形态由运行中/只读/空草稿三件事决定
                vbind({ busChatSendVisual(running(), readOnly(), sending()) }) {
                    val visual = busChatSendVisual(running(), readOnly(), sending())
                    val stop = visual.state == BusChatSendState.Stop
                    val disabled = visual.state == BusChatSendState.Disabled
                    View {
                        attr {
                            size(BusChatTokens.roundKey, BusChatTokens.roundKey)
                            borderRadius(BusChatTokens.roundKey / 2f)
                            alignItemsCenter()
                            justifyContentCenter()
                            backgroundColor(if (disabled) palette.chip else palette.solid)
                            opacity(
                                if (visual.state == BusChatSendState.Send && visual.busy) 0.6f else 1f,
                            )
                        }
                        event {
                            click {
                                when (visual.state) {
                                    BusChatSendState.Stop -> onStop()
                                    BusChatSendState.Send -> if (!visual.busy) onSend()
                                    BusChatSendState.Disabled -> Unit
                                }
                            }
                        }
                        if (stop) {
                            // 停止是实心方块：官方 square.svg 是描边矩形，缩到 12 会变成空心线框
                            View {
                                attr {
                                    size(BusChatTokens.stopSquare, BusChatTokens.stopSquare)
                                    borderRadius(busChatPx(2f))
                                    backgroundColor(palette.onSolid)
                                }
                            }
                        } else {
                            Image {
                                attr {
                                    src(ImageUri.commonAssets("arrow-up.svg"))
                                    size(BusChatTokens.glyph, BusChatTokens.glyph)
                                    tintColor(if (disabled) palette.faint else palette.onSolid)
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

/**
 * 草稿态的一枚选择芯片：26 高、r999，未选时灰字，选完回到主文字色
 *
 * 工作区用 folder 图标；Agent 未选时用中性头像，选完换成真实头像
 */
private fun ViewContainer<*, *>.BusChatDraftChoice(
    palette: BusChatPalette,
    asset: String,
    label: String,
    avatar: String,
    picked: Boolean,
    onClick: () -> Unit,
) {
    View {
        attr {
            height(busChatPx(26f))
            flexDirectionRow()
            alignItemsCenter()
            alignSelfFlexStart()
            paddingLeft(busChatPx(9f))
            paddingRight(busChatPx(9f))
            marginBottom(busChatPx(6f))
            borderRadius(busChatPx(999f))
            backgroundColor(palette.surface)
            border(Border(1f, BorderStyle.SOLID, palette.line))
        }
        event { click { onClick() } }
        if (avatar.isNotBlank()) {
            View {
                attr {
                    size(busChatPx(14f), busChatPx(14f))
                    borderRadius(busChatPx(7f))
                    backgroundColor(palette.field)
                    alignItemsCenter()
                    justifyContentCenter()
                }
                Image {
                    attr {
                        src(ImageUri.commonAssets(avatar))
                        size(busChatPx(14f), busChatPx(14f))
                        if (busAgentIconIsPhoto(avatar)) resizeCover() else resizeContain()
                    }
                }
            }
        } else if (asset.isNotBlank()) {
            val defaultAvatar = asset == "agent-default.svg"
            Image {
                attr {
                    src(ImageUri.commonAssets(asset))
                    size(
                        busChatPx(if (defaultAvatar) 14f else 12f),
                        busChatPx(if (defaultAvatar) 14f else 12f),
                    )
                    if (defaultAvatar) {
                        resizeCover()
                    } else {
                        tintColor(if (picked) palette.ink else palette.deep)
                    }
                }
            }
        }
        Text {
            attr {
                text(label)
                fontSize(busChatPx(11f))
                lineHeight(busChatPx(11f))
                fontWeightSemiBold()
                color(if (picked) palette.ink else palette.deep)
                marginLeft(busChatPx(5f))
                maxWidth(BUS_CHAT_CHIP_LABEL_MAX)
                lines(1)
                textOverFlowTail()
            }
        }
        Image {
            attr {
                src(ImageUri.commonAssets("chevron-down.svg"))
                size(busChatPx(8f), busChatPx(8f))
                tintColor(palette.mut)
                marginLeft(busChatPx(4f))
            }
        }
    }
}

/**
 * 芯片行的单颗胶囊：白底 + 发丝描边，选中态翻成墨底
 *
 * 尺寸固定 20 高、字 10.5/600，文字超长省略而不是撑开行
 */
private fun ViewContainer<*, *>.BusChatComposerChip(
    palette: BusChatPalette,
    asset: String,
    label: String,
    active: Boolean,
    maxLabelWidth: Float,
    onClick: () -> Unit,
) {
    View {
        attr {
            height(BusChatTokens.chipHeight)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(busChatPx(7f))
            paddingRight(busChatPx(7f))
            // 两个芯片之间 6，行尾那点留白自然被裁掉
            marginRight(busChatPx(6f))
            borderRadius(busChatPx(999f))
            backgroundColor(if (active) palette.solid else palette.surface)
            border(Border(1f, BorderStyle.SOLID, if (active) palette.solid else palette.line))
        }
        event { click { onClick() } }
        if (asset.isNotBlank()) {
            Image {
                attr {
                    src(ImageUri.commonAssets(asset))
                    size(busChatPx(11f), busChatPx(11f))
                    tintColor(if (active) palette.onSolid else palette.deep)
                }
            }
        }
        Text {
            attr {
                text(label)
                fontSize(BusChatTokens.chipFont)
                lineHeight(BusChatTokens.chipFont)
                fontWeightSemiBold()
                color(if (active) palette.onSolid else palette.deep)
                marginLeft(busChatPx(3f))
                maxWidth(maxLabelWidth)
                lines(1)
                textOverFlowTail()
            }
        }
    }
}
