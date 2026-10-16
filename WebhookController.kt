package com.shiva.gateway.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.shiva.gateway.config.GatewayProperties
import com.shiva.gateway.messaging.PaymentEvent
import com.shiva.gateway.messaging.PaymentEventPublisher
import com.shiva.gateway.service.SignatureVerifier
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.util.UUID

@RestController
@RequestMapping("/api/v1/webhooks")
class WebhookController(
    private val props: GatewayProperties,
    private val mapper: ObjectMapper,
    private val publisher: PaymentEventPublisher,
) {

    /** Receives Razorpay webhooks: verify signature on the raw body, then hand off to the queue and ack fast. */
    @PostMapping("/razorpay")
    fun razorpay(
        @RequestBody rawBody: String,
        @RequestHeader("X-Razorpay-Signature", required = false) signature: String?,
        @RequestHeader("X-Razorpay-Event-Id", required = false) eventId: String?,
    ): ResponseEntity<Map<String, String>> {
        if (!SignatureVerifier.verify(rawBody, signature, props.razorpay.webhookSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "invalid signature"))
        }
        val root = mapper.readTree(rawBody)
        val type = root.path("event").asText("")
        val orderId = root.path("payload").path("payment").path("entity").path("order_id").asText("")
        if (type.isBlank() || orderId.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "unsupported payload"))
        }
        val id = eventId ?: UUID.nameUUIDFromBytes(rawBody.toByteArray(StandardCharsets.UTF_8)).toString()
        publisher.publish(PaymentEvent(id, type, orderId))
        return ResponseEntity.ok(mapOf("status" to "accepted"))
    }
}
