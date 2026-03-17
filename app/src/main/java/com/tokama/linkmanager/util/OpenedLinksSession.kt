package com.tokama.linkmanager.util

object OpenedLinksSession {

    private val openedUrls = mutableSetOf<String>()

    fun markOpened(url: String) {
        openedUrls.add(url.trim())
    }

    fun unmarkOpened(url: String) {
        openedUrls.remove(url.trim())
    }

    fun isOpened(url: String): Boolean {
        return openedUrls.contains(url.trim())
    }

    fun getAll(): Set<String> {
        return openedUrls.toSet()
    }

    fun clear() {
        openedUrls.clear()
    }
}