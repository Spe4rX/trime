/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.bar

import com.osfans.trime.data.theme.ThemeTestSupport
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.util.yamlMapOf
import com.osfans.trime.util.yamlScalarOf
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * Toolbar button visibility is filtered by the current preset keyboard id.
 * An empty or omitted visible_on_keyboards list means the button is always
 * shown.
 */
class ToolBarVisibilityTest :
    BehaviorSpec({
        fun button(vararg keyboards: String): ToolBar.Button =
            ToolBar.Button(visibleOnKeyboards = keyboards.toList())

        Given("a button with no keyboard restriction") {
            val always = button()

            Then("it is visible on every keyboard") {
                ToolBarVisibility.isVisible(always, "luna_pinyin_t9") shouldBe true
                ToolBarVisibility.isVisible(always, "default") shouldBe true
                ToolBarVisibility.isVisible(always, "letter") shouldBe true
                ToolBarVisibility.isVisible(always, "number") shouldBe true
                ToolBarVisibility.isVisible(always, "symbols") shouldBe true
                ToolBarVisibility.isVisible(always, "") shouldBe true
            }
        }

        Given("a button limited to the Chinese T9 and full keyboards") {
            val toggle = button("luna_pinyin_t9", "default")

            Then("it is visible only on those two ids") {
                ToolBarVisibility.isVisible(toggle, "luna_pinyin_t9") shouldBe true
                ToolBarVisibility.isVisible(toggle, "default") shouldBe true
                ToolBarVisibility.isVisible(toggle, "letter") shouldBe false
                ToolBarVisibility.isVisible(toggle, "number") shouldBe false
                ToolBarVisibility.isVisible(toggle, "symbols") shouldBe false
                ToolBarVisibility.isVisible(toggle, "symbols_cn") shouldBe false
            }
        }

        Given("a toolbar button YAML with visible_on_keyboards") {
            val parsed = ThemeTestSupport.yaml.decodeFromYamlNode<ToolBar.Button>(
                yamlMapOf(
                    "action" to yamlScalarOf("Toggle_t9_zh26"),
                    "visible_on_keyboards" to ThemeTestSupport.yaml.parseToYamlNode("[luna_pinyin_t9, default]"),
                ),
            )

            Then("the restriction is read and applied") {
                parsed.action shouldBe "Toggle_t9_zh26"
                parsed.visibleOnKeyboards shouldBe listOf("luna_pinyin_t9", "default")
                ToolBarVisibility.isVisible(parsed, "luna_pinyin_t9") shouldBe true
                ToolBarVisibility.isVisible(parsed, "letter") shouldBe false
            }
        }

        Given("a toolbar button YAML without visible_on_keyboards") {
            val parsed = ThemeTestSupport.yaml.decodeFromYamlNode<ToolBar.Button>(
                yamlMapOf("action" to yamlScalarOf("Hide")),
            )

            Then("the list is empty and the button stays visible") {
                parsed.visibleOnKeyboards shouldBe emptyList()
                ToolBarVisibility.isVisible(parsed, "number") shouldBe true
            }
        }
    })
