package com.tokama.linkmanager

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceManager

/**
 * Zentrale UI-Einstellungen der App.
 *
 * Diese Klasse kapselt zwei Dinge an einer Stelle:
 * 1. den persistierten Theme-Modus der App
 * 2. die Auflösung des aktuell wirksamen App-Locale-Werts
 *
 * Dadurch greifen MainActivity, SettingsActivity, LinksActivity und das
 * Settings-Fragment auf dieselbe Logik zu und laufen nicht auseinander.
 */
object AppUiSettings {

    const val KEY_THEME_MODE = "app_theme"

    const val THEME_NIGHT = "night"
    const val THEME_DAY = "day"
    const val THEME_AUTO = "auto"

    /**
     * Muss vor dem Inflate der Activity ausgeführt werden, damit das korrekte
     * Day/Night-Theme schon beim ersten Rendern aktiv ist.
     */
    fun applySavedNightMode(context: Context) {
        AppCompatDelegate.setDefaultNightMode(toNightMode(getStoredThemeValue(context)))
    }

    fun getStoredThemeValue(context: Context): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val storedValue = preferences.getString(KEY_THEME_MODE, THEME_NIGHT)
        return normalizeThemeValue(storedValue)
    }

    fun normalizeThemeValue(rawValue: String?): String {
        return when (rawValue?.trim()?.lowercase()) {
            THEME_DAY -> THEME_DAY
            THEME_AUTO -> THEME_AUTO
            else -> THEME_NIGHT
        }
    }

    fun toNightMode(themeValue: String): Int {
        return when (normalizeThemeValue(themeValue)) {
            THEME_DAY -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_AUTO -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_YES
        }
    }

    /**
     * Signatur der aktuell global gesetzten UI-Konfiguration.
     *
     * Diese Signatur wird in Activities gespeichert und bei onResume geprüft.
     * Wenn sich Theme oder App-Sprache im Hintergrund geändert haben,
     * kann die Activity sich damit gezielt neu aufbauen.
     */
    fun buildUiStateSignature(context: Context): String {
        val themeValue = getStoredThemeValue(context)
        val languageValue = resolveCurrentAppLanguageValue(context)
        return "$themeValue|$languageValue"
    }

    fun resolveCurrentAppLanguageValue(context: Context): String {
        val applicationLocales = AppCompatDelegate.getApplicationLocales()
        val firstApplicationLocale = applicationLocales[0]

        val rawLanguageTag = when {
            firstApplicationLocale != null -> firstApplicationLocale.toLanguageTag()
            else -> {
                val configuration = context.resources.configuration
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    configuration.locales.get(0)?.toLanguageTag().orEmpty()
                } else {
                    @Suppress("DEPRECATION")
                    configuration.locale?.toLanguageTag().orEmpty()
                }
            }
        }

        return normalizeLanguageValue(rawLanguageTag)
    }

    fun normalizeLanguageValue(rawValue: String?): String {
        val normalizedValue = rawValue?.trim()?.lowercase().orEmpty()
        return if (normalizedValue.startsWith("de")) "de" else "en"
    }

    fun buildLocaleList(languageValue: String): LocaleListCompat {
        return when (normalizeLanguageValue(languageValue)) {
            "de" -> LocaleListCompat.forLanguageTags("de")
            else -> LocaleListCompat.forLanguageTags("en")
        }
    }

    fun isDarkModeActive(context: Context): Boolean {
        val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMask == Configuration.UI_MODE_NIGHT_YES
    }
}
