package com.shiva.gateway.provider

import com.fasterxml.jackson.databind.JsonNode
import com.shiva.gateway.config.GatewayProperties
import com.shiva.gateway.domain.PaymentStatus
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.UUID

interface PaymentProvider {
    /** Creates an order at the provider and returns the provider's order id. */
    fun createOrder(paymentId: UUID, amountPaise: Long, currency: String): String

    /** Fetches the current order status from the provider (used by reconciliation). */
    fun fetchOrderStatus(orderId: String): PaymentStatus
}

/**
 * Razorpay sandbox (test mode) integration.
 * If no key id is configured it falls back to simulated orders so the app runs locally without credentials.
 */
@Component
class RazorpayProvider(private val props: GatewayProperties) : PaymentProvider {

    private val log = LoggerFactory.getLogger(javaClass)
    private val simulated = props.razorpay.keyId.isBlank()

    private val client: RestClient by lazy {
        RestClient.builder()
            .baseUrl(props.razorpay.baseUrl)
            .defaultHeaders { it.setBasicAuth(props.razorpay.keyId, props.razorpay.keySecret) }
            .build()
    }

    override fun createOrder(paymentId: UUID, amountPaise: Long, currency: String): String {
        if (simulated) {
            log.info("Razorpay keys not set - creating simulated order for payment {}", paymentId)
            return "order_sim_${paymentId.toString().replace("-", "").take(14)}"
        }
        val body = mapOf("amount" to amountPaise, "currency" to currency, "receipt" to paymentId.toString())
        val node = client.post()
            .uri("/v1/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode::class.java)
        return node?.get("id")?.asText() ?: error("Razorpay response did not contain an order id")
    }

    override fun fetchOrderStatus(orderId: String): PaymentStatus {
        if (simulated) return PaymentStatus.PENDING
        val node = client.get().uri("/v1/orders/{id}", orderId).retrieve().body(JsonNode::class.java)
        return when (node?.get("status")?.asText()) {
            "paid" -> PaymentStatus.CAPTURED
            else -> PaymentStatus.PENDING
        }
    }
}
