package com.example.dsh.ui.home

import com.example.dsh.session.DshSession
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuikly.core.views.compose.Button
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme

internal fun ViewContainer<*, *>.DshTopBar(
    title: () -> String,
    onOpenDrawer: () -> Unit,
    onNewSession: () -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    View {
        attr {
            height(58f)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(12f)
            paddingRight(14f)
            backgroundColor(colors().topBarFill)
        }
//        左侧菜单图标：点击打开会话抽屉（实色圆形底圈 + 柔和外阴影）
        View {
            attr {
                size(38f, 38f)
                borderRadius(19f)
                allCenter()
                backgroundColor(colors().floatingButtonFill)
                boxShadow(BoxShadow(0f, 3f, 10f, colors().floatingButtonShadow))
            }
            Image {
                attr {
                    src(ImageUri.commonAssets("menu.svg"))
                    size(26f, 26f)
                    tintColor(colors().floatingButtonIcon)
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
//        右上角新建会话：点击创建一个新的空白会话（实色圆形底圈 + 柔和外阴影）
        View {
            attr {
                size(38f, 38f)
                borderRadius(19f)
                allCenter()
                backgroundColor(colors().floatingButtonFill)
                boxShadow(BoxShadow(0f, 3f, 10f, colors().floatingButtonShadow))
            }
            Image {
                attr {
                    src(ImageUri.commonAssets("new-session.svg"))
                    size(20f, 20f)
                    tintColor(colors().floatingButtonIcon)
                }
            }
            DshHitButton(onNewSession)
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
                    DshSessionButton(session, { activeId() == session.id }, onSelect, colors = colors)
                }
            }
        } else {
            Scroller {
                attr { flex(1f) }
                vfor({ sessions() }) { session ->
                    DshSessionButton(session, { activeId() == session.id }, onSelect, colors = colors)
                }
            }
        }
    }
}

internal fun ViewContainer<*, *>.DshSessionButton(
    session: DshSession,
    active: () -> Boolean,
    onSelect: (String) -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    Button {
        attr {
            height(48f)
            width(220f)
            marginBottom(4f)
            borderRadius(7f)
            backgroundColor(if (active()) colors().specificSidebarNavItemActive else Color(0x00000000))
            titleAttr {
                text(session.title)
                color(if (active()) colors().stateBusinessPrimary else colors().labelPrimary)
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
        DshDetailRow("状态", { if (running()) "运行中" else "空闲" }, colors = colors)
        DshDetailRow("模型", modelLabel, colors = colors)
        vif({ agentPreset().isNotEmpty() }) {
            DshDetailRow("Agent Preset", agentPreset, colors = colors)
        }
        DshDetailRow("队列", { "${queueCount()} 条" }, colors = colors)
        DshDetailRow("后台任务", { "${jobCount()} 个" }, colors = colors)
        vif({ cwd().isNotEmpty() }) {
            DshDetailRow("目录", cwd, colors = colors)
        }
    }
}

internal fun ViewContainer<*, *>.DshDetailRow(
    label: String,
    value: () -> String,
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
                text(value())
                marginTop(2f)
                fontSize(13f)
                color(colors().labelSecondary)
                lines(2)
            }
        }
    }
}
