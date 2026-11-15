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
