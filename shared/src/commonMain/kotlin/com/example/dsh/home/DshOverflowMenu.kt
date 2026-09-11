package com.example.dsh.home

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
import com.tencent.kuikly.core.layout.FlexWrap
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vforLazy
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.List
import com.tencent.kuikly.core.views.ScrollPicker
import com.tencent.kuikly.core.views.InputView
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.ScrollParams
import com.tencent.kuikly.core.views.ScrollerView
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** 会话 topbar overflow menu 中的一个操作项。 */
internal data class DshOverflowAction(
    val id: String,
    val label: String,
    val iconAsset: String,
    val danger: Boolean = false,
)

/**
 * 会话 topbar 右上角 overflow menu。
 *
 * 菜单项布局类似 CSS `justify-content: space-between`：左侧文字、右侧图标；
 * 删除项（danger）文字与图标同为错误红；其余三项文字用主文本色、图标用
 * DSH 原版菜单的 tertiary 灰（与重命名/归档原版图标颜色一致）。
 */
internal fun ViewContainer<*, *>.DshOverflowMenu(
    visible: () -> Boolean,
    actions: () -> ObservableList<DshOverflowAction>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    statusBarHeight: Float,
    pageViewWidth: Float,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    vif({ visible() }) {
        val menuWidth = 220f
        // 挂在 topbar（58dp）下方 6dp，右缘与 topbar padding 对齐
        val menuLeft = pageViewWidth - menuWidth - 12f
        val menuTop = statusBarHeight + 58f + 6f
        // 透明点击捕获层：点击空白处关闭菜单
        View {
            attr { absolutePositionAllZero() }
            event { click { onDismiss() } }
            View {
                attr {
                    positionAbsolute()
                    left(menuLeft)
                    top(menuTop)
                    width(menuWidth)
                    borderRadius(12f)
                    border(Border(1f, BorderStyle.SOLID, colors().borderL2))
                    backgroundColor(colors().specificMenu)
                    boxShadow(BoxShadow(0f, 4f, 16f, Color(0x33000000)))
                    paddingTop(6f)
                    paddingBottom(6f)
                }
                event { click { } } // 消费点击，避免穿透到遮罩
                vfor({ actions() }) { item ->
                    View {
                        attr {
                            height(44f)
                            flexDirectionRow()
                            alignItemsCenter()
                            justifyContentSpaceBetween()
                            paddingLeft(14f)
                            paddingRight(12f)
                            borderRadius(8f)
                        }
                        Text {
                            attr {
                                text(item.label)
                                fontSize(14f)
                                color(if (item.danger) colors().stateErrorPrimary else colors().labelPrimary)
                            }
                        }
                        Image {
                            attr {
                                src(ImageUri.commonAssets(item.iconAsset))
                                size(18f, 18f)
                                tintColor(if (item.danger) colors().stateErrorPrimary else colors().labelTertiary)
                            }
                        }
                        DshHitButton { onSelect(item.id) }
                    }
                }
            }
        }
    }
}

/** 重命名会话 弹窗：输入新标题 → session.rename。 */
internal fun ViewContainer<*, *>.DshSessionRenameDialog(
    visible: () -> Boolean,
    draft: () -> String,
    busy: () -> Boolean,
    error: () -> String,
    onDraftChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    pageViewWidth: Float,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    vif({ visible() }) {
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
                    width(pageViewWidth - 40f)
                    maxWidth(420f)
                    padding(20f)
                    borderRadius(16f)
                    backgroundColor(colors().bgLayer1)
                }
                Text { attr { text("重命名会话"); fontSize(18f); fontWeightBold(); color(colors().labelPrimary) } }
                Input {
                    attr {
                        height(38f)
                        marginTop(14f)
                        fontSize(14f)
                        placeholder("会话名称")
                        placeholderColor(colors().labelTertiary)
                        text(draft())
                        color(colors().labelPrimary)
                    }
                    event { textDidChange { onDraftChange(it.text) } }
                }
                vif({ error().isNotEmpty() }) {
                    Text { attr { text(error()); marginTop(8f); fontSize(12f); color(colors().stateErrorPrimary) } }
                }
                View {
                    attr { height(40f); marginTop(18f); flexDirectionRow(); justifyContentFlexEnd() }
                    Text {
                        attr { text("取消"); width(78f); height(38f); textAlignCenter(); fontSize(14f); color(colors().labelTertiary) }
                        event { click { onCancel() } }
                    }
                    Text {
                        attr { text(if (busy()) "保存中..." else "保存"); width(78f); height(38f); marginLeft(8f); textAlignCenter(); fontSize(14f); color(colors().stateBusinessPrimary) }
                        event { click { if (!busy()) onSave() } }
                    }
                }
            }
        }
    }
}

/** 归档会话 确认弹窗 → workspace.archiveSession。 */
internal fun ViewContainer<*, *>.DshSessionArchiveDialog(
    visible: () -> Boolean,
    busy: () -> Boolean,
    error: () -> String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    pageViewWidth: Float,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    vif({ visible() }) {
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
                    width(pageViewWidth - 40f)
                    maxWidth(420f)
                    padding(20f)
                    borderRadius(16f)
                    backgroundColor(colors().bgLayer1)
                }
                Text { attr { text("归档会话?"); fontSize(18f); fontWeightBold(); color(colors().labelPrimary) } }
                Text {
                    attr {
                        text("归档后会话会从主列表隐藏，可稍后在工作区中恢复；不会删除会话或日志。")
                        marginTop(8f)
                        fontSize(13f)
                        lineHeight(20f)
                        color(colors().labelSecondary)
                    }
                }
                vif({ error().isNotEmpty() }) {
                    Text { attr { text(error()); marginTop(8f); fontSize(12f); color(colors().stateErrorPrimary) } }
                }
                View {
                    attr { height(40f); marginTop(18f); flexDirectionRow(); justifyContentFlexEnd() }
                    Text {
                        attr { text("取消"); width(78f); height(38f); textAlignCenter(); fontSize(14f); color(colors().labelTertiary) }
                        event { click { onCancel() } }
                    }
                    Text {
                        attr { text(if (busy()) "归档中..." else "确认归档"); width(104f); height(38f); marginLeft(8f); textAlignCenter(); fontSize(14f); color(colors().stateBusinessPrimary) }
                        event { click { if (!busy()) onConfirm() } }
                    }
                }
            }
        }
    }
}

/** 删除会话 确认弹窗（danger，红色按钮）→ dsh-session-manager 插件 /delete 端点。 */
internal fun ViewContainer<*, *>.DshSessionDeleteDialog(
    visible: () -> Boolean,
    busy: () -> Boolean,
    error: () -> String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    pageViewWidth: Float,
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    vif({ visible() }) {
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
                    width(pageViewWidth - 40f)
                    maxWidth(420f)
                    padding(20f)
                    borderRadius(16f)
                    backgroundColor(colors().bgLayer1)
                }
                Text { attr { text("删除会话?"); fontSize(18f); fontWeightBold(); color(colors().labelPrimary) } }
                Text {
                    attr {
                        text("将永久删除该会话及其消息，此操作不可恢复。删除通过 dsh-session-manager 插件执行，若 Host 未安装该插件将无法完成。")
                        marginTop(8f)
                        fontSize(13f)
                        lineHeight(20f)
                        color(colors().labelSecondary)
                    }
                }
                vif({ error().isNotEmpty() }) {
                    Text { attr { text(error()); marginTop(8f); fontSize(12f); color(colors().stateErrorPrimary) } }
                }
                View {
                    attr { height(40f); marginTop(18f); flexDirectionRow(); justifyContentFlexEnd() }
                    Text {
                        attr { text("取消"); width(78f); height(38f); textAlignCenter(); fontSize(14f); color(colors().labelTertiary) }
                        event { click { onCancel() } }
                    }
                    Text {
                        attr { text(if (busy()) "删除中..." else "永久删除"); width(112f); height(38f); marginLeft(8f); textAlignCenter(); fontSize(14f); color(colors().stateErrorPrimary) }
                        event { click { if (!busy()) onConfirm() } }
                    }
                }
            }
        }
    }
}
