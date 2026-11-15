/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.filter

/**
 * Enumerates valid pinyin syllables that are a prefix of the remaining raw
 * input. Results are sorted longer-first, then alphabetically.
 */
object SyllableEnumerator {
    private val byT9: Map<String, List<String>> by lazy {
        PinyinSyllables.ALL.groupBy { InputEncoding.t9Encode(it) }
            .mapValues { (_, list) -> list.sorted() }
    }

    fun enumerate(remaining: String, mode: InputMode): List<String> {
        val rest = InputEncoding.skipDelimiters(remaining)
        if (rest.isEmpty() || mode == InputMode.NONE) return emptyList()
        val found = when (mode) {
            InputMode.T9 -> prefixesT9(rest)
            InputMode.LETTER -> prefixesLetter(rest)
            InputMode.NONE -> emptyList()
        }
        return found.sortedWith(compareByDescending<String> { it.length }.thenBy { it })
    }

    /**
     * Raw-input prefixes that end on a syllable boundary, not including the
     * full input. Used to query shorter words and single characters for
     * stepwise filtering. [locks] are consumed first and the locked prefix
     * itself is included so the confirmed syllable still yields 单字 / 短词.
     */
    fun rawPrefixes(rawInput: String, mode: InputMode, locks: List<String> = emptyList()): List<String> {
        if (mode == InputMode.NONE || rawInput.isEmpty()) return emptyList()
        val kept = InputEncoding.compatibleLocks(rawInput, locks, mode)
        val remaining = InputEncoding.remainingAfterLocks(rawInput, kept, mode)
        val head = prefixBeforeRemaining(rawInput, remaining)
        val out = ArrayList<String>()
        if (remaining.isEmpty()) {
            // Locks already consumed the input: still offer shorter confirmed
            // prefixes so 单字 / 短词 remain available.
            val source = head.ifEmpty { rawInput }
            for (cut in boundaryCuts(source, mode)) {
                if (cut.isNotEmpty() && cut != rawInput) out += cut
            }
            return out.distinct()
        }
        if (head.isNotEmpty()) out += head
        for (cut in boundaryCuts(remaining, mode)) {
            val combined = head + cut
            if (combined.isNotEmpty() && combined != rawInput) out += combined
        }
        return out.distinct()
    }

    private fun prefixBeforeRemaining(rawInput: String, remaining: String): String {
        if (remaining.isEmpty()) return rawInput
        val idx = rawInput.lastIndexOf(remaining)
        if (idx >= 0 && InputEncoding.skipDelimiters(rawInput.substring(idx)) == remaining) {
            return rawInput.substring(0, idx)
        }
        return if (rawInput.endsWith(remaining)) rawInput.dropLast(remaining.length) else ""
    }

    private fun boundaryCuts(remaining: String, mode: InputMode): List<String> {
        val rest = InputEncoding.skipDelimiters(remaining)
        if (rest.isEmpty()) return emptyList()
        val reachable = BooleanArray(rest.length + 1)
        reachable[0] = true
        for (i in 0 until rest.length) {
            if (!reachable[i]) continue
            val max = minOf(PinyinSyllables.MAX_LENGTH, rest.length - i)
            for (len in 1..max) {
                val key = rest.substring(i, i + len)
                if (isSyllableToken(key, mode)) reachable[i + len] = true
            }
        }
        val out = ArrayList<String>()
        for (i in 1..rest.length) {
            if (reachable[i]) out += rest.substring(0, i)
        }
        return out
    }

    private fun isSyllableToken(token: String, mode: InputMode): Boolean = when (mode) {
        InputMode.T9 -> token in byT9
        InputMode.LETTER -> token in PinyinSyllables.ALL
        InputMode.NONE -> false
    }

    private fun prefixesT9(digits: String): List<String> {
        val out = ArrayList<String>()
        val max = minOf(PinyinSyllables.MAX_LENGTH, digits.length)
        for (len in 1..max) {
            val key = digits.substring(0, len)
            val syllables = byT9[key]
            if (syllables != null) out += syllables
        }
        return out
    }

    private fun prefixesLetter(letters: String): List<String> {
        val rest = InputEncoding.normalizeSyllable(letters)
        if (rest.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        val max = minOf(PinyinSyllables.MAX_LENGTH, rest.length)
        for (len in 1..max) {
            val key = rest.substring(0, len)
            if (key in PinyinSyllables.ALL) out += key
        }
        return out
    }
}
