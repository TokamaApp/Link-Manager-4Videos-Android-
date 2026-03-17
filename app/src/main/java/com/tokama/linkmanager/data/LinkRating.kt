package com.tokama.linkmanager.data

/**
 * Bewertungsstufen für eine Link-Zeile.
 * Die Datei speichert die Bewertung weiterhin als Präfix direkt vor der URL.
 */
enum class LinkRating(
    val prefix: String,
    val label: String
) {
    NONE(prefix = "", label = "Ohne Bewertung"),
    VERY_GOOD(prefix = "[very good]", label = "Sehr gut"),
    GOOD(prefix = "[good]", label = "Gut"),
    OKAY(prefix = "[okay]", label = "Okay"),
    BAD(prefix = "[bad]", label = "Schlecht"),
    VERY_BAD(prefix = "[very bad]", label = "Sehr schlecht")
}