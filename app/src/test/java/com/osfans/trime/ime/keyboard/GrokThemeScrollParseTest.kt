/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import com.osfans.trime.data.theme.ThemeLoader
import com.osfans.trime.data.theme.ThemeTestSupport
import com.osfans.trime.data.theme.model.TextKeyboard.TextKey
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/**
 * The grok symbol tab strip is a flow-style list of YAML aliases. kaml plus
 * ThemeDslExpander must keep every tab, including when the same anchors are
 * reused on a second keyboard.
 */
class GrokThemeScrollParseTest :
    BehaviorSpec({
        Given("a horizontal strip whose children are YAML aliases in a flow list") {
            val yaml =
                """
                name: grok-scroll-fixture
                preset_color_schemes: {default: {back_color: "#000000"}}
                _tabs:
                  recent: &symbol_tab_recent { click: Keyboard_symbols_recent, label: "近", width: 10 }
                  cn: &symbol_tab_cn { click: Keyboard_symbols_cn, label: "中", width: 10 }
                  kata: &symbol_tab_kata { click: Keyboard_symbols_kata, label: "サ", width: 10 }
                preset_keyboards:
                  symbols:
                    keys:
                      - { scroll: horizontal, width: 86, keys: [ *symbol_tab_recent, *symbol_tab_cn, *symbol_tab_kata ] }
                      - { scroll: horizontal, width: 86, keys: [ *symbol_tab_recent, *symbol_tab_cn, *symbol_tab_kata ] }
                """.trimIndent()

            val theme = ThemeLoader.decodeSource(
                "grok-scroll-fixture",
                ThemeTestSupport.yaml.parseToYamlNode(yaml),
            ) { null }

            Then("both strips keep all three aliased tabs in order") {
                val strips = theme.presetKeyboards.getValue("symbols").keys.filter { it.isScrollSlot }
                strips shouldHaveSize 2
                strips.forEach { slot ->
                    slot.scroll shouldBe TextKey.Scroll.HORIZONTAL
                    slot.width shouldBe 86f
                    slot.keys.map { it.label } shouldBe listOf("近", "中", "サ")
                    slot.keys.map { it.width } shouldBe listOf(10f, 10f, 10f)
                }
            }
        }
    })
