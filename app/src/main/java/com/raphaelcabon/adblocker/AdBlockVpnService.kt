package com.raphaelcabon.adblocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * AdBlockVpnService — creates a local-only VPN that intercepts all DNS queries,
 * blocks ad/tracker domains using the Steven Black hosts list, and forwards
 * everything else to an upstream resolver (Cloudflare 1.1.1.1 by default).
 *
 * No data leaves the device through this VPN — it only intercepts DNS.
 * Real network traffic flows through the normal interface unchanged.
 */
class AdBlockVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.raphaelcabon.adblocker.START"
        const val ACTION_STOP  = "com.raphaelcabon.adblocker.STOP"

        // Broadcast to inform MainActivity of state changes
        const val BROADCAST_STATUS  = "com.raphaelcabon.adblocker.STATUS"
        const val EXTRA_IS_RUNNING  = "isRunning"
        const val EXTRA_BLOCKED_COUNT = "blockedCount"

        private const val NOTIF_CHANNEL_ID = "adblock_vpn"
        private const val NOTIF_ID         = 1

        // Our fake VPN address and DNS server address
        // All DNS queries from the OS will be routed here through the TUN interface.
        private const val VPN_ADDRESS    = "10.0.0.1"
        private const val VPN_DNS_SERVER = "10.0.0.2"
        private const val VPN_ROUTE      = "10.0.0.0"
        private const val VPN_PREFIX     = 24

        // Upstream resolver — used for queries that are NOT blocked
        private const val UPSTREAM_DNS = "1.1.1.1"
        private const val UPSTREAM_PORT = 53
        private const val DNS_TIMEOUT_MS = 4000
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    @Volatile private var running = false

    // Coroutine scope for async DNS forwarding
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Statistics
    @Volatile var blockedCount = 0L
    private var counterJob: kotlinx.coroutines.Job? = null

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVpn()
            ACTION_STOP  -> stopVpn()
        }
        return START_STICKY
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        ioScope.cancel()
        super.onDestroy()
    }

    // ── Start / Stop ─────────────────────────────────────────────────────────

    private fun startVpn() {
        if (running) return

        // Make sure the hosts list is loaded (HostsManager caches it in memory)
        HostsManager.ensureLoaded(applicationContext)

        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress(VPN_ADDRESS, VPN_PREFIX)
            .addDnsServer(VPN_DNS_SERVER)       // Redirect OS DNS queries into our TUN
            .addRoute(VPN_ROUTE, VPN_PREFIX)    // Route our local subnet through TUN
            .setMtu(1500)
            .setBlocking(true)                  // Blocking reads — simpler threading model

        vpnInterface = builder.establish() ?: return   // User declined VPN permission

        running = true
        blockedCount = 0

        vpnThread = Thread({ runVpnLoop() }, "vpn-packet-loop")
        vpnThread?.start()

        startForeground(NOTIF_ID, buildNotification(true))
        broadcastStatus(true)

        // Periodic live-count broadcast so the UI and the notification can
        // show a growing "N queries blocked" confirmation.
        counterJob = ioScope.launch {
            while (isActive && running) {
                broadcastStatus(true)
                updateNotification()
                kotlinx.coroutines.delay(2000)
                if (!running) break
            }
        }
    }

    private fun stopVpn() {
        if (!running) return
        running = false

        // counterJob is owned by the active VPN session: cancel it here when the
        // session ends, so the counter stops immediately (instead of waiting up
        // to delay(2000)). ioScope.cancel() in onDestroy() is only a safety net
        // for in-flight DNS-forwarding coroutines.
        counterJob?.cancel()
        counterJob = null

        // Closing the ParcelFileDescriptor unblocks the blocking read() in runVpnLoop()
        try { vpnInterface?.close() } catch (_: IOException) {}
        vpnInterface = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        broadcastStatus(false)
    }

    // ── VPN Packet Loop ──────────────────────────────────────────────────────

    /**
     * Runs on a dedicated thread. Reads raw IPv4 packets from the TUN interface,
     * filters UDP port 53 (DNS), and processes them.
     */
    private fun runVpnLoop() {
        val fd     = vpnInterface?.fileDescriptor ?: return
        val input  = FileInputStream(fd)
        val output = FileOutputStream(fd)
        val buffer = ByteArray(32767)

        try {
            while (running) {
                val len = input.read(buffer)
                if (len <= 0) continue
                processPacket(buffer, len, output)
            }
        } catch (e: IOException) {
            // Normal when stopVpn() closes the fd
        }
    }

    /**
     * Inspects one IP packet. If it's a DNS query (UDP → port 53), handle it.
     * Everything else is silently dropped — since we only route our tiny /24
     * subnet through the TUN, real traffic is unaffected.
     */
    private fun processPacket(buf: ByteArray, len: Int, output: FileOutputStream) {
        if (len < 28) return  // Minimum: 20 IP + 8 UDP headers

        // ── IPv4 header ──────────────────────────────────────────────────────
        val version = (buf[0].toInt() and 0xF0) shr 4
        if (version != 4) return  // Only IPv4

        val ihl      = (buf[0].toInt() and 0x0F) * 4  // IP header length in bytes
        val protocol = buf[9].toInt() and 0xFF
        if (protocol != 17) return  // Only UDP

        if (len < ihl + 8) return

        val srcIp = buf.copyOfRange(12, 16)
        val dstIp = buf.copyOfRange(16, 20)

        // ── UDP header ───────────────────────────────────────────────────────
        val srcPort = buf.getUShort(ihl)
        val dstPort = buf.getUShort(ihl + 2)
        if (dstPort != 53) return  // Only DNS

        val dnsOffset = ihl + 8
        val dnsLen    = len - dnsOffset
        if (dnsLen < 12) return

        val dnsPayload = buf.copyOfRange(dnsOffset, dnsOffset + dnsLen)

        // ── DNS processing ───────────────────────────────────────────────────
        val domain = DnsInterceptor.parseQueryDomain(dnsPayload)

        if (domain != null && isBlocked(domain)) {
            blockedCount++
            // Respond immediately with NXDOMAIN — no real request made
            val dnsResp   = DnsInterceptor.buildNxdomainResponse(dnsPayload)
            val pktResp   = buildIpUdpPacket(dstIp, srcIp, 53, srcPort, dnsResp)
            synchronized(output) { output.write(pktResp); output.flush() }
        } else {
            // Forward to upstream DNS asynchronously so we don't block the loop
            val capSrcIp   = srcIp.clone()
            val capDstIp   = dstIp.clone()
            val capSrcPort = srcPort
            val capPayload = dnsPayload.clone()

            ioScope.launch {
                val dnsResp = forwardToUpstream(capPayload) ?: return@launch
                val pktResp = buildIpUdpPacket(capDstIp, capSrcIp, 53, capSrcPort, dnsResp)
                synchronized(output) {
                    try { output.write(pktResp); output.flush() } catch (_: IOException) {}
                }
            }
        }
    }

    // ── Domain Blocking Check ─────────────────────────────────────────────────

    private fun isBlocked(domain: String): Boolean {
        val prefs = PrefsManager(applicationContext)
        // Whitelist takes priority
        if (prefs.isWhitelisted(domain)) return false
        // Custom blacklist
        if (prefs.isBlacklisted(domain)) return true
        // Steven Black hosts list (and subdomains)
        return HostsManager.isBlocked(domain)
    }

    // ── Upstream DNS Forwarding ───────────────────────────────────────────────

    /**
     * Sends [dnsQuery] to the upstream DNS server over a protected socket
     * (protected = bypasses our own VPN to avoid a forwarding loop).
     */
    private fun forwardToUpstream(dnsQuery: ByteArray): ByteArray? {
        return try {
            val socket = DatagramSocket()
            protect(socket)  // Crucial: excludes this socket from the VPN tunnel

            socket.soTimeout = DNS_TIMEOUT_MS

            val upstreamAddr = InetAddress.getByName(UPSTREAM_DNS)
            socket.send(DatagramPacket(dnsQuery, dnsQuery.size, upstreamAddr, UPSTREAM_PORT))

            val responseBuffer = ByteArray(4096)
            val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(responsePacket)
            socket.close()

            responseBuffer.copyOf(responsePacket.length)
        } catch (e: Exception) {
            null  // Timeout or network error — DNS query simply fails silently
        }
    }

    // ── Packet Building ───────────────────────────────────────────────────────

    /**
     * Wraps [dnsData] in a valid IPv4 + UDP packet for writing back to the TUN interface.
     *
     * @param srcIp  Source IPv4 address (4 bytes)
     * @param dstIp  Destination IPv4 address (4 bytes)
     * @param srcPort Source UDP port
     * @param dstPort Destination UDP port
     */
    private fun buildIpUdpPacket(
        srcIp:   ByteArray,
        dstIp:   ByteArray,
        srcPort: Int,
        dstPort: Int,
        dnsData: ByteArray
    ): ByteArray {
        val udpLen = 8 + dnsData.size
        val ipLen  = 20 + udpLen
        val pkt    = ByteArray(ipLen)

        // ── IPv4 Header ──────────────────────────────────────────────────────
        pkt[0]  = 0x45.toByte()            // Version=4, IHL=5 (20 bytes)
        pkt[1]  = 0x00                     // DSCP/ECN
        pkt[2]  = (ipLen shr 8).toByte()
        pkt[3]  = (ipLen and 0xFF).toByte()
        pkt[4]  = 0x00                     // Identification
        pkt[5]  = 0x00
        pkt[6]  = 0x40                     // Flags: Don't Fragment
        pkt[7]  = 0x00                     // Fragment offset
        pkt[8]  = 0x40                     // TTL = 64
        pkt[9]  = 0x11                     // Protocol = UDP
        pkt[10] = 0x00                     // Checksum placeholder
        pkt[11] = 0x00
        System.arraycopy(srcIp, 0, pkt, 12, 4)
        System.arraycopy(dstIp, 0, pkt, 16, 4)

        // Compute and insert IP header checksum
        val checksum = internetChecksum(pkt, 0, 20)
        pkt[10] = (checksum shr 8).toByte()
        pkt[11] = (checksum and 0xFF).toByte()

        // ── UDP Header ───────────────────────────────────────────────────────
        pkt[20] = (srcPort shr 8).toByte()
        pkt[21] = (srcPort and 0xFF).toByte()
        pkt[22] = (dstPort shr 8).toByte()
        pkt[23] = (dstPort and 0xFF).toByte()
        pkt[24] = (udpLen shr 8).toByte()
        pkt[25] = (udpLen and 0xFF).toByte()
        pkt[26] = 0x00  // UDP checksum — optional for IPv4, set to 0
        pkt[27] = 0x00

        // ── DNS Payload ──────────────────────────────────────────────────────
        System.arraycopy(dnsData, 0, pkt, 28, dnsData.size)

        return pkt
    }

    /**
     * RFC 1071 one's-complement Internet checksum.
     */
    private fun internetChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i   = offset
        while (i < offset + length - 1) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i   += 2
        }
        // If odd length, pad last byte
        if ((length and 1) != 0) {
            sum += (data[offset + length - 1].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun ByteArray.getUShort(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            "Ad Blocker VPN",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shown while the DNS ad blocker is active" }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun updateNotification() {
        val currentState = running
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIF_ID, buildNotification(currentState))
    }

    private fun buildNotification(running: Boolean): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, AdBlockVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_slash_notif)
            .setContentTitle(if (running) "Ad Blocker Active" else "Ad Blocker Stopped")
            .setContentText(
                if (running) "Blocking active — ${"%,d".format(blockedCount)} queries filtered"
                else "Ad blocker stopped"
            )
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_close, "Stop", stopIntent)
            .setOngoing(running)
            .build()
    }

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
}
