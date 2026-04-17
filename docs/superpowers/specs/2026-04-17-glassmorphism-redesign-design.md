# AdBlocker Android — Glassmorphism Redesign + Multi-Blocklist + Bug Fix

**Date:** 2026-04-17
**Author:** Raphaël (via Cowork)
**Status:** Proposed — awaiting user review

## 1. Goals

Ship one cohesive update that:

1. Fixes the "Ad blocking is OFF" display bug after tapping Start on Android 14.
2. Gives the app an unmistakable, high-quality glassmorphism visual identity.
3. Replaces the launcher icon with a matching, on-brand mark.
4. Adds user-selectable multiple blocklist sources (not just Steven Black).
5. Provides unambiguous live confirmation that filtering is working.

## 2. Non-goals

- No changes to DNS packet handling, `protect(socket)`, or upstream resolver selection.
- No new third-party libraries.
- No minSdk/targetSdk changes (stays 26 / 34).
- No rewrite of HostsManager's public contract beyond what multi-source requires.
- No automated background list refresh; updates remain manual.

## 3. The "stays OFF" bug — root cause and fix

**Root cause.** `MainActivity.onResume()` registers the status receiver with `RECEIVER_NOT_EXPORTED`. `AdBlockVpnService.broadcastStatus()` sends an implicit broadcast (action-only, no package set). Since API 34, Android drops implicit broadcasts to NOT_EXPORTED receivers. `MainActivity` therefore never gets the `isRunning=true` update after tapping Start, and the UI stays on "OFF" even while the VPN thread is running (proof: the persistent "Ad Blocker Active" notification appears).

**Fix.** Make broadcasts explicit by pinning the package on every broadcast Intent:

```kotlin
private fun broadcastStatus(isRunning: Boolean) {
    sendBroadcast(Intent(BROADCAST_STATUS).apply {
        setPackage(packageName)
        putExtra(EXTRA_IS_RUNNING, isRunning)
        putExtra(EXTRA_BLOCKED_COUNT, blockedCount)
    })
}
```

Low-risk, one-line change. Applies to the existing status broadcast and the new periodic counter broadcast (see §6).

## 4. Visual system

### 4.1 Colour palette (new)

Dark theme (default per system):

| Token | Value | Role |
|---|---|---|
| `gradient_top` | `#0B3D2E` | Top of background gradient — deep forest |
| `gradient_bottom` | `#0F1E3D` | Bottom of background gradient — midnight indigo |
| `glass_fill` | `#332A3D4A` (20%) | Glass card fill |
| `glass_stroke` | `#26FFFFFF` (15%) | Card 1dp hairline |
| `status_on` | `#34D399` | Active green |
| `status_on_glow` | `#3334D399` | Outer ring glow (20% alpha) |
| `status_off` | `#F87171` | Inactive red |
| `text_primary` | `#F5F7FB` | Primary text |
| `text_secondary` | `#A8B2C4` | Muted text |
| `accent` | `#7EE8C4` | Mint accent |

Light theme:

| Token | Value | Role |
|---|---|---|
| `gradient_top` | `#A8D5BA` | Soft emerald |
| `gradient_bottom` | `#C8D4F0` | Powder indigo |
| `glass_fill` | `#66FFFFFF` (40%) | Glass card fill |
| `glass_stroke` | `#2E0B3D2E` (18%) | Card hairline |
| `status_on` | `#0F9D65` | Active green |
| `status_off` | `#DC2626` | Inactive red |
| `text_primary` | `#0F1E3D` | Primary text |
| `text_secondary` | `#4A5872` | Muted text |

### 4.2 Glass cards

- `MaterialCardView` with 24dp corner radius.
- `cardBackgroundColor` = `@color/glass_fill`.
- 1dp stroke in `@color/glass_stroke`.
- `cardElevation` = 0. Glass depth comes from light/stroke, not shadow.
- On SDK 31+: apply `view.setRenderEffect(RenderEffect.createBlurEffect(30f, 30f, Shader.TileMode.MIRROR))` to a sibling "backdrop" view that sits behind the card and samples the background gradient. Pure-card translucency on SDK 26–30 (no blur, slightly higher opacity for legibility).

### 4.3 Typography and spacing

- System default (`Roboto` on stock, shipped font elsewhere), 600 for headings.
- Tabular figures via `android:fontFeatureSettings="tnum"` on numeric TextViews.
- Page padding: 20dp.
- Inter-card gap: 16dp.
- Inner card padding: 20dp.
- Edge-to-edge: `WindowCompat.setDecorFitsSystemWindows(window, false)` + insets applied to scroll container top/bottom.

## 5. Screen layout

Single scrolling screen with five glass cards in order:

1. **Hero status card** (§5.1)
2. **Hosts status card** — now shows "Active sources: N · M domains blocked"
3. **Filter sources card** (new, §5.3)
4. **Whitelist card**
5. **Blacklist card**

All share the glass styling from §4.2.

### 5.1 Hero status card

Height ~280dp. Contents, top to bottom:

1. `StatusRingView` (new custom View, ~140dp square, centered).
   - Draws two concentric arcs: outer glow ring (only when active, 20% alpha green), inner solid ring (3dp stroke).
   - Inactive: flat red ring, no animation, the same shield-with-slash glyph used by the launcher icon (§7), scaled to ~56dp, centred inside the ring.
   - Active: green ring, outer glow pulses via `ValueAnimator` (alpha 0.25→0.55, scale 1.0→1.08, 1600ms `DecelerateInterpolator`, infinite reverse). The shield-with-slash glyph fades out when active and is replaced by the blocked-count number in 28sp tabular — so the ring shows the glyph in OFF state and the live number in ON state, never both.
   - Exposes `setActive(Boolean)` and `setBlockedCount(Long)`.
2. Status headline TextView — `"Ad blocking is ON"` / `"OFF"` — 24sp, weight 600.
3. Subtitle TextView — 14sp muted. Active text: `"N queries blocked since start"` (tabular). Inactive text: `"Tap Start to activate the DNS filter"`. Single TextView whose content swaps with state — avoids duplicating the count in two places on the card.
4. Full-width `MaterialButton`, 56dp height, 28dp radius (pill). Active: outlined in red, label "Stop". Inactive: filled green, label "Start".

### 5.2 Filter sources card (new)

Title: "Filter sources".
Subtitle: "Select which blocklists to merge. More sources = more blocking but more memory."

Rows, one per source. Each row:

```
┌────────────────────────────────────────────────┐
│ ☑  Steven Black (unified)        130,000 ✓     │
│    Classic ads + trackers + malware            │
│                                                │
│ ☐  AdGuard DNS filter             ——           │
│    AdGuard's curated DNS-level list            │
│    ...                                         │
└────────────────────────────────────────────────┘
```

Left: `MaterialCheckBox` (tinted `accent`).
Right: domain count once downloaded, or "—" if not yet fetched, or a mini progress indicator during download.
Tap row toggles the checkbox; long-press opens an info dialog with the source URL.

Below the list, a full-width "Update selected sources" outlined button (replaces the old "Update Hosts List"). Disabled if no sources selected. Shows progress across all selected sources ("Downloading 2 of 4: AdGuard…").

**Sources shipped:**

| Key | Display name | URL | Default on |
|---|---|---|---|
| `steven_black` | Steven Black (unified) | `https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts` | Yes |
| `adguard_dns` | AdGuard DNS filter | `https://adguardteam.github.io/HostlistsRegistry/assets/filter_15.txt` | No |
| `oisd_full` | OISD full | `https://big.oisd.nl/hosts` | No |
| `peter_lowe` | Peter Lowe | `https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext` | No |
| `onehosts_lite` | 1Hosts Lite | `https://o0.pages.dev/Lite/hosts.txt` | No |

Sources keyed by a stable string so prefs migration is trivial. The list is hard-coded in a `FilterSource` sealed class / companion registry — no runtime-editable source URLs in v1.

### 5.3 Whitelist / Blacklist cards

Same external shape as today, but list rows become glass chip-rows:

- Item layout `res/layout/item_domain_chip.xml`: rounded 12dp corner, translucent fill (`#1AFFFFFF` dark / `#33FFFFFF` light), 1dp hairline, `ImageButton` with a bounded tap target (40dp × 40dp) using an `ic_close` vector.
- Long-press-to-remove is retained.
- Visible "× removes, long-press also works" micro-hint.
- Empty-state TextView gets a subtle centred shield icon above it.

Add buttons become `MaterialButton` tonal style with `+` leading icon from `ic_add` (vector drawable).

## 6. Live counter plumbing

- `AdBlockVpnService` starts a coroutine in `ioScope` at `startVpn()`:
  ```kotlin
  counterJob = ioScope.launch {
      while (running) {
          broadcastStatus(true)  // carries current blockedCount
          delay(2000)
      }
  }
  ```
- Cancelled in `stopVpn()`: `counterJob?.cancel(); counterJob = null`.
- Broadcast Intent has `.setPackage(packageName)` (see §3).
- `MainActivity.statusReceiver` reads `EXTRA_BLOCKED_COUNT` and updates both the hero counter TextView and the number rendered inside `StatusRingView`.
- Notification's `contentText` updated each tick to: `"Blocking active — N queries filtered"`.

## 7. Launcher icon

Adaptive icon — pure vector XML, no raster.

- `ic_launcher_background.xml`: rounded-square with vertical `aapt:attr name="android:fillColor"` linear gradient from `#0B3D2E` to `#0F1E3D`. Subtle diagonal highlight line at 20% white alpha in upper-left for a glass-sheen feel.
- `ic_launcher_foreground.xml`: white/mint shield outline with a diagonal slash crossing it, occupying the adaptive-icon safe zone (66dp of the 108dp canvas). Shield stroke 6dp. Slash stroke 7dp, rounded caps.
- Also update the monochrome layer for themed icons on Android 13+ (`ic_launcher_monochrome.xml`).
- Same artwork repurposed at 24dp for the notification small icon (replaces `android.R.drawable.ic_menu_compass`).

## 8. Multi-blocklist architecture

### 8.1 `FilterSource` model

```kotlin
data class FilterSource(
    val key: String,          // stable id, stored in prefs
    val displayName: String,
    val description: String,
    val url: String,
    val defaultEnabled: Boolean,
)

object FilterSources {
    val ALL: List<FilterSource> = listOf(/* 5 entries from §5.2 */)
    fun byKey(key: String): FilterSource? = ALL.firstOrNull { it.key == key }
}
```

### 8.2 `HostsManager` refactor

Current single-source-with-one-cache becomes multi-source-with-per-source-cache.

Files on disk (under `context.filesDir`):
- `hosts_<key>.txt` — raw downloaded list per source.
- `hosts_merged.set` — no longer kept on disk; merged set is built in-memory at load time from the enabled sources' raw files.

In-memory state:
- `private val blockedSet: HashSet<String> = HashSet()`  — the merged, deduped, lowercased domains.

Public surface (keeps current callers working):

```kotlin
object HostsManager {
    data class Status(
        val perSource: Map<String, SourceStatus>, // key -> status
        val totalDomainCount: Int,
        val mergedCacheExists: Boolean,
    )
    data class SourceStatus(
        val cacheExists: Boolean,
        val cacheLastModified: Long,
        val domainCount: Int,
    )

    fun getStatus(ctx: Context): Status
    fun ensureLoaded(ctx: Context)  // loads all enabled sources into blockedSet
    suspend fun downloadAndReload(ctx: Context, keys: List<String>): Result<Int>  // count = merged size
    fun isBlocked(domain: String): Boolean
}
```

`isBlocked()` logic unchanged: subdomain-walk check against `blockedSet`.

### 8.3 Prefs

`PrefsManager` gains:

```kotlin
private val KEY_ENABLED_SOURCES = "enabled_sources"  // StringSet
fun getEnabledSourceKeys(): Set<String>  // defaults to keys of FilterSources.ALL where defaultEnabled
fun setEnabledSourceKeys(keys: Set<String>)
fun isSourceEnabled(key: String): Boolean
```

Migration: on first launch after update, if `KEY_ENABLED_SOURCES` is absent, seed with `{"steven_black"}` to preserve today's behaviour.

### 8.4 Download semantics

`downloadAndReload(ctx, keys)`:

1. For each key in `keys`, fetch URL to `hosts_<key>.txt`. On failure, keep prior cache (don't wipe).
2. After all fetches complete (or fail individually), rebuild `blockedSet` from all enabled + successfully-cached sources.
3. Return merged-set size.

Download is sequential to keep memory flat on low-end devices. Individual failures are surfaced via a summary Toast ("Updated 3 of 4 sources; AdGuard failed — check connection").

## 9. Testing strategy

- **Manual**: unplug wifi, tap Start → status should flip to ON (was the bug). Tap domains in Chrome, watch counter increment. Toggle light/dark → gradient and glass re-render correctly.
- **Unit**: `FilterSources` parser — verify a hosts-format file with comments / inline IPs / blank lines is parsed into the expected domain set. Reuse existing parsing code.
- **Unit**: `PrefsManager` multi-source migration — given no existing key, returns `{steven_black}`; given a populated set, round-trips correctly.
- **UI (instrumented)**: skip — the app has no existing instrumented suite and adding one is out of scope.

## 10. Rollout order (for plan)

1. Bug fix + live counter (isolated, highest value).
2. Multi-blocklist refactor (enables §5.2 UI later).
3. Visual system: colours, themes, gradient background, glass card style.
4. Hero card + `StatusRingView`.
5. Chip-row list items.
6. Filter sources card (wires to §8).
7. Launcher + notification icons.
8. Build verification: `./gradlew assembleDebug`.

Each step is independently compilable so partial rollouts are possible.

## 11. Risks and mitigations

| Risk | Mitigation |
|---|---|
| `RenderEffect` blur is pre-31 only. | Conditional API check, graceful fallback to higher-opacity cards. |
| Merged set grows large (~400k domains). | Measured memory ~20MB for 400k strings in a `HashSet<String>` — well within app budget. Monitor; if issues, switch to `HashSet<Int>` of `hashCode()`s with collision tolerance. |
| A blocklist URL goes 404 in the future. | Per-source failure handling; the app keeps working with the other sources. |
| `ValueAnimator` running while screen off wastes battery. | Stop animator in `onPause()`, restart in `onResume()` when active. |
| Edge-to-edge layout overlaps status bar on some OEMs. | Standard `WindowInsetsCompat.Type.systemBars()` padding applied to scroll content. |

## 12. Open questions

None at time of writing. Proceed to writing-plans.
