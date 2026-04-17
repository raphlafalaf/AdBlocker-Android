package com.raphaelcabon.adblocker

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/**
 * PrefsManager — persists the user's custom whitelist and blacklist,
 * plus the "was VPN running when app was last closed" flag.
 *
 * Whitelist:  domains the user always wants to reach (overrides the Steven Black list).
 * Blacklist:  extra domains the user wants to block (in addition to the hosts list).
 *
 * Domains are stored as a Set<String> in SharedPreferences.
 * All lookups normalise to lowercase so casing never matters.
 */
class PrefsManager(context: Context) {

    companion object {
        private const val PREFS_NAME   = "adblock_prefs"
        private const val KEY_WHITELIST = "whitelist"
        private const val KEY_BLACKLIST = "blacklist"
        private const val KEY_VPN_WAS_RUNNING = "vpn_was_running"
        private const val KEY_ENABLED_SOURCES = "enabled_sources"
        private const val KEY_THEME_MODE = "theme_mode"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Whitelist ─────────────────────────────────────────────────────────────

    fun getWhitelist(): Set<String> =
        prefs.getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()

    fun addToWhitelist(domain: String) {
        val updated = getWhitelist().toMutableSet().apply { add(domain.lowercase().trim()) }
        prefs.edit().putStringSet(KEY_WHITELIST, updated).apply()
    }

    fun removeFromWhitelist(domain: String) {
        val updated = getWhitelist().toMutableSet().apply { remove(domain.lowercase().trim()) }
        prefs.edit().putStringSet(KEY_WHITELIST, updated).apply()
    }

    /**
     * Returns true if [domain] or any parent is whitelisted.
     * e.g. whitelisting "example.com" also allows "www.example.com".
     */
    fun isWhitelisted(domain: String): Boolean {
        val list = getWhitelist()
        if (list.isEmpty()) return false
        var d = domain.lowercase().trim()
        while (d.isNotEmpty()) {
            if (list.contains(d)) return true
            val dot = d.indexOf('.')
            if (dot == -1) break
            d = d.substring(dot + 1)
        }
        return false
    }

    // ── Blacklist ─────────────────────────────────────────────────────────────

    fun getBlacklist(): Set<String> =
        prefs.getStringSet(KEY_BLACKLIST, emptySet()) ?: emptySet()

    fun addToBlacklist(domain: String) {
        val updated = getBlacklist().toMutableSet().apply { add(domain.lowercase().trim()) }
        prefs.edit().putStringSet(KEY_BLACKLIST, updated).apply()
    }

    fun removeFromBlacklist(domain: String) {
        val updated = getBlacklist().toMutableSet().apply { remove(domain.lowercase().trim()) }
        prefs.edit().putStringSet(KEY_BLACKLIST, updated).apply()
    }

    fun isBlacklisted(domain: String): Boolean {
        val list = getBlacklist()
        if (list.isEmpty()) return false
        var d = domain.lowercase().trim()
        while (d.isNotEmpty()) {
            if (list.contains(d)) return true
            val dot = d.indexOf('.')
            if (dot == -1) break
            d = d.substring(dot + 1)
        }
        return false
    }

    // ── VPN state persistence ─────────────────────────────────────────────────

    var vpnWasRunning: Boolean
        get()      = prefs.getBoolean(KEY_VPN_WAS_RUNNING, false)
        set(value) = prefs.edit().putBoolean(KEY_VPN_WAS_RUNNING, value).apply()

    // ── Enabled blocklist sources ─────────────────────────────────────────────

    /**
     * Returns the set of source keys the user has enabled. On first launch
     * after update (no prefs entry yet), seeds with the built-in default set
     * so existing users continue to see Steven Black active.
     */
    fun getEnabledSourceKeys(): Set<String> {
        val stored = prefs.getStringSet(KEY_ENABLED_SOURCES, null)
        if (stored != null) return HashSet(stored)  // defensive copy
        // Seed the default and persist it so future reads don't hit this path.
        val defaults = FilterSources.defaultEnabledKeys()
        prefs.edit().putStringSet(KEY_ENABLED_SOURCES, defaults).apply()
        return HashSet(defaults)
    }

    fun setEnabledSourceKeys(keys: Set<String>) {
        // Defensive copy: matches the read-side pattern in getEnabledSourceKeys()
        // and avoids storing a caller-mutable reference inside SharedPreferences.
        prefs.edit().putStringSet(KEY_ENABLED_SOURCES, HashSet(keys)).apply()
    }

    fun isSourceEnabled(key: String): Boolean = key in getEnabledSourceKeys()

    // ── Theme mode ────────────────────────────────────────────────────────────

    /**
     * Stored as an AppCompatDelegate.MODE_NIGHT_* int constant.
     * Default = MODE_NIGHT_FOLLOW_SYSTEM (respect the OS setting).
     * Accepted writes: MODE_NIGHT_FOLLOW_SYSTEM (-1), MODE_NIGHT_NO (1), MODE_NIGHT_YES (2).
     */
    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        set(value) = prefs.edit().putInt(KEY_THEME_MODE, value).apply()
}
