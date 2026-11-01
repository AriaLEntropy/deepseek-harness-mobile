package com.example.dsh.ui.home

import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.Rotate
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.layout.FlexPositionType
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * Agent 选择面板（多选 + 可展开详情）。
 *
 * 只负责渲染：多选集合、展开行、重启中的行、长按 lastError 浮层这些状态都由调用方
 * 以 lambda 传进来（attr 内读页面 observable 才能局部刷新），面板自己不发任何命令；
 * 勾选语义走 `busToggleAgentFilter`（空集合 = 不过滤 = 全部 Agent）。
 *
 * 视觉全部对齐 `BusSheetPanel` 外壳 + 视觉规范 16.8：行卡白底 radius 14、
 * padding 8/14、行间距 5、未选中描边 #EFEFF2、报错行 1.5px #E5484D、
 * 停用行 opacity .55 且 padding 收为 4/12；勾选框 22 圆，选中 #1D1B20 + 白勾。
 */
internal fun ViewContainer<*, *>.BusAgentSheet(
    agents: List<BusHomeAgent>,
    palette: BusV86Palette,
    pageWidth: Float,
    pageHeight: Float,
    canRestartConnectors: Boolean,
    selectedKeys: () -> Set<String>,
    expandedKey: () -> String,
    busyKey: () -> String,
    errorKey: () -> String,
    onClose: () -> Unit,
    onToggle: (String) -> Unit,
    onExpand: (String) -> Unit,
    onRestart: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    onShowError: (String) -> Unit,
    onHideError: () -> Unit,
) {
    BusSheetPanel(
        title = "选择 Agent",
        palette = palette,
        pageWidth = pageWidth,
        pageHeight = pageHeight,
        onClose = { onHideError(); onClose() },
        floating = {
            // 长按浮层：盖在面板上，绝对定位；点它本身也关闭。
            vif({ errorKey().isNotEmpty() }) {
                BusAgentErrorPopup(
                    agent = agents.firstOrNull { it.key == errorKey() },
                    rowWidth = busSheetRowWidth(pageWidth),
                    palette = palette,
                    onTap = onHideError,
                )
            }
        },
    ) {
        if (busHomeAgentsEmpty(agents)) {
            BusAgentSheetEmptyState(palette = palette)
        } else {
            agents.forEach { agent ->
                BusAgentRow(
                    agent = agent,
                    rowWidth = busSheetRowWidth(pageWidth),
                    palette = palette,
                    canRestartConnectors = canRestartConnectors,
                    selected = { agent.key in selectedKeys() },
                    expanded = { busAgentRowIsExpanded(expandedKey(), agent.key) },
                    busy = { busyKey() == agent.key },
                    onToggle = {
                        onHideError()
                        onToggle(agent.key)
                    },
                    onExpand = {
                        onHideError()
                        onExpand(agent.key)
                    },
                    onRestart = { onRestart(agent.key) },
                    onOpenDetail = { onOpenDetail(agent.key) },
                    onLongPressError = { onShowError(agent.key) },
                )
            }
        }
    }
}

// ---------- 判定与文案映射（纯函数，页面与单测共用） ----------

/** 行右侧标签的种类：只决定配色，文案与种类分开，方便单测。 */
internal enum class BusAgentBadgeKind {
    /** 红：启动失败 / 异常 */
    Error,

    /** 琥珀：需确认 */
    Pending,

    /** 灰：未安装 / 已停用 */
    Muted,
}

/** 面板行右侧的状态标签。 */
internal data class BusAgentBadge(val label: String, val kind: BusAgentBadgeKind)

/**
 * 状态标签映射。
 *
 * 顺序按信息价值排：有 `lastError` 先说"启动失败"（红），其次不可用/停用（灰），
 * 再看连接器状态 error/degraded。全绿时不显示标签。
 */
internal fun busAgentRowBadge(agent: BusHomeAgent): BusAgentBadge? = when {
    agent.lastError.isNotBlank() -> BusAgentBadge("启动失败", BusAgentBadgeKind.Error)
    !agent.available -> BusAgentBadge("未安装", BusAgentBadgeKind.Muted)
    !agent.enabled -> BusAgentBadge("已停用", BusAgentBadgeKind.Muted)
    agent.status == "error" -> BusAgentBadge("异常", BusAgentBadgeKind.Error)
    agent.status == "degraded" -> BusAgentBadge("需确认", BusAgentBadgeKind.Pending)
    else -> null
}

/** 报错行：红描边 + lastError 摘要 + 重试/详情按钮（验收级 16.10 第 7 条）。 */
internal fun busAgentRowIsError(agent: BusHomeAgent): Boolean =
    agent.lastError.isNotBlank() || agent.status == "error"

/** 停用行：整行 opacity .55、padding 收为 4/12、名字转灰。 */
internal fun busAgentRowIsDisabled(agent: BusHomeAgent): Boolean = !agent.available || !agent.enabled

/** 停用/未安装的 Agent 不可勾选（验收级 16.10 第 7 条）。 */
internal fun busAgentRowIsCheckable(agent: BusHomeAgent): Boolean = !busAgentRowIsDisabled(agent)

/** 展开态：面板同时只展开一行，空 key = 全部收起。 */
internal fun busAgentRowIsExpanded(expandedKey: String, agentKey: String): Boolean =
    expandedKey.isNotEmpty() && expandedKey == agentKey

/**
 * 点 chevron 的纯函数版：再点同一个 key 收起。调用方用它算新的 `expandedKey`，
 * 面板自己不改状态。
 */
internal fun busToggleAgentExpanded(expandedKey: String, agentKey: String): String =
    if (expandedKey == agentKey) "" else agentKey

/** 模型摘要：名字用「 · 」连接；没有模型给一个短横占位。 */
internal fun busAgentModelSummary(models: List<BusHomeModelOption>): String =
    if (models.isEmpty()) {
        BUS_AGENT_EMPTY_VALUE
    } else {
        models.joinToString(" · ") { it.displayName.ifBlank { it.id } }
    }

/** 副行「N 个任务」。 */
internal fun busAgentTaskCountLabel(taskCount: Int): String = "$taskCount 个任务"

/** 重启按钮文案：进行中改成「重启中…」，避免重复下发。 */
internal fun busAgentRetryLabel(isBusy: Boolean): String = if (isBusy) "重启中…" else "重试启动"

/**
 * 官方图标是不是照片类（jpg/png）。
 *
 * 照片类铺满 36 圆（cover + 白底），线性类 contain 在 36 圆内（#F5F5F7 底）。
 */
internal fun busAgentIconIsPhoto(asset: String): Boolean = when (asset.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg", "png", "webp" -> true
    else -> false
}

/** 展开区的能力矩阵：文本行 + ✓/✗ 行两种形状，[supported] 为 null 时走 [text]。 */
internal data class BusAgentCapabilityRow(
    val label: String,
    val text: String = "",
    val supported: Boolean? = null,
)

/**
 * 能力矩阵内容（对齐设计稿展开区）：任务数、可用模型、可新建任务、
 * 支持自动批准、可删除会话、重启连接器（电脑级）。
 *
 * 「可新建任务」直接用模型层的 [BusHomeAgent.canStartTask]，
 * 「重启连接器」看这台电脑有没有报 `canRestartConnectors`。
 */
internal fun busAgentCapabilityRows(
    agent: BusHomeAgent,
    canRestartConnectors: Boolean,
): List<BusAgentCapabilityRow> = listOf(
    BusAgentCapabilityRow(label = "任务数", text = agent.taskCount.toString()),
    BusAgentCapabilityRow(label = "可用模型", text = busAgentModelSummary(agent.models)),
    BusAgentCapabilityRow(label = "可新建任务", supported = agent.canStartTask),
    BusAgentCapabilityRow(label = "支持自动批准", supported = agent.canAutoApprove),
    BusAgentCapabilityRow(label = "可删除会话", supported = agent.canDeleteTasks),
    BusAgentCapabilityRow(label = "重启连接器（电脑级）", supported = canRestartConnectors),
)

/** 没有文本值时的占位。 */
internal const val BUS_AGENT_EMPTY_VALUE = "—"

// ---------- 面板级尺寸（只在这里用，不进共用 token） ----------

private object AgentSheetTokens {
    /** 行名 14/600（[BusV86Tokens] 只有 13 与 15） */
    const val fontSizeRowName: Float = 14f

    /** 勾选框 22 圆，内部白勾 12 */
    const val checkboxSize: Float = 22f
    const val checkboxIconSize: Float = 12f

    /** 未选中勾选框描边 1.5 */
    const val checkboxBorderWidth: Float = 1.5f

    /** 展开箭头 12 */
    const val chevronSize: Float = 12f

    /** 状态标签：radius 9、padding 2/8、11 号 */
    const val tagRadius: Float = 9f
    const val tagPaddingVertical: Float = 2f
    const val tagPaddingHorizontal: Float = 8f

    /** 面板内小按钮：radius 8、padding 4/10、按钮间距 8 */
    const val buttonRadius: Float = 8f
    const val buttonPaddingVertical: Float = 4f
    const val buttonPaddingHorizontal: Float = 10f
    const val buttonGap: Float = 8f

    /** ✓/✗ 值位 16×16；✗ 是 1.5 高的短横 */
    const val markBoxSize: Float = 16f
    const val markIconSize: Float = 14f
    const val markDashWidth: Float = 8f
    const val markDashHeight: Float = 1.5f

    /** 浮层：标题图标 14、行距 18、阴影模糊 24 */
    const val popupIconSize: Float = 14f
    const val popupLineHeight: Float = 18f
    const val popupShadowRadius: Float = 24f

    /** 空态插画 92 */
    const val emptyIconSize: Float = 92f

    /** 官方图标为线性类时的可见尺寸（36 底盘内约 4px 内缩） */
    const val lineIconSize: Float = 28f
}

// ---------- 渲染 ----------

private fun ViewContainer<*, *>.BusAgentRow(
    agent: BusHomeAgent,
    rowWidth: Float,
    palette: BusV86Palette,
    canRestartConnectors: Boolean,
    selected: () -> Boolean,
    expanded: () -> Boolean,
    busy: () -> Boolean,
    onToggle: () -> Unit,
    onExpand: () -> Unit,
    onRestart: () -> Unit,
    onOpenDetail: () -> Unit,
    onLongPressError: () -> Unit,
) {
    val disabled = busAgentRowIsDisabled(agent)
    val hasError = busAgentRowIsError(agent)
    val paddingVertical =
        if (disabled) BusSheetTokens.rowPaddingDisabledVertical else BusSheetTokens.rowPaddingVertical
    val paddingHorizontal =
        if (disabled) BusSheetTokens.rowPaddingDisabledHorizontal else BusSheetTokens.rowPaddingHorizontal
    View {
        attr {
            width(rowWidth)
            marginBottom(BusSheetTokens.rowGap)
            borderRadius(BusSheetTokens.rowRadius)
            backgroundColor(busSheetRowFill(palette))
            border(
                Border(
                    if (hasError) BusSheetTokens.rowBorderWidthStrong else BusSheetTokens.rowBorderWidth,
                    BorderStyle.SOLID,
                    if (hasError) BusSheetTokens.errorBorder else palette.chipFill,
                ),
            )
            paddingTop(paddingVertical)
            paddingBottom(paddingVertical)
            paddingLeft(paddingHorizontal)
            paddingRight(paddingHorizontal)
            opacity(if (disabled) BusSheetTokens.rowDisabledOpacity else 1f)
        }
        // 长按报错行弹 lastError 浮层（设计稿 500ms，交给系统长按阈值）；
        // 只有真的带 lastError 的行才响应。
        event {
            longPress { params ->
                if (params.isStart && agent.lastError.isNotBlank()) onLongPressError()
            }
        }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
            }
            BusAgentIconDisc(
                agent = agent,
                isPhoto = busAgentIconIsPhoto(busAgentAssetName(agent)),
                palette = palette,
            )
            View {
                attr {
                    flex(1f)
                    flexDirectionColumn()
                    marginLeft(BusSheetTokens.rowGapInner)
                }
                // 名称 + 状态标签
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsCenter()
                    }
                    Text {
                        attr {
                            text(agent.name.ifBlank { agent.key })
                            fontSize(AgentSheetTokens.fontSizeRowName)
                            fontWeightBold()
                            color(if (disabled) palette.textTertiary else palette.textPrimary)
                            flex(1f)
                            lines(1)
                            textOverFlowTail()
                        }
                    }
                    BusAgentBadgeView(busAgentRowBadge(agent), palette)
                }
                // 任务数 + 展开箭头
                View {
                    attr {
                        flexDirectionRow()
                        alignItemsCenter()
                        marginTop(2f)
                    }
                    Text {
                        attr {
                            text(busAgentTaskCountLabel(agent.taskCount))
                            fontSize(BusV86Tokens.fontSizeCaption)
                            color(if (disabled) BusV86Tokens.textQuiet else palette.textTertiary)
                        }
                    }
                    View {
                        attr {
                            size(AgentSheetTokens.chevronSize, AgentSheetTokens.chevronSize)
                            marginLeft(6f)
                            alignItemsCenter()
                            justifyContentCenter()
                        }
                        event { click { onExpand() } }
                        Image {
                            attr {
                                src(ImageUri.commonAssets("chevron-down.svg"))
                                size(AgentSheetTokens.chevronSize, AgentSheetTokens.chevronSize)
                                tintColor(if (disabled) BusV86Tokens.textQuiet else palette.textTertiary)
                                // 展开时箭头转 180°（设计稿 transition transform .2s）
                                transform(Rotate(if (expanded()) 180f else 0f))
                            }
                        }
                    }
                }
                // 报错行：lastError 一行 + 重试启动 / 查看详情
                if (agent.lastError.isNotBlank()) {
                    Text {
                        attr {
                            text(agent.lastError)
                            fontSize(BusV86Tokens.fontSizeCaption)
                            color(palette.error)
                            marginTop(2f)
                            lines(1)
                            textOverFlowTail()
                        }
                    }
                }
                if (hasError) {
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                            marginTop(6f)
                        }
                        BusAgentTextButton(
                            label = { busAgentRetryLabel(busy()) },
                            foreground = palette.textPrimary,
                            background = BusV86Tokens.tagFill,
                            borderless = true,
                            enabled = { !busy() },
                            onClick = onRestart,
                        )
                        BusAgentTextButton(
                            label = { "查看详情" },
                            foreground = palette.textSecondary,
                            background = busSheetRowFill(palette),
                            borderless = false,
                            enabled = { true },
                            onClick = onOpenDetail,
                            marginLeft = AgentSheetTokens.buttonGap,
                        )
                    }
                }
            }
            // 勾选框：停用行不给勾
            if (busAgentRowIsCheckable(agent)) {
                View {
                    attr {
                        size(AgentSheetTokens.checkboxSize, AgentSheetTokens.checkboxSize)
                        borderRadius(AgentSheetTokens.checkboxSize / 2f)
                        marginLeft(BusSheetTokens.rowGapInner)
                        alignItemsCenter()
                        justifyContentCenter()
                        backgroundColor(if (selected()) palette.selectedFill else busSheetRowFill(palette))
                        // 选中时描边与底同色，等价于"无边"，但保持单条属性调用便于响应式更新
                        border(
                            Border(
                                AgentSheetTokens.checkboxBorderWidth,
                                BorderStyle.SOLID,
                                if (selected()) palette.selectedFill else BusV86Tokens.lineDashed,
                            ),
                        )
                    }
                    event { click { if (!busy()) onToggle() } }
                    Image {
                        attr {
                            src(ImageUri.commonAssets("check.svg"))
                            size(AgentSheetTokens.checkboxIconSize, AgentSheetTokens.checkboxIconSize)
                            tintColor(BusV86Tokens.selectedForeground)
                            opacity(if (selected()) 1f else 0f)
                        }
                    }
                }
            }
        }
        // 展开区：18dp 一行的能力矩阵
        vif({ expanded() }) {
            View {
                attr {
                    flexDirectionColumn()
                    marginTop(2f)
                }
                View {
                    attr {
                        height(1f)
                        backgroundColor(BusSheetTokens.rowDivider)
                    }
                }
                View {
                    attr {
                        paddingTop(2f)
                        flexDirectionColumn()
                    }
                    busAgentCapabilityRows(agent, canRestartConnectors).forEach { row ->
                        BusAgentCapabilityRowView(row, palette)
                    }
                }
            }
        }
    }
}

/** 图标资产名（官方图标统一走 [BusAgentVisuals]）。 */
private fun busAgentAssetName(agent: BusHomeAgent): String =
    BusAgentVisuals.assetFor(agent.source, agent.connectorId)

private fun ViewContainer<*, *>.BusAgentIconDisc(agent: BusHomeAgent, isPhoto: Boolean, palette: BusV86Palette) {
    View {
        attr {
            size(BusSheetTokens.rowIconSize, BusSheetTokens.rowIconSize)
            borderRadius(BusSheetTokens.rowIconSize / 2f)
            // 照片类铺满白底 cover，线性类 #F5F5F7 底 contain
            backgroundColor(if (isPhoto) busSheetRowFill(palette) else busSheetIconDiscFill(palette))
            alignItemsCenter()
            justifyContentCenter()
        }
        Image {
            attr {
                src(ImageUri.commonAssets(busAgentAssetName(agent)))
                if (isPhoto) {
                    size(BusSheetTokens.rowIconSize, BusSheetTokens.rowIconSize)
                    resizeCover()
                } else {
                    size(AgentSheetTokens.lineIconSize, AgentSheetTokens.lineIconSize)
                    resizeContain()
                }
            }
        }
    }
}

/** 状态标签：Error=红字红底、Pending=琥珀、Muted=灰。 */
private fun ViewContainer<*, *>.BusAgentBadgeView(badge: BusAgentBadge?, palette: BusV86Palette) {
    if (badge == null) return
    val colors = when (badge.kind) {
        // 设计稿：报错标签 11/500 #D92D20 on #FEE4E4
        BusAgentBadgeKind.Error -> palette.error to palette.errorFill
        // 「需确认」标签底色固定 #FBF1DF，文字 #9A6A1F
        BusAgentBadgeKind.Pending -> BusV86Tokens.pendingText to BusV86Tokens.pendingFill
        // 停用/未安装：11/500 #8E8E93 on #EFEFF2
        BusAgentBadgeKind.Muted -> palette.textTertiary to palette.chipFill
    }
    View {
        attr {
            borderRadius(AgentSheetTokens.tagRadius)
            backgroundColor(colors.second)
            paddingLeft(AgentSheetTokens.tagPaddingHorizontal)
            paddingRight(AgentSheetTokens.tagPaddingHorizontal)
            paddingTop(AgentSheetTokens.tagPaddingVertical)
            paddingBottom(AgentSheetTokens.tagPaddingVertical)
            marginLeft(8f)
        }
        Text {
            attr {
                text(badge.label)
                fontSize(BusV86Tokens.fontSizeCaption)
                fontWeightMedium()
                color(colors.first)
            }
        }
    }
}

private fun ViewContainer<*, *>.BusAgentCapabilityRowView(row: BusAgentCapabilityRow, palette: BusV86Palette) {
    View {
        attr {
            height(BusSheetTokens.capabilityRowHeight)
            flexDirectionRow()
            alignItemsCenter()
        }
        Text {
            attr {
                text(row.label)
                fontSize(BusV86Tokens.fontSizeMeta)
                color(palette.textTertiary)
            }
        }
        View {
            attr {
                flex(1f)
                flexDirectionRow()
                justifyContentFlexEnd()
                marginLeft(8f)
            }
            if (row.supported == null) {
                Text {
                    attr {
                        text(row.text)
                        fontSize(BusV86Tokens.fontSizeMeta)
                        fontWeightBold()
                        color(if (row.text == BUS_AGENT_EMPTY_VALUE) BusV86Tokens.textQuiet else palette.textPrimary)
                        lines(1)
                        textOverFlowTail()
                    }
                }
            } else {
                BusAgentCapabilityMark(supported = row.supported, palette = palette)
            }
        }
    }
}

/** ✓ 用黑色短勾，✗ 用一条 #C4C4CC 短横（设计稿 .cap-empty）。 */
private fun ViewContainer<*, *>.BusAgentCapabilityMark(supported: Boolean, palette: BusV86Palette) {
    View {
        attr {
            size(AgentSheetTokens.markBoxSize, AgentSheetTokens.markBoxSize)
            alignItemsCenter()
            justifyContentCenter()
        }
        if (supported) {
            Image {
                attr {
                    src(ImageUri.commonAssets("check.svg"))
                    size(AgentSheetTokens.markIconSize, AgentSheetTokens.markIconSize)
                    tintColor(palette.textPrimary)
                }
            }
        } else {
            View {
                attr {
                    width(AgentSheetTokens.markDashWidth)
                    height(AgentSheetTokens.markDashHeight)
                    borderRadius(AgentSheetTokens.markDashHeight / 2f)
                    backgroundColor(BusV86Tokens.textQuiet)
                }
            }
        }
    }
}

/**
 * 面板内小按钮。
 *
 * 文案 / 可用性都用 lambda，好让「重启中…」这种状态能局部刷新。
 */
private fun ViewContainer<*, *>.BusAgentTextButton(
    label: () -> String,
    foreground: Color,
    background: Color,
    borderless: Boolean,
    enabled: () -> Boolean,
    onClick: () -> Unit,
    marginLeft: Float = 0f,
) {
    View {
        attr {
            borderRadius(AgentSheetTokens.buttonRadius)
            backgroundColor(background)
            paddingLeft(AgentSheetTokens.buttonPaddingHorizontal)
            paddingRight(AgentSheetTokens.buttonPaddingHorizontal)
            paddingTop(AgentSheetTokens.buttonPaddingVertical)
            paddingBottom(AgentSheetTokens.buttonPaddingVertical)
            opacity(if (enabled()) 1f else BusSheetTokens.rowDisabledOpacity)
            // 白底描边按钮（查看详情）；另一个是纯色底按钮，同色描边等于无边
            border(
                Border(
                    BusSheetTokens.rowBorderWidth,
                    BorderStyle.SOLID,
                    if (borderless) background else BusV86Tokens.lineCard,
                ),
            )
            if (marginLeft > 0f) this.marginLeft(marginLeft)
        }
        event { click { if (enabled()) onClick() } }
        Text {
            attr {
                text(label())
                fontSize(BusV86Tokens.fontSizeCaption)
                fontWeightBold()
                color(foreground)
            }
        }
    }
}

/** 长按 lastError 浮层：白底 radius 14、1px #FDE2E2、阴影 0 8 24。 */
private fun ViewContainer<*, *>.BusAgentErrorPopup(
    agent: BusHomeAgent?,
    rowWidth: Float,
    palette: BusV86Palette,
    onTap: () -> Unit,
) {
    View {
        attr {
            positionType(FlexPositionType.ABSOLUTE)
            top(BusSheetTokens.popupTop)
            left(BusSheetTokens.scrollPadding)
            width(rowWidth)
            borderRadius(BusSheetTokens.popupRadius)
            backgroundColor(busSheetRowFill(palette))
            border(Border(BusSheetTokens.rowBorderWidth, BorderStyle.SOLID, BusSheetTokens.popupBorder))
            boxShadow(
                BoxShadow(
                    0f,
                    BusSheetTokens.popupOffsetY,
                    AgentSheetTokens.popupShadowRadius,
                    BusSheetTokens.popupShadow,
                ),
            )
            paddingTop(BusSheetTokens.popupPaddingVertical)
            paddingBottom(BusSheetTokens.popupPaddingVertical)
            paddingLeft(BusSheetTokens.popupPaddingHorizontal)
            paddingRight(BusSheetTokens.popupPaddingHorizontal)
        }
        event { click { onTap() } }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
            }
            Image {
                attr {
                    src(ImageUri.commonAssets("warning-outline.svg"))
                    size(AgentSheetTokens.popupIconSize, AgentSheetTokens.popupIconSize)
                    tintColor(BusSheetTokens.errorBorder)
                }
            }
            Text {
                attr {
                    text("lastError")
                    fontSize(BusV86Tokens.fontSizeBody)
                    fontWeightBold()
                    color(BusSheetTokens.errorBorder)
                    marginLeft(6f)
                }
            }
        }
        Text {
            attr {
                text(agent?.lastError.orEmpty().ifBlank { BUS_AGENT_EMPTY_VALUE })
                fontSize(BusV86Tokens.fontSizeMeta)
                lineHeight(AgentSheetTokens.popupLineHeight)
                color(palette.textPrimary)
                marginTop(8f)
                lines(6)
                textOverFlowTail()
            }
        }
        Text {
            attr {
                text("点击其他区域关闭")
                fontSize(BusV86Tokens.fontSizeCaption)
                color(palette.textTertiary)
                marginTop(8f)
            }
        }
    }
}

/** 面板空态：电脑上还没有 Agent。 */
private fun ViewContainer<*, *>.BusAgentSheetEmptyState(palette: BusV86Palette) {
    View {
        attr {
            flexDirectionColumn()
            alignItemsCenter()
            paddingTop(28f)
            paddingBottom(28f)
        }
        Image {
            attr {
                src(
                    ImageUri.commonAssets(
                        if (palette.isDark) "empty-no-agent-dark.svg" else "empty-no-agent.svg",
                    ),
                )
                size(AgentSheetTokens.emptyIconSize, AgentSheetTokens.emptyIconSize)
            }
        }
        Text {
            attr {
                text("还没有 Agent")
                fontSize(16f)
                fontWeightBold()
                color(palette.textSecondary)
                marginTop(20f)
                textAlignCenter()
            }
        }
        Text {
            attr {
                text("Agent 在电脑上运行\n连接后自动出现")
                fontSize(BusV86Tokens.fontSizeBody)
                color(palette.textTertiary)
                lineHeight(21f)
                marginTop(8f)
                textAlignCenter()
            }
        }
    }
}
