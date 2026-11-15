/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.filter

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan

/**
 * Colors for the stepwise reading bar: confirmed pinyin should look like an
 * accent (blue in the grok theme), and the unconfirmed remainder like body
 * candidate text.
 *
 * Theme keys are not consistent: some schemes put the accent on
 * [textColor], others on [hilitedTextColor]. Pick the most chromatic of
 * those plus [accentHint] (usually hilited candidate background).
 */
data class BarColors(val confirmed: Int, val remaining: Int)

data class ReadingRun(val text: String, val confirmed: Boolean)

/**
 * Split a reading into confirmed (locked) and remaining runs. Unlocked
 * input is a single remaining run so it is not painted as an accent.
 */
fun readingRuns(
    parts: CandidateFilter.BarParts,
    fallback: String?,
): List<ReadingRun> {
    if (parts.confirmed == null && parts.remaining == null) {
        val text = fallback?.takeIf { it.isNotEmpty() } ?: return emptyList()
        return listOf(ReadingRun(text, confirmed = false))
    }
    return buildList {
        parts.confirmed?.let { add(ReadingRun(it, confirmed = true)) }
        parts.remaining?.let { add(ReadingRun(it, confirmed = false)) }
    }
}

fun coloredReading(
    parts: CandidateFilter.BarParts,
    fallback: String?,
    confirmedColor: Int,
    normalColor: Int,
): CharSequence {
    val sb = SpannableStringBuilder()
    for (run in readingRuns(parts, fallback)) {
        if (sb.isNotEmpty()) sb.append(' ')
        val start = sb.length
        sb.append(run.text)
        val color = if (run.confirmed) confirmedColor else normalColor
        sb.setSpan(ForegroundColorSpan(color), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return sb
}

fun readingColors(
    textColor: Int,
    hilitedTextColor: Int,
    candidateTextColor: Int,
    accentHint: Int,
): BarColors {
    val confirmed = listOf(hilitedTextColor, textColor, accentHint).maxBy { chroma(it) }
    return BarColors(confirmed = confirmed, remaining = candidateTextColor)
}

internal fun chroma(color: Int): Int {
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF
    return maxOf(r, g, b) - minOf(r, g, b)
}
