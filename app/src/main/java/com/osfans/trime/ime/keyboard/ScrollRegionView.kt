/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
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
 * A slightly recessed track and edge chevrons mark the slot as scrollable
 * when content overflows the viewport.
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
    private val chevronPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chevronPath = Path()
    private val chevronSize = context.dp(10).toFloat()
    private val fadeLength = context.dp(20)

    init {
        clipToPadding = true
        clipChildren = true
        setWillNotDraw(false)
        applyTrackBackground(key)

        val content = LinearLayout(context).apply {
            orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        region.keys.forEachIndexed { index, child ->
            val keyView = KeyView(context, theme, child, keyboard, keyboardView, keyboardActionListener).apply {
                id = child.index.takeIf { it >= 0 } ?: index
                layoutParams = LinearLayout.LayoutParams(child.width, child.height)
                setPadding(
                    keyboard.horizontalGap / 2,
                    keyboard.verticalGap / 2,
                    keyboard.horizontalGap / 2,
                    keyboard.verticalGap / 2,
                )
            }
            content.addView(keyView)
        }

        scroller = if (horizontal) {
            DisallowingHorizontalScrollView(context).apply {
                isFillViewport = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
                isHorizontalFadingEdgeEnabled = true
                setFadingEdgeLength(fadeLength)
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
                isVerticalFadingEdgeEnabled = true
                setFadingEdgeLength(fadeLength)
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
        addView(scroller, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun applyTrackBackground(key: Key) {
        val base = runCatching { ColorManager.getColor("keyboard_back_color") }.getOrNull() ?: return
        val ink = runCatching { ColorManager.getColor("key_text_color") }.getOrNull() ?: 0xFF000000.toInt()
        val track = ColorUtils.blendARGB(base, ink, 0.08f)
        background = GradientDrawable().apply {
            setColor(track)
            cornerRadius = dp(key.roundCorner)
        }
        chevronPaint.color = ColorUtils.setAlphaComponent(ink, 0x99)
        chevronPaint.style = Paint.Style.FILL
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Chevrons depend on whether the scroller can still move.
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        drawChevrons(canvas)
    }

    private fun drawChevrons(canvas: Canvas) {
        if (chevronPaint.color == 0 || width == 0 || height == 0) return
        val inset = chevronSize
        if (horizontal) {
            if (scroller.canScrollHorizontally(-1)) {
                drawChevron(canvas, inset, height / 2f, left = true)
            }
            if (scroller.canScrollHorizontally(1)) {
                drawChevron(canvas, width - inset, height / 2f, left = false)
            }
        } else {
            if (scroller.canScrollVertically(-1)) {
                drawChevron(canvas, width / 2f, inset, up = true)
            }
            if (scroller.canScrollVertically(1)) {
                drawChevron(canvas, width / 2f, height - inset, up = false)
            }
        }
    }

    private fun drawChevron(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        left: Boolean = false,
        up: Boolean = false,
    ) {
        chevronPath.reset()
        val s = chevronSize
        when {
            horizontal && left -> {
                chevronPath.moveTo(cx + s / 2f, cy - s)
                chevronPath.lineTo(cx - s / 2f, cy)
                chevronPath.lineTo(cx + s / 2f, cy + s)
            }
            horizontal && !left -> {
                chevronPath.moveTo(cx - s / 2f, cy - s)
                chevronPath.lineTo(cx + s / 2f, cy)
                chevronPath.lineTo(cx - s / 2f, cy + s)
            }
            !horizontal && up -> {
                chevronPath.moveTo(cx - s, cy + s / 2f)
                chevronPath.lineTo(cx, cy - s / 2f)
                chevronPath.lineTo(cx + s, cy + s / 2f)
            }
            else -> {
                chevronPath.moveTo(cx - s, cy - s / 2f)
                chevronPath.lineTo(cx, cy + s / 2f)
                chevronPath.lineTo(cx + s, cy - s / 2f)
            }
        }
        chevronPath.close()
        canvas.drawPath(chevronPath, chevronPaint)
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
