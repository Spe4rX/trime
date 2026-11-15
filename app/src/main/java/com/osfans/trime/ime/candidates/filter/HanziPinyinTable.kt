/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.filter

import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.util.zip.GZIPInputStream

/**
 * Compact hanzi → toneless pinyin lookup. Polyphones are preserved in
 * file order (typically most common first).
 */
class HanziPinyinTable(
    private val readings: Map<Int, List<String>>,
) {
    fun readings(codePoint: Int): List<String> = readings[codePoint].orEmpty()

    fun readingsForChar(ch: Char): List<String> = readings(ch.code)

    companion object {
        fun fromMap(readings: Map<Int, List<String>>): HanziPinyinTable = HanziPinyinTable(readings)

        fun load(reader: Reader): HanziPinyinTable {
            val map = HashMap<Int, List<String>>()
            reader.buffered().useLines { lines ->
                lines.forEach { line ->
                    if (line.isEmpty() || line.startsWith('#')) return@forEach
                    val tab = line.indexOf('\t')
                    if (tab <= 0) return@forEach
                    val ch = line.codePointAt(0)
                    val readings = line.substring(tab + 1)
                        .split(',')
                        .map { InputEncoding.normalizeSyllable(it) }
                        .filter { it.isNotEmpty() }
                    if (readings.isNotEmpty()) {
                        map[ch] = readings
                    }
                }
            }
            return HanziPinyinTable(map)
        }

        fun loadGzip(stream: InputStream): HanziPinyinTable =
            load(InputStreamReader(GZIPInputStream(stream), Charsets.UTF_8))
    }
}
