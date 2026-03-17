package com.tokama.linkmanager.util

import android.util.Patterns
import com.tokama.linkmanager.data.LinkEntry
import com.tokama.linkmanager.data.LinkRating

/**
 * Zentrale Parsing-Logik für alle Link-Dateien.
 * Hier werden URLs extrahiert, Zeilen bewertet und Dateiinhalte normalisiert.
 */
object LinkParser {

    private const val PREFIX_VERY_GOOD = "[very good]"
    private const val PREFIX_GOOD = "[good]"
    private const val PREFIX_OKAY = "[okay]"
    private const val PREFIX_BAD = "[bad]"
    private const val PREFIX_VERY_BAD = "[very bad]"

    fun normalizeLineEndings(content: String): String {
        return content
            .replace("\r\n", "\n")
            .replace('\r', '\n')
    }

    /**
     * Extrahiert die erste URL aus beliebigem Freitext.
     * Nur HTTP/HTTPS wird akzeptiert.
     */
    fun extractFirstUrl(text: String): String? {
        val matcher = Patterns.WEB_URL.matcher(text)
        while (matcher.find()) {
            val candidate = matcher.group()?.trim().orEmpty()
            if (candidate.startsWith("http://", ignoreCase = true) ||
                candidate.startsWith("https://", ignoreCase = true)
            ) {
                return cleanupMatchedUrl(candidate)
            }
        }
        return null
    }

    fun extractAllLinesWithUrls(content: String): List<LinkEntry> {
        val normalizedContent = normalizeLineEndings(content)
        val lines = normalizedContent.split("\n")
        val entries = mutableListOf<LinkEntry>()

        lines.forEachIndexed { index, originalLine ->
            val url = extractFirstUrl(originalLine) ?: return@forEachIndexed
            entries += LinkEntry(
                lineIndex = index,
                rawLine = originalLine,
                url = url,
                rating = detectRating(originalLine)
            )
        }

        return entries
    }

    fun detectRating(line: String): LinkRating {
        val trimmed = line.trim().lowercase()
        return when {
            trimmed.startsWith(PREFIX_VERY_GOOD) -> LinkRating.VERY_GOOD
            trimmed.startsWith(PREFIX_GOOD) -> LinkRating.GOOD
            trimmed.startsWith(PREFIX_OKAY) -> LinkRating.OKAY
            trimmed.startsWith(PREFIX_VERY_BAD) -> LinkRating.VERY_BAD
            trimmed.startsWith(PREFIX_BAD) -> LinkRating.BAD
            else -> LinkRating.NONE
        }
    }

    /**
     * Baut die Zielzeile so auf, wie sie in der Datei stehen soll.
     * Ohne Bewertung bleibt nur die URL übrig.
     */
    fun buildLine(url: String, rating: LinkRating): String {
        return when (rating) {
            LinkRating.NONE -> url.trim()
            else -> "${rating.prefix} ${url.trim()}"
        }
    }

    private fun cleanupMatchedUrl(raw: String): String {
        return raw
            .trim()
            .trimEnd('.', ',', ';', ')', ']', '}')
    }
}