package com.example.dsh.session

import com.example.dsh.message.iconAsset
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Input
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme
import com.example.dsh.session.DSH_DRAWER_GROUP_FLAT
import com.example.dsh.session.DSH_DRAWER_GROUP_WORKSPACE
import com.example.dsh.session.DSH_DRAWER_ORDER_MANUAL
import com.example.dsh.session.DSH_DRAWER_ORDER_UPDATED
import com.example.dsh.ui.home.DshHitButton

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
    pageViewHeight: Float = 0f,
    // 点击会话行 ⋯ 时的屏幕坐标；-1 表示无锚点，回退到顶栏下方右侧定位。
    // 以 lambda 传入：抽屉只在可见性翻转时重建菜单，若按值捕获会拿到旧锚点导致弹错位置。
    anchorX: () -> Float = { -1f },
    anchorY: () -> Float = { -1f },
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    vif({ visible() }) {
        val menuWidth = 220f
        val menuHeight = 12f + actions().size * 44f
        val ax = anchorX()
        val ay = anchorY()
        val hasAnchor = ax >= 0f && ay >= 0f
        // 有锚点时贴着点击会话行的 ⋯ 按钮展开（在其左下方），否则回退顶栏下方右侧。
        val maxLeft = (pageViewWidth - menuWidth - 8f).coerceAtLeast(8f)
        val menuLeft = if (hasAnchor) {
            (ax - menuWidth - 8f).coerceIn(8f, maxLeft)
        } else {
            pageViewWidth - menuWidth - 12f
        }
        val minTop = statusBarHeight + 8f
        val maxTop = if (pageViewHeight > 0f) {
            (pageViewHeight - menuHeight - 8f).coerceAtLeast(minTop)
        } else {
            minTop
        }
        val menuTop = if (hasAnchor) {
            (ay - 12f).coerceIn(minTop, maxTop)
        } else {
            statusBarHeight + 58f + 6f
        }
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

/**
 * 抽屉「视图选项」菜单，对齐电脑端 WorkspaceBrowser 的 ViewOptionsMenu：
 * 分组方式（按工作区 / 单列表）与排序方式（手动排序 / 最近更新），选中项右侧打勾。
 * 默认按电脑端口径：分组方式「按工作区」、排序方式「最近更新」。
 */
internal fun ViewContainer<*, *>.DshViewOptionsMenu(
    visible: () -> Boolean,
    groupBy: () -> String,
    orderBy: () -> String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    statusBarHeight: Float,
    pageViewWidth: Float,
    pageViewHeight: Float = 0f,
    anchorX: () -> Float = { -1f },
    anchorY: () -> Float = { -1f },
    colors: () -> com.example.dsh.theme.DshColorTokens = { com.example.dsh.theme.DshDefaultTheme.light },
) {
    vif({ visible() }) {
        val menuWidth = 200f
        // 上下 4 padding + 两个标题（各 32）+ 四行选项（各 40）+ 分隔线（1 + 上下 4）。
        val menuHeight = 8f + 32f * 2f + 40f * 4f + 9f
        // 锚点取点击按钮的中心；菜单右对齐按钮右缘、紧贴按钮下方，跟随按钮位置。
        val ax = anchorX()
        val ay = anchorY()
        val hasAnchor = ax >= 0f && ay >= 0f
        val maxLeft = (pageViewWidth - menuWidth - 8f).coerceAtLeast(8f)
        val maxTop = if (pageViewHeight > 0f) {
            (pageViewHeight - menuHeight - 8f).coerceAtLeast(statusBarHeight + 8f)
        } else {
            statusBarHeight + 8f
        }
        val menuLeft = if (hasAnchor) (ax + 14f - menuWidth).coerceIn(8f, maxLeft) else maxLeft
        val menuTop = if (hasAnchor) (ay + 14f).coerceIn(statusBarHeight + 8f, maxTop) else statusBarHeight + 8f
        // 透明点击捕获层：点击空白处关闭菜单。
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
                    paddingTop(4f)
                    paddingBottom(4f)
                }
                event { click { } } // 消费点击，避免穿透到遮罩
                DshViewOptionsLabel("分组方式", colors)
                DshViewOptionsRow(
                    label = "按工作区",
                    selected = { groupBy() == DSH_DRAWER_GROUP_WORKSPACE },
                    onClick = { onSelect(DSH_DRAWER_GROUP_WORKSPACE) },
                    colors = colors,
                )
                DshViewOptionsRow(
                    label = "单列表",
                    selected = { groupBy() == DSH_DRAWER_GROUP_FLAT },
                    onClick = { onSelect(DSH_DRAWER_GROUP_FLAT) },
                    colors = colors,
                )
                View {
                    attr {
                        height(1f)
                        marginTop(4f)
                        marginBottom(4f)
                        marginLeft(2f)
                        marginRight(2f)
                        backgroundColor(colors().borderL1)
                    }
                }
                DshViewOptionsLabel("排序方式", colors)
                DshViewOptionsRow(
                    label = "手动排序",
                    selected = { orderBy() == DSH_DRAWER_ORDER_MANUAL },
                    onClick = { onSelect(DSH_DRAWER_ORDER_MANUAL) },
                    colors = colors,
                )
                DshViewOptionsRow(
                    label = "最近更新",
                    selected = { orderBy() == DSH_DRAWER_ORDER_UPDATED },
                    onClick = { onSelect(DSH_DRAWER_ORDER_UPDATED) },
                    colors = colors,
                )
            }
        }
    }
}

/** 视图选项菜单的非交互标题行（对齐电脑端 Menu label）。 */
private fun ViewContainer<*, *>.DshViewOptionsLabel(
    text: String,
    colors: () -> com.example.dsh.theme.DshColorTokens,
) {
    View {
        attr {
            height(32f)
            paddingLeft(10f)
            paddingRight(10f)
            flexDirectionRow()
            alignItemsCenter()
        }
        Text { attr { text(text); fontSize(12f); color(colors().labelTertiary) } }
    }
}

/** 视图选项菜单的单行选项：选中时右侧显示对勾（对齐电脑端 Menu cell）。 */
private fun ViewContainer<*, *>.DshViewOptionsRow(
    label: String,
    selected: () -> Boolean,
    onClick: () -> Unit,
    colors: () -> com.example.dsh.theme.DshColorTokens,
) {
    View {
        attr {
            height(40f)
            flexDirectionRow()
            alignItemsCenter()
            paddingLeft(10f)
            paddingRight(10f)
            borderRadius(10f)
        }
        Text {
            attr {
                text(label)
                flex(1f)
                fontSize(14f)
                color(colors().labelPrimary)
            }
        }
        vif({ selected() }) {
            Image {
                attr {
                    src(ImageUri.commonAssets("check.svg"))
                    size(16f, 16f)
                    tintColor(colors().labelPrimary)
                }
            }
        }
        DshHitButton { onClick() }
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
                    borderRadius(16f)
                    backgroundColor(colors().bgLayer1)
                }
                Text {
                    attr {
                        text("重命名对话")
                        marginTop(22f)
                        alignSelfCenter()
                        fontSize(18f)
                        fontWeightBold()
                        color(colors().labelPrimary)
                    }
                }
                View {
                    attr {
                        height(50f)
                        marginTop(18f)
                        marginLeft(20f)
                        marginRight(20f)
                        paddingLeft(14f)
                        paddingRight(14f)
                        borderRadius(12f)
                        backgroundColor(colors().specificSelector)
                    }
                    Input {
                        // Seed once on mount. Echoing every textDidChange through
                        // attr.text can overwrite newer native keystrokes/cursor state.
                        ref { it.view?.setText(draft()) }
                        attr {
                            flex(1f)
                            fontSize(15f)
                            placeholder("会话名称")
                            placeholderColor(colors().labelTertiary)
                            editable(!busy())
                            color(colors().labelPrimary)
                        }
                        event { textDidChange { onDraftChange(it.text) } }
                    }
                }
                vif({ error().isNotEmpty() }) {
                    Text {
                        attr {
                            text(error())
                            marginTop(8f)
                            marginLeft(20f)
                            marginRight(20f)
                            fontSize(12f)
                            color(colors().stateErrorPrimary)
                        }
                    }
                }
                View { attr { marginTop(18f); height(1f); backgroundColor(colors().borderL1) } }
                View {
                    attr { height(54f); flexDirectionRow() }
                    View {
                        attr { flex(1f); allCenter() }
                        Text {
                            attr { text("取消"); fontSize(16f); color(colors().labelSecondary); opacity(if (busy()) 0.4f else 1f) }
                        }
                        event { click { if (!busy()) onCancel() } }
                    }
                    View { attr { width(1f); backgroundColor(colors().borderL1) } }
                    View {
                        attr { flex(1f); allCenter() }
                        Text {
                            attr { text(if (busy()) "保存中..." else "确认"); fontSize(16f); fontWeightMedium(); color(colors().stateBusinessPrimary); opacity(if (busy()) 0.5f else 1f) }
                        }
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
                        text("归档后会话会从主列表隐藏，可在「已归档会话」中查看历史；不会删除会话或日志。")
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
