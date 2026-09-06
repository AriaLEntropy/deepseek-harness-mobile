package com.example.dsh.chat

import com.example.dsh.home.connectionStatusTone
import com.example.dsh.home.dotColor
import com.example.dsh.home.topBarConnectingText
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * 连接状态胶囊（Overlay）
 *
 * 浮在输入卡上方，仅非已连接时显示。不参与流式布局，不影响任何其他内容。
 * 毛玻璃效果：半透明白色背景 + 细描边 + 弥散阴影，与 DshMessageActionsMenu 的毛玻璃着色层视觉一致。
 * 指示点颜色随连接状态语义变化（已连接绿 / 进行中琥珀 / 失败红 / 断开橙 / 提示灰）。
 * 隐藏时淡出（opacity 动画）。
 */
internal fun ViewContainer<*, *>.DshConnectionStatusCapsule(
    visible: () -> Boolean,
    connectionLabel: () -> String,
    isBlankConversation: () -> Boolean,
    fadeOut: () -> Boolean,
    fadeOutAnimation: () -> Animation,
) {
    vif(visible) {
        // 全宽锚定容器：胶囊自底向上定位，水平居中
        View {
            attr {
                positionAbsolute()
                left(0f)
                right(0f)
                // 动态底部偏移：空白会话时 Hero 配置区(36f) + 输入卡(94f) + 内边距(20f) + 间距(8f) = 158f
                // 非空白会话时仅有输入卡(94f) + 内边距(20f) + 间距(8f) = 122f
                bottom(if (isBlankConversation()) 158f else 122f)
                height(26f)
                flexDirectionRow()
                justifyContentCenter()
                alignItemsCenter()
                // 淡出动画：fadeOut 为 true 时 opacity 从 1 动画到 0
                opacity(if (fadeOut()) 0f else 1f)
                animation(fadeOutAnimation(), fadeOut())
                // 无事件处理器，不拦截点击，穿透到输入卡
                zIndex(10)
            }
            // 圆角药丸
            View {
                attr {
                    height(26f)
                    paddingLeft(12f)
                    paddingRight(12f)
                    borderRadius(13f)
                    // 毛玻璃效果：半透明白色 + 细描边 + 弥散阴影
                    backgroundColor(Color(0xCCFFFFFF))
                    border(Border(0.5f, BorderStyle.SOLID, Color(0x26000000)))
                    boxShadow(BoxShadow(0f, 2f, 8f, Color(0x1A000000)))
                    flexDirectionRow()
                    alignItemsCenter()
                }
                // 状态指示点：颜色随连接状态语义变化
                View {
                    attr {
                        size(6f, 6f)
                        borderRadius(3f)
                        backgroundColor(Color(connectionStatusTone(connectionLabel()).dotColor()))
                        marginRight(6f)
                    }
                }
                // 连接状态文本
                Text {
                    attr {
                        text(topBarConnectingText(connectionLabel()))
                        fontSize(12f)
                        fontWeightMedium()
                        color(Color(0xFF6B7785))
                        lines(1)
                    }
                }
            }
        }
    }
}