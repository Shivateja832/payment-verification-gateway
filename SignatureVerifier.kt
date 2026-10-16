package com.shiva.gateway.service

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Verifies webhook authenticity: HMAC-SHA256(body, secret) compared in constant time. */
object SignatureVerifier {
    fun sign(body: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(body.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun verify(body: String, signature: String?, secret: String): Boolean {
        if (secret.isBlank() || signature.isNullOrBlank()) return false
        val expected = sign(body, secret).toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.isEqual(expected, signature.trim().toByteArray(StandardCharsets.UTF_8))
    }
}
