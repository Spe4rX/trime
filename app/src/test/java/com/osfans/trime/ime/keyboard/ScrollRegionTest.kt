/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import com.osfans.trime.data.theme.ThemeTestSupport
import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.TextKeyboard.TextKey
import com.osfans.trime.ime.keyboard.ScrollRegionLayout.ChildRect
import com.osfans.trime.util.yamlMapOf
import com.osfans.trime.util.yamlScalarOf
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * YAML shape and pure layout math for scroll slots: a key may declare
 * `scroll: vertical|horizontal`, `row_span`, `visible_count`, and a list of
 * child `keys`. Reserved holes on later rows keep the rest of those rows to
 * the right of a multi-row vertical column.
 */
class ScrollRegionTest :
    BehaviorSpec({
        Given("a key without scroll fields") {
            val key = ThemeTestSupport.yaml.decodeFromYamlNode<TextKey>(
                yamlMapOf("click" to yamlScalarOf("a")),
            )

            Then("it is not a scroll slot and new fields stay at their defaults") {
                key.isScrollSlot shouldBe false
                key.scroll shouldBe TextKey.Scroll.NONE
                key.rowSpan shouldBe 1
                key.visibleCount shouldBe 0
                key.keys shouldBe emptyList()
            }
        }

        Given("a key that also has an unknown field") {
            val key = ThemeTestSupport.yaml.decodeFromYamlNode<TextKey>(
                ThemeTestSupport.yaml.parseToYamlNode(
                    """
                    click: a
                    not_a_real_field: ignored
                    """.trimIndent(),
                ),
            )

            Then("strictMode is off so the key still decodes") {
                key.click shouldBe KeyActionToken.Plain("a")
                key.isScrollSlot shouldBe false
            }
        }

        Given("a vertical scroll column beside three rows") {
            val key = ThemeTestSupport.yaml.decodeFromYamlNode<TextKey>(
                ThemeTestSupport.yaml.parseToYamlNode(
                    """
                    scroll: vertical
                    width: 14
                    row_span: 3
                    keys:
                      - { click: "'", label: "'" }
                      - { click: "？", label: "？" }
                      - { click: "！", label: "！" }
                      - { click: "，", label: "，" }
                    """.trimIndent(),
                ),
            )

            Then("the slot and its children are read") {
                key.isScrollSlot shouldBe true
                key.scroll shouldBe TextKey.Scroll.VERTICAL
                key.width shouldBe 14f
                key.rowSpan shouldBe 3
                key.visibleCount shouldBe 0
                key.keys.map { it.label } shouldBe listOf("'", "？", "！", "，")
                ScrollRegionLayout.effectiveVisibleCount(key.visibleCount, key.rowSpan) shouldBe 3
            }
        }

        Given("a number-column slot that shows four keys in three rows") {
            val key = ThemeTestSupport.yaml.decodeFromYamlNode<TextKey>(
                ThemeTestSupport.yaml.parseToYamlNode(
                    """
                    scroll: vertical
                    width: 14
                    row_span: 3
                    visible_count: 4
                    keys:
                      - { click: "%" }
                      - { click: "+" }
                      - { click: "-" }
                      - { click: "*" }
                      - { click: "#" }
                    """.trimIndent(),
                ),
            )

            Then("visible_count wins over row_span for the child height") {
                key.visibleCount shouldBe 4
                ScrollRegionLayout.effectiveVisibleCount(key.visibleCount, key.rowSpan) shouldBe 4
            }
        }

        Given("a horizontal tab strip") {
            val key = ThemeTestSupport.yaml.decodeFromYamlNode<TextKey>(
                ThemeTestSupport.yaml.parseToYamlNode(
                    """
                    scroll: horizontal
                    width: 86
                    keys:
                      - { click: Keyboard_symbols_recent, label: "近", width: 10 }
                      - { click: Keyboard_symbols_cn, label: "中", width: 10 }
                    """.trimIndent(),
                ),
            )

            Then("the direction and child widths are preserved") {
                key.scroll shouldBe TextKey.Scroll.HORIZONTAL
                key.width shouldBe 86f
                key.rowSpan shouldBe 1
                key.keys.map { it.label to it.width } shouldBe listOf("近" to 10f, "中" to 10f)
            }
        }

        Given("viewport and child layout math") {
            Then("a three-row viewport is the sum of those row heights") {
                ScrollRegionLayout.viewportHeight(listOf(40, 40, 40, 50), 0, 3) shouldBe 120
                ScrollRegionLayout.viewportHeight(listOf(40, 40, 40, 50), 1, 3) shouldBe 130
                ScrollRegionLayout.effectiveRowSpan(0) shouldBe 1
            }

            Then("vertical children share the viewport width and a uniform height") {
                val (rects, content) = ScrollRegionLayout.layoutChildren(
                    TextKey.Scroll.VERTICAL,
                    viewportWidth = 14,
                    viewportHeight = 120,
                    visibleCount = 3,
                    childWidths = listOf(14, 14, 14, 14),
                )
                rects shouldBe listOf(
                    ChildRect(0, 0, 14, 40),
                    ChildRect(0, 40, 14, 40),
                    ChildRect(0, 80, 14, 40),
                    ChildRect(0, 120, 14, 40),
                )
                content shouldBe (14 to 160)
            }

            Then("horizontal children sit on one row and can overflow the viewport") {
                val (rects, content) = ScrollRegionLayout.layoutChildren(
                    TextKey.Scroll.HORIZONTAL,
                    viewportWidth = 86,
                    viewportHeight = 40,
                    visibleCount = 1,
                    childWidths = listOf(10, 10, 10, 20),
                )
                rects shouldBe listOf(
                    ChildRect(0, 0, 10, 40),
                    ChildRect(10, 0, 10, 40),
                    ChildRect(20, 0, 10, 40),
                    ChildRect(30, 0, 20, 40),
                )
                content shouldBe (50 to 40)
            }
        }

        Given("reserved holes on later rows") {
            val slot = ScrollRegionLayout.OccupiedSlot(
                startRow = 0,
                rowSpan = 3,
                xWeight = 0f,
                widthWeight = 14f,
            )

            Then("rows 1 and 2 start after the 14-weight column, row 0 and 3 do not") {
                ScrollRegionLayout.reservedWeightForRow(0, listOf(slot)) shouldBe 0f
                ScrollRegionLayout.reservedWeightForRow(1, listOf(slot)) shouldBe 14f
                ScrollRegionLayout.reservedWeightForRow(2, listOf(slot)) shouldBe 14f
                ScrollRegionLayout.reservedWeightForRow(3, listOf(slot)) shouldBe 0f
            }

            Then("a key placed at x=0 is pushed past the hole") {
                val holes = ScrollRegionLayout.holesForRow(1, listOf(slot)) { it.toInt() }
                holes shouldBe listOf(0 to 14)
                ScrollRegionLayout.advancePastHoles(0, 22, holes) shouldBe 14
                ScrollRegionLayout.advancePastHoles(14, 22, holes) shouldBe 14
                ScrollRegionLayout.advancePastHoles(0, 22, emptyList()) shouldBe 0
            }
        }

        Given("child width conversion") {
            Then("a zero weight uses the default, a positive weight is kept") {
                val toPx: (Float) -> Int = { (it * 10).toInt() }
                ScrollRegionLayout.childWidthPx(0f, 10f, toPx) shouldBe 100
                ScrollRegionLayout.childWidthPx(8.6f, 10f, toPx) shouldBe 86
            }
        }
    })
