// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.unrolled

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.ColorUtils
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.candidates.filter.CombinationFilterView
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.recyclerview.recyclerView

@SuppressLint("ViewConstructor")
class UnrolledCandidateLayout(
    context: Context,
    private val scope: ThemeScope,
) : ConstraintLayout(context) {
    private val theme: Theme
        get() = scope.theme

    val filterColumn = CombinationFilterView(context).apply {
        id = View.generateViewId()
        visibility = View.GONE
    }

    val recyclerView =
        recyclerView {
            isVerticalScrollBarEnabled = false
        }

    val backspaceButton = TextView(context).apply {
        text = "×"
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        typeface = Typeface.DEFAULT_BOLD
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Backspace"
        val fill = runCatching { ColorManager.getColor("off_key_back_color") }.getOrNull()
        val ink = runCatching { ColorManager.getColor("off_key_text_color") }.getOrNull()
            ?: runCatching { ColorManager.getColor("key_text_color") }.getOrNull()
            ?: 0xFFFFFFFF.toInt()
        setTextColor(ink)
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(fill ?: ColorUtils.setAlphaComponent(ink, 0x4D))
        }
    }

    init {
        id = R.id.unrolled_candidate_view
        background =
            scope.decorDrawable(
                "candidate_background",
                "candidate_border_color",
                dp(theme.style.candidateBorder),
                dp(theme.style.candidateBorderRound),
            )

        add(
            filterColumn,
            lParams(dp(56), matchParent) {
                startOfParent()
                topOfParent()
                bottomOfParent()
            },
        )
        add(
            recyclerView,
            lParams(0, matchParent) {
                startToEndOf(filterColumn)
                endOfParent()
                topOfParent()
                bottomOfParent()
            },
        )
        add(
            backspaceButton,
            lParams(dp(40), dp(40)) {
                endOfParent(dp(8))
                bottomOfParent(dp(8))
            },
        )
    }

    fun resetPosition() {
        recyclerView.scrollToPosition(0)
    }

    /** Restyles the container background after a scheme switch. */
    fun refreshColors() {
        background =
            scope.decorDrawable(
                "candidate_background",
                "candidate_border_color",
                dp(theme.style.candidateBorder),
                dp(theme.style.candidateBorderRound),
            )
        filterColumn.applyCapsuleColors()
        val fill = runCatching { ColorManager.getColor("off_key_back_color") }.getOrNull()
        val ink = runCatching { ColorManager.getColor("off_key_text_color") }.getOrNull()
            ?: runCatching { ColorManager.getColor("key_text_color") }.getOrNull()
            ?: 0xFFFFFFFF.toInt()
        backspaceButton.setTextColor(ink)
        (backspaceButton.background as? android.graphics.drawable.GradientDrawable)
            ?.setColor(fill ?: ColorUtils.setAlphaComponent(ink, 0x4D))
    }
}
