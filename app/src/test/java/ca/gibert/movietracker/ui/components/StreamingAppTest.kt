package ca.gibert.movietracker.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingAppTest {

    @Test
    fun `unknown or missing networks have no app`() {
        assertNull(streamingAppFor("Random Network"))
        assertNull(streamingAppFor(""))
        assertNull(streamingAppFor("   "))
        assertNull(streamingAppFor(null))
    }

    @Test
    fun `streaming services resolve to their own app`() {
        assertEquals("Netflix", streamingAppFor("Netflix")?.name)
        assertEquals("Disney+", streamingAppFor("Disney+")?.name)
        assertEquals("Prime Video", streamingAppFor("Amazon")?.name)
        assertEquals("Apple TV", streamingAppFor("Apple TV+")?.name)
        assertEquals("Apple TV", streamingAppFor("Apple")?.name)
        assertEquals("Max", streamingAppFor("HBO Max")?.name)
    }

    @Test
    fun `networks resolve to the service that carries them`() {
        assertEquals("Max", streamingAppFor("HBO")?.name)
        assertEquals("Hulu", streamingAppFor("FX on Hulu")?.name)
        assertEquals("Hulu", streamingAppFor("FX")?.name)
        assertEquals("Paramount+", streamingAppFor("CBS")?.name)
        assertEquals("Paramount+", streamingAppFor("Showtime")?.name)
        assertEquals("Peacock", streamingAppFor("NBC")?.name)
    }

    @Test
    fun `matching is case insensitive`() {
        assertEquals("Netflix", streamingAppFor("NETFLIX")?.name)
    }

    @Test
    fun `every package is declared in the manifest queries`() {
        // Unit tests run with the module directory as the working directory.
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val missing = allStreamingApps
            .flatMap { it.packages }
            .filterNot { "<package android:name=\"$it\" />" in manifest }
        assertTrue("Add to <queries> in AndroidManifest.xml: $missing", missing.isEmpty())
    }
}
