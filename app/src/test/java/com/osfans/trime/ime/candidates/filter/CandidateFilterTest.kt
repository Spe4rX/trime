/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.filter

import com.osfans.trime.core.CandidateProto
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe

class CandidateFilterTest :
    BehaviorSpec({
        val table = HanziPinyinTable.fromMap(
            mapOf(
                '你'.code to listOf("ni"),
                '好'.code to listOf("hao"),
                '米'.code to listOf("mi"),
                '糕'.code to listOf("gao"),
                '敢'.code to listOf("gan"),
                '号'.code to listOf("hao"),
                '高'.code to listOf("gao"),
                '行'.code to listOf("xing", "hang", "heng"),
                '银'.code to listOf("yin"),
            ),
        )

        fun proto(text: String) = CandidateProto(text, "", "")

        Given("a T9 digit sequence 64426") {
            val snapshot = CandidateFilter.compute("64426", emptyList())

            Then("the first-syllable list includes mi, ni, m, n, o and is longer-first") {
                snapshot.mode shouldBe InputMode.T9
                snapshot.nextSyllables.shouldContainAll("mi", "ni", "m", "n", "o")
                snapshot.nextSyllables shouldBe listOf("mi", "ni", "m", "n", "o")
            }
        }

        Given("64426 after locking mi") {
            val snapshot = CandidateFilter.compute("64426", listOf("mi"))

            Then("remaining 426 yields gan, gao, han, hao, ian, iao and shorter prefixes") {
                snapshot.locks shouldBe listOf("mi")
                snapshot.remaining shouldBe "426"
                snapshot.nextSyllables.shouldContainAll("gan", "gao", "han", "hao", "ian", "iao")
                snapshot.nextSyllables shouldBe listOf(
                    "gan", "gao", "han", "hao", "ian", "iao",
                    "ga", "ha", "ia",
                    "g", "h", "i",
                )
            }
        }

        Given("apostrophes in the raw T9 input") {
            val snapshot = CandidateFilter.compute("64'426", listOf("mi"))

            Then("delimiters are skipped when consuming locks and enumerating") {
                snapshot.remaining shouldBe "426"
                snapshot.nextSyllables.shouldContainAll("gan", "gao", "han", "hao")
            }
        }

        Given("candidates 你好 and 米糕 for input 64426") {
            val candidates = listOf(
                IndexedCandidate(0, proto("你好")),
                IndexedCandidate(1, proto("米糕")),
                IndexedCandidate(2, proto("👋")),
            )

            Then("with no locks both hanzi words and the symbol pass") {
                val snapshot = CandidateFilter.compute("64426", emptyList())
                val filtered = CandidateFilter.filter(candidates, table, snapshot, singleChar = false)
                filtered.map { it.candidate.text } shouldBe listOf("你好", "米糕", "👋")
                filtered.map { it.index } shouldBe listOf(0, 1, 2)
            }

            Then("locking mi keeps 米糕 and drops 你好") {
                val snapshot = CandidateFilter.compute("64426", listOf("mi"))
                val filtered = CandidateFilter.filter(candidates, table, snapshot, singleChar = false)
                filtered.map { it.candidate.text } shouldBe listOf("米糕")
                filtered.map { it.index } shouldBe listOf(1)
                CandidateFilter.displayReading("米糕", table, snapshot) shouldBe "mi gao"
                CandidateFilter.displayReading("你好", table, snapshot) shouldBe null
            }
        }

        Given("the 单字 filter") {
            val snapshot = CandidateFilter.compute("64", emptyList())
            val candidates = listOf(
                IndexedCandidate(0, proto("你")),
                IndexedCandidate(1, proto("你好")),
                IndexedCandidate(2, proto("米")),
            )

            Then("it keeps a single character and drops a two-character word") {
                val filtered = CandidateFilter.filter(candidates, table, snapshot, singleChar = true)
                filtered.map { it.candidate.text } shouldBe listOf("你", "米")
                CandidateMatcher.isSingleCharacter("你") shouldBe true
                CandidateMatcher.isSingleCharacter("你好") shouldBe false
            }
        }

        Given("letter-pinyin raw input nihao") {
            val snapshot = CandidateFilter.compute("nihao", emptyList())

            Then("first-syllable choices are ni then n") {
                snapshot.mode shouldBe InputMode.LETTER
                snapshot.nextSyllables shouldBe listOf("ni", "n")
            }

            Then("locking ni yields hao, ha, h and keeps 你好") {
                val locked = CandidateFilter.compute("nihao", listOf("ni"))
                locked.remaining shouldBe "hao"
                locked.nextSyllables shouldBe listOf("hao", "ha", "h")
                CandidateFilter.matches("你好", table, locked, singleChar = false) shouldBe true
                CandidateFilter.matches("米糕", table, locked, singleChar = false) shouldBe false
                CandidateFilter.displayReading("你好", table, locked) shouldBe "ni hao"
            }
        }

        Given("backspace that shrinks the raw input") {
            Then("a lock that no longer fits is dropped") {
                val snapshot = CandidateFilter.compute("6", listOf("mi"))
                snapshot.locks shouldBe emptyList()
                snapshot.nextSyllables.shouldContainAll("m", "n", "o")
            }

            Then("a still-compatible lock is kept") {
                val snapshot = CandidateFilter.compute("6442", listOf("mi"))
                snapshot.locks shouldBe listOf("mi")
                snapshot.remaining shouldBe "42"
                snapshot.nextSyllables.shouldContainAll("ga", "ha", "ia")
            }
        }

        Given("tapping filter items") {
            Then("tapping a next syllable appends a lock") {
                CandidateFilter.toggleLock(emptyList(), 0, "mi") shouldBe listOf("mi")
            }

            Then("tapping the last lock unlocks it") {
                CandidateFilter.toggleLock(listOf("mi"), 0, "mi") shouldBe emptyList()
            }

            Then("tapping an earlier lock pops back to before it") {
                CandidateFilter.toggleLock(listOf("mi", "gao"), 0, "mi") shouldBe emptyList()
            }
        }

        Given("a partial syllable that has not been fully typed") {
            Then("the reading label stays at the typed prefix, not the first candidate's full pinyin") {
                val letterH = CandidateFilter.compute("h", emptyList())
                CandidateFilter.displayReading("好", table, letterH) shouldBe "h"
                CandidateFilter.displayReading("好", table, CandidateFilter.compute("ha", emptyList())) shouldBe "ha"
                CandidateFilter.displayReading("好", table, CandidateFilter.compute("hao", emptyList())) shouldBe "hao"
                CandidateFilter.displayReading("你", table, CandidateFilter.compute("n", emptyList())) shouldBe "n"
                CandidateFilter.displayReading("你好", table, CandidateFilter.compute("ni", emptyList())) shouldBe "ni"
                CandidateFilter.displayReading("你好", table, CandidateFilter.compute("niha", emptyList())) shouldBe "ni ha"
            }

            Then("T9 digits also map only to the typed pinyin prefix") {
                CandidateFilter.displayReading("好", table, CandidateFilter.compute("4", emptyList())) shouldBe "h"
                CandidateFilter.displayReading("好", table, CandidateFilter.compute("42", emptyList())) shouldBe "ha"
                CandidateFilter.displayReading("好", table, CandidateFilter.compute("426", emptyList())) shouldBe "hao"
                CandidateFilter.displayReading("你好", table, CandidateFilter.compute("64", emptyList())) shouldBe "ni"
                CandidateFilter.displayReading("你好", table, CandidateFilter.compute("644", emptyList())) shouldBe "ni h"
                CandidateFilter.displayReading("你好", table, CandidateFilter.compute("64426", emptyList())) shouldBe "ni hao"
            }
        }

        Given("stepwise filtering") {
            Then("the left list shows only the next level, not already confirmed locks") {
                val locked = CandidateFilter.compute("64426", listOf("mi"))
                locked.filterItems shouldBe locked.nextSyllables
                locked.filterItems.contains("mi") shouldBe false
                locked.selectedIndex shouldBe -1
                CandidateFilter.popLastLock(listOf("mi", "gao")) shouldBe listOf("mi")
            }

            Then("the bar splits confirmed pinyin from the remaining reading") {
                val locked = CandidateFilter.compute("nihao", listOf("ni"))
                val parts = CandidateFilter.barParts(locked, "ni hao")
                parts.confirmed shouldBe "ni"
                parts.remaining shouldBe "hao"
                val four = CandidateFilter.barParts(
                    CandidateFilter.compute("gugugaga", listOf("gu")),
                    "gu gu ga ga",
                )
                four.confirmed shouldBe "gu"
                four.remaining shouldBe "gu'ga'ga"
            }

            Then("raw prefixes stop at syllable boundaries and omit the full input") {
                val full = SyllableEnumerator.rawPrefixes("64426", InputMode.T9)
                full.shouldContainAll("6", "64", "644", "6442")
                full.contains("64426") shouldBe false
                val afterMi = SyllableEnumerator.rawPrefixes("64426", InputMode.T9, listOf("mi"))
                afterMi.shouldContainAll("64", "644", "6442")
                afterMi.contains("64426") shouldBe false
                val letters = SyllableEnumerator.rawPrefixes("nihao", InputMode.LETTER)
                letters.shouldContainAll("n", "ni", "nih", "niha")
                letters.contains("nihao") shouldBe false
            }

            Then("committing a shorter candidate leaves the unused raw input") {
                val snapshot = CandidateFilter.compute("nihao", emptyList())
                CandidateFilter.leftoverRawInput("你", table, snapshot) shouldBe "hao"
                CandidateFilter.leftoverRawInput("你好", table, snapshot) shouldBe ""
                val t9 = CandidateFilter.compute("64426", emptyList())
                CandidateFilter.leftoverRawInput("你", table, t9) shouldBe "426"
                CandidateFilter.leftoverRawInput("米糕", table, t9) shouldBe ""
            }
        }


        Given("reading bar colors") {
            Then("the more chromatic theme color is used for confirmed pinyin") {
                // grok light: text is accent blue, hilited text is black
                val light = readingColors(
                    textColor = 0xFF3266A0.toInt(),
                    hilitedTextColor = 0xFF000000.toInt(),
                    candidateTextColor = 0xFF000000.toInt(),
                    accentHint = 0xFF3266A0.toInt(),
                )
                light.confirmed shouldBe 0xFF3266A0.toInt()
                light.remaining shouldBe 0xFF000000.toInt()

                // grok dark: text is light blue, hilited text is white,
                // candidate back is the stronger accent blue
                val dark = readingColors(
                    textColor = 0xFF8AB4E8.toInt(),
                    hilitedTextColor = 0xFFFFFFFF.toInt(),
                    candidateTextColor = 0xFFFFFFFF.toInt(),
                    accentHint = 0xFF3266A0.toInt(),
                )
                dark.confirmed shouldBe 0xFF3266A0.toInt()
                dark.remaining shouldBe 0xFFFFFFFF.toInt()

                // a typical scheme with the accent on hilited_text_color
                val typical = readingColors(
                    textColor = 0xFF5A676E.toInt(),
                    hilitedTextColor = 0xFF23948E.toInt(),
                    candidateTextColor = 0xFF5A676E.toInt(),
                    accentHint = 0xFFD3D7DA.toInt(),
                )
                typical.confirmed shouldBe 0xFF23948E.toInt()
                typical.remaining shouldBe 0xFF5A676E.toInt()
            }
        }

        Given("an empty composition") {
            Then("locks and combinations are cleared") {
                val snapshot = CandidateFilter.compute("", listOf("mi"))
                snapshot.mode shouldBe InputMode.NONE
                snapshot.locks shouldBe emptyList()
                snapshot.nextSyllables shouldBe emptyList()
                snapshot.hasCombinations shouldBe false
            }
        }
    })
