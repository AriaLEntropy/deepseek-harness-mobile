package com.example.dsh.ui.rendering

// 过程项左侧装饰连接线的度量：标题下方分成 [竖线 | 内容] 左右两列。
// 线体宽度、内容列整体右移预留的沟槽宽度，以及线与内容之间保留的空隙。
const val DSH_CONNECTOR_LINE_WIDTH = 1f
const val DSH_CONNECTOR_GUTTER = 14f
const val DSH_CONNECTOR_LINE_GAP = 6f
const val DSH_CONNECTOR_LINE_LEFT =
    DSH_CONNECTOR_GUTTER - DSH_CONNECTOR_LINE_GAP - DSH_CONNECTOR_LINE_WIDTH
