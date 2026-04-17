package com.dev.adblocker

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
