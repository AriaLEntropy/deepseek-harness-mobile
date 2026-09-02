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
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal data class DshMessageActionItem(
    val label: String,
    val iconAsset: String,
    val onClick: () -> Unit,
)

internal fun ViewContainer<*, *>.DshMessageActionsMenu(
    visible: () -> Boolean,
    items: () -> ObservableList<DshMessageActionItem>,
    onDismiss: () -> Unit,
) {
    vif({ visible() }) {
        Modal(inWindow = true) {
            attr {
                absolutePositionAllZero()
                justifyContentFlexEnd()
                backgroundColor(Color(0x66000000))
            }
            event {
                click { onDismiss() }
            }
            View {
                attr {
                    width(pagerData.pageViewWidth)
                    paddingTop(6f)
                    paddingBottom(6f + pagerData.safeAreaInsets.bottom)
                    borderRadius(18f, 18f, 0f, 0f)
                    backgroundColor(Color.WHITE)
                }
                vfor({ items() }) { item ->
                    View {
                        attr {
                            height(52f)
                            flexDirectionRow()
                            alignItemsCenter()
                            paddingLeft(20f)
                            paddingRight(20f)
                        }
                        Image {
                            attr {
                                src(ImageUri.commonAssets(item.iconAsset))
                                size(20f, 20f)
                            }
                        }
                        Text {
                            attr {
                                text(item.label)
                                marginLeft(14f)
                                fontSize(15f)
                                color(Color(0xFF1F2933))
                            }
                        }
                        DshHitButton(item.onClick)
                    }
                }
            }
        }
    }
}
