package app.kluddra.android.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlSecurityPolicyTest {

    @Test
    fun `bare domain defaults to https`() {
        val result = UrlSecurityPolicy.evaluate("notes.example.com/", allowLocalHttpConsent = false)
        assertEquals(
            UrlValidationResult.Valid("https://notes.example.com", isLocalHttp = false),
            result
        )
    }

    @Test
    fun `public http is strictly blocked even with local consent`() {
        val result = UrlSecurityPolicy.evaluate("http://notes.example.com", allowLocalHttpConsent = true)
        assertEquals(UrlValidationResult.PublicHttpBlocked, result)

        val publicIp = UrlSecurityPolicy.evaluate("http://203.0.113.10:8080", allowLocalHttpConsent = true)
        assertEquals(UrlValidationResult.PublicHttpBlocked, publicIp)
    }

    @Test
    fun `private LAN http requires explicit user consent`() {
        val withoutConsent = UrlSecurityPolicy.evaluate("http://192.168.1.50:8080", allowLocalHttpConsent = false)
        assertTrue(withoutConsent is UrlValidationResult.RequiresLocalHttpConsent)

        val withConsent = UrlSecurityPolicy.evaluate("http://192.168.1.50:8080", allowLocalHttpConsent = true)
        assertEquals(
            UrlValidationResult.Valid("http://192.168.1.50:8080", isLocalHttp = true),
            withConsent
        )
    }

    @Test
    fun `rfc1918 and local domain host detection`() {
        assertTrue(UrlSecurityPolicy.isPrivateLocalHost("192.168.0.10"))
        assertTrue(UrlSecurityPolicy.isPrivateLocalHost("10.0.0.5"))
        assertTrue(UrlSecurityPolicy.isPrivateLocalHost("172.16.4.2"))
        assertTrue(UrlSecurityPolicy.isPrivateLocalHost("nas.local"))
        assertTrue(UrlSecurityPolicy.isPrivateLocalHost("localhost"))
        assertFalse(UrlSecurityPolicy.isPrivateLocalHost("172.32.0.1"))
        assertFalse(UrlSecurityPolicy.isPrivateLocalHost("100.64.0.5")) // ISP CGNAT must not allow cleartext HTTP
        assertFalse(UrlSecurityPolicy.isPrivateLocalHost("example.com"))
    }

    @Test
    fun `embedded credentials, query strings, and fragments are rejected`() {
        assertEquals(UrlValidationResult.Invalid, UrlSecurityPolicy.evaluate("https://user:pass@notes.example.com", false))
        assertEquals(UrlValidationResult.Invalid, UrlSecurityPolicy.evaluate("https://notes.example.com?foo=bar", false))
        assertEquals(UrlValidationResult.Invalid, UrlSecurityPolicy.evaluate("https://notes.example.com#frag", false))
    }
}
