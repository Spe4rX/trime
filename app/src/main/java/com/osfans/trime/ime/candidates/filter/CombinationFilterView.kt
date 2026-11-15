/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.filter

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.osfans.trime.data.theme.ColorManager
import splitties.dimensions.dp

/**
 * Capsule-styled vertical list used as the combination filter column in
 * the expanded candidate view. Matches the keyboard [com.osfans.trime.ime.keyboard.ScrollRegionView]
 * chrome: rounded bar, edge-to-edge hairlines on the content strip.
 */
@SuppressLint("ViewConstructor")
class CombinationFilterView(context: Context) : FrameLayout(context) {

    var onItemClick: ((index: Int, syllable: String) -> Unit)? = null

    private val insetH = dp(3)
    private val insetV = dp(3)
    private val cornerRadius = dp(8).toFloat()
    private val itemHeight = dp(36)
    private val clipRect = RectF()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(1).toFloat().coerceAtLeast(1f)
    }
    private var selectedTextColor = 0xFFFFFFFF.toInt()
    private var dimTextColor = 0x80FFFFFF.toInt()

    private val content = ContentStrip(context).apply {
        orientation = LinearLayout.VERTICAL
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private val scroller = ScrollView(context).apply {
        isFillViewport = false
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    init {
        clipToPadding = true
        clipChildren = false
        setWillNotDraw(false)
        applyCapsuleColors()
        addView(
            scroller,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                leftMargin = insetH
                topMargin = insetV
                rightMargin = insetH
                bottomMargin = insetV
            },
        )
    }

    fun applyCapsuleColors() {
        val fill = runCatching { ColorManager.getColor("off_key_back_color") }.getOrNull()
            ?: runCatching { ColorManager.getColor("key_back_color") }.getOrNull()
        val ink = runCatching { ColorManager.getColor("off_key_text_color") }.getOrNull()
            ?: runCatching { ColorManager.getColor("key_text_color") }.getOrNull()
            ?: 0xFFFFFFFF.toInt()
        val bar = fill ?: ColorUtils.setAlphaComponent(ink, 0x4D)
        trackPaint.color = bar
        val hairline = if (ColorUtils.calculateLuminance(bar) < 0.6) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        separatorPaint.color = ColorUtils.setAlphaComponent(hairline, 0x4D)
        selectedTextColor = ink
        dimTextColor = ColorUtils.setAlphaComponent(ink, 0x99)
        for (i in 0 until content.childCount) {
            (content.getChildAt(i) as? TextView)?.let { label ->
                label.setTextColor(if (label.isSelected) selectedTextColor else dimTextColor)
            }
        }
        invalidate()
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

    fun setItems(items: List<String>, selectedIndex: Int) {
        content.removeAllViews()
        items.forEachIndexed { index, syllable ->
            val selected = index == selectedIndex
            val label = TextView(context).apply {
                text = syllable
                gravity = Gravity.CENTER
                setTextColor(if (selected) selectedTextColor else dimTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                includeFontPadding = false
                isClickable = true
                isFocusable = true
                isSelected = selected
                setOnClickListener { onItemClick?.invoke(index, syllable) }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    itemHeight,
                )
            }
            content.addView(label)
        }
        visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    private inner class ContentStrip(context: Context) : LinearLayout(context) {
        init {
            setWillNotDraw(false)
        }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            val last = childCount - 1
            if (last <= 0) return
            for (i in 0 until last) {
                val child = getChildAt(i)
                val next = getChildAt(i + 1)
                val y = (child.bottom + next.top) / 2f
                canvas.drawLine(0f, y, width.toFloat(), y, separatorPaint)
            }
        }
    }

}
