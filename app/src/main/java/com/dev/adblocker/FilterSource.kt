package com.dev.adblocker

/**
 * A user-selectable blocklist source.
 *
 * [key] is the stable string stored in SharedPreferences. Never change an
 * existing key without a migration.
 * [url] is fetched via plain HTTP(S) and parsed by [HostsParser].
 */
data class FilterSource(
    val key: String,
    val displayName: String,
    val description: String,
    val url: String,
    val defaultEnabled: Boolean,
)

/**
 * The static registry of blocklists shipped with the app.
 * Ordered as they appear in the UI.
 */
object FilterSources {

    val ALL: List<FilterSource> = listOf(
        FilterSource(
            key = "steven_black",
            displayName = "Steven Black (unified)",
            description = "A popular blocklist curated from various sources, focusing on adware, malware, and tracking domains. Relatively moderate in size and good for catching common bad actors without being too aggressive.",
            url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            defaultEnabled = true,
        ),
        FilterSource(
            key = "adguard_dns",
            displayName = "AdGuard DNS filter",
            description = "AdGuard's own filter designed to block ads, tracking, malware, and phishing. Well-maintained and specifically tuned for balancing blocking effectiveness with false positive avoidance.",
            url = "https://adguardteam.github.io/HostlistsRegistry/assets/filter_15.txt",
            defaultEnabled = false,
        ),
        FilterSource(
            key = "oisd_full",
            displayName = "OISD full",
            description = "One of the most comprehensive and aggressive blocklists available. Consolidates many sources and blocks not just malware and tracking, but also ads and various unnecessary sites. Significantly larger than others.",
            url = "https://abp.oisd.nl/",
            defaultEnabled = false,
        ),
        FilterSource(
            key = "peter_lowe",
            displayName = "Peter Lowe",
            description = "A blocklist specifically focused on malware and ad-serving domains. Quite minimal and conservative, making it good for combining with other lists without causing too many false positives.",
            url = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
            defaultEnabled = false,
        ),
        FilterSource(
            key = "onehosts_lite",
            displayName = "1Hosts Lite",
            description = "A lighter-weight blocklist that focuses on the most critical blocking without being overly aggressive. Good for balance between security and usability.",
            url = "https://o0.pages.dev/Lite/hosts.txt",
            defaultEnabled = false,
        ),
    )

    fun byKey(key: String): FilterSource? = ALL.firstOrNull { it.key == key }

    fun defaultEnabledKeys(): Set<String> =
        ALL.filter { it.defaultEnabled }.map { it.key }.toSet()
}
