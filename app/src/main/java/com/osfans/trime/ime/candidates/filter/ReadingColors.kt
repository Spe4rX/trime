/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.filter

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
