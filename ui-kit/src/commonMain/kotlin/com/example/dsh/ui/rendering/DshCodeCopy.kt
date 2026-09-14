package com.example.dsh.ui.rendering

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.layout.FlexJustifyContent
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.RichText
import com.tencent.kuikly.core.views.Span
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuiklybase.components.MarkdownComponentModel
import com.tencent.kuiklybase.config.FontWeight
import com.tencent.kuiklybase.config.MarkdownConfig
import com.tencent.kuiklybase.config.TextStyleConfig
import com.tencent.kuiklybase.elements.CodeHighlighter
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode

/** Extract from the existing parser AST, retaining blank lines/indentation and excluding fences/language. */
fun dshCodeFenceSource(content: String, node: ASTNode): String = buildString {
    var body = false
    for (child in node.children) {
        if (child.type == MarkdownTokenTypes.CODE_FENCE_END) break
        if (body && (child.type == MarkdownTokenTypes.CODE_FENCE_CONTENT || child.type == MarkdownTokenTypes.EOL)) {
            append(child.getTextInNode(content))
        }
        if (child.type == MarkdownTokenTypes.EOL) body = true
    }
}

/**
 * 代码围栏容器：头部左侧为语言标签、最右侧为复制图标，下方依次是分割线与高亮代码。
 * 复制入口由容器上方的独立行移入容器头部，避免额外占用正文垂直空间。
 */
fun ViewContainer<*, *>.dshCodeFenceBlock(
    model: MarkdownComponentModel,
    onCopy: (String) -> Unit,
) {
    val config = model.config
    val style = model.typography.code
    val language = model.node.findChildOfType(MarkdownTokenTypes.FENCE_LANG)
        ?.getTextInNode(model.content)?.toString()
    // 与 KuiklyMarkdown 的 markdownCodeFence 保持一致：只拼接 CODE_FENCE_CONTENT，避免渲染多余空行。
    val codeNodes = model.node.children.filter { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
    val code = if (codeNodes.isNotEmpty()) {
        codeNodes.joinToString("\n") { it.getTextInNode(model.content).toString() }
    } else {
        dshCodeFenceSource(model.content, model.node)
    }
    val copySource = dshCodeFenceSource(model.content, model.node)

    View {
        attr {
            backgroundColor(config.colors.codeBackground)
            borderRadius(config.dimens.codeBackgroundCornerSize)
            marginTop(config.padding.block)
            marginBottom(config.padding.block)
            val p = config.padding.codeBlock
            padding(p, p, p, p)
        }
        View {
            attr {
                flexDirectionRow()
                alignItemsCenter()
                justifyContent(FlexJustifyContent.SPACE_BETWEEN)
                marginBottom(4f)
            }
            Text {
                attr {
                    text(language.orEmpty())
                    fontSize(style.fontSize * 0.85f)
                    color(config.colors.codeText)
                    if (style.fontWeight == FontWeight.Bold) {
                        fontWeightBold()
                    }
                }
            }
            View {
                attr { size(20f, 20f); allCenter() }
                Image {
                    attr {
                        src(ImageUri.commonAssets("copy.svg"))
                        size(16f, 16f)
                        tintColor(Color(config.colors.codeText))
                    }
                }
                event { click { onCopy(copySource) } }
            }
        }
        if (!language.isNullOrBlank()) {
            View {
                attr {
                    height(0.5f)
                    backgroundColor(config.colors.dividerColor)
                    marginBottom(8f)
                }
            }
        }
        dshCodeText(code, language, style, config)
    }
}

/** 代码正文：优先语法高亮，无高亮信息时回退为纯文本。 */
private fun ViewContainer<*, *>.dshCodeText(
    code: String,
    language: String?,
    style: TextStyleConfig,
    config: MarkdownConfig,
) {
    if (config.codeHighlightEnabled) {
        val segments = CodeHighlighter.highlight(
            code = code,
            language = language,
            darkTheme = config.codeHighlightDarkTheme,
        )
        if (segments.any { it.color != null || it.bold }) {
            RichText {
                attr { lines(Int.MAX_VALUE) }
                segments.forEach { segment ->
                    Span {
                        text(segment.text)
                        fontSize(style.fontSize)
                        style.lineHeight?.let { lineHeight(it) }
                        color(segment.color ?: config.colors.codeText)
                        if (segment.bold) {
                            fontWeightBold()
                        }
                    }
                }
            }
            return
        }
    }
    Text {
        attr {
            text(code)
            fontSize(style.fontSize)
            color(config.colors.codeText)
            lines(Int.MAX_VALUE)
            style.lineHeight?.let { lineHeight(it) }
        }
    }
}
