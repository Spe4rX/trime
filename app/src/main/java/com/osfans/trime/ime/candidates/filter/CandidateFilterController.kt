/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.filter

import android.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import com.osfans.trime.core.Candidates
import com.osfans.trime.core.CompositionProto
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import com.osfans.trime.R
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Session-scoped filter state: locked syllables, 单字 mode, and the current
 * combination list derived from Rime raw input.
 */
class CandidateFilterController(override val di: DI) :
    DIAware,
    InputBroadcastReceiver {
    private val context: ContextThemeWrapper by instance()
    private val rime: RimeSession by instance()
    data class State(
        val snapshot: FilterSnapshot = FilterSnapshot(InputMode.NONE, "", emptyList(), "", emptyList()),
        val singleChar: Boolean = false,
        val composing: Boolean = false,
        val hasCandidates: Boolean = false,
        val displayReading: String? = null,
        val confirmedPrefix: String? = null,
    ) {
        val showCompactFilter: Boolean
            get() = composing && hasCandidates && snapshot.hasCombinations

        val showExpandedFilter: Boolean
            get() = composing && snapshot.hasCombinations

        val barParts: CandidateFilter.BarParts
            get() = CandidateFilter.barParts(snapshot, displayReading)

        /**
         * Confirmed pinyin (locks) plus the unconfirmed reading. Hanzi already
         * chosen in the composition still wins when present, so the bar stays
         * in sync with a mid-commit prefix like 你.
         */
        val barLabel: String?
            get() {
                confirmedPrefix?.takeIf { it.isNotEmpty() }?.let { return it }
                val parts = barParts
                return when {
                    parts.confirmed != null && parts.remaining != null ->
                        "${parts.confirmed} ${parts.remaining}"
                    parts.confirmed != null -> parts.confirmed
                    parts.remaining != null -> parts.remaining
                    else -> displayReading
                }
            }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val current: State get() = _state.value

    val table: HanziPinyinTable by lazy { loadTable() }

    private var requestedLocks: List<String> = emptyList()
    private var singleChar: Boolean = false
    private var composing: Boolean = false
    private var hasCandidates: Boolean = false
    private var highlightedText: String? = null
    private var confirmedPrefix: String? = null
    private var persistSingleChar = false
    private var refreshSeq = 0

    private fun loadTable(): HanziPinyinTable {
        val stream: InputStream = context.resources.openRawResource(R.raw.hanzi_pinyin)
        return stream.use { HanziPinyinTable.load(InputStreamReader(it, Charsets.UTF_8)) }
    }

    override fun onStartInput(info: android.view.inputmethod.EditorInfo) {
        reset()
    }

    override fun onCompositionUpdate(data: CompositionProto) {
        val nowComposing = data.length > 0
        if (!nowComposing) {
            reset()
            return
        }
        composing = true
        confirmedPrefix = SegmentCommit.split(data)?.confirmed
        refreshFromRime()
    }

    override fun onCandidateListUpdate(data: Candidates.Bulk) {
        hasCandidates = data.candidates.isNotEmpty() || data.total > 0
        highlightedText = data.candidates.getOrNull(data.highlighted)?.text
            ?: data.candidates.firstOrNull()?.text
        if (!hasCandidates && !composing) {
            reset()
            return
        }
        refreshFromRime()
    }

    fun onFilterClick(itemIndex: Int, syllable: String) {
        requestedLocks = current.snapshot.locks + syllable
        refreshFromRime()
    }

    fun popLastLock() {
        val locks = current.snapshot.locks
        if (locks.isEmpty()) return
        requestedLocks = CandidateFilter.popLastLock(locks)
        refreshFromRime()
    }

    /**
     * Consume a backspace to drop the last lock. Returns true when a lock
     * was removed so the raw input should be left unchanged.
     */
    fun handleBackspace(): Boolean {
        if (current.snapshot.locks.isEmpty()) return false
        popLastLock()
        return true
    }

    fun setSingleChar(enabled: Boolean) {
        if (singleChar == enabled) return
        singleChar = enabled
        publish(current.snapshot)
    }

    fun toggleSingleChar() {
        setSingleChar(!singleChar)
    }

    /**
     * Keep 单字 mode across the brief empty composition between committing a
     * character and re-feeding the leftover input.
     */
    fun beginSegmentCommit() {
        persistSingleChar = true
    }

    fun endSegmentCommit(keepSingleChar: Boolean) {
        persistSingleChar = false
        if (!keepSingleChar && singleChar) {
            singleChar = false
            publish(current.snapshot, current.displayReading)
        }
    }

    /**
     * Prefer the first filtered (or still-highlighted) candidate for the
     * friendlier preedit label.
     */
    fun setDisplayCandidate(text: String?) {
        val snapshot = current.snapshot
        val reading = text?.let { CandidateFilter.displayReading(it, table, snapshot) }
        if (reading == current.displayReading) return
        publish(snapshot, reading)
    }

    fun matches(text: String): Boolean =
        CandidateFilter.matches(text, table, current.snapshot, singleChar)

    fun filter(candidates: List<IndexedCandidate>): List<IndexedCandidate> =
        CandidateFilter.filter(candidates, table, current.snapshot, singleChar)

    private fun refreshFromRime() {
        val seq = ++refreshSeq
        rime.launchOnReady { api ->
            val raw = api.getRawInput()
            if (seq != refreshSeq) return@launchOnReady
            ContextCompat.getMainExecutor(context).execute {
                if (seq != refreshSeq) return@execute
                applyRawInput(raw)
            }
        }
    }

    private fun applyRawInput(raw: String) {
        if (raw.isEmpty()) {
            reset()
            return
        }
        val snapshot = CandidateFilter.compute(raw, requestedLocks)
        requestedLocks = snapshot.locks
        val reading = highlightedText
            ?.takeIf { CandidateFilter.matches(it, table, snapshot, singleChar) }
            ?.let { CandidateFilter.displayReading(it, table, snapshot) }
        publish(snapshot, reading)
    }

    private fun publish(snapshot: FilterSnapshot, reading: String? = current.displayReading) {
        _state.value = State(
            snapshot = snapshot,
            singleChar = singleChar,
            composing = composing,
            hasCandidates = hasCandidates,
            displayReading = reading,
            confirmedPrefix = confirmedPrefix,
        )
    }

    private fun reset() {
        requestedLocks = emptyList()
        if (!persistSingleChar) singleChar = false
        composing = false
        hasCandidates = false
        highlightedText = null
        confirmedPrefix = null
        _state.value = State(singleChar = singleChar)
    }
}
