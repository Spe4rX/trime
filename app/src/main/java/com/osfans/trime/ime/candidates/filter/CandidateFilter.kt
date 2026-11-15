/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.filter

import com.osfans.trime.core.CandidateProto

/**
 * Client-side candidate filter: possible input combinations from the raw
 * T9/letter input, and matching of Rime candidates against locked syllables.
 */
data class FilterSnapshot(
    val mode: InputMode,
    val rawInput: String,
    val locks: List<String>,
    val remaining: String,
    val nextSyllables: List<String>,
) {
    /** Locks (for stepping back) followed by the next possible syllables. */
    val filterItems: List<String> get() = locks + nextSyllables

    val selectedIndex: Int get() = if (locks.isEmpty()) -1 else locks.lastIndex

    val hasCombinations: Boolean get() = filterItems.isNotEmpty()
}

object CandidateFilter {
    fun compute(rawInput: String, requestedLocks: List<String>): FilterSnapshot {
        val mode = InputEncoding.modeOf(rawInput)
        if (mode == InputMode.NONE || rawInput.isEmpty()) {
            return FilterSnapshot(InputMode.NONE, rawInput, emptyList(), "", emptyList())
        }
        val locks = InputEncoding.compatibleLocks(rawInput, requestedLocks, mode)
        val remaining = InputEncoding.remainingAfterLocks(rawInput, locks, mode)
        val next = SyllableEnumerator.enumerate(remaining, mode)
        return FilterSnapshot(mode, rawInput, locks, remaining, next)
    }

    fun toggleLock(locks: List<String>, itemIndex: Int, syllable: String): List<String> {
        if (itemIndex in locks.indices) {
            return locks.subList(0, itemIndex)
        }
        return locks + syllable
    }

    fun matches(
        text: String,
        table: HanziPinyinTable,
        snapshot: FilterSnapshot,
        singleChar: Boolean,
    ): Boolean = CandidateMatcher.matches(
        text = text,
        table = table,
        locks = snapshot.locks,
        remaining = snapshot.remaining,
        mode = snapshot.mode,
        singleChar = singleChar,
    )

    fun displayReading(
        text: String,
        table: HanziPinyinTable,
        snapshot: FilterSnapshot,
    ): String? = CandidateMatcher.displayReading(
        text = text,
        table = table,
        locks = snapshot.locks,
        remaining = snapshot.remaining,
        mode = snapshot.mode,
    )

    fun filter(
        candidates: List<IndexedCandidate>,
        table: HanziPinyinTable,
        snapshot: FilterSnapshot,
        singleChar: Boolean,
    ): List<IndexedCandidate> {
        if (snapshot.mode == InputMode.NONE && !singleChar) return candidates
        return candidates.filter { matches(it.candidate.text, table, snapshot, singleChar) }
    }

    fun filterProtos(
        candidates: Array<CandidateProto>,
        startIndex: Int,
        table: HanziPinyinTable,
        snapshot: FilterSnapshot,
        singleChar: Boolean,
    ): List<IndexedCandidate> {
        val indexed = candidates.mapIndexed { i, c -> IndexedCandidate(startIndex + i, c) }
        return filter(indexed, table, snapshot, singleChar)
    }
}
