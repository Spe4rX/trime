/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.filter

/**
 * Matches a candidate's hanzi against locked syllables plus the remaining
 * raw-input pattern. Any reading sequence that is consistent is accepted.
 */
object CandidateMatcher {
    fun isHan(codePoint: Int): Boolean =
        codePoint == 0x3007 ||
            codePoint in 0x3400..0x9FFF ||
            codePoint in 0xF900..0xFAFF ||
            codePoint in 0x20000..0x2CEAF

    fun isSingleCharacter(text: String): Boolean =
        text.isNotEmpty() && Character.codePointCount(text, 0, text.length) == 1

    fun matches(
        text: String,
        table: HanziPinyinTable,
        locks: List<String>,
        remaining: String,
        mode: InputMode,
        singleChar: Boolean,
    ): Boolean {
        if (singleChar && !isSingleCharacter(text)) return false
        if (mode == InputMode.NONE) return !singleChar || isSingleCharacter(text)
        val codePoints = text.codePoints().toArray()
        val hanCount = codePoints.count { isHan(it) }
        if (hanCount == 0) {
            return locks.isEmpty()
        }
        return matchPath(codePoints, 0, 0, InputEncoding.skipDelimiters(remaining), table, locks, mode) != null
    }

    /**
     * Pinyin of [text] that is consistent with [locks] and [remaining],
     * syllables joined by spaces. Returns null when no sequence matches.
     */
    fun displayReading(
        text: String,
        table: HanziPinyinTable,
        locks: List<String>,
        remaining: String,
        mode: InputMode,
    ): String? {
        val codePoints = text.codePoints().toArray()
        if (codePoints.none { isHan(it) }) return null
        val path = if (mode == InputMode.NONE) {
            defaultReadings(codePoints, table)
        } else {
            matchPath(codePoints, 0, 0, InputEncoding.skipDelimiters(remaining), table, locks, mode)
        } ?: return null
        return clipPathToTyped(path, locks, remaining, mode)
            .joinToString(" ") { formatDisplay(it) }
            .ifEmpty { null }
    }

    /**
     * Show locked syllables as chosen, and only the already-typed prefix of
     * any later syllable. Do not auto-complete "h" to "hao", or show a
     * predictive extra syllable the user has not typed yet.
     */
    fun clipPathToTyped(
        path: List<String>,
        locks: List<String>,
        remaining: String,
        mode: InputMode,
    ): List<String> {
        if (mode == InputMode.NONE) return path
        val out = ArrayList<String>()
        var rem = InputEncoding.skipDelimiters(remaining)
        var lockIndex = 0
        for (reading in path) {
            if (lockIndex < locks.size) {
                val lock = InputEncoding.normalizeSyllable(locks[lockIndex])
                if (lock.isNotEmpty()) out += lock
                lockIndex++
                continue
            }
            if (rem.isEmpty()) break
            val encoded = InputEncoding.encode(reading, mode)
            if (encoded.isEmpty()) break
            when {
                rem.startsWith(encoded) -> {
                    out += reading
                    rem = InputEncoding.skipDelimiters(rem.substring(encoded.length))
                }
                encoded.startsWith(rem) -> {
                    val prefix = prefixOfReading(reading, rem, mode)
                    if (prefix.isNotEmpty()) out += prefix
                    rem = ""
                }
                else -> break
            }
        }
        return out
    }

    private fun prefixOfReading(reading: String, encodedPrefix: String, mode: InputMode): String {
        if (encodedPrefix.isEmpty()) return ""
        if (mode == InputMode.LETTER) {
            return if (reading.startsWith(encodedPrefix)) encodedPrefix else reading.take(encodedPrefix.length)
        }
        val buf = StringBuilder()
        for (ch in reading) {
            buf.append(ch)
            val enc = InputEncoding.encode(buf.toString(), mode)
            if (enc.length >= encodedPrefix.length) return buf.toString()
        }
        return buf.toString()
    }

    fun formatDisplay(syllable: String): String = syllable
        .replace("nve", "nüe")
        .replace("lve", "lüe")
        .replace("nv", "nü")
        .replace("lv", "lü")
        .replace('v', 'ü')

    private fun defaultReadings(codePoints: IntArray, table: HanziPinyinTable): List<String>? {
        val out = ArrayList<String>()
        for (cp in codePoints) {
            if (!isHan(cp)) continue
            val reading = table.readings(cp).firstOrNull() ?: return null
            out += reading
        }
        return out.takeIf { it.isNotEmpty() }
    }

    private fun matchPath(
        cps: IntArray,
        charIndex: Int,
        lockIndex: Int,
        remaining: String,
        table: HanziPinyinTable,
        locks: List<String>,
        mode: InputMode,
    ): List<String>? {
        var i = charIndex
        while (i < cps.size && !isHan(cps[i])) i++
        if (i >= cps.size) {
            return if (lockIndex >= locks.size) emptyList() else null
        }
        val readings = table.readings(cps[i])
        if (readings.isEmpty()) return null
        for (reading in readings) {
            val norm = InputEncoding.normalizeSyllable(reading)
            if (norm.isEmpty()) continue
            val consumed = consumeReading(norm, lockIndex, remaining, locks, mode) ?: continue
            val tail = matchPath(cps, i + 1, consumed.lockIndex, consumed.remaining, table, locks, mode)
            if (tail != null) return listOf(norm) + tail
        }
        return null
    }

    private data class Consume(
        val lockIndex: Int,
        val remaining: String,
    )

    private fun consumeReading(
        reading: String,
        lockIndex: Int,
        remaining: String,
        locks: List<String>,
        mode: InputMode,
    ): Consume? {
        val encoded = InputEncoding.encode(reading, mode)
        if (encoded.isEmpty()) return null
        if (lockIndex < locks.size) {
            val lock = InputEncoding.normalizeSyllable(locks[lockIndex])
            if (lock.isEmpty()) return null
            return if (lock == reading) {
                Consume(lockIndex + 1, remaining)
            } else if (PinyinSyllables.isAbbreviation(lock) && reading.startsWith(lock)) {
                val lockEnc = InputEncoding.encode(lock, mode)
                if (!encoded.startsWith(lockEnc)) return null
                val leftover = encoded.substring(lockEnc.length)
                val rem = applyEncoded(leftover, remaining) ?: return null
                Consume(lockIndex + 1, rem)
            } else {
                null
            }
        }
        val rem = applyEncoded(encoded, remaining) ?: return null
        return Consume(lockIndex, rem)
    }

    /**
     * Align [encoded] with [remaining]: the reading may be a prefix of the
     * leftover input, the leftover input may end mid-syllable, or there may
     * be no leftover input (predictive extra characters).
     */
    private fun applyEncoded(encoded: String, remaining: String): String? {
        if (encoded.isEmpty()) return remaining
        val rem = InputEncoding.skipDelimiters(remaining)
        return when {
            rem.isEmpty() -> rem
            rem.startsWith(encoded) -> rem.substring(encoded.length)
            encoded.startsWith(rem) -> ""
            else -> null
        }
    }
}
