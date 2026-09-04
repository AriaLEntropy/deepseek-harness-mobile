package com.example.dsh.chat

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
import com.tencent.kuikly.core.directives.vforIndex
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal data class DshMessageActionItem(
    val label: String,
    val iconAsset: String,
    val onClick: () -> Unit,
)

private const val MENU_ITEM_HEIGHT = 40f
private const val MENU_V_PADDING = 4f
private const val MENU_DIVIDER_HEIGHT = 0.5f
private const val MENU_EDGE_MARGIN = 12f

/**
 * 消息长按操作菜单（Context Menu）。
 *
 * 独立浮动卡片，跟随长按坐标弹出。毛玻璃只作用于卡片自身覆盖的区域：
 * 截取全屏 → 模糊 → 在卡片内以 (-x, -y) 偏移显示，由卡片圆角裁剪。
 * 不使用 Modal(inWindow)，避免新建窗口导致页面整体位移。
 */
internal fun ViewContainer<*, *>.DshMessageActionsMenu(
    visible: () -> Boolean,
    items: () -> ObservableList<DshMessageActionItem>,
    blurUri: () -> String,
    x: () -> Float,
    y: () -> Float,
    onDismiss: () -> Unit,
) {
    vif({ visible() }) {
        val pageData = getPager().pageData
        // 浮动卡片：跟随长按坐标，超出屏幕时向内钳制
        val itemCount = items().size
        val menuWidth = (pageData.pageViewWidth * 0.52f).coerceAtMost(240f)
        val menuHeight = itemCount * MENU_ITEM_HEIGHT + MENU_V_PADDING * 2 +
                (itemCount - 1).coerceAtLeast(0) * MENU_DIVIDER_HEIGHT
        val clampedX = (x() - menuWidth / 2f).coerceIn(
            MENU_EDGE_MARGIN,
            (pageData.pageViewWidth - menuWidth - MENU_EDGE_MARGIN).coerceAtLeast(MENU_EDGE_MARGIN),
        )
        val clampedY = if (y() + menuHeight > pageData.pageViewHeight - MENU_EDGE_MARGIN) {
            (y() - menuHeight - MENU_EDGE_MARGIN).coerceAtLeast(MENU_EDGE_MARGIN)
        } else {
            y()
        }
        // 透明点击捕获层：点击空白处关闭菜单，但不改变背景外观
        View {
            attr {
                absolutePositionAllZero()
            }
            event {
                click { onDismiss() }
            }
            View {
                attr {
                    positionAbsolute()
                    left(clampedX)
                    top(clampedY)
                    width(menuWidth)
                    borderRadius(14f)
                    boxShadow(BoxShadow(0f, 4f, 16f, Color(0x33000000)))
                    paddingTop(MENU_V_PADDING)
                    paddingBottom(MENU_V_PADDING)
                }
                event {
                    click { } // 消费点击，避免穿透到遮罩
                }
                // 毛玻璃背景：模糊后的全屏图在卡片内偏移 (-clampedX, -clampedY)，
                // 露出卡片覆盖区域的背景，由圆角裁剪
                vif({ blurUri().isNotEmpty() }) {
                    Image {
                        attr {
                            src(ImageUri.file(blurUri()))
                            positionAbsolute()
                            left(-clampedX)
                            top(-clampedY)
                            width(pageData.pageViewWidth)
                            height(pageData.pageViewHeight)
                            resizeStretch()
                        }
                    }
                }
                // 半透明白色毛玻璃着色层（模糊图加载前也作为兜底背景）
                View {
                    attr {
                        absolutePositionAllZero()
                        backgroundColor(Color(0xCCFFFFFF))
                    }
                }
                // 菜单项：左文右图，项间细分割线
                vforIndex({ items() }) { item, index, count ->
                    View {
                        attr {
                            flexDirectionColumn()
                        }
                        View {
                            attr {
                                height(MENU_ITEM_HEIGHT)
                                flexDirectionRow()
                                alignItemsCenter()
                                justifyContentSpaceBetween()
                                paddingLeft(16f)
                                paddingRight(16f)
                            }
                            Text {
                                attr {
                                    text(item.label)
                                    fontSize(15f)
                                    color(Color(0xFF1F2933))
                                }
                            }
                            Image {
                                attr {
                                    src(ImageUri.commonAssets(item.iconAsset))
                                    size(18f, 18f)
                                }
                            }
                            DshHitButton(item.onClick)
                        }
                        vif({ index < count - 1 }) {
                            View {
                                attr {
                                    height(MENU_DIVIDER_HEIGHT)
                                    marginLeft(16f)
                                    marginRight(16f)
                                    backgroundColor(Color(0x1A000000))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
