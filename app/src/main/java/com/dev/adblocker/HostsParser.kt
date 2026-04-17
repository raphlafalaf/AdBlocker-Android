package com.dev.adblocker

/**
 * Pure parser — no Android dependencies.
 *
 * Accepts three formats commonly used by blocklists:
 *  - Hosts format:      "0.0.0.0 domain.com" or "127.0.0.1 domain.com"
 *  - Bare-domain:       "domain.com"
 *  - ABP filter format: "||domain.com^" or "||domain.com^$options"
 *    (as published by OISD and others after discontinuing the hosts format)
 *
 * Strips comments (#, !), ABP metadata ([Adblock …]), blank lines, allowlist
 * rules (@@), URL-pattern rules (/…/), wildcard entries, and common localhost
 * entries. Lowercases everything.
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
            // Comments and ABP metadata headers
            if (line.startsWith("#") || line.startsWith("!") || line.startsWith("[")) continue
            // ABP allowlist rules — skip entirely
            if (line.startsWith("@@")) continue

            val candidate: String

            if (line.startsWith("||")) {
                // ABP format: ||domain.com^ or ||domain.com^$options
                // Extract the domain between "||" and the first "^".
                val afterPipes = line.removePrefix("||")
                val raw = afterPipes.substringBefore("^").lowercase()
                // Skip wildcard entries (e.g. ||ads.*.example.com^)
                if ('*' in raw) continue
                candidate = raw
            } else {
                // Hosts format or bare-domain format.
                // Split on any whitespace. Expect either one token (bare domain)
                // or two+ tokens where the domain is the second.
                val parts = line.split(Regex("\\s+"))
                candidate = when (parts.size) {
                    1 -> parts[0]
                    else -> parts[1]
                }.lowercase()
            }

            if (candidate in LOCALHOST_NAMES) continue
            if (IP_LIKE_REGEX.matches(candidate)) continue
            if (!DOMAIN_REGEX.matches(candidate)) continue

            out.add(candidate)
        }
        return out
    }
}
