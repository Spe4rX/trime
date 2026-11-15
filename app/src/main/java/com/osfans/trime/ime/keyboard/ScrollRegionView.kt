/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
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
    private val content: LinearLayout
    private val insetH = keyboard.horizontalGap / 2
    private val insetV = keyboard.verticalGap / 2
    private val cornerRadius = dp(key.roundCorner).toFloat()
    private val separatorInset = context.dp(8).toFloat()

    private val clipRect = RectF()
    private val childRect = Rect()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(1).toFloat().coerceAtLeast(1f)
    }

    init {
        clipToPadding = true
        clipChildren = false
        setWillNotDraw(false)
        applyCapsuleColors()

        content = LinearLayout(context).apply {
            orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        region.keys.forEachIndexed { index, child ->
            val keyView = KeyView(context, theme, child, keyboard, keyboardView, keyboardActionListener).apply {
                id = child.index.takeIf { it >= 0 } ?: index
                layoutParams = LinearLayout.LayoutParams(child.width, child.height)
                drawKeyBackground = false
            }
            content.addView(keyView)
        }

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

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        drawSeparators(canvas)
    }

    private fun drawSeparators(canvas: Canvas) {
        val last = content.childCount - 1
        if (last <= 0) return
        for (i in 0 until last) {
            val child = content.getChildAt(i)
            childRect.set(0, 0, child.width, child.height)
            offsetDescendantRectToMyCoords(child, childRect)
            if (horizontal) {
                val x = childRect.right.toFloat()
                canvas.drawLine(
                    x,
                    childRect.top + separatorInset,
                    x,
                    childRect.bottom - separatorInset,
                    separatorPaint,
                )
            } else {
                val y = childRect.bottom.toFloat()
                canvas.drawLine(
                    childRect.left + separatorInset,
                    y,
                    childRect.right - separatorInset,
                    y,
                    separatorPaint,
                )
            }
        }
    }

    /**
     * Ask ancestors not to steal the gesture, so a ScrollView inside an IME
     * keyboard can take over after touch slop and cancel the child key press.
     */
    private fun ViewGroup.disallowParentIntercept() {
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun onRegionScrolled() {
        invalidate()
    }

    private inner class DisallowingScrollView(context: Context) : ScrollView(context) {
        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                disallowParentIntercept()
            }
            return super.onInterceptTouchEvent(ev)
        }

        override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
            super.onScrollChanged(l, t, oldl, oldt)
            onRegionScrolled()
        }
    }

    private inner class DisallowingHorizontalScrollView(context: Context) : HorizontalScrollView(context) {
        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                disallowParentIntercept()
            }
            return super.onInterceptTouchEvent(ev)
        }

        override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
            super.onScrollChanged(l, t, oldl, oldt)
            onRegionScrolled()
        }
    }
}
