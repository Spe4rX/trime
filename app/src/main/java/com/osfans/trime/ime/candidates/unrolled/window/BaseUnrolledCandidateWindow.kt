/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.unrolled.window

import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.KeyEvent
import android.view.View
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.bar.InputBarDelegate
import com.osfans.trime.ime.bar.UnrollButtonStateMachine
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.candidates.CandidateViewHolder
import com.osfans.trime.ime.candidates.compact.CompactCandidateDelegate
import com.osfans.trime.ime.candidates.filter.CandidateFilterController
import com.osfans.trime.ime.candidates.filter.FilteringCandidatesPagingSource
import com.osfans.trime.ime.candidates.filter.InputMode
import com.osfans.trime.ime.candidates.unrolled.PagingCandidateViewAdapter
import com.osfans.trime.ime.candidates.unrolled.UnrolledCandidateLayout
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.instance
import splitties.dimensions.dp
import kotlin.math.max

abstract class BaseUnrolledCandidateWindow(di: DI) :
    BoardWindow.NoBarBoardWindow(di),
    InputBroadcastReceiver {
    protected val service: TrimeInputMethodService by instance()
    protected val rime: RimeSession by instance()
    protected val scope: ThemeScope by instance()
    private val inputView: InputView by instance()
    private val bar: InputBarDelegate by instance()
    private val windowManager: BoardWindowManager by instance()
    private val compactCandidate: CompactCandidateDelegate by instance()
    private val filterController: CandidateFilterController by instance()
    private val commonKeyboardActionListener: CommonKeyboardActionListener by instance()

    protected val theme: Theme
        get() = scope.theme

    private lateinit var lifecycleCoroutineScope: LifecycleCoroutineScope
    private lateinit var candidateLayout: UnrolledCandidateLayout

    protected val separatorDrawable by lazy {
        ShapeDrawable(RectShape()).apply {
            val spacing = theme.style.candidateSpacing
            val intrinsicSize = max(spacing, context.dp(spacing)).toInt()
            intrinsicWidth = intrinsicSize
            intrinsicHeight = intrinsicSize
            paint.color = scope.colors.candidateSeparatorColor
        }
    }

    override fun refreshColors() {
        if (!::candidateLayout.isInitialized) return
        // the decorations share this drawable, so re-coloring its paint repaints the dividers
        separatorDrawable.paint.color = scope.colors.candidateSeparatorColor
        candidateLayout.refreshColors()
        // visible rows re-apply their colors on rebind
        adapter.notifyDataSetChanged()
    }

    abstract fun onCreateCandidateLayout(): UnrolledCandidateLayout

    final override fun onCreateView(): View {
        candidateLayout =
            onCreateCandidateLayout().apply {
                recyclerView.apply {
                    // disable item cross-fade animation
                    itemAnimator = null
                }
            }
        return candidateLayout
    }

    abstract val adapter: PagingCandidateViewAdapter
    abstract val layoutManager: RecyclerView.LayoutManager

    private var offsetJob: Job? = null

    private val candidatesPager by lazy {
        Pager(
            config = PagingConfig(
                pageSize = 48,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                val rimeTotal = compactCandidate.adapter.total
                // compact.total is already the filtered count when a filter is
                // active, so walk the raw Rime menu instead.
                val rawTotal = lastRimeTotal
                FilteringCandidatesPagingSource(
                    rime,
                    rimeTotal = rawTotal,
                    predicate = { candidate, _ ->
                        val state = filterController.current
                        if (!state.singleChar &&
                            state.snapshot.mode == InputMode.NONE
                        ) {
                            true
                        } else {
                            filterController.matches(candidate.text)
                        }
                    },
                )
            },
        )
    }

    private var lastRimeTotal: Int = -1
    private var filterCollectJob: Job? = null

    private var candidatesSubmitJob: Job? = null

    override fun onAttached() {
        lifecycleCoroutineScope = candidateLayout.findViewTreeLifecycleOwner()!!.lifecycleScope
        bar.unrollButtonStateMachine.push(UnrollButtonStateMachine.TransitionEvent.UnrolledCandidatesAttached)
        lastRimeTotal = if (filterController.current.singleChar ||
            filterController.current.snapshot.mode != InputMode.NONE
        ) {
            -1
        } else {
            compactCandidate.adapter.total
        }
        candidateLayout.backspaceButton.setOnClickListener {
            val listener = commonKeyboardActionListener.listener
            listener.onPress(KeyEvent.KEYCODE_DEL)
            listener.onKey(KeyEvent.KEYCODE_DEL, 0)
        }
        candidateLayout.filterColumn.onItemClick = { index, syllable ->
            filterController.onFilterClick(index, syllable)
        }
        applyFilterColumn(filterController.current)
        offsetJob =
            lifecycleCoroutineScope.launch {
                compactCandidate.unrolledCandidateOffset.collect {
                    if (compactCandidate.adapter.total <= 0) {
                        windowManager.attachWindow(KeyboardWindow)
                    } else {
                        candidateLayout.resetPosition()
                        adapter.refreshWith(
                            offset = 0,
                            highlightedIndex = compactCandidate.adapter.highlightedIdx,
                        )
                    }
                }
            }
        candidatesSubmitJob =
            lifecycleCoroutineScope.launch {
                candidatesPager.flow.collectLatest {
                    adapter.submitData(it)
                }
            }
        filterCollectJob =
            lifecycleCoroutineScope.launch {
                filterController.state.collect { state ->
                    applyFilterColumn(state)
                    lastRimeTotal = if (state.singleChar ||
                        state.snapshot.mode != InputMode.NONE
                    ) {
                        -1
                    } else {
                        compactCandidate.adapter.total
                    }
                    adapter.refreshWith(0, compactCandidate.adapter.highlightedIdx)
                }
            }
    }

    private fun applyFilterColumn(state: CandidateFilterController.State) {
        if (!::candidateLayout.isInitialized) return
        if (state.showExpandedFilter) {
            candidateLayout.filterColumn.setItems(
                state.snapshot.filterItems,
                state.snapshot.selectedIndex,
            )
        } else {
            candidateLayout.filterColumn.setItems(emptyList(), -1)
        }
    }

    fun bindCandidateUiViewHolder(holder: CandidateViewHolder) {
        holder.itemView.run {
            setOnClickListener { _ ->
                rime.launchOnReady { it.selectCandidate(holder.idx, global = true) }
            }
            setOnLongClickListener { view ->
                inputView.showCandidateActionMenu(holder.idx, holder.text, view, global = true)
                true
            }
        }
    }

    override fun onDetached() {
        bar.unrollButtonStateMachine.push(
            UnrollButtonStateMachine.TransitionEvent.UnrolledCandidatesDetached,
            UnrollButtonStateMachine.BooleanKey.UnrolledCandidatesEmpty to
                (compactCandidate.adapter.total == adapter.offset),
        )
        offsetJob?.cancel()
        candidatesSubmitJob?.cancel()
        filterCollectJob?.cancel()
    }
}
