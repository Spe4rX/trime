/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.view.children
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.ime.bar.ToolBarVisibility
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.view

class ButtonsBarUi(
    override val ctx: Context,
    private val scope: ThemeScope,
    private val onButtonClick: ((String) -> Unit)? = null,
) : Ui {
    private val theme: Theme get() = scope.theme

    override val root = view(::FlexboxLayout) {
        alignItems = AlignItems.CENTER
        justifyContent = JustifyContent.FLEX_START
        flexDirection = FlexDirection.ROW_REVERSE
    }

    val firstButton: ToolButton?

    private val defaultButtonSize = theme.style.run { candidateViewHeight + commentHeight }

    private fun toolButton(
        buttonConfig: ToolBar.Button?,
        @DrawableRes icon: Int = 0,
    ): ToolButton = if (buttonConfig != null) {
        ToolButton(ctx, buttonConfig, scope).apply {
            setOnClickListener { onButtonClick?.invoke(buttonConfig.action) }
            val longPressAction = buttonConfig.longPressAction
            if (longPressAction.isNotEmpty()) {
                setOnLongClickListener {
                    onButtonClick?.invoke(longPressAction)
                    true
                }
            }
        }
    } else {
        ToolButton(ctx, icon, scope).apply {
            setOnClickListener { onButtonClick?.invoke("") }
        }
    }

    init {
        val buttons = theme.toolBar.buttons
        firstButton = buttons.firstOrNull()?.let { toolButton(it) }

        buttons.drop(1).forEach { config ->
            val button = toolButton(config)
            // Restricted buttons start hidden until the first keyboard id arrives,
            // so they cannot flash on letter/number/symbol keyboards at startup.
            if (config.visibleOnKeyboards.isNotEmpty()) {
                button.visibility = View.GONE
            }
            val size = getButtonSize(config)
            val lParams = FlexboxLayout.LayoutParams(size.first, size.second).apply {
                marginEnd = ctx.dp(theme.toolBar.buttonSpacing)
            }
            root.addView(button, lParams)
        }
    }

    internal fun getButtonSize(config: ToolBar.Button?, customDefaultSize: Int = defaultButtonSize): Pair<Int, Int> {
        val sizeList = config?.size?.takeIf { it.size == 2 } ?: List(2) { customDefaultSize }
        val (width, height) = sizeList

        val finalWidth = if (width >= 0) ctx.dp(width) else width
        val finalHeight = if (height >= 0) ctx.dp(height) else height

        return finalWidth to finalHeight
    }

    /**
     * Show or hide flexbox buttons according to [keyboardId]. The rightmost
     * Hide button is [firstButton] and lives outside this Flexbox; it is not
     * filtered here and stays visible.
     */
    fun updateVisibility(keyboardId: String) {
        val configs = theme.toolBar.buttons.drop(1)
        root.children.forEachIndexed { index, view ->
            val config = configs.getOrNull(index) ?: return@forEachIndexed
            view.visibility = if (ToolBarVisibility.isVisible(config, keyboardId)) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    fun updateStyle(option: String, enabled: Boolean) {
        root.children.forEach { (it as ToolButton).updateStyle(option, enabled) }
    }

    /** Restyles the toolbar buttons after a scheme switch. */
    fun refreshColors() {
        firstButton?.refreshColors()
        root.children.forEach { (it as ToolButton).refreshColors() }
    }
}
