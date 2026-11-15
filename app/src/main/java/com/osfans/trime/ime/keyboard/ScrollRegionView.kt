/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.TextKeyboard.TextKey
import splitties.dimensions.dp

/**
 * A scrollable rectangle inside [KeyboardView]. Child [KeyView]s live in a
 * vertical [ScrollView] or a [HorizontalScrollView], stacked with a
 * [LinearLayout] so content size comes from the children themselves.
 * The parent keyboard does not intercept these gestures, and a scroll cancels
 * the child key press (standard Android [ACTION_CANCEL] after the scroll view
 * takes over).
 *
 * The slot is drawn as one rounded bar (the same fill as a key) with hairline
 * separators between items, matching the layout sketches: a grouped strip,
 * not a recessed track or overlay arrows.
 *
 * Separators are drawn on the content strip so they move and stretch with the
 * child keys when the scroll view overscrolls.
 */
@SuppressLint("ViewConstructor")
class ScrollRegionView(
    context: Context,
    theme: Theme,
    key: Key,
    keyboard: Keyboard,
    keyboardView: KeyboardView,
    keyboardActionListener: KeyboardActionListener,
) : FrameLayout(context) {

    private val region = requireNotNull(key.scrollRegion)
    private val horizontal = region.direction == TextKey.Scroll.HORIZONTAL
    private val scroller: ViewGroup
    private val content: ContentStrip
    private val insetH = keyboard.horizontalGap / 2
    private val insetV = keyboard.verticalGap / 2
    private val cornerRadius = dp(key.roundCorner)

    private val clipRect = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(1).toFloat().coerceAtLeast(1f)
    }

    val isVertical: Boolean get() = !horizontal

    private val originalKeyViews = mutableListOf<View>()
    private var filterMode = false
    private var filterCellWidth = 0
    private var filterCellHeight = 0
    private var selectedFilterColor = 0xFFFFFFFF.toInt()
    private var dimFilterColor = 0x80FFFFFF.toInt()

    init {
        clipToPadding = true
        clipChildren = false
        setWillNotDraw(false)
        applyCapsuleColors()

        content = ContentStrip(context).apply {
            orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        // Child sizes are computed from the full slot; the scroller sits inside
        // the capsule insets, so shrink the cross/main sizes to that viewport
        // or the last visible item is clipped.
        val innerW = (key.width - 2 * insetH).coerceAtLeast(1)
        val innerH = (key.height - 2 * insetV).coerceAtLeast(1)
        val visible = if (horizontal) {
            val sample = region.keys.firstOrNull()?.width ?: innerW
            if (sample > 0) (key.width / sample).coerceAtLeast(1) else 1
        } else {
            val sample = region.keys.firstOrNull()?.height ?: innerH
            if (sample > 0) (key.height / sample).coerceAtLeast(1) else 1
        }
        region.keys.forEachIndexed { index, child ->
            val cellW = if (horizontal) child.width else innerW
            val cellH = if (horizontal) innerH else innerH / visible
            val keyView = KeyView(context, theme, child, keyboard, keyboardView, keyboardActionListener).apply {
                id = child.index.takeIf { it >= 0 } ?: index
                layoutParams = LinearLayout.LayoutParams(cellW, cellH)
                drawKeyBackground = false
            }
            content.addView(keyView)
            if (!horizontal && filterCellWidth == 0) {
                filterCellWidth = cellW
                filterCellHeight = cellH
            }
        }
        originalKeyViews.addAll((0 until content.childCount).map { content.getChildAt(it) })

        scroller = if (horizontal) {
            DisallowingHorizontalScrollView(context).apply {
                isFillViewport = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
                isNestedScrollingEnabled = true
                addView(
                    content,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        } else {
            DisallowingScrollView(context).apply {
                isFillViewport = false
                isVerticalScrollBarEnabled = false
                overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
                isNestedScrollingEnabled = true
                addView(
                    content,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        }
        addView(
            scroller,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                leftMargin = insetH
                topMargin = insetV
                rightMargin = insetH
                bottomMargin = insetV
            },
        )
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun applyCapsuleColors() {
        // These slots are usually themed as functional keys (gray bar, light text).
        // Fill the grouped capsule with that same color so labels stay readable.
        val fill = runCatching { ColorManager.getColor("off_key_back_color") }.getOrNull()
            ?: runCatching { ColorManager.getColor("key_back_color") }.getOrNull()
        val ink = runCatching { ColorManager.getColor("off_key_text_color") }.getOrNull()
            ?: runCatching { ColorManager.getColor("key_text_color") }.getOrNull()
            ?: 0xFFFFFFFF.toInt()
        val bar = fill ?: ColorUtils.setAlphaComponent(ink, 0x4D)
        trackPaint.color = bar
        val hairline = if (ColorUtils.calculateLuminance(bar) < 0.6) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        separatorPaint.color = ColorUtils.setAlphaComponent(hairline, 0x4D)
        selectedFilterColor = ink
        dimFilterColor = ColorUtils.setAlphaComponent(ink, 0x99)
        if (filterMode) {
            for (i in 0 until content.childCount) {
                (content.getChildAt(i) as? TextView)?.let { label ->
                    label.setTextColor(if (label.isSelected) selectedFilterColor else dimFilterColor)
                }
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clipRect.set(
            insetH.toFloat(),
            insetV.toFloat(),
            (w - insetH).toFloat(),
            (h - insetV).toFloat(),
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (!clipRect.isEmpty) {
            canvas.drawRoundRect(clipRect, cornerRadius, cornerRadius, trackPaint)
        }
    }

    /**
     * Swap the slot's child keys for possible input combinations. Clicking an
     * item does not go through normal Rime text key actions. An empty [items]
     * list restores the original keys.
     */
    fun setCombinationFilters(
        items: List<String>,
        selectedIndex: Int,
        onItemClick: (index: Int, syllable: String) -> Unit,
    ) {
        if (horizontal) return
        if (items.isEmpty()) {
            restoreOriginalKeys()
            return
        }
        filterMode = true
        content.removeAllViews()
        val cellW = filterCellWidth.coerceAtLeast(1)
        val cellH = filterCellHeight.coerceAtLeast(1)
        items.forEachIndexed { index, syllable ->
            val selected = index == selectedIndex
            val label = TextView(context).apply {
                text = syllable
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(if (selected) selectedFilterColor else dimFilterColor)
                isClickable = true
                isFocusable = true
                isSelected = selected
                setOnClickListener { onItemClick(index, syllable) }
                layoutParams = LinearLayout.LayoutParams(cellW, cellH)
            }
            content.addView(label)
        }
        content.requestLayout()
        invalidate()
    }

    fun restoreOriginalKeys() {
        if (!filterMode) return
        filterMode = false
        content.removeAllViews()
        originalKeyViews.forEach { child ->
            (child.parent as? ViewGroup)?.removeView(child)
            content.addView(child)
        }
        content.requestLayout()
        invalidate()
    }

    /**
     * Ask ancestors not to steal the gesture, so a ScrollView inside an IME
     * keyboard can take over after touch slop and cancel the child key press.
     */
    private fun ViewGroup.disallowParentIntercept() {
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private inner class ContentStrip(context: Context) : LinearLayout(context) {
        init {
            setWillNotDraw(false)
        }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            drawSeparators(canvas)
        }

        private fun drawSeparators(canvas: Canvas) {
            val last = childCount - 1
            if (last <= 0) return
            for (i in 0 until last) {
                val child = getChildAt(i)
                val next = getChildAt(i + 1)
                if (horizontal) {
                    val x = (child.right + next.left) / 2f
                    canvas.drawLine(x, 0f, x, height.toFloat(), separatorPaint)
                } else {
                    val y = (child.bottom + next.top) / 2f
                    canvas.drawLine(0f, y, width.toFloat(), y, separatorPaint)
                }
            }
        }
    }

    private inner class DisallowingScrollView(context: Context) : ScrollView(context) {
        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                disallowParentIntercept()
            }
            return super.onInterceptTouchEvent(ev)
        }
    }

    private inner class DisallowingHorizontalScrollView(context: Context) : HorizontalScrollView(context) {
        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                disallowParentIntercept()
            }
            return super.onInterceptTouchEvent(ev)
        }
    }
}
