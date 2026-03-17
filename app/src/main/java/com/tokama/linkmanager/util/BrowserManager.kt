package com.tokama.linkmanager.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.tokama.linkmanager.R

object BrowserManager {

    private const val PREFS_NAME = "browser_settings"
    private const val KEY_SELECTED_BROWSER = "selected_browser"

    private const val CHROME_PACKAGE = "com.android.chrome"
    private const val FIREFOX_PACKAGE = "org.mozilla.firefox"
    private const val BRAVE_PACKAGE = "com.brave.browser"
    private const val EDGE_PACKAGE = "com.microsoft.emmx"
    private const val SAMSUNG_INTERNET_PACKAGE = "com.sec.android.app.sbrowser"
    private const val VIVALDI_PACKAGE = "com.vivaldi.browser"
    private const val OPERA_PACKAGE = "com.opera.browser"
    private const val DUCKDUCKGO_PACKAGE = "com.duckduckgo.mobile.android"
    private const val TOR_PACKAGE = "org.torproject.torbrowser"

    private const val CHROME_INCOGNITO_EXTRA =
        "com.google.android.apps.chrome.EXTRA_OPEN_NEW_INCOGNITO_TAB"

    /**
     * Für Firefox privat historisch verwendet, aber nicht als stabile öffentliche API dokumentiert.
     * Deshalb in der App nur als experimentell behandeln.
     */
    private const val FIREFOX_PRIVATE_EXTRA = "is_private_tab"

    private const val OPTION_CHROME_NORMAL = "chrome_normal"
    private const val OPTION_CHROME_INCOGNITO = "chrome_incognito"
    private const val OPTION_FIREFOX_NORMAL = "firefox_normal"
    private const val OPTION_FIREFOX_PRIVATE = "firefox_private"
    private const val OPTION_BRAVE_NORMAL = "brave_normal"
    private const val OPTION_EDGE_NORMAL = "edge_normal"
    private const val OPTION_SAMSUNG_INTERNET_NORMAL = "samsung_internet_normal"
    private const val OPTION_VIVALDI_NORMAL = "vivaldi_normal"
    private const val OPTION_OPERA_NORMAL = "opera_normal"
    private const val OPTION_DUCKDUCKGO_NORMAL = "duckduckgo_normal"
    private const val OPTION_TOR = "tor_browser"

    data class BrowserOption(
        val id: String,
        val packageName: String,
        val label: String,
        val mode: BrowserMode
    )

    enum class BrowserMode {
        NORMAL,
        PRIVATE_EXPERIMENTAL,
        TOR
    }

    fun getSupportedBrowsers(context: Context): List<BrowserOption> {
        val options = mutableListOf<BrowserOption>()

        if (isPackageInstalled(context, CHROME_PACKAGE)) {
            options += BrowserOption(
                id = OPTION_CHROME_NORMAL,
                packageName = CHROME_PACKAGE,
                label = context.getString(R.string.browser_chrome_normal),
                mode = BrowserMode.NORMAL
            )
            options += BrowserOption(
                id = OPTION_CHROME_INCOGNITO,
                packageName = CHROME_PACKAGE,
                label = context.getString(R.string.browser_chrome_incognito_experimental),
                mode = BrowserMode.PRIVATE_EXPERIMENTAL
            )
        }

        if (isPackageInstalled(context, FIREFOX_PACKAGE)) {
            options += BrowserOption(
                id = OPTION_FIREFOX_NORMAL,
                packageName = FIREFOX_PACKAGE,
                label = context.getString(R.string.browser_firefox_normal),
                mode = BrowserMode.NORMAL
            )
            options += BrowserOption(
                id = OPTION_FIREFOX_PRIVATE,
                packageName = FIREFOX_PACKAGE,
                label = context.getString(R.string.browser_firefox_private_experimental),
                mode = BrowserMode.PRIVATE_EXPERIMENTAL
            )
        }

        if (isPackageInstalled(context, BRAVE_PACKAGE)) {
            options += BrowserOption(
                id = OPTION_BRAVE_NORMAL,
                packageName = BRAVE_PACKAGE,
                label = context.getString(R.string.browser_brave_normal),
                mode = BrowserMode.NORMAL
            )
        }

        if (isPackageInstalled(context, EDGE_PACKAGE)) {
            options += BrowserOption(
                id = OPTION_EDGE_NORMAL,
                packageName = EDGE_PACKAGE,
                label = context.getString(R.string.browser_edge_normal),
                mode = BrowserMode.NORMAL
            )
        }

        if (isPackageInstalled(context, SAMSUNG_INTERNET_PACKAGE)) {
            options += BrowserOption(
                id = OPTION_SAMSUNG_INTERNET_NORMAL,
                packageName = SAMSUNG_INTERNET_PACKAGE,
                label = context.getString(R.string.browser_samsung_internet_normal),
                mode = BrowserMode.NORMAL
            )
        }

        if (isPackageInstalled(context, VIVALDI_PACKAGE)) {
            options += BrowserOption(
                id = OPTION_VIVALDI_NORMAL,
                packageName = VIVALDI_PACKAGE,
                label = context.getString(R.string.browser_vivaldi_normal),
                mode = BrowserMode.NORMAL
            )
        }

        if (isPackageInstalled(context, OPERA_PACKAGE)) {
            options += BrowserOption(
                id = OPTION_OPERA_NORMAL,
                packageName = OPERA_PACKAGE,
                label = context.getString(R.string.browser_opera_normal),
                mode = BrowserMode.NORMAL
            )
        }

        if (isPackageInstalled(context, DUCKDUCKGO_PACKAGE)) {
            options += BrowserOption(
                id = OPTION_DUCKDUCKGO_NORMAL,
                packageName = DUCKDUCKGO_PACKAGE,
                label = context.getString(R.string.browser_duckduckgo_normal),
                mode = BrowserMode.NORMAL
            )
        }

        if (isPackageInstalled(context, TOR_PACKAGE)) {
            options += BrowserOption(
                id = OPTION_TOR,
                packageName = TOR_PACKAGE,
                label = context.getString(R.string.browser_tor),
                mode = BrowserMode.TOR
            )
        }

        return options
    }

    fun getSelectedBrowserId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_SELECTED_BROWSER, null)
        val supportedIds = getSupportedBrowsers(context).map { it.id }

        return when {
            stored != null && supportedIds.contains(stored) -> stored
            supportedIds.contains(OPTION_CHROME_NORMAL) -> OPTION_CHROME_NORMAL
            supportedIds.contains(OPTION_FIREFOX_NORMAL) -> OPTION_FIREFOX_NORMAL
            supportedIds.contains(OPTION_BRAVE_NORMAL) -> OPTION_BRAVE_NORMAL
            supportedIds.contains(OPTION_EDGE_NORMAL) -> OPTION_EDGE_NORMAL
            supportedIds.contains(OPTION_SAMSUNG_INTERNET_NORMAL) -> OPTION_SAMSUNG_INTERNET_NORMAL
            supportedIds.contains(OPTION_VIVALDI_NORMAL) -> OPTION_VIVALDI_NORMAL
            supportedIds.contains(OPTION_OPERA_NORMAL) -> OPTION_OPERA_NORMAL
            supportedIds.contains(OPTION_DUCKDUCKGO_NORMAL) -> OPTION_DUCKDUCKGO_NORMAL
            supportedIds.contains(OPTION_TOR) -> OPTION_TOR
            supportedIds.isNotEmpty() -> supportedIds.first()
            else -> null
        }
    }

    fun getSelectedBrowserLabel(context: Context): String {
        val selectedId = getSelectedBrowserId(context)
        return getSupportedBrowsers(context)
            .firstOrNull { it.id == selectedId }
            ?.label
            ?: context.getString(R.string.no_supported_browser)
    }

    fun saveSelectedBrowser(context: Context, browserId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_BROWSER, browserId)
            .apply()
    }

    fun openLink(context: Context, url: String) {
        when (getSelectedBrowserId(context)) {
            OPTION_CHROME_NORMAL -> {
                if (openInSpecificBrowser(context, url, CHROME_PACKAGE)) return
            }

            OPTION_CHROME_INCOGNITO -> {
                if (openChromeIncognito(context, url)) return
                if (openInSpecificBrowser(context, url, CHROME_PACKAGE)) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.incognito_not_supported_fallback),
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
            }

            OPTION_FIREFOX_NORMAL -> {
                if (openInSpecificBrowser(context, url, FIREFOX_PACKAGE)) return
            }

            OPTION_FIREFOX_PRIVATE -> {
                if (openFirefoxPrivate(context, url)) return
                if (openInSpecificBrowser(context, url, FIREFOX_PACKAGE)) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.firefox_private_not_supported_fallback),
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
            }

            OPTION_BRAVE_NORMAL -> {
                if (openInSpecificBrowser(context, url, BRAVE_PACKAGE)) return
            }

            OPTION_EDGE_NORMAL -> {
                if (openInSpecificBrowser(context, url, EDGE_PACKAGE)) return
            }

            OPTION_SAMSUNG_INTERNET_NORMAL -> {
                if (openInSpecificBrowser(context, url, SAMSUNG_INTERNET_PACKAGE)) return
            }

            OPTION_VIVALDI_NORMAL -> {
                if (openInSpecificBrowser(context, url, VIVALDI_PACKAGE)) return
            }

            OPTION_OPERA_NORMAL -> {
                if (openInSpecificBrowser(context, url, OPERA_PACKAGE)) return
            }

            OPTION_DUCKDUCKGO_NORMAL -> {
                if (openInSpecificBrowser(context, url, DUCKDUCKGO_PACKAGE)) return
            }

            OPTION_TOR -> {
                if (openInSpecificBrowser(context, url, TOR_PACKAGE)) return
            }
        }

        openNormalBrowser(context, url)
        Toast.makeText(
            context,
            context.getString(R.string.open_normal_browser_fallback),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun openChromeIncognito(context: Context, url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage(CHROME_PACKAGE)
                putExtra(CHROME_INCOGNITO_EXTRA, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun openFirefoxPrivate(context: Context, url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage(FIREFOX_PACKAGE)
                putExtra(FIREFOX_PRIVATE_EXTRA, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun openInSpecificBrowser(context: Context, url: String, packageName: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun openNormalBrowser(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}
