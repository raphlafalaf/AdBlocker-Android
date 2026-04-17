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

    private val IP_LIKE_REGEX = Regex("^[0-9.]+$")

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
            if (IP_LIKE_REGEX.matches(candidate)) continue
            if (!DOMAIN_REGEX.matches(candidate)) continue

            out.add(candidate)
        }
        return out
    }
}
