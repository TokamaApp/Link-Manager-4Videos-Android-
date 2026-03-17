package com.tokama.linkmanager.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.tokama.linkmanager.data.LinkEntry
import com.tokama.linkmanager.data.LinkRating
import com.tokama.linkmanager.util.LinkParser
import java.util.Locale

/**
 * Führt alle Datei-Operationen direkt auf der Originaldatei aus.
 *
 * Ziel-Format der Datei:
 * - pro Eintrag genau eine Zeile
 * - zwischen zwei Einträgen immer genau eine Leerzeile
 * - keine führenden oder abschließenden Leerzeilen
 *
 * Beispiel:
 * https://a
 *
 * https://b
 *
 * [good] https://c
 */
class FileRepository(private val context: Context) {

    fun getDisplayName(uri: Uri): String {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return uri.lastPathSegment ?: "Unbekannte Datei"
    }

    /**
     * Baut aus einer SAF-/content-URI einen möglichst normal verständlichen Pfad
     * für die Dateiliste auf.
     */
    fun getReadablePath(
        uri: Uri,
        fallbackDisplayName: String = getDisplayName(uri)
    ): String {
        val safeFallbackName = fallbackDisplayName.ifBlank { getDisplayName(uri) }

        if (uri.scheme.equals("file", ignoreCase = true)) {
            val rawPath = uri.path.orEmpty()
            return if (rawPath.isBlank()) {
                safeFallbackName
            } else {
                prettifyRawFileSystemPath(rawPath)
            }
        }

        if (!uri.scheme.equals("content", ignoreCase = true)) {
            return safeFallbackName
        }

        resolveDocumentPath(uri, safeFallbackName)?.let { readablePath ->
            return readablePath
        }

        val decodedLastSegment = Uri.decode(uri.lastPathSegment.orEmpty()).trim('/')
        return if (decodedLastSegment.isNotBlank()) {
            if (decodedLastSegment.endsWith(safeFallbackName, ignoreCase = true)) {
                decodedLastSegment
            } else {
                appendPath(decodedLastSegment, safeFallbackName)
            }
        } else {
            safeFallbackName
        }
    }

    fun readContent(uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
            it.readText()
        }.orEmpty()
    }

    fun readLinks(uri: Uri): List<LinkEntry> {
        return LinkParser.extractAllLinesWithUrls(readContent(uri))
    }

    /**
     * Hängt einen neuen Link ans Ende an.
     */
    fun appendLink(uri: Uri, url: String) {
        val lines = readNormalizedNonEmptyLines(uri).toMutableList()
        lines.add(url.trim())
        writeFormattedEntries(uri, lines)
    }

    /**
     * Setzt oder entfernt die Bewertung einer bestehenden Link-Zeile.
     */
    fun updateRating(uri: Uri, entry: LinkEntry, rating: LinkRating) {
        val lines = readNormalizedLines(uri).toMutableList()
        if (entry.lineIndex !in lines.indices) return

        lines[entry.lineIndex] = LinkParser.buildLine(entry.url, rating)
        writeNormalizedLinesKeepingEntries(uri, lines)
    }

    /**
     * Löscht genau eine Link-Zeile.
     */
    fun deleteLine(uri: Uri, entry: LinkEntry) {
        val lines = readNormalizedLines(uri).toMutableList()
        if (entry.lineIndex !in lines.indices) return

        lines.removeAt(entry.lineIndex)
        writeNormalizedLinesKeepingEntries(uri, lines)
    }

    /**
     * Persistiert eine neue Link-Reihenfolge direkt in der Datei.
     * Dabei wird der Dateiinhalt vollständig anhand der neuen Reihenfolge neu geschrieben.
     */
    fun reorderLinks(uri: Uri, orderedEntries: List<LinkEntry>) {
        val normalizedEntries = orderedEntries.map { entry ->
            LinkParser.buildLine(entry.url, entry.rating)
        }
        writeFormattedEntries(uri, normalizedEntries)
    }

    private fun readNormalizedLines(uri: Uri): List<String> {
        val content = LinkParser.normalizeLineEndings(readContent(uri))
        return if (content.isEmpty()) emptyList() else content.split("\n")
    }

    private fun readNormalizedNonEmptyLines(uri: Uri): List<String> {
        return readNormalizedLines(uri)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun writeNormalizedLinesKeepingEntries(uri: Uri, lines: List<String>) {
        val cleanedEntries = lines
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        writeFormattedEntries(uri, cleanedEntries)
    }

    private fun writeFormattedEntries(uri: Uri, entries: List<String>) {
        val normalizedContent = entries.joinToString("\n\n")
        writeContent(uri, normalizedContent)
    }

    private fun writeContent(uri: Uri, content: String) {
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(content)
        } ?: error("Datei konnte nicht geöffnet werden")
    }

    private fun resolveDocumentPath(uri: Uri, fallbackDisplayName: String): String? {
        if (!DocumentsContract.isDocumentUri(context, uri)) return null

        val authority = uri.authority.orEmpty()
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }
            .getOrNull()
            .orEmpty()

        if (documentId.isBlank()) return null

        return when (authority) {
            EXTERNAL_STORAGE_AUTHORITY -> buildExternalStoragePath(documentId, fallbackDisplayName)
            DOWNLOADS_AUTHORITY -> buildDownloadsPath(documentId, fallbackDisplayName)
            MEDIA_AUTHORITY -> buildMediaPath(documentId, fallbackDisplayName)
            else -> buildGenericDocumentPath(documentId, fallbackDisplayName)
        }
    }

    private fun buildExternalStoragePath(
        documentId: String,
        fallbackDisplayName: String
    ): String {
        val decodedDocumentId = Uri.decode(documentId)
        val parts = decodedDocumentId.split(":", limit = 2)

        val volumeId = parts.getOrNull(0).orEmpty()
        val relativePath = parts.getOrNull(1).orEmpty().trim('/')

        val basePath = when (volumeId.lowercase(Locale.ROOT)) {
            "", "primary", "home" -> "Interner Speicher"
            else -> "Speicher $volumeId"
        }

        return when {
            relativePath.isBlank() -> appendPath(basePath, fallbackDisplayName)
            relativePath.endsWith("/$fallbackDisplayName", ignoreCase = true) -> {
                appendPath(basePath, relativePath)
            }
            relativePath.equals(fallbackDisplayName, ignoreCase = true) -> {
                appendPath(basePath, relativePath)
            }
            else -> appendPath(basePath, appendPath(relativePath, fallbackDisplayName))
        }
    }

    private fun buildDownloadsPath(
        documentId: String,
        fallbackDisplayName: String
    ): String {
        val decodedDocumentId = Uri.decode(documentId)

        when {
            decodedDocumentId.startsWith("raw:", ignoreCase = true) -> {
                val rawPath = decodedDocumentId.substringAfter(':')
                return prettifyRawFileSystemPath(rawPath)
            }

            decodedDocumentId.startsWith("/") -> {
                return prettifyRawFileSystemPath(decodedDocumentId)
            }
        }

        val candidatePathPart = decodedDocumentId.substringAfter(':', "").trim('/')

        if (candidatePathPart.contains('/')) {
            return if (candidatePathPart.endsWith("/$fallbackDisplayName", ignoreCase = true)) {
                appendPath("Downloads", candidatePathPart)
            } else {
                appendPath("Downloads", appendPath(candidatePathPart, fallbackDisplayName))
            }
        }

        return appendPath("Downloads", fallbackDisplayName)
    }

    private fun buildMediaPath(
        documentId: String,
        fallbackDisplayName: String
    ): String {
        val mediaType = Uri.decode(documentId).substringBefore(':').lowercase(Locale.ROOT)

        val basePath = when (mediaType) {
            "image" -> "Bilder"
            "video" -> "Videos"
            "audio" -> "Audio"
            else -> "Medien"
        }

        return appendPath(basePath, fallbackDisplayName)
    }

    private fun buildGenericDocumentPath(
        documentId: String,
        fallbackDisplayName: String
    ): String {
        val decodedDocumentId = Uri.decode(documentId)
        val relativePath = decodedDocumentId.substringAfter(':', decodedDocumentId).trim('/')

        return when {
            relativePath.isBlank() -> fallbackDisplayName
            relativePath.equals(fallbackDisplayName, ignoreCase = true) -> relativePath
            relativePath.endsWith("/$fallbackDisplayName", ignoreCase = true) -> relativePath
            else -> appendPath(relativePath, fallbackDisplayName)
        }
    }

    private fun prettifyRawFileSystemPath(rawPath: String): String {
        val normalizedPath = rawPath
            .replace('\\', '/')
            .replace(Regex("/{2,}"), "/")
            .trim()

        if (normalizedPath.isBlank()) {
            return "Unbekannte Datei"
        }

        return when {
            normalizedPath == "/storage/emulated/0" -> "Interner Speicher"
            normalizedPath.startsWith("/storage/emulated/0/") -> {
                appendPath("Interner Speicher", normalizedPath.removePrefix("/storage/emulated/0/"))
            }

            normalizedPath == "/sdcard" -> "Interner Speicher"
            normalizedPath.startsWith("/sdcard/") -> {
                appendPath("Interner Speicher", normalizedPath.removePrefix("/sdcard/"))
            }

            normalizedPath.startsWith("/storage/") -> {
                appendPath("Externer Speicher", normalizedPath.removePrefix("/storage/"))
            }

            else -> normalizedPath
        }
    }

    private fun appendPath(basePath: String, childPath: String): String {
        val left = basePath.trim().trim('/')
        val right = childPath.trim().trim('/')

        return when {
            left.isBlank() -> right
            right.isBlank() -> left
            else -> "$left/$right"
        }
    }

    companion object {
        private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        private const val DOWNLOADS_AUTHORITY = "com.android.providers.downloads.documents"
        private const val MEDIA_AUTHORITY = "com.android.providers.media.documents"
    }
}
