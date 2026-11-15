/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.filter

/**
 * T9 letter-to-digit mapping and helpers for classifying Rime raw input.
 *
 * 2=abc, 3=def, 4=ghi, 5=jkl, 6=mno, 7=pqrs, 8=tuv, 9=wxyz.
 * ü is treated as v (digit 8). Apostrophes are syllable delimiters.
 */
enum class InputMode {
    T9,
    LETTER,
    NONE,
}

object InputEncoding {
    fun digitFor(ch: Char): Char? = when (ch.lowercaseChar()) {
        'a', 'b', 'c' -> '2'
        'd', 'e', 'f' -> '3'
        'g', 'h', 'i' -> '4'
        'j', 'k', 'l' -> '5'
        'm', 'n', 'o' -> '6'
        'p', 'q', 'r', 's' -> '7'
        't', 'u', 'v', 'ü' -> '8'
        'w', 'x', 'y', 'z' -> '9'
        else -> null
    }

    /** Lowercase, ü→v, drop anything that is not a letter. */
    fun normalizeSyllable(raw: String): String = buildString(raw.length) {
        for (ch in raw) {
            val c = ch.lowercaseChar()
            when {
                c == 'ü' -> append('v')
                c in 'a'..'z' -> append(c)
            }
        }
    }

    fun t9Encode(syllable: String): String = buildString(syllable.length) {
        for (ch in normalizeSyllable(syllable)) {
            append(digitFor(ch) ?: return "")
        }
    }

    fun encode(syllable: String, mode: InputMode): String = when (mode) {
        InputMode.T9 -> t9Encode(syllable)
        InputMode.LETTER -> normalizeSyllable(syllable)
        InputMode.NONE -> ""
    }

    fun modeOf(rawInput: String): InputMode {
        val chars = rawInput.filter { it != '\'' }
        if (chars.isEmpty()) return InputMode.NONE
        if (chars.all { it in '2'..'9' }) return InputMode.T9
        if (chars.all { it.isLetter() }) return InputMode.LETTER
        return InputMode.NONE
    }

    fun skipDelimiters(input: String): String {
        var i = 0
        while (i < input.length && input[i] == '\'') i++
        return if (i == 0) input else input.substring(i)
    }

    /**
     * If [token] is a prefix of [input] (after leading apostrophes), return the
     * remainder; otherwise null.
     */
    fun consume(input: String, token: String): String? {
        if (token.isEmpty()) return null
        val rest = skipDelimiters(input)
        return if (rest.startsWith(token)) rest.substring(token.length) else null
    }

    /**
     * Keep leading locks that still match the start of [rawInput].
     */
    fun compatibleLocks(rawInput: String, locks: List<String>, mode: InputMode): List<String> {
        if (mode == InputMode.NONE || locks.isEmpty()) return emptyList()
        var rest = rawInput
        val kept = ArrayList<String>(locks.size)
        for (lock in locks) {
            val token = encode(lock, mode)
            rest = consume(rest, token) ?: break
            kept += lock
        }
        return kept
    }

    fun remainingAfterLocks(rawInput: String, locks: List<String>, mode: InputMode): String {
        if (mode == InputMode.NONE) return ""
        var rest = rawInput
        for (lock in locks) {
            val token = encode(lock, mode)
            rest = consume(rest, token) ?: return skipDelimiters(rest)
        }
        return skipDelimiters(rest)
    }
}
