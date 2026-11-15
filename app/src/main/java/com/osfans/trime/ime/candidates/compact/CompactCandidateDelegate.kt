/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.compact

import android.content.res.Configuration
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.ContextThemeWrapper
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayoutManager
import androidx.core.content.ContextCompat
import com.osfans.trime.R
import com.osfans.trime.core.Candidates
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.bar.InputBarDelegate
import com.osfans.trime.ime.bar.UnrollButtonStateMachine
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.ime.candidates.filter.CandidateFilter
import com.osfans.trime.ime.candidates.filter.CandidateFilterController
import com.osfans.trime.ime.candidates.filter.SegmentCommit
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.candidates.filter.IndexedCandidate
import com.osfans.trime.ime.candidates.filter.InputMode
import com.osfans.trime.ime.candidates.unrolled.decoration.FlexboxVerticalDecoration
import com.osfans.trime.ime.core.InputView
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import splitties.dimensions.dp
import splitties.views.dsl.recyclerview.recyclerView
import kotlin.math.max

class CompactCandidateDelegate(override val di: DI) :
    DIAware,
    InputBroadcastReceiver {
    private val context: ContextThemeWrapper by instance()
    private val rime: RimeSession by instance()
    private val scope: ThemeScope by instance()
    private val inputView: InputView by instance()
    private val bar: InputBarDelegate by instance()
    private val filterController: CandidateFilterController by instance()
    private val service: TrimeInputMethodService by instance()

    private val theme: Theme
        get() = scope.theme

    private val fillStyle by AppPrefs.defaultInstance().keyboard.horizontalCandidateMode

    private var lastBulk: Candidates.Bulk? = null
    private var publishSeq = 0
    private var collectJob: Job? = null
    private var lastFilterKey: Pair<Any, Boolean>? = null

    private val maxSpanCountPref by lazy {
        AppPrefs.defaultInstance().keyboard.run {
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                maxSpanCount
            } else {
                maxSpanCountLandscape
            }
        }
    }

    private var layoutMinWidth = 0
    private var layoutFlexGrow = 0f

    /**
     * (for [CompactCandidateMode.AUTO_FILL] only)
     * Second layout pass is needed when:
     * [^1] total candidates count < maxSpanCount && [^2] RecyclerView cannot display all of them
     * In that case, displayed candidates should be stretched evenly (by setting flexGrow to 1.0f).
     */
    private var secondLayoutPassNeeded = false
    private var secondLayoutPassDone = false

    private val _unrolledCandidateOffset =
        MutableSharedFlow<Int>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val unrolledCandidateOffset = _unrolledCandidateOffset.asSharedFlow()

    fun refreshUnrolled(childCount: Int) {
        _unrolledCandidateOffset.tryEmit(childCount)
        bar.unrollButtonStateMachine.push(
            UnrollButtonStateMachine.TransitionEvent.UnrolledCandidatesUpdated,
            UnrollButtonStateMachine.BooleanKey.UnrolledCandidatesEmpty to
                (adapter.total == childCount),
        )
    }

    /** Restyles the compact list after a scheme switch; visible rows re-bind. */
    fun refreshColors() {
        separatorDrawable.paint.color = scope.colors.candidateSeparatorColor
        adapter.notifyDataSetChanged()
    }

    val adapter by lazy {
        CompactCandidateViewAdapter(scope).apply {
            setOnItemClickListener { _, _, position ->
                val item = items.getOrNull(position) ?: return@setOnItemClickListener
                rime.launchOnReady { api ->
                    if (item.index >= 0) {
                        api.selectCandidate(item.index, global = true)
                        SegmentCommit.commitIfNeeded(api, service, filterController)
                    } else {
                        val snapshot = filterController.current.snapshot
                        val leftover = CandidateFilter.leftoverRawInput(
                            item.candidate.text,
                            filterController.table,
                            snapshot,
                        ) ?: ""
                        SegmentCommit.commitSynthetic(
                            api,
                            service,
                            filterController,
                            item.candidate.text,
                            leftover,
                        )
                    }
                }
            }
            setOnItemLongClickListener { _, view, position ->
                val item = items.getOrNull(position) ?: return@setOnItemLongClickListener false
                if (item.index < 0) return@setOnItemLongClickListener true
                inputView.showCandidateActionMenu(item.index, item.candidate.text, view, global = true)
                true
            }
        }
    }

    fun updateLayoutParams(minWidth: Int, flexGrow: Float) {
        layoutMinWidth = minWidth
        layoutFlexGrow = flexGrow
    }

    val layoutManager by lazy {
        object : FlexboxLayoutManager(context) {
            override fun canScrollHorizontally(): Boolean = false

            override fun canScrollVertically(): Boolean = false

            override fun onLayoutCompleted(state: RecyclerView.State?) {
                super.onLayoutCompleted(state)
                val cnt = this.childCount
                if (secondLayoutPassNeeded) {
                    if (cnt < adapter.itemCount) {
                        // [^2] RecyclerView can't display all candidates
                        // update LayoutParams in onLayoutCompleted would trigger another
                        // onLayoutCompleted, skip the second one to avoid infinite loop
                        if (secondLayoutPassDone) return
                        secondLayoutPassDone = true
                        for (i in 0 until cnt) {
                            getChildAt(i)!!.updateLayoutParams<LayoutParams> {
                                flexGrow = 1f
                            }
                        }
                    } else {
                        secondLayoutPassNeeded = false
                    }
                }
                refreshUnrolled(cnt)
            }
        }
    }

    private val separatorDrawable by lazy {
        ShapeDrawable(RectShape()).apply {
            val spacing = theme.style.candidateSpacing
            val intrinsicSize = max(spacing, context.dp(spacing)).toInt()
            intrinsicWidth = intrinsicSize
            intrinsicHeight = intrinsicSize
            paint.color = scope.colors.candidateSeparatorColor
        }
    }

    val view by lazy {
        object : RecyclerView(context) {
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                if (fillStyle == CompactCandidateMode.AUTO_FILL) {
                    val maxSpanCount = maxSpanCountPref.getValue()
                    layoutMinWidth = w / maxSpanCount - separatorDrawable.intrinsicWidth
                }
            }
        }
        context.recyclerView(R.id.candidate_view) {
            itemAnimator = null
            isFocusable = false
            isFocusableInTouchMode = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                defaultFocusHighlightEnabled = false
            }
            adapter = this@CompactCandidateDelegate.adapter
            layoutManager = this@CompactCandidateDelegate.layoutManager
            addItemDecoration(FlexboxVerticalDecoration(separatorDrawable))
        }
    }

    init {
        collectJob = rime.lifecycleScope.launch(Dispatchers.Main) {
            filterController.state.collect { state ->
                val key = state.snapshot to state.singleChar
                if (key != lastFilterKey) {
                    lastFilterKey = key
                    lastBulk?.let { publish(it) }
                }
            }
        }
    }

    override fun onCandidateListUpdate(data: Candidates.Bulk) {
        lastBulk = data
        publish(data)
    }

    private fun publish(data: Candidates.Bulk) {
        val (total, highlighted, candidates) = data
        val maxSpanCount = maxSpanCountPref.getValue()
        val needsFilter = filterController.current.singleChar ||
            filterController.current.snapshot.mode != InputMode.NONE

        applyFillStyle(if (needsFilter) maxSpanCount else candidates.size, maxSpanCount)

        if (!needsFilter) {
            val indexed = candidates.mapIndexed { i, c -> IndexedCandidate(i, c) }
            adapter.updateLayoutParams(layoutMinWidth, layoutFlexGrow)
            adapter.updateCandidates(indexed, total, highlighted)
            if (indexed.isEmpty()) refreshUnrolled(0)
            val first = indexed.firstOrNull { it.index == highlighted } ?: indexed.firstOrNull()
            filterController.setDisplayCandidate(first?.candidate?.text)
            return
        }

        val seq = ++publishSeq
        rime.launchOnReady { api ->
            if (seq != publishSeq) return@launchOnReady
            val want = max(maxSpanCount * 2, 24)
            val acc = ArrayList<IndexedCandidate>(want)
            var start = 0
            val rimeTotal = if (total >= 0) total else Int.MAX_VALUE
            // Start from the already-delivered page, then walk further if needed.
            fun accept(index: Int, text: CandidateProto) {
                if (filterController.matches(text.text)) {
                    acc += IndexedCandidate(index, text)
                }
            }
            candidates.forEachIndexed { i, c -> accept(i, c) }
            start = candidates.size
            var exhausted = start >= rimeTotal || candidates.size < 1 && total == 0
            val pageSize = 80
            while (acc.size < want && start < rimeTotal) {
                val chunk = api.getCandidates(start, pageSize)
                if (chunk.isEmpty()) {
                    exhausted = true
                    break
                }
                chunk.forEachIndexed { i, c -> accept(start + i, c) }
                start += chunk.size
                if (chunk.size < pageSize) {
                    exhausted = true
                    break
                }
            }
            if (start >= rimeTotal) exhausted = true
            val prefixes = CandidateFilter.prefixInputs(filterController.current.snapshot)
            if (prefixes.isNotEmpty() && acc.size < want) {
                val seen = acc.mapTo(HashSet()) { it.candidate.text }
                var synthetic = -1
                for (input in prefixes.sortedByDescending { it.length }) {
                    if (acc.size >= want) break
                    val chunk = api.peekCandidates(input, 24)
                    for (candidate in chunk) {
                        if (acc.size >= want) break
                        if (!filterController.matches(candidate.text)) continue
                        if (!seen.add(candidate.text)) continue
                        acc += IndexedCandidate(synthetic, candidate)
                        synthetic--
                    }
                }
                if (acc.size >= want) exhausted = false
            }
            val filteredTotal = if (exhausted) acc.size else acc.size + 1
            val highlightedIndex = acc.firstOrNull { it.index == highlighted }?.index
                ?: acc.firstOrNull()?.index
                ?: -1
            ContextCompat.getMainExecutor(context).execute {
                if (seq != publishSeq) return@execute
                adapter.updateLayoutParams(layoutMinWidth, layoutFlexGrow)
                adapter.updateCandidates(acc, filteredTotal, highlightedIndex)
                if (acc.isEmpty()) refreshUnrolled(0)
                val display = acc.firstOrNull { it.index == highlightedIndex } ?: acc.firstOrNull()
                filterController.setDisplayCandidate(display?.candidate?.text)
            }
        }
    }

    private fun applyFillStyle(visibleCount: Int, maxSpanCount: Int) {
        when (fillStyle) {
            CompactCandidateMode.NEVER_FILL -> {
                layoutMinWidth = 0
                layoutFlexGrow = 0f
                secondLayoutPassNeeded = false
            }

            CompactCandidateMode.AUTO_FILL -> {
                layoutMinWidth = view.width / maxSpanCount - separatorDrawable.intrinsicWidth
                layoutFlexGrow = if (visibleCount < maxSpanCount) 0f else 1f
                // [^1] total candidates count < maxSpanCount
                secondLayoutPassNeeded = visibleCount < maxSpanCount
                secondLayoutPassDone = false
            }

            CompactCandidateMode.ALWAYS_FILL -> {
                layoutMinWidth = 0
                layoutFlexGrow = 1f
                secondLayoutPassNeeded = false
            }
        }
    }
}
