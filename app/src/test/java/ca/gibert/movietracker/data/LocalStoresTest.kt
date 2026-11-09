package ca.gibert.movietracker.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalStoresTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `service overrides round trip and skip blanks`() {
        val overrides = listOf(
            ServiceOverride(trakt = 1, title = "Heat", service = " Plex "),
            ServiceOverride(trakt = 2, title = "Alien", service = ""),
        )
        val decoded = decodeServiceOverrides(json, encodeServiceOverrides(json, overrides))
        assertEquals(overrides, decoded)
        assertEquals(mapOf(1 to "Plex"), decoded.toOverrideMap())
    }

    @Test
    fun `corrupt service overrides decode to nothing`() {
        assertTrue(decodeServiceOverrides(json, "{not json").isEmpty())
        assertTrue(decodeServiceOverrides(json, null).isEmpty())
    }

    @Test
    fun `regions must be two letter codes`() {
        assertEquals("CA", normalizeRegion("ca"))
        assertEquals("US", normalizeRegion(" us "))
        assertNull(normalizeRegion("USA"))
        assertNull(normalizeRegion("1A"))
        assertNull(normalizeRegion(""))
        assertNull(normalizeRegion(null))
    }
}
