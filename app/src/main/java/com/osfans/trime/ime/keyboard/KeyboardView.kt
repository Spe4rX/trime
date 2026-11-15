/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.children
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.broadcast.EnterKeyDisplayDelegate
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.popup.PopupDelegate

// TODO: move layout calculation responsibilities from Keyboard to KeyboardView using ConstraintLayout
@SuppressLint("ViewConstructor")
class KeyboardView(
    context: Context,
    private val theme: Theme,
    private val keyboard: Keyboard,
    val popup: PopupDelegate,
    val service: TrimeInputMethodService,
    private val keyboardActionListener: KeyboardActionListener,
    private val enterKeyDisplay: EnterKeyDisplayDelegate,
) : FrameLayout(context) {

    private val keys get() = keyboard.keys

    internal val labelEnter: String
        get() = enterKeyDisplay.keyLabel
    internal val keyTextSize = theme.style.keyTextSize
    internal val keyLongTextSize = theme.style.keyLongTextSize.takeIf { it > 0 } ?: keyTextSize
    internal val symbolTextSize = theme.style.symbolTextSize.takeIf { it > 0 } ?: keyTextSize
    internal val popupOnKeyPress by AppPrefs.defaultInstance().keyboard.popupOnKeyPress
    internal val hookShiftArrow: Boolean by AppPrefs.defaultInstance().keyboard.hookShiftArrow
    internal val hideKeySymbol: Boolean by AppPrefs.defaultInstance().keyboard.hideKeySymbol
    internal val hideKeyHint: Boolean by AppPrefs.defaultInstance().keyboard.hideKeyHint

    init {
        setWillNotDraw(false)
        buildKeyViews()
    }

    private fun buildKeyViews() {
        removeAllViews()

        keys.forEachIndexed { index, key ->
            addView(createSlotView(index, key))
        }
    }

    private fun createSlotView(index: Int, key: Key): View {
        val totalWidth = key.width + key.extraWidthLeft + key.extraWidthRight
        val params = LayoutParams(totalWidth, key.height)
        val x = (key.x - key.extraWidthLeft).toFloat()
        val y = key.y.toFloat()
        return if (key.scrollRegion != null) {
            ScrollRegionView(context, theme, key, keyboard, this, keyboardActionListener).apply {
                id = index
                layoutParams = params
                translationX = x
                translationY = y
            }
        } else {
            createKeyView(index, key, params, x, y)
        }
    }

    private fun createKeyView(
        index: Int,
        key: Key,
        params: LayoutParams,
        x: Float,
        y: Float,
    ): KeyView = KeyView(context, theme, key, keyboard, this, keyboardActionListener).apply {
        id = index
        layoutParams = params
        translationX = x
        translationY = y
        setPadding(
            keyboard.horizontalGap / 2 + key.extraWidthLeft,
            keyboard.verticalGap / 2,
            keyboard.horizontalGap / 2 + key.extraWidthRight,
            keyboard.verticalGap / 2,
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fullWidth = keyboard.minWidth + paddingLeft + paddingRight
        val fullHeight = keyboard.height + paddingTop + paddingBottom

        val measuredWidth = minOf(
            MeasureSpec.getSize(widthMeasureSpec),
            fullWidth,
        )

        measureChildren(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, fullHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }

    fun invalidateAllKeys() {
        invalidateKeyViews(this)
    }

    fun invalidateKeyByIndex(index: Int) {
        findKeyViewByIndex(this, index)?.invalidate()
    }

    private fun invalidateKeyViews(view: View) {
        if (view is KeyView) {
            view.invalidate()
        }
        if (view is ViewGroup) {
            view.children.forEach { invalidateKeyViews(it) }
        }
    }

    private fun findKeyViewByIndex(view: View, index: Int): KeyView? {
        if (view is KeyView && view.id == index) return view
        if (view is ViewGroup) {
            view.children.forEach { child ->
                findKeyViewByIndex(child, index)?.let { return it }
            }
        }
        return null
    }

    fun firstVerticalScrollRegion(): ScrollRegionView? {
        children.filterIsInstance<ScrollRegionView>().forEach { child ->
            if (child.isVertical) return child
        }
        return null
    }

    fun applyCombinationFilters(
        items: List<String>,
        selectedIndex: Int,
        onItemClick: (index: Int, syllable: String) -> Unit,
    ) {
        val slot = firstVerticalScrollRegion() ?: return
        slot.setCombinationFilters(items, selectedIndex, onItemClick)
    }

    fun restoreCombinationFilters() {
        firstVerticalScrollRegion()?.restoreOriginalKeys()
    }

    val isCapsOn: Boolean
        get() = keyboard.mShiftKey?.isOn == true

    fun onDetach() {
        popup.dismissAll()
    }
}
