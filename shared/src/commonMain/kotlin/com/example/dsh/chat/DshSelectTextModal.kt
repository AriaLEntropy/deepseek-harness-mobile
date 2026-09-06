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
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.layout.FlexPositionType
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.TextArea
import com.tencent.kuikly.core.views.View

/**
 * 选择文本弹窗：从底部滑出的 sheet 容器（顶部大圆角、标题居中、右上角关闭）。
 * 正文用多行 TextArea 承载完整回合正文（原始 markdown 不渲染），
 * TextArea 天然具备原生文本选择能力，长按即可拖动选区、用系统菜单复制，
 * 规避了在消息列表里跨多种容器做选区的跨容器问题。
 */
internal fun ViewContainer<*, *>.DshSelectTextModal(
    visible: () -> Boolean,
    content: () -> String,
    onClose: () -> Unit,
) {
    vif({ visible() }) {
        Modal(inWindow = true) {
            attr {
                absolutePositionAllZero()
                backgroundColor(Color(0x66000000))
            }
            // 底部 sheet 容器：顶部大圆角，贴底，固定高度让内部 flex 撑开
            View {
                attr {
                    width(pagerData.pageViewWidth)
                    height(pagerData.pageViewHeight * 0.7f)
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
                            text("选择文本")
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
                // 可选中正文：外层 View 处理内边距，多行 TextArea 承载全部文本并滚动
                View {
                    attr {
                        flex(1f)
                        padding(12f, 20f, 20f, 20f)
                    }
                    TextArea {
                        ref { it.view?.setText(content()) }
                        attr {
                            flex(1f)
                            textAlignLeft()
                            useDpFontSizeDim()
                            color(Color(0xFF343E47))
                            fontSize(14f)
                            lineHeight(21f)
                        }
                    }
                }
            }
        }
    }
}