/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.filter

import com.osfans.trime.core.CompositionProto
import com.osfans.trime.core.RimeApi
import com.osfans.trime.ime.core.TrimeInputMethodService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * After [RimeApi.selectCandidate], Rime often keeps the chosen text in the
 * composition (preedit) instead of emitting a commit, especially when raw
 * input remains. In 单字 mode the chosen character should go to the editor
 * immediately, and only the leftover input should stay in composition.
 */
object SegmentCommit {
    data class Split(val confirmed: String, val remaining: String)

    fun split(composition: CompositionProto): Split? {
        val preedit = composition.preedit ?: return null
        if (composition.length <= 0 || preedit.isEmpty()) return null
        val cpCount = preedit.codePointCount(0, preedit.length)
        val start = composition.selStart.coerceIn(0, cpCount)
        val startIdx = preedit.offsetByCodePoints(0, start)
        val confirmed = preedit.substring(0, startIdx).replace(CARET, "").trim()
        val remaining = preedit.substring(startIdx)
            .replace(CARET, "")
            .filter { it.code < 128 && it.isLetterOrDigit() }
        if (confirmed.isEmpty()) return null
        return Split(confirmed, remaining)
    }

    /**
     * Prefer the unconfirmed preedit suffix when it is a suffix of (or equal
     * to) the raw input, so a still-full raw string like 64426 with leftover
     * 6 does not get typed again.
     */
    fun resolveRemaining(rawInput: String, preeditRemaining: String): String {
        val rem = preeditRemaining
        return when {
            rem.isEmpty() -> ""
            rawInput == rem || rawInput.endsWith(rem) -> rem
            else -> rem
        }
    }

    suspend fun commitIfNeeded(
        api: RimeApi,
        service: TrimeInputMethodService,
        filterController: CandidateFilterController,
    ) {
        if (!filterController.current.singleChar) return
        val split = split(api.compositionCached) ?: return
        val remaining = resolveRemaining(api.getRawInput(), split.remaining)
        filterController.beginSegmentCommit()
        try {
            withContext(Dispatchers.Main.immediate) {
                service.commitText(split.confirmed)
            }
            api.clearComposition()
            if (remaining.isNotEmpty()) {
                remaining.forEach { ch ->
                    api.processKey(ch.code)
                }
            }
        } finally {
            filterController.endSegmentCommit(keepSingleChar = remaining.isNotEmpty())
        }
    }

    private const val CARET = "‸"
}
