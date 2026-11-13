package com.notifyhub

import com.notifyhub.sync.UrlCheck
import com.notifyhub.sync.UrlPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {
    @Test fun httpsAccepted() =
        assertEquals(UrlCheck.Valid("https://notify.example.com"), UrlPolicy.validate(" https://notify.example.com/ ", false))

    @Test fun uppercaseSchemeAccepted() =
        assertTrue(UrlPolicy.validate("HTTPS://Host.example", false) is UrlCheck.Valid)

    @Test fun httpRejectedByDefault() =
        assertTrue(UrlPolicy.validate("http://192.168.1.10:8080", false) is UrlCheck.Invalid)

    @Test fun httpAllowedWhenEnabled() =
        assertEquals(UrlCheck.Valid("http://192.168.1.10:8080"), UrlPolicy.validate("http://192.168.1.10:8080", true))

    @Test fun invalidInputsRejected() {
        listOf("", "   ", "example.com", "ftp://example.com", "https://", "https://exa mple.com").forEach {
            assertTrue("should reject '$it'", UrlPolicy.validate(it, true) is UrlCheck.Invalid)
        }
    }
}
