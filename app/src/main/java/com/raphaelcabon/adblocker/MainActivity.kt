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
import androidx.appcompat.app.AppCompatDelegate
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
        // Apply persisted theme preference BEFORE super.onCreate so the activity
        // is inflated with the correct day/night configuration from the start.
        prefs = PrefsManager(this)
        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)

        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyEdgeToEdgeInsets()

        setupButtons()
        setupThemeToggle()
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

    // ── Theme toggle ──────────────────────────────────────────────────────

    private fun setupThemeToggle() {
        // Reflect the current persisted selection.
        val currentId = when (prefs.themeMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.id.themeLight
            AppCompatDelegate.MODE_NIGHT_YES -> R.id.themeDark
            else -> R.id.themeSystem
        }
        binding.themeToggle.check(currentId)

        binding.themeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newMode = when (checkedId) {
                R.id.themeLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.themeDark  -> AppCompatDelegate.MODE_NIGHT_YES
                else            -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            if (newMode == prefs.themeMode) return@addOnButtonCheckedListener
            prefs.themeMode = newMode
            // Triggers Activity.recreate() under the hood when the night
            // configuration actually changes — the activity re-inflates with
            // the correct values-night/ resources.
            AppCompatDelegate.setDefaultNightMode(newMode)
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
