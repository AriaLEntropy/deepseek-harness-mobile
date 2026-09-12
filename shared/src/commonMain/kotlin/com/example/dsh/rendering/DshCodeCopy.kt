package com.example.dsh.rendering

import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode

/** Extract from the existing parser AST, retaining blank lines/indentation and excluding fences/language. */
internal fun dshCodeFenceSource(content: String, node: ASTNode): String = buildString {
    var body = false
    for (child in node.children) {
        if (child.type == MarkdownTokenTypes.CODE_FENCE_END) break
        if (body && (child.type == MarkdownTokenTypes.CODE_FENCE_CONTENT || child.type == MarkdownTokenTypes.EOL)) {
            append(child.getTextInNode(content))
        }
        if (child.type == MarkdownTokenTypes.EOL) body = true
    }
}
