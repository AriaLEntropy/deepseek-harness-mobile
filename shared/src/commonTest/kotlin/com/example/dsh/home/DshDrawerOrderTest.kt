package com.example.dsh.home

import kotlin.test.Test
import kotlin.test.assertEquals

class DshDrawerOrderTest {

    @Test
    fun applyOrderKeepsUnknownItemsLastAndStable() {
        val items = listOf("a", "b", "c")
        assertEquals(items, dshApplyOrder(items, emptyList()) { it })
        assertEquals(listOf("c", "a", "b"), dshApplyOrder(items, listOf("c", "a")) { it })
    }

    @Test
    fun moveItemMovesAndGuardsInvalidInput() {
        assertEquals(listOf("b", "c", "a"), dshMoveItem(listOf("a", "b", "c"), 0, 2))
        assertEquals(listOf("a", "b", "c"), dshMoveItem(listOf("a", "b", "c"), 1, 1))
        assertEquals(listOf("a", "b", "c"), dshMoveItem(listOf("a", "b", "c"), -1, 0))
        assertEquals(listOf("a", "b", "c"), dshMoveItem(listOf("a", "b", "c"), 0, 9))
        assertEquals(emptyList<String>(), dshMoveItem(emptyList<String>(), 0, 1))
    }

    @Test
    fun dropIndexPicksClosestRowCenter() {
        val heights = listOf(48f, 48f, 48f)
        assertEquals(0, dshDropIndex(0f, 0, heights))
        assertEquals(1, dshDropIndex(48f, 0, heights))
        assertEquals(1, dshDropIndex(0f, 1, heights))
        assertEquals(-1, dshDropIndex(0f, 0, emptyList()))
        assertEquals(1, dshDropIndex(100f, 0, listOf(48f, 48f)))
    }

    @Test
    fun dragOffsetFollowsRowAndShiftsCrossedRows() {
        val down = DshDrawerDrag(
            kind = DshDrawerDragKind.SESSION,
            key = "x",
            groupKey = "g",
            startIndex = 0,
            targetIndex = 2,
            offsetY = 10f,
            itemHeight = 48f,
        )
        assertEquals(10f, dshRowDragOffset(down, "g", "x", 0))
        assertEquals(-48f, dshRowDragOffset(down, "g", "y", 1))
        assertEquals(-48f, dshRowDragOffset(down, "g", "z", 2))
        assertEquals(0f, dshRowDragOffset(down, "g", "w", 3))
        assertEquals(0f, dshRowDragOffset(down, "other", "y", 1))

        val up = down.copy(startIndex = 2, targetIndex = 0)
        assertEquals(48f, dshRowDragOffset(up, "g", "y", 1))
        assertEquals(48f, dshRowDragOffset(up, "g", "z", 0))
        assertEquals(0f, dshRowDragOffset(DshDrawerDrag(), "g", "x", 0))
    }
}
