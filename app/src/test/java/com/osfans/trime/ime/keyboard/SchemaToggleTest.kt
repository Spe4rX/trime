/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import com.osfans.trime.data.theme.ThemeTestSupport
import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.util.yamlMapOf
import com.osfans.trime.util.yamlScalarOf
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * Selection rules for the `toggle_schema` preset / toolbar action: cycle
 * through the listed ids, and fall back to the first id when the current
 * schema is not in the list.
 */
class SchemaToggleTest :
    BehaviorSpec({
        Given("a pair of schemas") {
            val schemas = listOf("luna_pinyin_t9", "luna_pinyin_simp")

            When("the current schema is the first") {
                Then("the next schema is the second") {
                    SchemaToggle.next("luna_pinyin_t9", schemas) shouldBe "luna_pinyin_simp"
                }
            }

            When("the current schema is the second") {
                Then("the next schema wraps to the first") {
                    SchemaToggle.next("luna_pinyin_simp", schemas) shouldBe "luna_pinyin_t9"
                }
            }

            When("the current schema is neither listed nor blank") {
                Then("the first schema is chosen") {
                    SchemaToggle.next("luna_pinyin", schemas) shouldBe "luna_pinyin_t9"
                    SchemaToggle.next("english", schemas) shouldBe "luna_pinyin_t9"
                }
            }

            When("the current schema id is blank") {
                Then("the first schema is chosen") {
                    SchemaToggle.next("", schemas) shouldBe "luna_pinyin_t9"
                }
            }
        }

        Given("a theme that has a keyboard named after the schema") {
            val ids = listOf("luna_pinyin_t9", "default", "letter")

            Then("the schema id is used as the keyboard id") {
                SchemaToggle.keyboardForSchema("luna_pinyin_t9", ids) shouldBe "luna_pinyin_t9"
                SchemaToggle.keyboardForSchema("default", ids) shouldBe "default"
            }

            Then("an unknown or blank schema falls back to .default") {
                SchemaToggle.keyboardForSchema("luna_pinyin_simp", ids) shouldBe ".default"
                SchemaToggle.keyboardForSchema("", ids) shouldBe ".default"
            }
        }

        Given("an empty schema list") {
            Then("the result is empty") {
                SchemaToggle.next("luna_pinyin_t9", emptyList()) shouldBe ""
            }
        }

        Given("a single schema") {
            Then("the listed schema is always selected") {
                SchemaToggle.next("other", listOf("luna_pinyin_simp")) shouldBe "luna_pinyin_simp"
                SchemaToggle.next("luna_pinyin_simp", listOf("luna_pinyin_simp")) shouldBe "luna_pinyin_simp"
            }
        }

        Given("a preset key that declares toggle_schema") {
            val preset = ThemeTestSupport.yaml.decodeFromYamlNode<PresetKey>(
                yamlMapOf(
                    "label" to yamlScalarOf("九/全"),
                    "toggle_schema" to ThemeTestSupport.yaml.parseToYamlNode("[luna_pinyin_t9, luna_pinyin_simp]"),
                ),
            )

            Then("the list is preserved in order") {
                preset.toggleSchema shouldBe listOf("luna_pinyin_t9", "luna_pinyin_simp")
                preset.label shouldBe "九/全"
            }
        }

        Given("a preset key without toggle_schema") {
            Then("the list is empty and the rest keep their defaults") {
                val preset = ThemeTestSupport.yaml.decodeFromYamlNode<PresetKey>(yamlMapOf())
                preset.toggleSchema shouldBe emptyList()
                preset.send shouldBe ""
            }
        }
    })
