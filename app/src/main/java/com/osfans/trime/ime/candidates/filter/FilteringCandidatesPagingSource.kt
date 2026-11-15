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
) : PagingSource<Int, IndexedCandidate>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, IndexedCandidate> {
        val startIndex = params.key ?: 0
        val want = params.loadSize.coerceAtLeast(1)
        val acc = ArrayList<IndexedCandidate>(want)
        var cursor = startIndex
        val chunkSize = 64
        val total = if (rimeTotal >= 0) rimeTotal else Int.MAX_VALUE
        var exhausted = cursor >= total
        while (acc.size < want && !exhausted) {
            val limit = chunkSize
            val chunk = rime.runOnReady { getCandidates(cursor, limit) }
            if (chunk.isEmpty()) {
                exhausted = true
                break
            }
            chunk.forEachIndexed { i, candidate ->
                val index = cursor + i
                if (predicate(candidate, index)) {
                    acc += IndexedCandidate(index, candidate)
                }
            }
            cursor += chunk.size
            if (chunk.size < limit || cursor >= total) exhausted = true
        }
        val nextKey = if (exhausted) null else cursor
        return LoadResult.Page(
            data = acc,
            prevKey = null,
            nextKey = nextKey,
        )
    }

    override fun getRefreshKey(state: PagingState<Int, IndexedCandidate>) = null
}
