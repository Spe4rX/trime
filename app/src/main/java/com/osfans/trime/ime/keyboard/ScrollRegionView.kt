/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
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
        val contentWidth = region.keys.maxOfOrNull { it.x + it.width } ?: key.width
        val contentHeight = region.keys.maxOfOrNull { it.y + it.height } ?: key.height
        val content = FrameLayout(context).apply {
            layoutParams = LayoutParams(contentWidth, contentHeight)
        }
        region.keys.forEachIndexed { index, child ->
            val keyView = KeyView(context, theme, child, keyboard, keyboardView, keyboardActionListener).apply {
                id = child.index.takeIf { it >= 0 } ?: index
                layoutParams = LayoutParams(child.width, child.height)
                translationX = child.x.toFloat()
                translationY = child.y.toFloat()
                setPadding(
                    keyboard.horizontalGap / 2,
                    keyboard.verticalGap / 2,
                    keyboard.horizontalGap / 2,
                    keyboard.verticalGap / 2,
                )
            }
            content.addView(keyView)
        }
        val scroller: View = if (region.direction == TextKey.Scroll.HORIZONTAL) {
            HorizontalScrollView(context).apply {
                isFillViewport = true
                isHorizontalScrollBarEnabled = false
                overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
                isHorizontalFadingEdgeEnabled = true
                addView(content)
            }
        } else {
            ScrollView(context).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = false
                overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
                isVerticalFadingEdgeEnabled = true
                addView(content)
            }
        }
        addView(scroller, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
}
