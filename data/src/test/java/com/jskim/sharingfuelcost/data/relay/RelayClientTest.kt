package com.jskim.sharingfuelcost.data.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayClientTest {

    @Test
    fun `blank relay url is not configured`() {
        assertFalse(RelayClient("  ", appCheckToken = { null }).isConfigured)
    }

    @Test
    fun `prefix is appended whether or not relay url ends with slash`() {
        val withSlash = RelayClient("https://relay.example.workers.dev/", appCheckToken = { null })
        val withoutSlash = RelayClient("https://relay.example.workers.dev", appCheckToken = { null })
        assertTrue(withSlash.isConfigured)
        assertEquals("https://relay.example.workers.dev/opinet/", withSlash.retrofit("opinet").baseUrl().toString())
        assertEquals("https://relay.example.workers.dev/opinet/", withoutSlash.retrofit("opinet").baseUrl().toString())
    }
}
