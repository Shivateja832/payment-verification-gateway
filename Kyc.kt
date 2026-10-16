package com.shiva.gateway.provider

import org.springframework.stereotype.Component

data class KycResult(val approved: Boolean, val reason: String? = null)

/** Verification vendor abstraction (bank/KYC vendors are integrated behind this interface). */
interface KycClient {
    fun verify(customerId: String): KycResult
}

/** Mock KYC vendor: customers whose id starts with "blocked-" are rejected. */
@Component
class MockKycClient : KycClient {
    override fun verify(customerId: String): KycResult =
        if (customerId.startsWith("blocked-")) KycResult(false, "Customer failed verification")
        else KycResult(true)
}
