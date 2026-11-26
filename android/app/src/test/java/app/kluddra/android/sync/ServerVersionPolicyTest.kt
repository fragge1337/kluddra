package app.kluddra.android.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerVersionPolicyTest {

    @Test
    fun `flags older server versions and older api versions as outdated`() {
        assertTrue(ServerVersionPolicy.evaluate("0.9.5", "1", minRecommended = "1.0.0").isOutdated)
        assertTrue(ServerVersionPolicy.evaluate("v0.9.0-dev", "1", minRecommended = "1.0.0").isOutdated)
        assertTrue(ServerVersionPolicy.evaluate("1.0.0", "1", minRecommended = "1.1.0").isOutdated)
        assertTrue(ServerVersionPolicy.evaluate("1.0.0", "0", minRecommended = "1.0.0").isOutdated)
    }

    @Test
    fun `accepts matching, dev, and newer server versions`() {
        assertFalse(ServerVersionPolicy.evaluate("1.0.0", "1", minRecommended = "1.0.0").isOutdated)
        assertFalse(ServerVersionPolicy.evaluate("1.0.0-dev", "1", minRecommended = "1.0.0").isOutdated)
        assertFalse(ServerVersionPolicy.evaluate("v1.2.3", "1", minRecommended = "1.0.0").isOutdated)
        assertFalse(ServerVersionPolicy.evaluate(null, null, minRecommended = "1.0.0").isOutdated)
    }
}
