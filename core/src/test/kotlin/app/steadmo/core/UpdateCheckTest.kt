package app.steadmo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    @Test fun `parses versionCode from GitHub latest release redirect URL`() {
        assertEquals(92, UpdateCheck.parseVersionCode("https://github.com/mikelward/steadmo/releases/tag/v92"))
        assertEquals(123, UpdateCheck.parseVersionCode("/mikelward/steadmo/releases/tag/v123"))
        assertEquals(7, UpdateCheck.parseVersionCode("releases/tag/v7"))
    }

    @Test fun `strips trailing slash, query string and fragment`() {
        assertEquals(93, UpdateCheck.parseVersionCode("https://github.com/mikelward/steadmo/releases/tag/v93/"))
        assertEquals(93, UpdateCheck.parseVersionCode("https://github.com/mikelward/steadmo/releases/tag/v93?from=latest#top"))
        assertEquals(93, UpdateCheck.parseVersionCode("  https://github.com/mikelward/steadmo/releases/tag/v93  "))
    }

    @Test fun `rejects missing, non-numeric, zero or malformed tags`() {
        assertNull(UpdateCheck.parseVersionCode(null))
        assertNull(UpdateCheck.parseVersionCode(""))
        assertNull(UpdateCheck.parseVersionCode("   "))
        assertNull(UpdateCheck.parseVersionCode("https://github.com/mikelward/steadmo/releases"))
        assertNull(UpdateCheck.parseVersionCode("https://github.com/mikelward/steadmo/releases/tag/v0"))
        assertNull(UpdateCheck.parseVersionCode("https://github.com/mikelward/steadmo/releases/tag/v1.2.3"))
        assertNull(UpdateCheck.parseVersionCode("https://github.com/mikelward/steadmo/releases/tag/v92-beta"))
        assertNull(UpdateCheck.parseVersionCode("https://github.com/mikelward/steadmo/releases/tag/v999999999999"))
        assertNull(UpdateCheck.parseVersionCode("https://github.com/mikelward/steadmo/notreleases/tag/v93"))
        assertNull(UpdateCheck.parseVersionCode("https://github.com/mikelward/steadmo/releases/tag/v93/extra"))
    }

    @Test fun `reports update available only when latest versionCode is strictly newer`() {
        assertTrue(UpdateCheck.isUpdateAvailable(92, "https://github.com/mikelward/steadmo/releases/tag/v93"))
        assertFalse(UpdateCheck.isUpdateAvailable(92, "https://github.com/mikelward/steadmo/releases/tag/v92"))
        assertFalse(UpdateCheck.isUpdateAvailable(92, "https://github.com/mikelward/steadmo/releases/tag/v91"))
        assertFalse(UpdateCheck.isUpdateAvailable(92, null))
        assertFalse(UpdateCheck.isUpdateAvailable(92, "https://github.com/mikelward/steadmo/releases"))
    }
}
