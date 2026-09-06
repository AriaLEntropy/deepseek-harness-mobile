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
import com.tencent.kuikly.core.layout.FlexPositionType
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/** 「+」附件/命令面板里的三个附件方块类型 */
internal enum class DshCommandSheetTile(val label: String, val iconAsset: String) {
    CAMERA("拍照", "camera.svg"),
    GALLERY("相册", "gallery.svg"),
    FILE("文件", "file.svg"),
}

/**
 * 点击输入框「+」图标弹出的半屏底部面板（与「选择文本」弹窗同款容器）：
 * 顶部居中标题 + 右上角关闭；下方一排三个附件方块（拍照/相册/文件）；
 * 再下方是原版命令列表，点击命令把 "/command " 写入输入框。
 */
internal fun ViewContainer<*, *>.DshCommandSheet(
    visible: () -> Boolean,
    onClose: () -> Unit,
    onPickCommand: (DshCommand) -> Unit,
    onPickTile: (DshCommandSheetTile) -> Unit = {},
) {
    vif({ visible() }) {
        val pageData = getPager().pageData
        Modal(inWindow = true) {
            attr {
                absolutePositionAllZero()
                backgroundColor(Color(0x66000000))
            }
            // 底部 sheet：顶部大圆角、贴底，高度约 62%
            View {
                attr {
                    width(pageData.pageViewWidth)
                    height(pageData.pageViewHeight * 0.62f)
                    positionType(FlexPositionType.ABSOLUTE)
                    bottom(0f)
                    flexDirectionColumn()
                    borderRadius(BorderRectRadius(20f, 20f, 0f, 0f))
                    backgroundColor(Color.WHITE)
                }
                // 头部：标题居中，右上角关闭
                View {
                    attr {
                        height(52f)
                        flexDirectionRow()
                        alignItemsCenter()
                        justifyContentCenter()
                    }
                    Text {
                        attr {
                            text("命令")
                            lines(1)
                            fontSize(17f)
                            fontWeightBold()
                            color(Color(0xFF000000))
                        }
                    }
                    View {
                        attr { positionType(FlexPositionType.ABSOLUTE); right(16f); size(32f, 32f); allCenter() }
                        Image { attr { src(ImageUri.commonAssets("x.svg")); size(20f, 20f) } }
                        DshHitButton { onClose() }
                    }
                }
                // 三个附件方块：横向排布，图标 + 下方标签
                View {
                    attr {
                        flexDirectionRow()
                        paddingLeft(20f)
                        paddingRight(20f)
                        marginTop(4f)
                    }
                    DshCommandSheetTileRow(DshCommandSheetTile.CAMERA) { onPickTile(it) }
                    View { attr { width(12f) } }
                    DshCommandSheetTileRow(DshCommandSheetTile.GALLERY) { onPickTile(it) }
                    View { attr { width(12f) } }
                    DshCommandSheetTileRow(DshCommandSheetTile.FILE) { onPickTile(it) }
                }
                // 命令列表：左命令名 + 右描述，点击写入输入框
                View {
                    attr {
                        flex(1f)
                        marginTop(10f)
                        flexDirectionColumn()
                    }
                    vforIndex({ dshCommandCatalog }) { command, index, count ->
                        // vfor creator 只允许一个根节点：命令行内放绝对定位的分隔线
                        View {
                            attr {
                                height(44f)
                                flexDirectionRow()
                                alignItemsCenter()
                                paddingLeft(20f)
                                paddingRight(20f)
                            }
                            Text {
                                attr {
                                    text("/${command.name}")
                                    width(110f)
                                    fontSize(15f)
                                    fontWeightMedium()
                                    color(Color(0xFF1F2933))
                                }
                            }
                            Text {
                                attr {
                                    text(command.description)
                                    flex(1f)
                                    lines(1)
                                    fontSize(12f)
                                    color(Color(0xFF9098A0))
                                }
                            }
                            DshHitButton { onPickCommand(command) }
                            vif({ index < count - 1 }) {
                                View {
                                    attr {
                                        positionType(FlexPositionType.ABSOLUTE)
                                        left(20f)
                                        right(20f)
                                        bottom(0f)
                                        height(0.5f)
                                        backgroundColor(Color(0x14000000))
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

/** 单个附件方块：浅灰圆角底，居中图标 + 标签 */
internal fun ViewContainer<*, *>.DshCommandSheetTileRow(
    tile: DshCommandSheetTile,
    onClick: (DshCommandSheetTile) -> Unit,
) {
    View {
        attr {
            flex(1f)
            flexDirectionColumn()
            alignItemsCenter()
            paddingTop(16f)
            paddingBottom(16f)
            borderRadius(14f)
            backgroundColor(Color(0xFFF5F6F7))
        }
        Image {
            attr {
                src(ImageUri.commonAssets(tile.iconAsset))
                size(28f, 28f)
            }
        }
        Text {
            attr {
                text(tile.label)
                marginTop(8f)
                fontSize(13f)
                color(Color(0xFF3B4147))
            }
        }
        DshHitButton { onClick(tile) }
    }
}