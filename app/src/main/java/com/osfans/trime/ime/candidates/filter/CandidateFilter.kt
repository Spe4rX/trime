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
    /**
     * Next-level syllables and prefixes only. Confirmed locks are shown in
     * the reading bar, not repeated in this list.
     */
    val filterItems: List<String> get() = nextSyllables

    val selectedIndex: Int get() = -1

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

    fun leftoverRawInput(
        text: String,
        table: HanziPinyinTable,
        snapshot: FilterSnapshot,
    ): String? = CandidateMatcher.leftoverRawInput(
        text = text,
        table = table,
        rawInput = snapshot.rawInput,
        locks = snapshot.locks,
        remaining = snapshot.remaining,
        mode = snapshot.mode,
    )

    /**
     * Confirmed (locked) syllables and the still-unconfirmed reading. The
     * unconfirmed part is joined with apostrophes, as in the stepwise bar.
     */
    data class BarParts(val confirmed: String?, val remaining: String?)

    fun barParts(snapshot: FilterSnapshot, displayReading: String?): BarParts {
        if (snapshot.mode == InputMode.NONE && snapshot.locks.isEmpty()) {
            return BarParts(null, null)
        }
        val remParts = remainingSyllables(snapshot, displayReading)
            .map { CandidateMatcher.formatDisplay(it) }
        val remaining = remParts
            .joinToString(if (snapshot.locks.isEmpty()) " " else "'")
            .ifEmpty { null }
        if (snapshot.locks.isEmpty()) return BarParts(null, remaining)
        val confirmed = snapshot.locks
            .joinToString(" ") { CandidateMatcher.formatDisplay(InputEncoding.normalizeSyllable(it)) }
            .ifEmpty { null }
        return BarParts(confirmed, remaining)
    }

    /**
     * Unconfirmed syllables that together cover every remaining typed key.
     * A highlighted candidate's reading is used only when it already
     * accounts for the whole remainder; otherwise the remainder is
     * segmented from the raw input so short words cannot drop letters.
     */
    fun remainingSyllables(snapshot: FilterSnapshot, displayReading: String?): List<String> {
        if (snapshot.mode == InputMode.NONE) return emptyList()
        val rem = InputEncoding.skipDelimiters(snapshot.remaining)
        if (rem.isEmpty()) return emptyList()
        val preferred = displayReading
            ?.split(' ')
            ?.filter { it.isNotEmpty() }
            ?.drop(snapshot.locks.size)
            ?.map { InputEncoding.normalizeSyllable(it) }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        return coverRemaining(rem, snapshot.mode, preferred)
    }

    private fun coverRemaining(
        remaining: String,
        mode: InputMode,
        preferred: List<String>,
    ): List<String> {
        if (preferred.isNotEmpty() && pathCoversRemaining(preferred, remaining, mode)) {
            return CandidateMatcher.clipPathToTyped(preferred, emptyList(), remaining, mode)
        }
        return SyllableEnumerator.segment(remaining, mode)
    }

    private fun pathCoversRemaining(
        path: List<String>,
        remaining: String,
        mode: InputMode,
    ): Boolean {
        var rem = InputEncoding.skipDelimiters(remaining)
        for (reading in path) {
            if (rem.isEmpty()) return true
            val encoded = InputEncoding.encode(reading, mode)
            if (encoded.isEmpty()) return false
            when {
                rem.startsWith(encoded) ->
                    rem = InputEncoding.skipDelimiters(rem.substring(encoded.length))
                encoded.startsWith(rem) -> return true
                else -> return false
            }
        }
        return rem.isEmpty()
    }

    fun prefixInputs(snapshot: FilterSnapshot): List<String> =
        SyllableEnumerator.rawPrefixes(snapshot.rawInput, snapshot.mode, snapshot.locks)

    fun popLastLock(locks: List<String>): List<String> =
        if (locks.isEmpty()) emptyList() else locks.subList(0, locks.lastIndex)

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
