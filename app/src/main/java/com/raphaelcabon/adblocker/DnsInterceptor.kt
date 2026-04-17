package com.raphaelcabon.adblocker

/**
 * DnsInterceptor — pure DNS packet utility functions.
 *
 * DNS wire format (RFC 1035):
 *  Header  : 12 bytes
 *    [0-1]  Transaction ID
 *    [2-3]  Flags
 *    [4-5]  QDCOUNT (number of questions)
 *    [6-7]  ANCOUNT (number of answer RRs)
 *    [8-9]  NSCOUNT (authority RRs)
 *    [10-11] ARCOUNT (additional RRs)
 *  Questions section: variable
 *    QNAME  : sequence of length-prefixed labels, terminated by 0x00
 *    QTYPE  : 2 bytes
 *    QCLASS : 2 bytes
 */
object DnsInterceptor {

    /**
     * Parses the first question's domain name from a raw DNS query payload.
     *
     * Returns the fully-qualified domain name in lowercase (e.g. "ads.example.com"),
     * or null if the packet is malformed or is not a query.
     */
    fun parseQueryDomain(dns: ByteArray): String? {
        if (dns.size < 12) return null

        // Flags: QR bit must be 0 (query, not response)
        val flags = dns.getUShort(2)
        if ((flags and 0x8000) != 0) return null  // It's a response — ignore

        // QDCOUNT must be >= 1
        val qdCount = dns.getUShort(4)
        if (qdCount < 1) return null

        // Parse QNAME starting at offset 12
        val sb  = StringBuilder()
        var pos = 12

        while (pos < dns.size) {
            val labelLen = dns[pos].toInt() and 0xFF
            if (labelLen == 0) break   // Root label — end of QNAME
            if (labelLen and 0xC0 == 0xC0) return null  // Pointer compression not expected in queries
            pos++
            if (pos + labelLen > dns.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(dns, pos, labelLen, Charsets.US_ASCII))
            pos += labelLen
        }

        return if (sb.isEmpty()) null else sb.toString().lowercase()
    }

    /**
     * Builds a minimal NXDOMAIN (Name Error) response for the given query.
     *
     * The response:
     *  - Keeps the same Transaction ID
     *  - Sets QR=1 (response), RA=1 (recursion available), RCODE=3 (NXDOMAIN)
     *  - Copies the question section unchanged
     *  - Returns zero answer RRs
     *
     * This tells the requesting app "that domain does not exist", which causes
     * it to silently give up on the ad/tracker request.
     */
    fun buildNxdomainResponse(query: ByteArray): ByteArray {
        val response = query.copyOf()

        // Flags byte 0: set QR bit (0x80), keep OPCODE and RD as-is
        response[2] = (response[2].toInt() or 0x80).toByte()

        // Flags byte 1: set RA (0x80) and RCODE=3 (NXDOMAIN)
        // Clear lower 4 bits (existing RCODE) then set 0x83
        response[3] = ((response[3].toInt() and 0xF0) or 0x83).toByte()

        // Zero out the answer, authority, and additional record counts
        response[6]  = 0; response[7]  = 0  // ANCOUNT
        response[8]  = 0; response[9]  = 0  // NSCOUNT
        response[10] = 0; response[11] = 0  // ARCOUNT

        return response
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun ByteArray.getUShort(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)
}
