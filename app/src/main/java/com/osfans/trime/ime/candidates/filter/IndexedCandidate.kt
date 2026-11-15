/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.filter

import com.osfans.trime.core.CandidateProto

/** A Rime candidate paired with its global menu index. */
data class IndexedCandidate(
    val index: Int,
    val candidate: CandidateProto,
)
