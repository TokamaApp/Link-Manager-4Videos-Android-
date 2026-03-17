package com.tokama.linkmanager.data

import org.json.JSONObject

/**
 * Ein gemerkter Storage-Access-Framework-Eintrag.
 * Gespeichert wird URI + Anzeigename + Zeitpunkt des Hinzufügens.
 * [sortOrder] ist optional und wird für manuelle Reihenfolge verwendet.
 */
data class SavedFile(
    val uriString: String,
    val displayName: String,
    val addedAt: Long = System.currentTimeMillis(),
    val sortOrder: Int? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("uri", uriString)
            put("displayName", displayName)
            put("addedAt", addedAt)
            if (sortOrder != null) {
                put("sortOrder", sortOrder)
            }
        }
    }

    companion object {
        fun fromJson(jsonObject: JSONObject): SavedFile {
            return SavedFile(
                uriString = jsonObject.optString("uri"),
                displayName = jsonObject.optString("displayName"),
                addedAt = jsonObject.optLong("addedAt", 0L),
                sortOrder = if (jsonObject.has("sortOrder")) jsonObject.optInt("sortOrder") else null
            )
        }
    }
}
