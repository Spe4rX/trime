// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.osfans.trime.R
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams as viewLParams
import splitties.views.dsl.core.wrapContent
import splitties.views.horizontalPadding

class CandidateUi(
    override val ctx: Context,
    private val scope: ThemeScope,
    private val compatView: View,
) : Ui {
    private val theme: Theme get() = scope.theme

    val unrollButton =
        ToolButton(ctx, R.drawable.ic_baseline_expand_more_24, scope).apply {
            visibility = View.INVISIBLE
        }

    val readingLabel = TextView(ctx).apply {
        id = View.generateViewId()
        visibility = View.GONE
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        setTextColor(scope.colors.textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = theme.fonts.text
        horizontalPadding = ctx.dp(8)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    val allButton = modeButton("全部")
    val singleButton = modeButton("单字")

    var onModeClick: ((singleChar: Boolean) -> Unit)? = null

    private val modeBar = LinearLayout(ctx).apply {
        id = View.generateViewId()
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        visibility = View.GONE
        addView(allButton, viewLParams(wrapContent, wrapContent))
        addView(singleButton, viewLParams(wrapContent, wrapContent).apply {
            leftMargin = ctx.dp(16)
        })
    }

    private fun modeButton(label: String) = TextView(ctx).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setPadding(ctx.dp(12), ctx.dp(4), ctx.dp(12), ctx.dp(4))
        isClickable = true
        isFocusable = true
    }

    override val root =
        ctx.constraintLayout {
            add(
                unrollButton,
                lParams(dp(40)) {
                    centerVertically()
                    endOfParent()
                },
            )
            add(
                readingLabel,
                lParams(wrapContent, wrapContent) {
                    centerVertically()
                    startOfParent()
                    endToStartOf(modeBar)
                    horizontalBias = 0f
                },
            )
            add(
                modeBar,
                lParams(wrapContent, wrapContent) {
                    centerInParent()
                },
            )
            add(
                compatView,
                lParams {
                    centerVertically()
                    startOfParent(dp(theme.style.candidatePadding / 2))
                    before(unrollButton)
                },
            )
        }

    init {
        allButton.setOnClickListener { onModeClick?.invoke(false) }
        singleButton.setOnClickListener { onModeClick?.invoke(true) }
        setSingleChar(false)
    }

    fun setExpanded(expanded: Boolean) {
        compatView.visibility = if (expanded) View.GONE else View.VISIBLE
        readingLabel.visibility = if (expanded) View.VISIBLE else View.GONE
        modeBar.visibility = if (expanded) View.VISIBLE else View.GONE
    }

    fun setReading(text: String?) {
        readingLabel.text = text.orEmpty()
    }

    fun setSingleChar(enabled: Boolean) {
        styleModeButton(allButton, selected = !enabled)
        styleModeButton(singleButton, selected = enabled)
    }

    private fun styleModeButton(button: TextView, selected: Boolean) {
        val padH = ctx.dp(12)
        val padV = ctx.dp(4)
        if (selected) {
            button.setTextColor(scope.colors.hilitedCandidateTextColor)
            button.typeface = Typeface.DEFAULT_BOLD
            button.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = button.dp(999).toFloat()
                setColor(scope.colors.hilitedCandidateBackColor)
            }
        } else {
            button.setTextColor(ColorUtils.setAlphaComponent(scope.colors.candidateTextColor, 0x88))
            button.typeface = Typeface.DEFAULT
            button.background = null
        }
        // setBackground can reset padding
        button.setPadding(padH, padV, padH, padV)
    }

    /** Restyles the candidate bar after a scheme switch. */
    fun refreshColors() {
        unrollButton.refreshColors()
        readingLabel.setTextColor(scope.colors.textColor)
        setSingleChar(singleButton.typeface == Typeface.DEFAULT_BOLD)
    }
}
