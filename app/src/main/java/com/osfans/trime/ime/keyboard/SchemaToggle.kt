/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

/**
 * Pure selection logic for a toolbar / preset key that toggles between a list
 * of Rime schema ids (see `toggle_schema` on [com.osfans.trime.data.theme.model.PresetKey]).
 *
 * The current schema is compared to the list in order:
 * - if it matches an entry, the next entry is chosen (wrapping from the last
 *   back to the first);
 * - if it is not in the list, or the current id is blank, the first entry is
 *   chosen (a documented, stable default, typically the theme's "home" schema);
 * - an empty list is a no-op and yields an empty string.
 */
object SchemaToggle {
    fun next(currentSchemaId: String, schemas: List<String>): String {
        if (schemas.isEmpty()) return ""
        val index = schemas.indexOf(currentSchemaId)
        if (index < 0) return schemas.first()
        return schemas[(index + 1) % schemas.size]
    }

    /**
     * Prefer a keyboard whose id equals the schema id, the same rule
     * [com.osfans.trime.ime.keyboard.KeyboardWindow] uses in smart matching.
     * Fall back to `.default` so the window can still pick by alphabet.
     */
    fun keyboardForSchema(schemaId: String, presetKeyboardIds: Collection<String>): String {
        if (schemaId.isNotEmpty() && presetKeyboardIds.contains(schemaId)) return schemaId
        return ".default"
    }
}
