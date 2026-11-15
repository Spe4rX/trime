/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import com.osfans.trime.data.theme.model.TextKeyboard.TextKey

/**
 * A scrollable slot on a text keyboard: a rectangle that hosts child [Key]s
 * inside a vertical or horizontal scroll view. Child coordinates are relative
 * to the scroll content, not the keyboard.
 */
data class ScrollRegion(
    val direction: TextKey.Scroll,
    val keys: List<Key>,
)

/**
 * Pure layout helpers for scroll slots: viewport size, child rectangles, and
 * the reserved "holes" a multi-row vertical slot punches in later rows so
 * the rest of those rows start after the slot.
 */
object ScrollRegionLayout {
    data class OccupiedSlot(
        val startRow: Int,
        val rowSpan: Int,
        val xWeight: Float,
        val widthWeight: Float,
    ) {
        val endRow: Int get() = startRow + rowSpan.coerceAtLeast(1)

        fun reservesRow(row: Int): Boolean = row > startRow && row < endRow
    }

    data class ChildRect(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    )

    fun effectiveRowSpan(rowSpan: Int): Int = rowSpan.coerceAtLeast(1)

    /**
     * How many child keys should fit in the viewport. When [visibleCount] is
     * not set (0 or negative), a vertical slot shows [rowSpan] keys at a time
     * so they line up with the rows it occupies.
     */
    fun effectiveVisibleCount(visibleCount: Int, rowSpan: Int): Int =
        if (visibleCount > 0) visibleCount else effectiveRowSpan(rowSpan)

    fun viewportHeight(rowHeights: List<Int>, startRow: Int, rowSpan: Int): Int {
        if (rowHeights.isEmpty() || startRow !in rowHeights.indices) return 0
        return rowHeights.drop(startRow).take(effectiveRowSpan(rowSpan)).sum()
    }

    fun reservedWeightForRow(row: Int, slots: List<OccupiedSlot>): Float =
        slots.filter { it.reservesRow(row) }.map { it.widthWeight }.sum()

    /** Pixel holes (start x, width) reserved on [row] by earlier multi-row slots. */
    fun holesForRow(
        row: Int,
        slots: List<OccupiedSlot>,
        weightToPx: (Float) -> Int,
    ): List<Pair<Int, Int>> = slots
        .filter { it.reservesRow(row) }
        .map { weightToPx(it.xWeight) to weightToPx(it.widthWeight) }

    /**
     * Shift [x] forward so that `[x, x + width)` does not overlap any hole.
     * Holes are visited left to right; a later hole can push the position
     * again.
     */
    fun advancePastHoles(x: Int, width: Int, holes: List<Pair<Int, Int>>): Int {
        if (holes.isEmpty() || width < 0) return x
        var pos = x
        for ((holeX, holeWidth) in holes.sortedBy { it.first }) {
            if (holeWidth <= 0) continue
            val holeEnd = holeX + holeWidth
            if (pos + width <= holeX) return pos
            if (pos < holeEnd) pos = holeEnd
        }
        return pos
    }

    /**
     * Lay out [childWidths] inside a viewport. Vertical children share the
     * viewport width and a uniform height of `viewportHeight / visibleCount`.
     * Horizontal children share the viewport height and keep their own widths.
     * Returns the child rectangles and the scroll content size.
     */
    fun layoutChildren(
        direction: TextKey.Scroll,
        viewportWidth: Int,
        viewportHeight: Int,
        visibleCount: Int,
        childWidths: List<Int>,
    ): Pair<List<ChildRect>, Pair<Int, Int>> {
        if (direction == TextKey.Scroll.NONE || childWidths.isEmpty()) {
            return emptyList<ChildRect>() to (viewportWidth to viewportHeight)
        }
        val count = childWidths.size
        return if (direction == TextKey.Scroll.HORIZONTAL) {
            var x = 0
            val rects = childWidths.map { w ->
                val rect = ChildRect(x, 0, w.coerceAtLeast(0), viewportHeight)
                x += w.coerceAtLeast(0)
                rect
            }
            rects to (x.coerceAtLeast(viewportWidth) to viewportHeight)
        } else {
            val shown = effectiveVisibleCount(visibleCount, 1).coerceAtLeast(1)
            val childHeight = if (shown > 0) viewportHeight / shown else viewportHeight
            val rects = List(count) { index ->
                ChildRect(0, index * childHeight, viewportWidth, childHeight)
            }
            val contentHeight = (count * childHeight).coerceAtLeast(viewportHeight)
            rects to (viewportWidth to contentHeight)
        }
    }

    fun childWidthPx(widthWeight: Float, defaultWeight: Float, weightToPx: (Float) -> Int): Int {
        val weight = if (widthWeight > 0f) widthWeight else defaultWeight
        return weightToPx(weight).coerceAtLeast(0)
    }
}
