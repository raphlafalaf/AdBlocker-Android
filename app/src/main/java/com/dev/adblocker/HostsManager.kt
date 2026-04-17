package com.dev.adblocker

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Multi-source blocklist manager.
 *
 * Each [FilterSource] has a cache file at `filesDir/hosts_<key>.txt`.
 * The in-memory [blockedSet] is the union of all enabled sources that
 * have a cached file. `isBlocked(domain)` walks subdomains so that
 * blocking `ads.example.com` also covers `tracker.ads.example.com`.
 *
 * Thread-safety: [blockedSet] is replaced atomically on reload. Reads
 * during reload see the old set; writes are serialised by callers
 * (currently a single coroutine on Dispatchers.IO in `downloadAndReload`).
 */
object HostsManager {

    data class SourceStatus(
        val cacheExists: Boolean,
        val cacheLastModified: Long,
        val domainCount: Int,
    )

    data class Status(
        val perSource: Map<String, SourceStatus>,
        val totalDomainCount: Int,
        val enabledCount: Int,
        val lastUpdated: Long,   // max modified time across enabled+cached files, 0 if none
    )

    @Volatile private var blockedSet: Set<String> = emptySet()
    @Volatile private var loaded: Boolean = false

    private fun cacheFile(ctx: Context, key: String): File =
        File(ctx.filesDir, "hosts_$key.txt")

    // ─── Public API ─────────────────────────────────────────────────────────

    fun getStatus(ctx: Context): Status {
        val prefs = PrefsManager(ctx)
        val perSource = FilterSources.ALL.associate { src ->
            val f = cacheFile(ctx, src.key)
            val exists = f.exists() && f.length() > 0
            val count = if (exists) {
                // Lightweight count: read size once cached; avoid reparsing every UI refresh.
                perSourceDomainCount[src.key] ?: countDomainsAndCache(src.key, f)
            } else 0
            src.key to SourceStatus(
                cacheExists = exists,
                cacheLastModified = if (exists) f.lastModified() else 0L,
                domainCount = count,
            )
        }
        val enabledKeys = prefs.getEnabledSourceKeys()
        val total = perSource.filter { it.key in enabledKeys }
            .values.sumOf { it.domainCount }
        val lastUpd = perSource.filter { it.key in enabledKeys }
            .values.maxOfOrNull { it.cacheLastModified } ?: 0L
        return Status(
            perSource = perSource,
            totalDomainCount = total,
            enabledCount = enabledKeys.size,
            lastUpdated = lastUpd,
        )
    }

    @Synchronized
    fun ensureLoaded(ctx: Context) {
        if (loaded) return
        reloadBlockedSet(ctx)
        loaded = true
    }

    /**
     * Downloads the given source keys (or all enabled if [keys] is null),
     * updates their caches, and rebuilds the merged blocked-set.
     *
     * Returns success with the merged domain count if at least one source
     * succeeded, or failure if all targeted sources failed.
     */
    suspend fun downloadAndReload(
        ctx: Context,
        keys: List<String>? = null,
    ): Result<Int> = withContext(Dispatchers.IO) {
        val prefs = PrefsManager(ctx)
        val targets = (keys ?: prefs.getEnabledSourceKeys().toList())
            .mapNotNull { FilterSources.byKey(it) }
        if (targets.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No sources selected"))
        }

        val failures = mutableListOf<String>()
        for (src in targets) {
            try {
                downloadOne(ctx, src)
            } catch (e: Exception) {
                failures += "${src.displayName} (${e.javaClass.simpleName})"
            }
        }

        // Rebuild merged set regardless of individual failures; users may still
        // benefit from the sources that succeeded.
        reloadBlockedSet(ctx)
        loaded = true

        when {
            failures.isEmpty() -> Result.success(blockedSet.size)
            failures.size == targets.size ->
                Result.failure(RuntimeException("All downloads failed: ${failures.joinToString()}"))
            else -> Result.success(blockedSet.size)   // Partial success — UI queries status to surface details.
        }
    }

    /**
     * Checks both the full domain and each parent sub-domain segment.
     * e.g. "tracker.ads.example.com" → tracker.ads.example.com → ads.example.com → example.com
     */
    fun isBlocked(domain: String): Boolean {
        val set = blockedSet
        if (set.isEmpty()) return false
        // trimEnd('.') handles FQDN form (e.g. "ads.example.com.") that some
        // resolvers emit; the cached blocklists never contain the trailing dot.
        val lower = domain.lowercase().trimEnd('.')
        if (lower in set) return true
        var d = lower
        while (true) {
            val dot = d.indexOf('.')
            if (dot < 0 || dot == d.length - 1) return false
            d = d.substring(dot + 1)
            if (d in set) return true
        }
    }

    // ─── Internals ──────────────────────────────────────────────────────────

    // Concurrent — written on IO thread (reloadBlockedSet / downloadOne)
    // and read on the UI thread (getStatus).
    private val perSourceDomainCount: MutableMap<String, Int> = ConcurrentHashMap()

    private fun countDomainsAndCache(key: String, file: File): Int {
        val set = HostsParser.parse(file.readText())
        val n = set.size
        perSourceDomainCount[key] = n
        return n
    }

    private fun reloadBlockedSet(ctx: Context) {
        val prefs = PrefsManager(ctx)
        val enabled = prefs.getEnabledSourceKeys()
        val merged = HashSet<String>()
        for (key in enabled) {
            val f = cacheFile(ctx, key)
            if (!f.exists() || f.length() == 0L) continue
            val parsed = HostsParser.parse(f.readText())
            perSourceDomainCount[key] = parsed.size
            merged.addAll(parsed)
        }
        blockedSet = merged
    }

    private fun downloadOne(ctx: Context, src: FilterSource) {
        val url = URL(src.url)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "AdBlockerAndroid/1.0")
        try {
            conn.connect()
            if (conn.responseCode !in 200..299) {
                throw RuntimeException("HTTP ${conn.responseCode}")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            if (body.isBlank()) throw RuntimeException("Empty response")
            val tmp = File(ctx.filesDir, "hosts_${src.key}.tmp")
            tmp.writeText(body)
            val dest = cacheFile(ctx, src.key)
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) throw RuntimeException("Could not persist cache file")
            // Recount on next access.
            perSourceDomainCount.remove(src.key)
        } finally {
            conn.disconnect()
        }
    }
}
