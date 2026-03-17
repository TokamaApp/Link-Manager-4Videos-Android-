package com.tokama.linkmanager.storage

import android.content.Context
import com.tokama.linkmanager.data.SavedFile
import org.json.JSONArray

/**
 * Persistiert die Merkliste der vom Nutzer gewählten Dateien.
 * Die Reihenfolge ist manuell verschiebbar und wird dauerhaft gespeichert.
 */
class SavedFilesStore(context: Context) {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<SavedFile> {
        val rawJson = preferences.getString(KEY_FILES, "[]") ?: "[]"
        val jsonArray = JSONArray(rawJson)
        val files = mutableListOf<SavedFile>()

        for (index in 0 until jsonArray.length()) {
            files += SavedFile.fromJson(jsonArray.getJSONObject(index))
        }

        val allHaveManualOrder = files.isNotEmpty() && files.all { it.sortOrder != null }

        return if (allHaveManualOrder) {
            files.sortedBy { it.sortOrder }
        } else {
            // Migrationspfad für alte Datenbestände ohne sortOrder.
            files.sortedByDescending { it.addedAt }
        }
    }

    fun add(file: SavedFile): Boolean {
        val currentFiles = getAll().toMutableList()
        if (currentFiles.any { it.uriString == file.uriString }) {
            return false
        }

        // Neue Dateien weiterhin oben einfügen.
        currentFiles.add(0, file.copy(addedAt = System.currentTimeMillis()))
        save(currentFiles)
        return true
    }

    fun remove(uriString: String) {
        val updatedFiles = getAll().filterNot { it.uriString == uriString }
        save(updatedFiles)
    }

    /**
     * Ersetzt einen bestehenden Eintrag, z. B. nach Umbenennen, ohne die Reihenfolge zu verlieren.
     */
    fun update(oldUriString: String, newFile: SavedFile) {
        val currentFiles = getAll().toMutableList()
        val index = currentFiles.indexOfFirst { it.uriString == oldUriString }

        if (index >= 0) {
            currentFiles[index] = newFile
            save(currentFiles)
        }
    }

    /**
     * Persistiert die aktuelle manuelle Reihenfolge nach einem Drag & Drop in MainActivity.
     */
    fun replaceAllInOrder(files: List<SavedFile>) {
        save(files)
    }

    private fun save(files: List<SavedFile>) {
        val normalizedFiles = files.mapIndexed { index, file ->
            file.copy(sortOrder = index)
        }

        val jsonArray = JSONArray()
        normalizedFiles.forEach { jsonArray.put(it.toJson()) }

        preferences.edit()
            .putString(KEY_FILES, jsonArray.toString())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "saved_files_store"
        private const val KEY_FILES = "files"
    }
}
