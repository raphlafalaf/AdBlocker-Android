# AdBlocker Android — Glassmorphism Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the Android-14 "stays OFF" broadcast bug, add live per-second confirmation counter, redesign the UI with emerald glassmorphism, ship a new shield-with-slash launcher icon, and let the user enable multiple blocklist sources (Steven Black + AdGuard + OISD + Peter Lowe + 1Hosts Lite).

**Architecture:** Keep all DNS/packet logic intact. Fix the broadcast bug with `setPackage`. Add a periodic coroutine broadcast for live counter. Refactor `HostsManager` from single-source to multi-source (per-source cache files, merged in-memory set). Rewrite `activity_main.xml` around glass `MaterialCardView`s on a full-bleed gradient. Build a custom `StatusRingView` for the hero. Swap the launcher icon for a vector shield-with-slash.

**Tech Stack:** Kotlin, Android SDK 26–34, AndroidX/Material3 components already in the project, Kotlin coroutines already present. No new dependencies except JUnit4 as `testImplementation` for the one pure-logic unit test.

**Project state notes:**
- Project root: the AdBlocker Android folder.
- The project is **not** currently a git repository. The plan below uses "save checkpoints" (no git commits). If the engineer wants git, they may `git init` first; otherwise, ignore commit-style language.
- Spec reference: `docs/superpowers/specs/2026-04-17-glassmorphism-redesign-design.md`.

---

## File Structure

**New Kotlin files (app/src/main/java/com/raphaelcabon/adblocker/):**
- `FilterSource.kt` — data model + static registry of the 5 shipped blocklists.
- `HostsParser.kt` — pure function turning a raw hosts-file string into `Set<String>`.
- `StatusRingView.kt` — custom `View` drawing the animated ring + glyph/number.
- `GlassCardUtil.kt` — helper for applying `RenderEffect` blur on SDK 31+.
- `DomainChipAdapter.kt` — `ArrayAdapter<String>` rendering chip-row list items.

**Modified Kotlin files:**
- `AdBlockVpnService.kt` — add `setPackage` to broadcasts, add periodic counter coroutine.
- `HostsManager.kt` — multi-source refactor.
- `PrefsManager.kt` — enabled-sources API.
- `MainActivity.kt` — full rewrite of UI wiring (same responsibilities, new layout bindings + filter-sources + live counter).

**New unit-test files (app/src/test/java/com/raphaelcabon/adblocker/):**
- `HostsParserTest.kt` — JVM JUnit test for the parser.

**Modified Gradle:**
- `app/build.gradle` — add `testImplementation 'junit:junit:4.13.2'`.

**New/modified resources (app/src/main/res/):**
- `values/colors.xml` — replaced with new palette.
- `values-night/colors.xml` — new, dark palette.
- `values/themes.xml` — updated.
- `values-night/themes.xml` — new, dark theme.
- `values/styles.xml` — new, glass card + chip styles.
- `drawable/bg_gradient.xml` — emerald→midnight gradient.
- `drawable/bg_chip.xml` — translucent chip background.
- `drawable/ic_shield_slash.xml` — shield-with-slash vector (64dp source, scalable).
- `drawable/ic_shield_slash_notif.xml` — 24dp version for notification.
- `drawable/ic_close.xml` — × icon.
- `drawable/ic_add.xml` — + icon.
- `drawable/ic_launcher_foreground.xml` — replace.
- `drawable/ic_launcher_background.xml` — replace.
- `drawable/ic_launcher_monochrome.xml` — new, for themed icons.
- `mipmap-anydpi-v26/ic_launcher.xml` — add monochrome layer reference.
- `mipmap-anydpi-v26/ic_launcher_round.xml` — add monochrome layer reference.
- `layout/activity_main.xml` — full rewrite.
- `layout/item_domain_chip.xml` — new, whitelist/blacklist row.
- `layout/item_filter_source.xml` — new, filter-source checkbox row.

---

## Tasks

1. Fix the "stays OFF" broadcast bug
2. Add JUnit test infrastructure
3. Create `HostsParser` + unit test (TDD)
4. Create `FilterSource` data model + registry
5. Refactor `HostsManager` to multi-source
6. Update `PrefsManager` with enabled-sources API
7. Add periodic blocked-count broadcast
8. New colour palette (light + dark)
9. Gradient background drawable
10. Updated themes + glass card style
11. `GlassCardUtil` blur helper
12. `ic_shield_slash` vector drawable
13. `StatusRingView` custom view
14. Chip row layout + background + `DomainChipAdapter`
15. Filter source row layout
16. Icon helpers (`ic_close`, `ic_add`)
17. New launcher icon (foreground/background/monochrome) + mipmap wiring
18. New notification small icon
19. Full `activity_main.xml` rewrite
20. `MainActivity` rewrite
21. Update notification to show live count
22. Manual verification + build

Each task is self-contained and compiles on its own. After tasks 1 + 2 + 7, the bug is fixed (a partial ship). After task 22, everything is in.

---

### Task 1: Fix the "stays OFF" broadcast bug

**Files:**
- Modify: `app/src/main/java/com/raphaelcabon/adblocker/AdBlockVpnService.kt` (the `broadcastStatus` function, currently at the bottom of the file)

- [ ] **Step 1: Replace `broadcastStatus` to pin the package on the Intent**

Find the existing function:

```kotlin
    private fun broadcastStatus(isRunning: Boolean) {
        sendBroadcast(Intent(BROADCAST_STATUS).apply {
            putExtra(EXTRA_IS_RUNNING, isRunning)
            putExtra(EXTRA_BLOCKED_COUNT, blockedCount)
        })
    }
```

Replace with:

```kotlin
    private fun broadcastStatus(isRunning: Boolean) {
        // setPackage is REQUIRED on Android 14+: RECEIVER_NOT_EXPORTED receivers
        // silently drop implicit broadcasts. Without this, MainActivity never
        // learns the VPN is running and the UI stays stuck on "OFF".
        sendBroadcast(Intent(BROADCAST_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_IS_RUNNING, isRunning)
            putExtra(EXTRA_BLOCKED_COUNT, blockedCount)
        })
    }
```

- [ ] **Step 2: Verify the change compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. No other files should need changes for this step.

- [ ] **Step 3: Manual verification of fix (after install)**

On a device running Android 14:
1. Install the app (`./gradlew installDebug`).
2. Open it. Tap **Start**. Grant the VPN permission.
3. Status card should switch to "Ad blocking is ON" with a green indicator.
4. A persistent "Ad Blocker Active" notification should appear in the status bar.
5. Tap **Stop**. Status should switch back to "OFF".

If the UI now reflects state correctly, the bug is fixed. (The live counter from Task 7 is a separate concern.)

- [ ] **Step 4: Save checkpoint**

No git; just note in a running changelog that Task 1 is complete. If using git: `git add . && git commit -m "fix: pin package on VPN status broadcast (Android 14)"`.

---


### Task 2: Add JUnit test infrastructure

**Files:**
- Modify: `app/build.gradle`

- [ ] **Step 1: Add JUnit to `testImplementation`**

In `app/build.gradle`, inside the `dependencies { ... }` block, add this line immediately before the closing `}`:

```groovy
    testImplementation 'junit:junit:4.13.2'
```

The final block should look like:

```groovy
dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'

    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'

    testImplementation 'junit:junit:4.13.2'
}
```

- [ ] **Step 2: Create the test source directory**

The tests will live at `app/src/test/java/com/raphaelcabon/adblocker/`. Create it:

```bash
mkdir -p app/src/test/java/com/raphaelcabon/adblocker
```

- [ ] **Step 3: Gradle sync to pick up the new dependency**

Run: `./gradlew :app:dependencies --configuration testDebugRuntimeClasspath | grep junit`
Expected: output contains `junit:junit:4.13.2`.

- [ ] **Step 4: Save checkpoint**

---


### Task 3: Create `HostsParser` + unit test (TDD)

The parser is pure: raw hosts-file text → `Set<String>` of lowercased domains.
Hosts format lines look like `0.0.0.0 ads.example.com` or `127.0.0.1 tracker.example.net`. Comments start with `#`. Blank lines and inline `localhost` entries (`0.0.0.0 localhost`) must be skipped.

**Files:**
- Create: `app/src/test/java/com/raphaelcabon/adblocker/HostsParserTest.kt`
- Create: `app/src/main/java/com/raphaelcabon/adblocker/HostsParser.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/raphaelcabon/adblocker/HostsParserTest.kt`:

```kotlin
package com.raphaelcabon.adblocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostsParserTest {

    @Test fun parsesStandardHostsLines() {
        val input = """
            0.0.0.0 ads.example.com
            0.0.0.0 tracker.example.net
        """.trimIndent()
        val result = HostsParser.parse(input)
        assertEquals(setOf("ads.example.com", "tracker.example.net"), result)
    }

    @Test fun ignoresCommentsAndBlankLines() {
        val input = """
            # This is a comment
            0.0.0.0 ads.example.com

            ! Another style comment
            0.0.0.0 tracker.example.net
        """.trimIndent()
        val result = HostsParser.parse(input)
        assertEquals(setOf("ads.example.com", "tracker.example.net"), result)
    }

    @Test fun skipsLocalhostEntries() {
        val input = """
            0.0.0.0 localhost
            127.0.0.1 localhost
            0.0.0.0 localhost.localdomain
            0.0.0.0 ads.example.com
        """.trimIndent()
        val result = HostsParser.parse(input)
        assertEquals(setOf("ads.example.com"), result)
    }

    @Test fun lowercasesDomains() {
        val input = "0.0.0.0 Ads.Example.COM"
        val result = HostsParser.parse(input)
        assertTrue(result.contains("ads.example.com"))
        assertFalse(result.contains("Ads.Example.COM"))
    }

    @Test fun handlesAdblockPlainDomainLines() {
        // Some lists omit the IP prefix and just list domains.
        val input = """
            ads.example.com
            tracker.example.net
        """.trimIndent()
        val result = HostsParser.parse(input)
        assertEquals(setOf("ads.example.com", "tracker.example.net"), result)
    }

    @Test fun ignoresMalformedLines() {
        val input = """
            0.0.0.0
            this is not a domain line with spaces
            0.0.0.0 valid.example.com
        """.trimIndent()
        val result = HostsParser.parse(input)
        assertEquals(setOf("valid.example.com"), result)
    }

    @Test fun emptyInputYieldsEmptySet() {
        assertTrue(HostsParser.parse("").isEmpty())
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.raphaelcabon.adblocker.HostsParserTest"`
Expected: **FAIL**. The compiler will complain that `HostsParser` is unresolved.

- [ ] **Step 3: Implement `HostsParser`**

Create `app/src/main/java/com/raphaelcabon/adblocker/HostsParser.kt`:

```kotlin
package com.raphaelcabon.adblocker

/**
 * Pure parser — no Android dependencies.
 *
 * Accepts both "IP  domain" hosts-format lines and bare-domain lines
 * (some blocklists publish domain-only files). Strips comments (#, !),
 * blank lines, and common localhost entries. Lowercases everything.
 */
object HostsParser {

    private val LOCALHOST_NAMES = setOf(
        "localhost",
        "localhost.localdomain",
        "local",
        "broadcasthost",
        "ip6-localhost",
        "ip6-loopback",
        "ip6-localnet",
        "ip6-mcastprefix",
        "ip6-allnodes",
        "ip6-allrouters",
        "ip6-allhosts",
    )

    private val DOMAIN_REGEX =
        Regex("^[a-z0-9]([a-z0-9\\-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9\\-]{0,61}[a-z0-9])?)+$")

    fun parse(text: String): Set<String> {
        val out = HashSet<String>(text.length / 32)  // rough sizing hint
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#") || line.startsWith("!")) continue

            // Split on any whitespace. Expect either one token (bare domain)
            // or two+ tokens where the domain is the second.
            val parts = line.split(Regex("\\s+"))
            val candidate = when (parts.size) {
                1 -> parts[0]
                else -> parts[1]
            }.lowercase()

            if (candidate in LOCALHOST_NAMES) continue
            if (!DOMAIN_REGEX.matches(candidate)) continue

            out.add(candidate)
        }
        return out
    }
}
```

- [ ] **Step 4: Run the test to confirm it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.raphaelcabon.adblocker.HostsParserTest"`
Expected: **PASS**, all 7 tests green.

- [ ] **Step 5: Save checkpoint**

---


### Task 4: Create `FilterSource` data model + registry

**Files:**
- Create: `app/src/main/java/com/raphaelcabon/adblocker/FilterSource.kt`

- [ ] **Step 1: Create the data model + registry**

Create `app/src/main/java/com/raphaelcabon/adblocker/FilterSource.kt`:

```kotlin
package com.raphaelcabon.adblocker

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
            description = "Classic ads + trackers + malware (~130k domains)",
            url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            defaultEnabled = true,
        ),
        FilterSource(
            key = "adguard_dns",
            displayName = "AdGuard DNS filter",
            description = "AdGuard's curated DNS-level list",
            url = "https://adguardteam.github.io/HostlistsRegistry/assets/filter_15.txt",
            defaultEnabled = false,
        ),
        FilterSource(
            key = "oisd_full",
            displayName = "OISD full",
            description = "Comprehensive, low false-positive list",
            url = "https://big.oisd.nl/hosts",
            defaultEnabled = false,
        ),
        FilterSource(
            key = "peter_lowe",
            displayName = "Peter Lowe",
            description = "Classic ad-servers + trackers list",
            url = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
            defaultEnabled = false,
        ),
        FilterSource(
            key = "onehosts_lite",
            displayName = "1Hosts Lite",
            description = "Small, fast-loading general list",
            url = "https://o0.pages.dev/Lite/hosts.txt",
            defaultEnabled = false,
        ),
    )

    fun byKey(key: String): FilterSource? = ALL.firstOrNull { it.key == key }

    fun defaultEnabledKeys(): Set<String> =
        ALL.filter { it.defaultEnabled }.map { it.key }.toSet()
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Save checkpoint**

---


### Task 5: Refactor `HostsManager` to multi-source

Before starting this task, read the current `HostsManager.kt` — you will replace it entirely. Its existing public surface (`getStatus`, `ensureLoaded`, `downloadAndReload`, `isBlocked`) is referenced by `AdBlockVpnService.kt` and `MainActivity.kt`. Keep `isBlocked(domain: String): Boolean` identical; everything else changes shape.

**Files:**
- Replace: `app/src/main/java/com/raphaelcabon/adblocker/HostsManager.kt`

- [ ] **Step 1: Replace `HostsManager.kt` with the multi-source version**

Overwrite `app/src/main/java/com/raphaelcabon/adblocker/HostsManager.kt` with:

```kotlin
package com.raphaelcabon.adblocker

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

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
     * Returns the merged domain count on success, or failure carrying a
     * summary message like "2 of 4 sources failed".
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

        if (failures.isEmpty()) {
            Result.success(blockedSet.size)
        } else if (failures.size == targets.size) {
            Result.failure(RuntimeException("All downloads failed: ${failures.joinToString()}"))
        } else {
            // Partial success — still return the size but with a message for the UI via exception chain.
            Result.success(blockedSet.size).also {
                // We attach no exception here; MainActivity queries status to surface partials.
            }
        }
    }

    /**
     * Checks both the full domain and each parent sub-domain segment.
     * e.g. "tracker.ads.example.com" → tracker.ads.example.com → ads.example.com → example.com
     */
    fun isBlocked(domain: String): Boolean {
        val set = blockedSet
        if (set.isEmpty()) return false
        val lower = domain.lowercase()
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

    private val perSourceDomainCount: MutableMap<String, Int> = HashMap()

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
```

- [ ] **Step 2: Update the three existing call sites that pass old parameters**

`AdBlockVpnService.kt` already calls `HostsManager.ensureLoaded(applicationContext)` and `HostsManager.isBlocked(domain)` — these signatures are unchanged, nothing to edit here.

`MainActivity.kt` currently calls `HostsManager.getStatus(this)` and `HostsManager.downloadAndReload(applicationContext)` (which used to take no keys argument). **The old call with no keys is preserved — it now means "download all enabled sources."** So no edit is needed at this task's boundary; the full `MainActivity` rewrite in Task 20 will use the new API explicitly.

`MainActivity.kt` also reads properties like `status.cacheExists`, `status.cacheLastModified`, `status.domainCount` — these moved into the `SourceStatus` per-source map. Do **not** modify `MainActivity.kt` in this task; it will be rewritten in Task 20. For now, ignore the compile errors you will see in `MainActivity.kt` — you can short-circuit them by temporarily stubbing if you must run `installDebug`, but the recommended flow is: after Task 5 your build will not succeed, and Task 20 restores it. If stepwise buildability matters to the executing engineer, they can skip running `installDebug` between Task 5 and Task 20.

- [ ] **Step 3: Verify Kotlin compiles (excluding MainActivity)**

Run: `./gradlew :app:compileDebugKotlin` — expect failures pointing only to `MainActivity.kt` for the status field references. This is expected; they will be fixed in Task 20. If compile errors appear in any other file, something is wrong — re-check this task.

- [ ] **Step 4: Save checkpoint**

---


### Task 6: Update `PrefsManager` with enabled-sources API

**Files:**
- Modify: `app/src/main/java/com/raphaelcabon/adblocker/PrefsManager.kt`

Before editing, read the existing `PrefsManager.kt` to see its style. Add the methods below without disturbing existing ones.

- [ ] **Step 1: Add enabled-sources API to `PrefsManager`**

Open `app/src/main/java/com/raphaelcabon/adblocker/PrefsManager.kt` and add (inside the existing class body) these members:

```kotlin
    // ── Enabled blocklist sources ─────────────────────────────────────────

    private companion object {
        const val KEY_ENABLED_SOURCES = "enabled_sources"
    }

    /**
     * Returns the set of source keys the user has enabled. On first launch
     * after update (no prefs entry yet), seeds with the built-in default set
     * so existing users continue to see Steven Black active.
     */
    fun getEnabledSourceKeys(): Set<String> {
        val stored = sharedPrefs.getStringSet(KEY_ENABLED_SOURCES, null)
        if (stored != null) return HashSet(stored)  // defensive copy
        // Seed the default and persist it so future reads don't hit this path.
        val defaults = FilterSources.defaultEnabledKeys()
        sharedPrefs.edit().putStringSet(KEY_ENABLED_SOURCES, defaults).apply()
        return HashSet(defaults)
    }

    fun setEnabledSourceKeys(keys: Set<String>) {
        sharedPrefs.edit().putStringSet(KEY_ENABLED_SOURCES, keys).apply()
    }

    fun isSourceEnabled(key: String): Boolean = key in getEnabledSourceKeys()
```

If `PrefsManager` doesn't already expose a `sharedPrefs` field, rename the reference to whatever the existing class uses (likely `prefs` or similar). Do not create a duplicate SharedPreferences instance.

- [ ] **Step 2: Verify `PrefsManager` compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL` except for any pre-existing errors from Task 5 in `MainActivity.kt`. No new errors in `PrefsManager.kt`.

- [ ] **Step 3: Save checkpoint**

---


### Task 7: Add periodic blocked-count broadcast

**Files:**
- Modify: `app/src/main/java/com/raphaelcabon/adblocker/AdBlockVpnService.kt`

- [ ] **Step 1: Add the counter coroutine fields + lifecycle wiring**

In `AdBlockVpnService.kt`, near the top of the class (just after `@Volatile var blockedCount = 0L`), add:

```kotlin
    private var counterJob: kotlinx.coroutines.Job? = null
```

Then inside `startVpn()`, immediately after the line `broadcastStatus(true)`, add:

```kotlin
        // Periodic live-count broadcast so the UI and the notification can
        // show a growing "N queries blocked" confirmation.
        counterJob = ioScope.launch {
            while (running) {
                broadcastStatus(true)
                updateNotification()
                kotlinx.coroutines.delay(2000)
            }
        }
```

Inside `stopVpn()`, immediately after `running = false`, add:

```kotlin
        counterJob?.cancel()
        counterJob = null
```

- [ ] **Step 2: Add `updateNotification()` helper**

Inside the class, alongside `buildNotification`, add:

```kotlin
    private fun updateNotification() {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIF_ID, buildNotification(running))
    }
```

- [ ] **Step 3: Update `buildNotification` to include the live count**

Find the existing `buildNotification(running: Boolean)` function. Replace its `.setContentText("Blocking ads system-wide via DNS")` line with:

```kotlin
            .setContentText(
                if (running) "Blocking active — ${"%,d".format(blockedCount)} queries filtered"
                else "Ad blocker stopped"
            )
```

- [ ] **Step 4: Verify compilation (still expect MainActivity errors pending Task 20)**

Run: `./gradlew :app:compileDebugKotlin` — expect only the pre-existing `MainActivity.kt` errors from Task 5. No new errors.

- [ ] **Step 5: Save checkpoint**

---


### Task 8: New colour palette (light + dark)

**Files:**
- Replace: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values-night/colors.xml`

- [ ] **Step 1: Replace `values/colors.xml` with the light palette**

Overwrite `app/src/main/res/values/colors.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Gradient background -->
    <color name="gradient_top">#A8D5BA</color>
    <color name="gradient_bottom">#C8D4F0</color>

    <!-- Glass -->
    <color name="glass_fill">#66FFFFFF</color>
    <color name="glass_stroke">#2E0B3D2E</color>
    <color name="chip_fill">#33FFFFFF</color>
    <color name="chip_stroke">#2E0B3D2E</color>

    <!-- Status -->
    <color name="status_on">#0F9D65</color>
    <color name="status_on_glow">#330F9D65</color>
    <color name="status_off">#DC2626</color>
    <color name="accent">#0F9D65</color>

    <!-- Text -->
    <color name="text_primary">#0F1E3D</color>
    <color name="text_secondary">#4A5872</color>

    <!-- Legacy tokens kept to avoid breaking anywhere still referencing them -->
    <color name="background">#C8D4F0</color>
    <color name="green_active">#0F9D65</color>
    <color name="red_inactive">#DC2626</color>
</resources>
```

- [ ] **Step 2: Create `values-night/colors.xml` with the dark palette**

Create the directory if needed and write `app/src/main/res/values-night/colors.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="gradient_top">#0B3D2E</color>
    <color name="gradient_bottom">#0F1E3D</color>

    <color name="glass_fill">#332A3D4A</color>
    <color name="glass_stroke">#26FFFFFF</color>
    <color name="chip_fill">#1AFFFFFF</color>
    <color name="chip_stroke">#26FFFFFF</color>

    <color name="status_on">#34D399</color>
    <color name="status_on_glow">#3334D399</color>
    <color name="status_off">#F87171</color>
    <color name="accent">#7EE8C4</color>

    <color name="text_primary">#F5F7FB</color>
    <color name="text_secondary">#A8B2C4</color>

    <color name="background">#0F1E3D</color>
    <color name="green_active">#34D399</color>
    <color name="red_inactive">#F87171</color>
</resources>
```

- [ ] **Step 3: Verify resource compilation**

Run: `./gradlew :app:mergeDebugResources`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Save checkpoint**

---


### Task 9: Gradient background drawable

**Files:**
- Create: `app/src/main/res/drawable/bg_gradient.xml`

- [ ] **Step 1: Create the drawable**

Create `app/src/main/res/drawable/bg_gradient.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:angle="270"
        android:startColor="@color/gradient_top"
        android:endColor="@color/gradient_bottom"
        android:type="linear" />
</shape>
```

`angle=270` paints top→bottom (0° is left→right, 90° is bottom→top). Colour tokens are overridden in `values-night` so the same drawable produces both themes.

- [ ] **Step 2: Verify**

Run: `./gradlew :app:mergeDebugResources`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Save checkpoint**

---


### Task 10: Updated themes + glass card style

**Files:**
- Replace: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/values-night/themes.xml`
- Create: `app/src/main/res/values/styles.xml`

- [ ] **Step 1: Replace `values/themes.xml` (light)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.AdBlocker" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="colorPrimary">@color/accent</item>
        <item name="colorOnPrimary">#FFFFFF</item>
        <item name="android:windowBackground">@drawable/bg_gradient</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar" tools:targetApi="m">true</item>
        <item name="android:windowLightNavigationBar" tools:targetApi="o_mr1">true</item>
        <item name="android:textColorPrimary">@color/text_primary</item>
        <item name="android:textColorSecondary">@color/text_secondary</item>
    </style>
</resources>
```

- [ ] **Step 2: Create `values-night/themes.xml` (dark overrides)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.AdBlocker" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="colorPrimary">@color/accent</item>
        <item name="colorOnPrimary">#0F1E3D</item>
        <item name="android:windowBackground">@drawable/bg_gradient</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar" tools:targetApi="m">false</item>
        <item name="android:windowLightNavigationBar" tools:targetApi="o_mr1">false</item>
        <item name="android:textColorPrimary">@color/text_primary</item>
        <item name="android:textColorSecondary">@color/text_secondary</item>
    </style>
</resources>
```

- [ ] **Step 3: Ensure `AndroidManifest.xml` applies `Theme.AdBlocker`**

Open `app/src/main/AndroidManifest.xml`. Confirm the `<application>` tag has `android:theme="@style/Theme.AdBlocker"`. If it has a different theme name, update it. If the attribute is missing, add it.

- [ ] **Step 4: Create `values/styles.xml` with the glass card style**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="GlassCard" parent="Widget.Material3.CardView.Elevated">
        <item name="cardBackgroundColor">@color/glass_fill</item>
        <item name="cardCornerRadius">24dp</item>
        <item name="cardElevation">0dp</item>
        <item name="strokeColor">@color/glass_stroke</item>
        <item name="strokeWidth">1dp</item>
        <item name="android:layout_marginBottom">16dp</item>
    </style>

    <style name="GlassCard.Hero">
        <!-- same as GlassCard but with extra padding handled in layout -->
    </style>

    <style name="PillButton" parent="Widget.Material3.Button">
        <item name="cornerRadius">28dp</item>
        <item name="android:minHeight">56dp</item>
        <item name="android:textStyle">bold</item>
        <item name="android:letterSpacing">0.02</item>
    </style>

    <style name="PillButton.Outlined" parent="Widget.Material3.Button.OutlinedButton">
        <item name="cornerRadius">28dp</item>
        <item name="android:minHeight">56dp</item>
        <item name="android:textStyle">bold</item>
        <item name="strokeColor">@color/status_off</item>
        <item name="android:textColor">@color/status_off</item>
    </style>
</resources>
```

- [ ] **Step 5: Verify resource compilation**

Run: `./gradlew :app:mergeDebugResources`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Save checkpoint**

---


### Task 11: `GlassCardUtil` blur helper

**Files:**
- Create: `app/src/main/java/com/raphaelcabon/adblocker/GlassCardUtil.kt`

- [ ] **Step 1: Create the helper**

```kotlin
package com.raphaelcabon.adblocker

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View

/**
 * Applies a frosted-glass blur to the given view's background on SDK 31+.
 * On older devices it's a no-op — the glass card still looks fine due to
 * its translucent fill and hairline stroke.
 */
object GlassCardUtil {
    fun applyBlur(view: View, radius: Float = 30f) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setRenderEffect(
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.MIRROR)
            )
        }
    }

    fun clearBlur(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setRenderEffect(null)
        }
    }
}
```

**Note:** The blur is applied to the *card's own contents* — which means only child views get blurred, not the gradient behind the card. In this app we rely on the hairline stroke + translucent fill for the frosted effect; the blur is used on a dedicated "backdrop" `View` if we add one. The helper is provided for future use. For the present design, callers may keep `GlassCardUtil.applyBlur(...)` unused; the glass look still works via fill/stroke alone. Keep this file in place — it's called from `MainActivity` in Task 20 only if the design is tightened later.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin` — expect only Task-5 leftover MainActivity errors.

- [ ] **Step 3: Save checkpoint**

---


### Task 12: `ic_shield_slash` vector drawable

**Files:**
- Create: `app/src/main/res/drawable/ic_shield_slash.xml`

- [ ] **Step 1: Create the 64dp vector**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="64dp"
    android:height="64dp"
    android:viewportWidth="24"
    android:viewportHeight="24">

    <!-- Shield outline -->
    <path
        android:fillColor="#00000000"
        android:strokeColor="?attr/colorOnPrimary"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M12,3 L20,6 L20,12 C20,16.4 16.8,20.4 12,21 C7.2,20.4 4,16.4 4,12 L4,6 Z" />

    <!-- Diagonal slash -->
    <path
        android:fillColor="#00000000"
        android:strokeColor="?attr/colorOnPrimary"
        android:strokeWidth="2.2"
        android:strokeLineCap="round"
        android:pathData="M7,6 L17,18" />
</vector>
```

Using `?attr/colorOnPrimary` means the glyph adapts automatically between light and dark themes (white-ish on dark, deep-indigo-ish on light).

- [ ] **Step 2: Verify**

Run: `./gradlew :app:mergeDebugResources`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Save checkpoint**

---


### Task 13: `StatusRingView` custom view

**Files:**
- Create: `app/src/main/java/com/raphaelcabon/adblocker/StatusRingView.kt`

- [ ] **Step 1: Create the view**

```kotlin
package com.raphaelcabon.adblocker

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * A circular ring that visualises ad-blocker state:
 *  - OFF: flat red ring, shield-with-slash glyph centred.
 *  - ON : emerald ring with a pulsing outer glow, live blocked-count centred.
 *
 * Public API:
 *   setActive(Boolean)
 *   setBlockedCount(Long)
 */
class StatusRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(10f)
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(28f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val colorOn = ContextCompat.getColor(context, R.color.status_on)
    private val colorOnGlow = ContextCompat.getColor(context, R.color.status_on_glow)
    private val colorOff = ContextCompat.getColor(context, R.color.status_off)
    private val colorText = ContextCompat.getColor(context, R.color.text_primary)

    private val shield: Drawable? =
        ContextCompat.getDrawable(context, R.drawable.ic_shield_slash)

    private var active: Boolean = false
    private var blockedCount: Long = 0L
    private var glowAlpha: Float = 0.25f
    private var glowScale: Float = 1.0f

    private var animator: ValueAnimator? = null

    fun setActive(isActive: Boolean) {
        if (active == isActive) return
        active = isActive
        if (active) startPulse() else stopPulse()
        invalidate()
    }

    fun setBlockedCount(count: Long) {
        if (blockedCount == count) return
        blockedCount = count
        if (active) invalidate()
    }

    override fun onDetachedFromWindow() {
        stopPulse()
        super.onDetachedFromWindow()
    }

    private fun startPulse() {
        stopPulse()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                glowAlpha = 0.25f + 0.30f * t
                glowScale = 1.00f + 0.08f * t
                invalidate()
            }
            start()
        }
    }

    private fun stopPulse() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = (min(width, height) / 2f) - dp(8f)

        if (active) {
            glowPaint.color = colorOnGlow
            glowPaint.alpha = (glowAlpha * 255).toInt().coerceIn(0, 255)
            val rGlow = r * glowScale
            canvas.drawCircle(cx, cy, rGlow, glowPaint)
        }

        ringPaint.color = if (active) colorOn else colorOff
        canvas.drawCircle(cx, cy, r, ringPaint)

        if (active) {
            numberPaint.color = colorText
            val text = "%,d".format(blockedCount)
            // Centre vertically: baseline = cy - (ascent + descent) / 2
            val fm = numberPaint.fontMetrics
            val baseline = cy - (fm.ascent + fm.descent) / 2f
            canvas.drawText(text, cx, baseline, numberPaint)
        } else {
            shield?.let { d ->
                val size = dp(56f).toInt()
                val left = (cx - size / 2f).toInt()
                val top = (cy - size / 2f).toInt()
                d.setBounds(left, top, left + size, top + size)
                d.draw(canvas)
            }
        }
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density

    private fun sp(value: Float): Float =
        value * resources.displayMetrics.scaledDensity
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin` — expect only the known `MainActivity.kt` errors.

- [ ] **Step 3: Save checkpoint**

---


### Task 14: Chip row layout + background + `DomainChipAdapter`

**Files:**
- Create: `app/src/main/res/drawable/bg_chip.xml`
- Create: `app/src/main/res/layout/item_domain_chip.xml`
- Create: `app/src/main/java/com/raphaelcabon/adblocker/DomainChipAdapter.kt`

- [ ] **Step 1: Chip background drawable**

`app/src/main/res/drawable/bg_chip.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/chip_fill" />
    <stroke android:width="1dp" android:color="@color/chip_stroke" />
    <corners android:radius="12dp" />
</shape>
```

- [ ] **Step 2: Row layout**

`app/src/main/res/layout/item_domain_chip.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/bg_chip"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingStart="14dp"
    android:paddingEnd="6dp"
    android:paddingTop="4dp"
    android:paddingBottom="4dp"
    android:layout_marginBottom="6dp">

    <TextView
        android:id="@+id/tvDomain"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:textColor="@color/text_primary"
        android:textSize="14sp"
        android:ellipsize="end"
        android:singleLine="true" />

    <ImageButton
        android:id="@+id/btnRemove"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:background="?selectableItemBackgroundBorderless"
        android:contentDescription="Remove"
        android:src="@drawable/ic_close"
        app:tint="@color/text_secondary"
        xmlns:app="http://schemas.android.com/apk/res-auto" />
</LinearLayout>
```

- [ ] **Step 3: `DomainChipAdapter.kt`**

```kotlin
package com.raphaelcabon.adblocker

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.TextView

class DomainChipAdapter(
    context: Context,
    private val items: List<String>,
    private val onRemove: (String) -> Unit,
) : ArrayAdapter<String>(context, 0, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_domain_chip, parent, false)
        val domain = items[position]
        v.findViewById<TextView>(R.id.tvDomain).text = domain
        v.findViewById<ImageButton>(R.id.btnRemove).setOnClickListener { onRemove(domain) }
        return v
    }
}
```

- [ ] **Step 4: Verify compilation + resources**

Run: `./gradlew :app:compileDebugKotlin :app:mergeDebugResources`
Expected: only the known `MainActivity.kt` errors; all other files compile.

- [ ] **Step 5: Save checkpoint**

---


### Task 15: Filter source row layout

**Files:**
- Create: `app/src/main/res/layout/item_filter_source.xml`

- [ ] **Step 1: Create the row layout**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingVertical="10dp">

    <com.google.android.material.checkbox.MaterialCheckBox
        android:id="@+id/cbEnabled"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        app:buttonTint="@color/accent" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical"
        android:layout_marginStart="8dp">

        <TextView
            android:id="@+id/tvSourceName"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/text_primary"
            android:textStyle="bold"
            android:textSize="15sp" />

        <TextView
            android:id="@+id/tvSourceDesc"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/text_secondary"
            android:textSize="12sp" />
    </LinearLayout>

    <TextView
        android:id="@+id/tvSourceCount"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textColor="@color/text_secondary"
        android:textSize="12sp"
        android:fontFeatureSettings="tnum" />

</LinearLayout>
```

- [ ] **Step 2: Verify**

Run: `./gradlew :app:mergeDebugResources`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Save checkpoint**

---


### Task 16: Icon helpers (`ic_close`, `ic_add`)

**Files:**
- Create: `app/src/main/res/drawable/ic_close.xml`
- Create: `app/src/main/res/drawable/ic_add.xml`

- [ ] **Step 1: `ic_close.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M19,6.41 L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z" />
</vector>
```

- [ ] **Step 2: `ic_add.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M19,13 L13,13 L13,19 L11,19 L11,13 L5,13 L5,11 L11,11 L11,5 L13,5 L13,11 L19,11z" />
</vector>
```

- [ ] **Step 3: Verify**

Run: `./gradlew :app:mergeDebugResources`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Save checkpoint**

---


### Task 17: New launcher icon (foreground / background / monochrome) + mipmap wiring

**Files:**
- Replace: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Replace: `app/src/main/res/drawable/ic_launcher_background.xml`
- Create: `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

- [ ] **Step 1: `ic_launcher_background.xml` — emerald gradient**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0 L108,0 L108,108 L0,108 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:startX="0" android:startY="0"
                android:endX="0" android:endY="108"
                android:type="linear">
                <item android:offset="0" android:color="#0B3D2E" />
                <item android:offset="1" android:color="#0F1E3D" />
            </gradient>
        </aapt:attr>
    </path>
    <!-- Glass-sheen highlight line in upper-left -->
    <path
        android:pathData="M8,22 L60,8"
        android:strokeColor="#33FFFFFF"
        android:strokeWidth="2"
        android:strokeLineCap="round" />
</vector>
```

- [ ] **Step 2: `ic_launcher_foreground.xml` — shield with slash**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Adaptive-icon safe zone is a 66dp circle centred on 108dp canvas. -->
    <!-- Shield path in the safe zone, roughly occupying 36-84 horizontally and 24-92 vertically -->
    <path
        android:fillColor="#00000000"
        android:strokeColor="#FFF5F7FB"
        android:strokeWidth="6"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M54,22 L80,32 L80,58 C80,74.5 68.8,87.3 54,90 C39.2,87.3 28,74.5 28,58 L28,32 Z" />
    <!-- Mint inner fill for a touch of brand colour -->
    <path
        android:fillColor="#337EE8C4"
        android:pathData="M54,22 L80,32 L80,58 C80,74.5 68.8,87.3 54,90 C39.2,87.3 28,74.5 28,58 L28,32 Z" />
    <!-- Diagonal slash -->
    <path
        android:fillColor="#00000000"
        android:strokeColor="#FFF5F7FB"
        android:strokeWidth="7"
        android:strokeLineCap="round"
        android:pathData="M34,30 L74,82" />
</vector>
```

- [ ] **Step 3: `ic_launcher_monochrome.xml` — themed-icon layer (Android 13+)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#000000"
        android:pathData="M54,22 L80,32 L80,58 C80,74.5 68.8,87.3 54,90 C39.2,87.3 28,74.5 28,58 L28,32 Z" />
    <path
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFF"
        android:strokeWidth="7"
        android:strokeLineCap="round"
        android:pathData="M34,30 L74,82" />
</vector>
```

(For themed icons, Android uses alpha: solid areas become the system-tinted colour, transparent areas are transparent. This produces a readable monochrome mark.)

- [ ] **Step 4: Update mipmap-anydpi-v26 XMLs to reference the monochrome layer**

`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
```

`app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`: same content as above (identical file).

- [ ] **Step 5: Verify**

Run: `./gradlew :app:mergeDebugResources`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Save checkpoint**

---


### Task 18: New notification small icon

**Files:**
- Create: `app/src/main/res/drawable/ic_shield_slash_notif.xml`

Notification icons must be pure white on transparent (Android tints them).

- [ ] **Step 1: Create the 24dp notification glyph**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M12,2 L20,5 L20,11.5 C20,16 16.75,19.74 12,21 C7.25,19.74 4,16 4,11.5 L4,5 Z M12,4.2 L6,6.45 L6,11.5 C6,15 8.6,17.94 12,18.94 C15.4,17.94 18,15 18,11.5 L18,6.45 Z" />
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M7.5,5.5 L17.0,16.5 L16.0,17.5 L6.5,6.5 Z" />
</vector>
```

This is a filled shield silhouette with a diagonal slash bar — readable at 24×24 when the system tints it white/monochrome in the status bar.

- [ ] **Step 2: Verify**

Run: `./gradlew :app:mergeDebugResources`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Save checkpoint**

---


### Task 19: Full `activity_main.xml` rewrite

**Files:**
- Replace: `app/src/main/res/layout/activity_main.xml`

- [ ] **Step 1: Overwrite the layout**

Replace the entire contents with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/scrollRoot"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true"
    android:clipToPadding="false"
    android:background="@android:color/transparent">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="20dp">

        <!-- ── Hero status card ──────────────────────────────────────────── -->
        <com.google.android.material.card.MaterialCardView
            style="@style/GlassCard.Hero"
            android:layout_width="match_parent"
            android:layout_height="wrap_content">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:gravity="center_horizontal"
                android:padding="24dp">

                <com.raphaelcabon.adblocker.StatusRingView
                    android:id="@+id/statusRing"
                    android:layout_width="160dp"
                    android:layout_height="160dp"
                    android:layout_marginBottom="12dp" />

                <TextView
                    android:id="@+id/tvStatus"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Ad blocking is OFF"
                    android:textColor="@color/text_primary"
                    android:textSize="24sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/tvStatusSub"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Tap Start to activate the DNS filter"
                    android:textColor="@color/text_secondary"
                    android:textSize="14sp"
                    android:fontFeatureSettings="tnum"
                    android:layout_marginTop="4dp"
                    android:layout_marginBottom="20dp" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btnToggle"
                    style="@style/PillButton"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="Start"
                    app:backgroundTint="@color/status_on" />

                <!-- Hidden legacy view kept only to avoid binding lookups
                     against the old id in Task-1-only partial builds. -->
                <View
                    android:id="@+id/statusIndicator"
                    android:layout_width="0dp"
                    android:layout_height="0dp"
                    android:visibility="gone" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <!-- ── Hosts status card ─────────────────────────────────────────── -->
        <com.google.android.material.card.MaterialCardView
            style="@style/GlassCard"
            android:layout_width="match_parent"
            android:layout_height="wrap_content">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="20dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Blocklist status"
                    android:textColor="@color/text_primary"
                    android:textStyle="bold"
                    android:textSize="16sp"
                    android:layout_marginBottom="6dp" />

                <TextView
                    android:id="@+id/tvHostsCount"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="No lists downloaded yet"
                    android:textColor="@color/text_primary"
                    android:textSize="14sp"
                    android:fontFeatureSettings="tnum" />

                <TextView
                    android:id="@+id/tvHostsUpdated"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Select sources below and tap \"Update selected sources\""
                    android:textColor="@color/text_secondary"
                    android:textSize="12sp"
                    android:layout_marginTop="2dp" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <!-- ── Filter sources card ──────────────────────────────────────── -->
        <com.google.android.material.card.MaterialCardView
            style="@style/GlassCard"
            android:layout_width="match_parent"
            android:layout_height="wrap_content">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="20dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Filter sources"
                    android:textColor="@color/text_primary"
                    android:textStyle="bold"
                    android:textSize="16sp" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Select which blocklists to merge. More sources = more blocking."
                    android:textColor="@color/text_secondary"
                    android:textSize="12sp"
                    android:layout_marginBottom="8dp" />

                <LinearLayout
                    android:id="@+id/filterSourceRows"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:layout_marginBottom="12dp" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btnUpdateHosts"
                    style="@style/Widget.Material3.Button.OutlinedButton"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="Update selected sources" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <!-- ── Whitelist card ────────────────────────────────────────────── -->
        <com.google.android.material.card.MaterialCardView
            style="@style/GlassCard"
            android:layout_width="match_parent"
            android:layout_height="wrap_content">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="20dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical">

                    <TextView
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="Whitelist"
                        android:textColor="@color/text_primary"
                        android:textStyle="bold"
                        android:textSize="16sp" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btnAddWhitelist"
                        style="@style/Widget.Material3.Button.OutlinedButton"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Add"
                        app:icon="@drawable/ic_add" />
                </LinearLayout>

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Domains you always want to allow (overrides block lists)"
                    android:textColor="@color/text_secondary"
                    android:textSize="12sp"
                    android:layout_marginBottom="10dp" />

                <TextView
                    android:id="@+id/tvWhitelistEmpty"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="No domains whitelisted yet"
                    android:textColor="@color/text_secondary"
                    android:textSize="13sp" />

                <ListView
                    android:id="@+id/listWhitelist"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:nestedScrollingEnabled="false"
                    android:divider="@android:color/transparent"
                    android:dividerHeight="0dp" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

        <!-- ── Blacklist card ────────────────────────────────────────────── -->
        <com.google.android.material.card.MaterialCardView
            style="@style/GlassCard"
            android:layout_width="match_parent"
            android:layout_height="wrap_content">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="20dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical">

                    <TextView
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:text="Blacklist"
                        android:textColor="@color/text_primary"
                        android:textStyle="bold"
                        android:textSize="16sp" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btnAddBlacklist"
                        style="@style/Widget.Material3.Button.OutlinedButton"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Add"
                        app:icon="@drawable/ic_add" />
                </LinearLayout>

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Extra domains to block (on top of your selected block lists)"
                    android:textColor="@color/text_secondary"
                    android:textSize="12sp"
                    android:layout_marginBottom="10dp" />

                <TextView
                    android:id="@+id/tvBlacklistEmpty"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="No custom domains blacklisted yet"
                    android:textColor="@color/text_secondary"
                    android:textSize="13sp" />

                <ListView
                    android:id="@+id/listBlacklist"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:nestedScrollingEnabled="false"
                    android:divider="@android:color/transparent"
                    android:dividerHeight="0dp" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

    </LinearLayout>
</ScrollView>
```

- [ ] **Step 2: Verify resource compilation**

Run: `./gradlew :app:mergeDebugResources`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Save checkpoint**

---


### Task 20: `MainActivity` rewrite

**Files:**
- Replace: `app/src/main/java/com/raphaelcabon/adblocker/MainActivity.kt`

This task is the big one — it re-wires the Activity against the new layout, the new `HostsManager` API, the new live-counter broadcast, and the new filter-sources UI.

- [ ] **Step 1: Overwrite `MainActivity.kt`**

```kotlin
package com.raphaelcabon.adblocker

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.raphaelcabon.adblocker.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager

    private var isVpnRunning = false

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) launchVpnService()
            else Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
        }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val running = intent?.getBooleanExtra(AdBlockVpnService.EXTRA_IS_RUNNING, false) ?: return
            val count = intent.getLongExtra(AdBlockVpnService.EXTRA_BLOCKED_COUNT, 0L)
            updateUiState(running, count)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdgeInsets()

        prefs = PrefsManager(this)

        setupButtons()
        renderFilterSources()
        refreshHostsStatus()
        refreshCustomLists()
        updateUiState(isVpnRunning = false, blockedCount = 0L)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(
            statusReceiver,
            IntentFilter(AdBlockVpnService.BROADCAST_STATUS),
            RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(statusReceiver)
    }

    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
    }

    // ── Button setup ──────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btnToggle.setOnClickListener {
            if (isVpnRunning) stopVpn() else startVpn()
        }
        binding.btnUpdateHosts.setOnClickListener { downloadEnabledSources() }
        binding.btnAddWhitelist.setOnClickListener {
            showAddDomainDialog("Add to whitelist") { domain ->
                prefs.addToWhitelist(domain); refreshCustomLists()
            }
        }
        binding.btnAddBlacklist.setOnClickListener {
            showAddDomainDialog("Add to blacklist") { domain ->
                prefs.addToBlacklist(domain); refreshCustomLists()
            }
        }
    }

    // ── VPN control ───────────────────────────────────────────────────────

    private fun startVpn() {
        val status = HostsManager.getStatus(this)
        val anyCached = status.perSource.values.any { it.cacheExists }
        if (!anyCached) {
            AlertDialog.Builder(this)
                .setTitle("No blocklists downloaded")
                .setMessage("You haven't downloaded any blocklists yet.\n\n" +
                    "The VPN will start but won't block anything until you tap \"Update selected sources\".\n\n" +
                    "Continue anyway?")
                .setPositiveButton("Start anyway") { _, _ -> requestVpnPermissionAndStart() }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            requestVpnPermissionAndStart()
        }
    }

    private fun requestVpnPermissionAndStart() {
        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent != null) vpnPermissionLauncher.launch(permissionIntent)
        else launchVpnService()
    }

    private fun launchVpnService() {
        val intent = Intent(this, AdBlockVpnService::class.java).apply {
            action = AdBlockVpnService.ACTION_START
        }
        startForegroundService(intent)
        prefs.vpnWasRunning = true
    }

    private fun stopVpn() {
        val intent = Intent(this, AdBlockVpnService::class.java).apply {
            action = AdBlockVpnService.ACTION_STOP
        }
        startService(intent)
        prefs.vpnWasRunning = false
    }

    // ── UI state ──────────────────────────────────────────────────────────

    private fun updateUiState(isVpnRunning: Boolean, blockedCount: Long) {
        this.isVpnRunning = isVpnRunning
        binding.statusRing.setActive(isVpnRunning)
        binding.statusRing.setBlockedCount(blockedCount)

        if (isVpnRunning) {
            binding.tvStatus.text = "Ad blocking is ON"
            binding.tvStatusSub.text =
                "%,d queries blocked since start".format(blockedCount)
            binding.btnToggle.text = "Stop"
            (binding.btnToggle as MaterialButton).apply {
                setBackgroundColor(getColor(R.color.status_off))
            }
        } else {
            binding.tvStatus.text = "Ad blocking is OFF"
            binding.tvStatusSub.text = "Tap Start to activate the DNS filter"
            binding.btnToggle.text = "Start"
            (binding.btnToggle as MaterialButton).apply {
                setBackgroundColor(getColor(R.color.status_on))
            }
        }

        refreshHostsStatus()
    }

    // ── Hosts status ──────────────────────────────────────────────────────

    private fun refreshHostsStatus() {
        val status = HostsManager.getStatus(this)
        val enabledCached = status.perSource.filter { (k, s) ->
            prefs.isSourceEnabled(k) && s.cacheExists
        }

        if (enabledCached.isEmpty()) {
            binding.tvHostsCount.text = "No lists downloaded yet"
            binding.tvHostsUpdated.text =
                "Select sources below and tap \"Update selected sources\""
        } else {
            binding.tvHostsCount.text =
                "%,d domains blocked · %d sources".format(
                    status.totalDomainCount, status.enabledCount
                )
            val df = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
            binding.tvHostsUpdated.text =
                "Last updated: ${df.format(Date(status.lastUpdated))}"
        }

        // Per-row domain counts refresh too.
        updateFilterSourceCounts(status)
    }

    // ── Filter sources UI ─────────────────────────────────────────────────

    private fun renderFilterSources() {
        val container = binding.filterSourceRows
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (src in FilterSources.ALL) {
            val row = inflater.inflate(R.layout.item_filter_source, container, false)
            val cb = row.findViewById<MaterialCheckBox>(R.id.cbEnabled)
            val name = row.findViewById<TextView>(R.id.tvSourceName)
            val desc = row.findViewById<TextView>(R.id.tvSourceDesc)
            name.text = src.displayName
            desc.text = src.description
            cb.isChecked = prefs.isSourceEnabled(src.key)
            cb.setOnCheckedChangeListener { _, checked ->
                val current = prefs.getEnabledSourceKeys().toMutableSet()
                if (checked) current.add(src.key) else current.remove(src.key)
                prefs.setEnabledSourceKeys(current)
                refreshHostsStatus()
            }
            row.setOnClickListener { cb.isChecked = !cb.isChecked }
            container.addView(row)
        }
        updateFilterSourceCounts(HostsManager.getStatus(this))
    }

    private fun updateFilterSourceCounts(status: HostsManager.Status) {
        val rows = binding.filterSourceRows
        for (i in 0 until rows.childCount) {
            val row = rows.getChildAt(i)
            val src = FilterSources.ALL[i]
            val per = status.perSource[src.key] ?: continue
            val countView = row.findViewById<TextView>(R.id.tvSourceCount)
            countView.text = if (per.cacheExists) "%,d".format(per.domainCount) else "—"
        }
    }

    private fun downloadEnabledSources() {
        val enabled = prefs.getEnabledSourceKeys().toList()
        if (enabled.isEmpty()) {
            Toast.makeText(this, "Select at least one source first", Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnUpdateHosts.isEnabled = false
        binding.tvHostsUpdated.text = "Downloading…"
        lifecycleScope.launch {
            val result = HostsManager.downloadAndReload(applicationContext, enabled)
            binding.btnUpdateHosts.isEnabled = true
            result.fold(
                onSuccess = { merged ->
                    Toast.makeText(
                        this@MainActivity,
                        "Lists updated: %,d domains".format(merged),
                        Toast.LENGTH_LONG
                    ).show()
                    refreshHostsStatus()
                },
                onFailure = { err ->
                    Toast.makeText(
                        this@MainActivity,
                        "Download failed: ${err.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    refreshHostsStatus()
                }
            )
        }
    }

    // ── Whitelist / Blacklist ─────────────────────────────────────────────

    private fun refreshCustomLists() {
        val whitelist = prefs.getWhitelist().sorted()
        binding.listWhitelist.adapter =
            DomainChipAdapter(this, whitelist) { removed ->
                confirmRemove("Remove \"$removed\" from whitelist?") {
                    prefs.removeFromWhitelist(removed); refreshCustomLists()
                }
            }
        binding.listWhitelist.setOnItemLongClickListener { _, _, position, _ ->
            confirmRemove("Remove \"${whitelist[position]}\" from whitelist?") {
                prefs.removeFromWhitelist(whitelist[position]); refreshCustomLists()
            }
            true
        }
        binding.tvWhitelistEmpty.visibility =
            if (whitelist.isEmpty()) View.VISIBLE else View.GONE

        val blacklist = prefs.getBlacklist().sorted()
        binding.listBlacklist.adapter =
            DomainChipAdapter(this, blacklist) { removed ->
                confirmRemove("Remove \"$removed\" from blacklist?") {
                    prefs.removeFromBlacklist(removed); refreshCustomLists()
                }
            }
        binding.listBlacklist.setOnItemLongClickListener { _, _, position, _ ->
            confirmRemove("Remove \"${blacklist[position]}\" from blacklist?") {
                prefs.removeFromBlacklist(blacklist[position]); refreshCustomLists()
            }
            true
        }
        binding.tvBlacklistEmpty.visibility =
            if (blacklist.isEmpty()) View.VISIBLE else View.GONE
    }

    // ── Dialogs ───────────────────────────────────────────────────────────

    private fun showAddDomainDialog(title: String, onConfirm: (String) -> Unit) {
        val inputView = LayoutInflater.from(this).inflate(R.layout.dialog_add_domain, null)
        val editText = inputView.findViewById<EditText>(R.id.etDomain)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(inputView)
            .setPositiveButton("Add") { _, _ ->
                val domain = editText.text.toString().trim().lowercase()
                when {
                    domain.isEmpty() ->
                        Toast.makeText(this, "Please enter a domain", Toast.LENGTH_SHORT).show()
                    !isValidDomain(domain) ->
                        Toast.makeText(this, "Invalid domain (e.g. example.com)", Toast.LENGTH_SHORT).show()
                    else -> onConfirm(domain)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmRemove(message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("Remove") { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun isValidDomain(domain: String): Boolean =
        domain.matches(Regex("^[a-z0-9]([a-z0-9\\-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9\\-]{0,61}[a-z0-9])?)*\$"))
}
```

- [ ] **Step 2: Verify the full project compiles**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. If any reference to old `HostsManager.Status` fields remains, fix the call site to use the new `perSource` / `totalDomainCount` / `lastUpdated` fields.

- [ ] **Step 3: Save checkpoint**

---


### Task 21: Update notification to use the new shield icon

**Files:**
- Modify: `app/src/main/java/com/raphaelcabon/adblocker/AdBlockVpnService.kt` (just the `buildNotification` smallIcon reference)

- [ ] **Step 1: Swap `setSmallIcon` to the new drawable**

In `AdBlockVpnService.buildNotification`, change:

```kotlin
            .setSmallIcon(android.R.drawable.ic_menu_compass)
```

to:

```kotlin
            .setSmallIcon(R.drawable.ic_shield_slash_notif)
```

Also update the stop-action icon for consistency. Change:

```kotlin
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
```

to:

```kotlin
            .addAction(R.drawable.ic_close, "Stop", stopIntent)
```

- [ ] **Step 2: Verify**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Save checkpoint**

---


### Task 22: Manual verification + build

- [ ] **Step 1: Build the signed debug APK**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 2: Run the unit test**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 7 HostsParser tests pass.

- [ ] **Step 3: Install and smoke-test on a device running Android 14**

Run: `./gradlew installDebug`

Manual checklist:
1. Launcher icon: open the launcher drawer. The new shield-with-slash icon is visible against the emerald/midnight gradient.
2. App open: background shows the emerald→midnight gradient. Glass cards are visible with hairline strokes.
3. Tap **Start**. Grant VPN permission. Status ring turns emerald and starts pulsing. Headline switches to "Ad blocking is ON". Subtitle shows "0 queries blocked since start" initially.
4. Open Chrome, browse to a couple of ad-heavy news sites. Watch the counter increment every ~2 seconds both in the subtitle and inside the status ring.
5. Pull down the status bar. Persistent notification shows "Blocking active — N queries filtered" with the new shield icon.
6. Tap **Stop**. Ring goes red, shield-with-slash glyph returns, subtitle shows "Tap Start to activate the DNS filter", notification is dismissed.
7. In the Filter sources card: toggle "AdGuard DNS filter" on. Tap "Update selected sources". Expect a Toast showing the merged domain count. Both the hosts-status line and the per-row counts update.
8. Add a whitelist and a blacklist entry via the Add buttons; confirm chip rows render; tap the × icon to remove; confirm long-press also removes.
9. Toggle device dark/light mode: both themes render the gradient correctly; text remains legible in each.
10. Rotate the device (or lock/unlock): the pulse animator re-starts on `onResume` and stops on `onPause`.

- [ ] **Step 4: Known limitations to note**

These are not bugs — document for the engineer:
- `RenderEffect` blur is skipped below SDK 31; glass look relies on fill + stroke alone there.
- Per-source "Downloading 2 of 4" progress is simplified in this plan to a single Toast at completion. If granular progress is wanted, extend `HostsManager.downloadAndReload` to emit a `Flow<ProgressEvent>`; out of scope for v1.
- `blockedCount` resets to zero every time the VPN starts. A persistent lifetime counter is out of scope for v1.

- [ ] **Step 5: Save final checkpoint**

---

## Self-Review Summary

**Spec coverage:** All nine sections of the spec are covered. §3 bug fix → Task 1. §4 visual system → Tasks 8, 9, 10, 11. §5 screen layout → Tasks 13, 14, 15, 19, 20. §6 live counter → Task 7. §7 launcher icon → Tasks 12, 17, 18. §8 multi-blocklist architecture → Tasks 4, 5, 6, 20. §9 testing → Tasks 2, 3, 22.

**Placeholder scan:** No TBDs. Every code block is complete. No "similar to Task N" references.

**Type consistency:** `HostsManager.Status` / `SourceStatus` names are identical across Tasks 5, 20, 22. `FilterSources.ALL` / `.byKey` / `.defaultEnabledKeys` names are identical across Tasks 4, 5, 6, 20. `PrefsManager.getEnabledSourceKeys` / `setEnabledSourceKeys` / `isSourceEnabled` names are identical across Tasks 6, 20. `StatusRingView.setActive` / `setBlockedCount` are called only by Task 20 where they're needed.

**Scope check:** One cohesive product update. All tasks touch the same app, ship together, and share a clear narrative (glassmorphism visual refresh + multi-source blocklists + reliability fix). A single plan is appropriate.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-17-glassmorphism-redesign.md`. Two execution options:

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach would you like?

