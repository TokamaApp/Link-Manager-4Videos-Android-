package com.tokama.linkmanager.data

/**
 * Repräsentiert genau eine Zeile aus der Datei, in der eine URL gefunden wurde.
 * [lineIndex] zeigt auf die originale Zeilenposition innerhalb der Datei.
 */
data class LinkEntry(
    val lineIndex: Int,
    val rawLine: String,
    val url: String,
    val rating: LinkRating
) {
    val displayLine: String
        get() = rawLine.trim().ifBlank {
            if (rating == LinkRating.NONE) url else "${rating.prefix} $url"
        }
}