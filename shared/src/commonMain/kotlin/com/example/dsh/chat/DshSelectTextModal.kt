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
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Modal
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.TextArea
import com.tencent.kuikly.core.views.View

/**
 * 选择文本弹窗：把 AI 回复的完整正文（原始 markdown 字符串，不渲染）放入多行
 * 输入框（TextArea）。
 *
 * Kuikly 的 Text 不支持原生文本选择，而多行 TextArea 天然具备原生选择能力，
 * 因此用它在独立 Modal 窗口内承载全部正文，用户长按即可拖动选区、用系统菜单复制，
 * 规避了在消息列表里跨多种容器做选区的跨容器问题。
 */
internal fun ViewContainer<*, *>.DshSelectTextModal(
    visible: () -> Boolean,
    content: () -> String,
    onCopyAll: () -> Unit,
    onClose: () -> Unit,
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
                    width(pagerData.pageViewWidth - 40f)
                    maxWidth(560f)
                    maxHeight(pagerData.pageViewHeight - 96f)
                    flexDirectionColumn()
                    padding(18f)
                    borderRadius(16f)
                    backgroundColor(Color.WHITE)
                }
                // 标题栏 + 复制全部 + 关闭
                View {
                    attr { height(36f); flexDirectionRow(); alignItemsCenter() }
                    Text {
                        attr {
                            text("选择文本")
                            flex(1f)
                            fontSize(17f)
                            fontWeightBold()
                            color(Color(0xFF1F2933))
                        }
                    }
                    Text {
                        attr {
                            text("复制全部")
                            marginRight(12f)
                            fontSize(13f)
                            color(Color(0xFF4176E6))
                        }
                        event { click { onCopyAll() } }
                    }
                    View {
                        attr { size(32f, 32f); allCenter() }
                        Image { attr { src(ImageUri.commonAssets("x.svg")); size(20f, 20f) } }
                        DshHitButton { onClose() }
                    }
                }
                // 操作提示
                Text {
                    attr {
                        text("长按正文拖动选区手柄，仅复制选中的部分")
                        marginTop(4f)
                        fontSize(12f)
                        color(Color(0xFF98A1A9))
                    }
                }
                // 可选中正文：多行 TextArea 承载全部文本，自身可滚动
                TextArea {
                    ref { it.view?.setText(content()) }
                    attr {
                        flex(1f)
                        marginTop(10f)
                        borderRadius(8f)
                        backgroundColor(Color(0xFFF7F9FA))
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