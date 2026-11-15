/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.filter

import com.osfans.trime.core.CompositionProto
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class SegmentCommitTest :
    BehaviorSpec({
        Given("a composition after selecting 米哈 from 64426") {
            val composition = CompositionProto(
                length = 4,
                cursorPos = 3,
                selStart = 2,
                selEnd = 3,
                preedit = "米哈6‸",
                commitTextPreview = "米哈哦",
            )

            Then("the confirmed prefix is 米哈 and remaining input is 6") {
                SegmentCommit.split(composition) shouldBe SegmentCommit.Split("米哈", "6")
            }

            Then("remaining raw is the leftover 6 even if getRawInput is still 64426") {
                SegmentCommit.resolveRemaining("64426", "6") shouldBe "6"
                SegmentCommit.resolveRemaining("6", "6") shouldBe "6"
                SegmentCommit.resolveRemaining("64426", "") shouldBe ""
            }
        }

        Given("a composition after selecting the single character 你") {
            val composition = CompositionProto(
                length = 5,
                cursorPos = 4,
                selStart = 1,
                selEnd = 4,
                preedit = "你426‸",
                commitTextPreview = "你好",
            )

            Then("the confirmed prefix is 你 and remaining input is 426") {
                SegmentCommit.split(composition) shouldBe SegmentCommit.Split("你", "426")
            }
        }

        Given("an unconfirmed preedit that is only raw input") {
            val composition = CompositionProto(
                length = 6,
                cursorPos = 5,
                selStart = 0,
                selEnd = 5,
                preedit = "64426‸",
            )

            Then("there is no confirmed prefix to commit") {
                SegmentCommit.split(composition) shouldBe null
            }
        }
    })
