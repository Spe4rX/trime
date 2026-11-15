/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.filter

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.daemon.RimeSession

/**
 * Walks Rime [RimeSession] candidate pages and emits only items that satisfy
 * [predicate], preserving each item's global index.
 */
class FilteringCandidatesPagingSource(
    private val rime: RimeSession,
    private val rimeTotal: Int,
    private val predicate: (CandidateProto, Int) -> Boolean,
    private val prefixInputs: List<String> = emptyList(),
    private val prefixLimit: Int = 24,
) : PagingSource<Int, IndexedCandidate>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, IndexedCandidate> {
        val startIndex = params.key ?: 0
        val want = params.loadSize.coerceAtLeast(1)
        val mergePrefixes = startIndex == 0 && prefixInputs.isNotEmpty()
        val mainWant = if (mergePrefixes) maxOf(want, 64) else want
        val acc = ArrayList<IndexedCandidate>(mainWant)
        val seen = HashSet<String>()
        var cursor = startIndex
        val chunkSize = 64
        val total = if (rimeTotal >= 0) rimeTotal else Int.MAX_VALUE
        var exhausted = cursor >= total
        while (acc.size < mainWant && !exhausted) {
            val limit = chunkSize
            val chunk = rime.runOnReady { getCandidates(cursor, limit) }
            if (chunk.isEmpty()) {
                exhausted = true
                break
            }
            chunk.forEachIndexed { i, candidate ->
                val index = cursor + i
                if (predicate(candidate, index) && seen.add(candidate.text)) {
                    acc += IndexedCandidate(index, candidate)
                }
            }
            cursor += chunk.size
            if (chunk.size < limit || cursor >= total) exhausted = true
        }
        if (mergePrefixes) {
            appendPrefixCandidates(acc, seen)
            // Keep shorter prefix results at the end; do not append more
            // full-length words after them on later pages.
            return LoadResult.Page(data = acc, prevKey = null, nextKey = null)
        }
        val nextKey = if (exhausted) null else cursor
        return LoadResult.Page(
            data = acc,
            prevKey = null,
            nextKey = nextKey,
        )
    }

    private suspend fun appendPrefixCandidates(
        acc: ArrayList<IndexedCandidate>,
        seen: MutableSet<String>,
    ) {
        // Longer prefixes first so 4-character words stay above 2-character
        // and single-character results, matching the stepwise sketches.
        val prefixes = prefixInputs.sortedByDescending { it.length }
        var synthetic = -1
        for (input in prefixes) {
            val chunk = rime.runOnReady { peekCandidates(input, prefixLimit) }
            for (candidate in chunk) {
                if (!predicate(candidate, synthetic)) continue
                if (!seen.add(candidate.text)) continue
                acc += IndexedCandidate(synthetic, candidate)
                synthetic--
            }
        }
    }

    override fun getRefreshKey(state: PagingState<Int, IndexedCandidate>) = null
}
