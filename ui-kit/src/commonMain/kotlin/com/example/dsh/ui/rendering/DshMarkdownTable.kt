package com.example.dsh.ui.rendering

import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.SelectableOption
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuiklybase.components.MarkdownComponentModel
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import kotlin.math.ceil
import kotlin.math.max

private enum class CellAlign { LEFT, CENTER, RIGHT }

private data class ParsedCell(val text: String, val align: CellAlign)

private data class ParsedTable(
    val header: List<ParsedCell>,
    val rows: List<List<ParsedCell>>,
)

private const val CELL_PADDING_H = 8f
private const val CELL_PADDING_V = 6f
private const val TABLE_FONT_SIZE = DSH_CHAT_BODY_FONT - 4f
private const val TABLE_LINE_HEIGHT_RATIO = 1.4f
private const val MIN_COL_EM = 3f
private const val MAX_COL_EM = 10f

/**
 * 自定义 Markdown 表格渲染：列宽估算，超宽时整表横向滚动。
 *
 * 布局策略：
 * - 按单元格文本估算每列宽度（全角 1em，半角 0.58em），clamp 到 [3em, 10em]
 * - 所有列宽之和 ≤ 可用宽度时，按比例铺满，不进 Scroller
 * - 超出时，保持估算列宽，整表放进横向 Scroller，纵向手势穿透给外层消息列表
 *
 * 高度在布局阶段直接计算（根据列宽和换行行数），不需要异步回读。
 */
fun ViewContainer<*, *>.dshMarkdownTable(
    model: MarkdownComponentModel,
    contentWidth: Float,
    dark: Boolean,
) {
    val table = parseTable(model.node, model.content)
    if (table.header.isEmpty() || contentWidth <= 0f) return

    val colCount = table.header.size
    val fontScale = TABLE_FONT_SIZE
    val minW = fontScale * MIN_COL_EM
    val maxW = fontScale * MAX_COL_EM

    // 估算每列宽度
    val colWidths = FloatArray(colCount)
    for (col in 0 until colCount) {
        var widest = 0f
        // 表头
        widest = max(widest, estimateTextWidth(table.header[col].text, fontScale))
        // 数据行
        for (row in table.rows) {
            if (col < row.size) {
                widest = max(widest, estimateTextWidth(row[col].text, fontScale))
            }
        }
        // 加左右 padding 后 clamp
        val w = (widest + CELL_PADDING_H * 2).coerceIn(minW, maxW)
        colWidths[col] = w
    }

    val totalWidth = colWidths.sum()
    val rowHeight = fontScale * TABLE_LINE_HEIGHT_RATIO + CELL_PADDING_V * 2

    // 颜色
    val textColor = if (dark) 0xFFE4E6EA else 0xFF1F1F23
    val subTextColor = if (dark) 0xFFB7BBC2 else 0xFF61666D
    val bgColor = if (dark) 0xFF202124 else 0xFFFAFAFA
    val borderColor = if (dark) 0xFF3A3B3F else 0xFFE5E5E5

    val overflow = totalWidth > contentWidth

    View {
        attr {
            selectable(SelectableOption.DISABLE)
            width(contentWidth)
            if (!overflow) {
                backgroundColor(bgColor)
                borderRadius(8f)
                marginTop(8f)
                marginBottom(8f)
            }
        }

        if (overflow) {
            // 超宽：横向 Scroller
            // 计算表格总高度
            val totalHeight = calcTableHeight(table, colWidths, fontScale, rowHeight)
            Scroller {
                attr {
                    flexDirectionRow()
                    width(contentWidth)
                    height(totalHeight)
                    backgroundColor(bgColor)
                    borderRadius(8f)
                    marginTop(8f)
                    marginBottom(8f)
                }
                renderTableContent(table, colWidths, totalWidth, fontScale, rowHeight, textColor, subTextColor, borderColor)
            }
        } else {
            // 铺满：按比例缩放列宽
            val scale = contentWidth / totalWidth
            val scaledWidths = colWidths.map { it * scale }.toFloatArray()
            renderTableContent(table, scaledWidths, contentWidth, fontScale, rowHeight, textColor, subTextColor, borderColor)
        }
    }
}

private fun ViewContainer<*, *>.renderTableContent(
    table: ParsedTable,
    colWidths: FloatArray,
    totalWidth: Float,
    fontSize: Float,
    rowHeight: Float,
    textColor: Long,
    subTextColor: Long,
    borderColor: Long,
) {
    View {
        attr {
            width(totalWidth)
            flexDirectionColumn()
        }
        // 表头
        View {
            attr {
                flexDirectionRow()
                width(totalWidth)
                border(Border(1f, BorderStyle.SOLID, Color(borderColor)))
            }
            for (col in colWidths.indices) {
                val align = if (col < table.header.size) table.header[col].align else CellAlign.LEFT
                TableCell(
                    text = table.header[col].text,
                    width = colWidths[col],
                    fontSize = fontSize,
                    rowHeight = rowHeight,
                    color = textColor,
                    bold = true,
                    align = align,
                    borderColor = borderColor,
                )
            }
        }
        // 数据行
        for (row in table.rows) {
            View {
                attr {
                    flexDirectionRow()
                    width(totalWidth)
                }
                for (col in colWidths.indices) {
                    val cellText = if (col < row.size) row[col].text else ""
                    val align = if (col < row.size) row[col].align else CellAlign.LEFT
                    TableCell(
                        text = cellText,
                        width = colWidths[col],
                        fontSize = fontSize,
                        rowHeight = rowHeight,
                        color = subTextColor,
                        bold = false,
                        align = align,
                        borderColor = borderColor,
                    )
                }
            }
        }
    }
}

private fun ViewContainer<*, *>.TableCell(
    text: String,
    width: Float,
    fontSize: Float,
    rowHeight: Float,
    color: Long,
    bold: Boolean,
    align: CellAlign,
    borderColor: Long,
) {
    View {
        attr {
            flexDirectionRow()
            width(width)
            padding(CELL_PADDING_V, CELL_PADDING_H, CELL_PADDING_V, CELL_PADDING_H)
            border(Border(1f, BorderStyle.SOLID, Color(borderColor)))
        }
        Text {
            attr {
                this.text(text)
                fontSize(fontSize)
                color(color)
                lineHeight(fontSize * TABLE_LINE_HEIGHT_RATIO)
                if (bold) fontWeightSemiBold()
                when (align) {
                    CellAlign.CENTER -> textAlignCenter()
                    CellAlign.RIGHT -> textAlignRight()
                    CellAlign.LEFT -> textAlignLeft()
                }
                textOverFlowWordWrapping()
            }
        }
    }
}

/** 估算文本渲染宽度：全角 1em，半角 0.58em */
private fun estimateTextWidth(text: String, fontSize: Float): Float {
    var width = 0f
    for (ch in text) {
        width += if (isFullWidth(ch)) fontSize else fontSize * 0.58f
    }
    return width
}

private fun isFullWidth(ch: Char): Boolean {
    val code = ch.code
    // CJK Unified Ideographs, Hiragana, Katakana, Hangul, CJK punctuation, fullwidth forms
    return (code in 0x4E00..0x9FFF) ||
        (code in 0x3040..0x30FF) ||
        (code in 0xAC00..0xD7AF) ||
        (code in 0x3000..0x303F) ||
        (code in 0xFF00..0xFFEF)
}

/** 根据列宽和换行行数计算表格总高度 */
private fun calcTableHeight(
    table: ParsedTable,
    colWidths: FloatArray,
    fontSize: Float,
    rowHeight: Float,
): Float {
    // 表头行数
    var maxLines = 1
    for (col in table.header.indices) {
        val lines = estimateLineCount(table.header[col].text, colWidths[col], fontSize)
        maxLines = max(maxLines, lines)
    }
    var totalHeight = maxLines * rowHeight
    // 数据行
    for (row in table.rows) {
        var rowMaxLines = 1
        for (col in row.indices) {
            if (col < colWidths.size) {
                val lines = estimateLineCount(row[col].text, colWidths[col], fontSize)
                rowMaxLines = max(rowMaxLines, lines)
            }
        }
        totalHeight += rowMaxLines * rowHeight
    }
    return totalHeight
}

private fun estimateLineCount(text: String, columnWidth: Float, fontSize: Float): Int {
    val textWidth = estimateTextWidth(text, fontSize)
    val availableWidth = columnWidth - CELL_PADDING_H * 2
    if (availableWidth <= 0f) return 1
    return ceil(textWidth / availableWidth).toInt().coerceAtLeast(1)
}

/** 从 AST 解析表格：表头、分隔线（对齐）、数据行 */
private fun parseTable(node: ASTNode, content: String): ParsedTable {
    val aligns = parseAlignment(node, content)

    // 表头
    val headerNode = node.findChildOfType(GFMElementTypes.HEADER)
    val headerCells = headerNode?.children
        ?.filter { it.type == GFMTokenTypes.CELL }
        ?.map { cell ->
            val text = cell.getTextInNode(content).toString().trim()
            ParsedCell(text, aligns.getOrElse(headerNode.children.indexOf(cell)) { CellAlign.LEFT })
        }
        ?: emptyList()

    // 数据行
    val rows = node.children
        .filter { it.type == GFMElementTypes.ROW }
        .map { rowNode ->
            val cells = rowNode.children.filter { it.type == GFMTokenTypes.CELL }
            cells.mapIndexed { index, cell ->
                val text = cell.getTextInNode(content).toString().trim()
                ParsedCell(text, aligns.getOrElse(index) { CellAlign.LEFT })
            }
        }

    return ParsedTable(headerCells, rows)
}

/** 从 TABLE_SEPARATOR 行解析每列对齐方式 */
private fun parseAlignment(node: ASTNode, content: String): List<CellAlign> {
    val sepNode = node.children.find { it.type == GFMTokenTypes.TABLE_SEPARATOR } ?: return emptyList()
    return sepNode.children
        .filter { it.type == GFMTokenTypes.CELL }
        .map { cell ->
            val raw = cell.getTextInNode(content).toString().trim()
            val leftColon = raw.startsWith(":")
            val rightColon = raw.endsWith(":")
            when {
                leftColon && rightColon -> CellAlign.CENTER
                rightColon -> CellAlign.RIGHT
                else -> CellAlign.LEFT
            }
        }
}
