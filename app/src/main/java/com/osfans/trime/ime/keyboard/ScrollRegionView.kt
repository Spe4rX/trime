/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.TextKeyboard.TextKey

/**
 * A scrollable rectangle inside [KeyboardView]. Child [KeyView]s live in a
 * vertical [ScrollView] or a [HorizontalScrollView]. The parent keyboard does
 * not intercept these gestures, and a scroll cancels the child key press
 * (standard Android [ACTION_CANCEL] after the scroll view takes over).
 *
 * Child keys are placed with layout margins, not translation. ScrollView
 * measures its content from layout bounds; translation would leave the
 * content the size of one key, clip the rest, and make the slot unscrollable.
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

    init {
        clipToPadding = true
        clipChildren = true
        val contentWidth = (region.keys.maxOfOrNull { it.x + it.width } ?: key.width).coerceAtLeast(key.width)
        val contentHeight = (region.keys.maxOfOrNull { it.y + it.height } ?: key.height).coerceAtLeast(key.height)
        val content = FrameLayout(context)
        region.keys.forEachIndexed { index, child ->
            val keyView = KeyView(context, theme, child, keyboard, keyboardView, keyboardActionListener).apply {
                id = child.index.takeIf { it >= 0 } ?: index
                layoutParams = LayoutParams(child.width, child.height).apply {
                    leftMargin = child.x
                    topMargin = child.y
                }
                setPadding(
                    keyboard.horizontalGap / 2,
                    keyboard.verticalGap / 2,
                    keyboard.horizontalGap / 2,
                    keyboard.verticalGap / 2,
                )
            }
            content.addView(keyView)
        }
        val contentParams = ViewGroup.MarginLayoutParams(contentWidth, contentHeight)
        val scroller = if (region.direction == TextKey.Scroll.HORIZONTAL) {
            DisallowingHorizontalScrollView(context).apply {
                isFillViewport = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
                isHorizontalFadingEdgeEnabled = true
                isNestedScrollingEnabled = true
                addView(content, contentParams)
            }
        } else {
            DisallowingScrollView(context).apply {
                isFillViewport = false
                isVerticalScrollBarEnabled = false
                overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
                isVerticalFadingEdgeEnabled = true
                isNestedScrollingEnabled = true
                addView(content, contentParams)
            }
        }
        addView(scroller, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /**
     * Ask ancestors not to steal the gesture, so a ScrollView inside an IME
     * keyboard can take over after touch slop and cancel the child key press.
     */
    private fun ViewGroup.disallowParentIntercept() {
        parent?.requestDisallowInterceptTouchEvent(true)
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
