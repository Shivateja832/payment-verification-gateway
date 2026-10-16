package com.shiva.gateway

import com.shiva.gateway.service.SignatureVerifier
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SignatureVerifierTest {
    private val secret = "whsec_test"
    private val body = """{"event":"payment.captured"}"""

    @Test
    fun `valid signature is accepted`() {
        val sig = SignatureVerifier.sign(body, secret)
        assertTrue(SignatureVerifier.verify(body, sig, secret))
    }

    @Test
    fun `tampered body is rejected`() {
        val sig = SignatureVerifier.sign(body, secret)
        assertFalse(SignatureVerifier.verify("""{"event":"payment.failed"}""", sig, secret))
    }

    @Test
    fun `missing signature or blank secret is rejected`() {
        assertFalse(SignatureVerifier.verify(body, null, secret))
        assertFalse(SignatureVerifier.verify(body, SignatureVerifier.sign(body, secret), ""))
    }
}
