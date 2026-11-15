package com.osfans.trime.ime.candidates.filter

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ReadingColorsTest :
    BehaviorSpec({
        Given("unlocked pinyin") {
            Then("the whole reading is a single unconfirmed run") {
                val parts = CandidateFilter.barParts(
                    CandidateFilter.compute("gugugaga", emptyList()),
                    "hu hu ga ga",
                )
                readingRuns(parts, parts.remaining) shouldBe listOf(
                    ReadingRun("gu gu ga ga", confirmed = false),
                )
                readingRuns(CandidateFilter.BarParts(null, null), "ni hao") shouldBe listOf(
                    ReadingRun("ni hao", confirmed = false),
                )
            }
        }

        Given("a locked syllable") {
            Then("only the confirmed run is marked as locked") {
                val parts = CandidateFilter.barParts(
                    CandidateFilter.compute("gugugaga", listOf("gu")),
                    "gu hui",
                )
                parts.confirmed shouldBe "gu"
                parts.remaining shouldBe "gu'ga'ga"
                readingRuns(parts, null) shouldBe listOf(
                    ReadingRun("gu", confirmed = true),
                    ReadingRun("gu'ga'ga", confirmed = false),
                )
            }
        }
    })
