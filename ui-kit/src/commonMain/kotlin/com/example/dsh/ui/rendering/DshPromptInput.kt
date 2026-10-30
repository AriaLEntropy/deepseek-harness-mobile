package com.example.dsh.ui.rendering

import com.example.dsh.theme.DshColorTokens
import com.example.dsh.theme.DshDefaultTheme
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.TextArea
import com.tencent.kuikly.core.views.TextAreaView
import com.tencent.kuikly.core.views.View

/**
 * 聊天输入框风格的 Prompt 输入组件：
 * 圆角容器 + 可换行 TextArea + 右下角模型标签。
 *
 * 新建任务和后续需要“第一条消息”的场景共用；模型标签由调用方根据
 * 当前 Agent 的 ConnectorInfo.models 提供。
 */
fun ViewContainer<*, *>.DshPromptInput(
    placeholder: String,
    modelLabel: () -> String,
    modelEnabled: () -> Boolean,
    onPromptChange: (String) -> Unit,
    onModelClick: () -> Unit,
    colors: () -> DshColorTokens = { DshDefaultTheme.light },
    onKeyboardHeightChange: (Float) -> Unit = {},
    inputRef: (TextAreaView) -> Unit = {},
) {
    View {
        attr {
            minHeight(150f)
            flexDirectionColumn()
            padding(12f)
            borderRadius(16f)
            backgroundColor(colors().bgLayer1)
            border(Border(1f, BorderStyle.SOLID, colors().borderL1))
        }
        TextArea {
            ref { viewRef -> viewRef.view?.let(inputRef) }
            attr {
                flex(1f)
                minHeight(86f)
                maxHeight(130f)
                fontSize(15f)
                lineHeight(21f)
                color(colors().labelPrimary)
                placeholder(placeholder)
                placeholderColor(colors().labelTertiary)
                backgroundColor(Color(0x00000000))
            }
            event {
                textDidChange { onPromptChange(it.text) }
                keyboardHeightChange { onKeyboardHeightChange(it.height) }
                inputBlur { onKeyboardHeightChange(0f) }
            }
        }
        View {
            attr {
                height(30f)
                flexDirectionRow()
                alignItemsCenter()
                marginTop(8f)
            }
            View { attr { flex(1f) } }
            View {
                attr {
                    height(30f)
                    flexDirectionRow()
                    alignItemsCenter()
                    paddingLeft(11f)
                    paddingRight(8f)
                    borderRadius(15f)
                    backgroundColor(colors().specificSelector)
                    opacity(if (modelEnabled()) 1f else 0.6f)
                }
                event { click { if (modelEnabled()) onModelClick() } }
                Text {
                    attr {
                        text(modelLabel())
                        fontSize(11f)
                        fontWeightMedium()
                        color(colors().labelSecondary)
                    }
                }
                Image {
                    attr {
                        src(ImageUri.commonAssets("chevron-down.svg"))
                        size(10f, 10f)
                        tintColor(colors().labelTertiary)
                        marginLeft(4f)
                    }
                }
            }
        }
    }
}
