/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.bar

import com.osfans.trime.data.theme.model.ToolBar

/**
 * Whether a toolbar button should be shown for the keyboard that is currently
 * attached. An empty or omitted [ToolBar.Button.visibleOnKeyboards] list means
 * the button is always visible. Matching is by exact preset keyboard id
 * (for example `luna_pinyin_t9` or `default`), not by schema id.
 */
object ToolBarVisibility {
    fun isVisible(button: ToolBar.Button, keyboardId: String): Boolean {
        val allowed = button.visibleOnKeyboards
        return allowed.isEmpty() || keyboardId in allowed
    }
}
