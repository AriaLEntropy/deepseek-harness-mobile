package com.example.dsh.chat

import com.example.dsh.message.dshFormatTurnDuration
import com.example.dsh.message.dshTurnStatusLabel
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme

// AI 回答下方横向操作容器（footer）的可用操作项，对齐 dsh 原版 IconActions 行
internal enum class DshMessageFooterAction { COPY, GOOD, BAD, BRANCH, SHARE }

// 过程项左侧装饰连接线的度量：标题下方分成 [竖线 | 内容] 左右两列。
// 线体宽度、内容列整体右移预留的沟槽宽度，以及线与内容之间保留的空隙。
internal const val DSH_CONNECTOR_LINE_WIDTH = 1f
internal const val DSH_CONNECTOR_GUTTER = 14f
internal const val DSH_CONNECTOR_LINE_GAP = 6f
internal const val DSH_CONNECTOR_LINE_LEFT =
    DSH_CONNECTOR_GUTTER - DSH_CONNECTOR_LINE_GAP - DSH_CONNECTOR_LINE_WIDTH

internal fun ViewContainer<*, *>.DshTurnStatus(
    visible: () -> Boolean,
    reconnecting: () -> Boolean,
    elapsedMs: () -> Long,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    // 回合状态条：思考中/重连中提示 + 已耗时
    vif({ visible() }) {
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                height(26f)
                marginTop(4f)
                marginBottom(8f)
            }
            Text {
                attr {
                    text(dshTurnStatusLabel(reconnecting()))
                    fontSize(14f)
                    fontWeightBold()
                    color(colors().stateBusinessPrimary)
                }
            }
            vif({ elapsedMs() >= TURN_STATUS_CLOCK_AFTER_MS }) {
                Text {
                    attr {
                        text(dshFormatTurnDuration(elapsedMs()))
                        fontSize(13f)
                        color(colors().labelTertiary)
                        marginLeft(8f)
                    }
                }
            }
        }
    }
}

internal const val TURN_STATUS_CLOCK_AFTER_MS = 15_000L

// 空白会话首页：无消息时的占位引导（logo + 标语 + 预览版徽标）
internal fun ViewContainer<*, *>.DshNewSessionHome(colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light }) {
    View {
        attr {
            absolutePositionAllZero()
            allCenter()
            zIndex(2)
            paddingLeft(28f)
            paddingRight(28f)
        }
        event {
            click { }
        }
        View {
            attr {
                flexDirectionColumn()
                alignItemsCenter()
            }
            Image {
                attr {
                    src(ImageUri.commonAssets("fish.svg"))
                    size(56f, 56f)
                    tintColor(if (colors().isDark) Color.WHITE else null)
                }
            }
            View {
                attr {
                    marginTop(16f)
                    flexDirectionRow()
                    alignItemsCenter()
                }
                Text {
                    attr {
                        text("探索未至之境")
                        fontSize(26f)
                        fontWeightBold()
                        color(colors().labelPrimary)
                    }
                }
                View {
                    attr {
                        marginLeft(8f)
                        paddingLeft(8f)
                        paddingRight(8f)
                        height(22f)
                        allCenter()
                        borderRadius(11f)
                        backgroundColor(colors().stateBusinessTertiary)
                    }
                    Text {
                        attr {
                            text("预览版")
                            fontSize(11f)
                            fontWeightMedium()
                            color(colors().stateBusinessPrimary)
                        }
                    }
                }
            }
        }
    }
}
