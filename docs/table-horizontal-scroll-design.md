# Markdown 表格横向滚动设计

## 问题

KuiklyMarkdown 默认表格每列 `flex(1f)` 平分可用宽度，列多时单元格文字互相挤压、换行严重。移动端需要：列多时表格整体可横向滚动。

## 方案

通过 `markdownComponents(table = …)` 注入自定义表格渲染器，替换默认实现。核心思路：



1. 解析表格 AST，拿到表头行、数据行、每列对齐方式

2. 按单元格文本估算每列宽度

3. 如果所有列宽之和 ≤ 可用宽度，按比例铺满（和默认行为一致）

4. 如果超出可用宽度，整表放进横向 Scroller

## 文件结构

新增一个文件：



```
ui-kit/src/commonMain/kotlin/com/example/dsh/ui/rendering/DshMarkdownTable.kt
```

导出一个 `ViewContainer<*, *>.dshMarkdownTable(model) { ... }` 扩展函数，在 `DshMarkdownView` 的 `markdownComponents` 里作为 `table` 参数传入。

## 核心数据结构



```
private class TableCell(

&#x20;   val text: String,

&#x20;   val alignment: CellAlign, // LEFT / CENTER / RIGHT

)

private enum class CellAlign { LEFT, CENTER, RIGHT }

private data class TableData(

&#x20;   val header: List\<TableCell>,

&#x20;   val rows: List\<List\<TableCell>>,

)
```

## 列宽估算

对每列，遍历该列所有单元格（含表头），估算最长文本的渲染宽度：



```
private fun estimateColumnWidth(cells: List\<TableCell>, fontSize: Float): Float {

&#x20;   var maxWidth = 0f

&#x20;   for (cell in cells) {

&#x20;       val w = estimateTextWidth(cell.text, fontSize)

&#x20;       if (w > maxWidth) maxWidth = w

&#x20;   }

&#x20;   // 加上左右 padding

&#x20;   return maxWidth + CELL\_PADDING\_H \* 2

}
```

文本宽度估算规则：



* 全角字符（CJK、全角标点）：约 `fontSize`

* 半角字符（拉丁字母、数字、半角标点）：约 `fontSize * 0.58`

* 上下限：最小 `fontSize * 3`（保证可读），最大 `fontSize * 10`（超过则单元格内换行）



```
private fun estimateTextWidth(text: String, fontSize: Float): Float {

&#x20;   var width = 0f

&#x20;   for (ch in text) {

&#x20;       width += if (ch.isFullWidth()) fontSize else fontSize \* 0.58f

&#x20;   }

&#x20;   return width

}
```

列宽 clamp 到 `[MIN_COL_WIDTH, MAX_COL_WIDTH]`：



```
private val MIN\_COL\_WIDTH get() = DSH\_CHAT\_BODY\_FONT \* 3

private val MAX\_COL\_WIDTH get() = DSH\_CHAT\_BODY\_FONT \* 10
```

超过 MAX\_COL\_WIDTH 的单元格文本会在列内换行（`lines(Int.MAX_VALUE)` + `wordWrapping`）。

## 布局决策



```
val totalWidth = columnWidths.sum()

val availableWidth = contentWidth

if (totalWidth <= availableWidth) {

&#x20;   // 铺满模式：按 availableWidth 比例分配列宽

&#x20;   val scale = availableWidth / totalWidth

&#x20;   val scaledWidths = columnWidths.map { it \* scale }

&#x20;   // 渲染普通列布局，不进 Scroller

} else {

&#x20;   // 横向滚动模式：保持估算列宽，整体放进横向 Scroller

}
```

## 横向 Scroller 的高度处理

Kuikly 横向 Scroller 的内容视图是绝对定位的（`absolutePosition(top=0, left=0, bottom=0)`），不会随内容自动撑高。需要手动给 Scroller 设高度。

**做法**：根据行数和行高直接计算高度，不需要异步回读：



```
// 每列换行行数的最大值 = 总行高

val rowsHeight = data.rows.size \* rowHeight

val headerHeight = HEADER\_FONT\_SIZE \* LINE\_HEIGHT\_RATIO + HEADER\_PADDING\_V \* 2

val tableHeight = headerHeight + rowsHeight

// 加上上下 padding
```

行高计算：



* 表头行：`HEADER_FONT_SIZE * 1.4 + 上下padding`

* 数据行：`BODY_FONT_SIZE * 1.5 + 上下padding`

* 单元格换行时，每行高度 × 换行数

由于列宽已经估算好了，每个单元格在固定列宽下的换行行数可以提前算出：



```
private fun estimateLineCount(text: String, columnWidth: Float, fontSize: Float): Int {

&#x20;   val textWidth = estimateTextWidth(text, fontSize)

&#x20;   val availableWidth = columnWidth - CELL\_PADDING\_H \* 2

&#x20;   return ceil(textWidth / availableWidth).toInt().coerceAtLeast(1)

}
```

最终表格高度 = `(headerLines + maxRowLines * rowCount) * lineHeight + padding`。

这样在布局阶段就能算出准确高度，不需要 `layoutFrameDidChange` 回调。

## 渲染结构



```
View (外层，宽度 = contentWidth)

└── Scroller (flexDirectionRow, 高度 = 计算出的 tableHeight)

&#x20;   └── View (内容容器，宽度 = totalWidth, flexDirectionColumn)

&#x20;       ├── 表头行 (flexDirectionRow, 背景色, 底部边框)

&#x20;       │   └── 每列 Text (宽度 = columnWidth, 对齐)

&#x20;       └── 数据行 (flexDirectionRow)

&#x20;           └── 每列 Text (宽度 = columnWidth, 对齐, 底部细分割线)
```

铺满模式下不包 Scroller，直接渲染上述 View。

## 流式 vs 已结算



* **流式中**：表格内容还在变化，列宽每次都可能变。直接用当前已有的行数据估算，不做特殊缓存。流式中表格高度变化会触发外层 relayout，这是可接受的（和其他 markdown block 一致）。

* **已结算**：表格内容固定，列宽估算一次即可。

## selectable 协同

表格根 View 设 `selectable(SelectableOption.DISABLE)`，表格内文字不参与文本选择。避免选择手势和横向滚动冲突。

## 手势方向

横向 Scroller 设 `flexDirectionRow()`，Kuikly 自动处理为横向滚动。纵向手势自然穿透给外层消息列表（Scroller 只响应横向拖拽）。不需要额外的 nestedScroll 配置。

## AST 解析

从 `MarkdownComponentModel.node` 解析表格结构：



```
private fun parseTable(content: String, node: ASTNode): TableData {

&#x20;   // node 是 GitHubTableMarkerBlock

&#x20;   // 子节点：表头行、分隔线行（对齐）、数据行

&#x20;   // 每行的子节点是表格单元格

&#x20;   // 对齐信息从分隔线行的 :---:, ---, ---: 推断

}
```

对齐方式从分隔线行解析：



* `:---` 或 `---` → LEFT

* `:---:` → CENTER

* `---:` → RIGHT

## 边界情况



1. **空表格**（0 行或 0 列）：不渲染，返回空 View

2. **单列**：直接铺满，不进 Scroller

3. **超长文本**：列宽 clamp 到 MAX，单元格内换行

4. **流式中表格不完整**：只有部分行，按当前行数估算

5. **表头和数据行列数不一致**：以表头列数为准，数据行缺列补空字符串

## 接入点

在 `DshMarkdownView.body()` 的 `markdownComponents` 调用里加 `table` 参数：



```
components = markdownComponents(

&#x20;   codeFence = { ... },

&#x20;   paragraph = { ... },

&#x20;   table = { model, container ->

&#x20;       container.dshMarkdownTable(

&#x20;           model = model,

&#x20;           contentWidth = ctx.attr.contentWidth,

&#x20;           dark = ctx.attr.darkMode,

&#x20;       )

&#x20;   },

),
```