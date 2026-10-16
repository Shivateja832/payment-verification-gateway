package com.shiva.gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gateway")
data class GatewayProperties(
    val razorpay: Razorpay = Razorpay(),
    val reconciliation: Reconciliation = Reconciliation(),
) {
    data class Razorpay(
        val keyId: String = "",
        val keySecret: String = "",
        val webhookSecret: String = "",
        val baseUrl: String = "https://api.razorpay.com",
    )

    data class Reconciliation(
        val staleAfterMinutes: Long = 10,
    )
}
