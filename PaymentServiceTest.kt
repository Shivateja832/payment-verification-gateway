package com.shiva.gateway

import com.shiva.gateway.domain.PaymentRepository
import com.shiva.gateway.domain.PaymentStatus
import com.shiva.gateway.provider.KycClient
import com.shiva.gateway.provider.KycResult
import com.shiva.gateway.provider.PaymentProvider
import com.shiva.gateway.service.CreatePaymentCommand
import com.shiva.gateway.service.IdempotencyConflictException
import com.shiva.gateway.service.PaymentNotFoundException
import com.shiva.gateway.service.PaymentService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import java.util.UUID

private class FakeKyc : KycClient {
    override fun verify(customerId: String) =
        if (customerId.startsWith("blocked-")) KycResult(false, "blocked") else KycResult(true)
}

private class FakeProvider : PaymentProvider {
    var createCalls = 0
    override fun createOrder(paymentId: UUID, amountPaise: Long, currency: String): String {
        createCalls++
        return "order_test_$createCalls"
    }
    override fun fetchOrderStatus(orderId: String) = PaymentStatus.PENDING
}

@DataJpaTest(properties = ["spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"])
class PaymentServiceTest @Autowired constructor(private val repo: PaymentRepository) {

    private val provider = FakeProvider()
    private val service by lazy { PaymentService(repo, FakeKyc(), provider) }
    private val cmd = CreatePaymentCommand("cust-1", 50_000, "INR")

    @Test
    fun `same idempotency key returns same payment and calls provider once`() {
        val first = service.create("key-1", cmd)
        val second = service.create("key-1", cmd)

        assertEquals(first.id, second.id)
        assertEquals(1, provider.createCalls)
        assertEquals(PaymentStatus.PENDING, first.status)
    }

    @Test
    fun `reusing a key with a different body is a conflict`() {
        service.create("key-2", cmd)
        assertThrows<IdempotencyConflictException> {
            service.create("key-2", cmd.copy(amountPaise = 99_900))
        }
    }

    @Test
    fun `kyc rejected customer never reaches the provider`() {
        val payment = service.create("key-3", cmd.copy(customerId = "blocked-42"))

        assertEquals(PaymentStatus.REJECTED_KYC, payment.status)
        assertEquals(0, provider.createCalls)
    }

    @Test
    fun `captured payment is not downgraded by a later failed event`() {
        val payment = service.create("key-4", cmd)
        val orderId = payment.providerOrderId!!

        service.applyStatus(orderId, PaymentStatus.CAPTURED)
        service.applyStatus(orderId, PaymentStatus.FAILED)

        assertEquals(PaymentStatus.CAPTURED, service.get(payment.id).status)
    }

    @Test
    fun `unknown provider order raises not found so the message can be retried and dead-lettered`() {
        assertThrows<PaymentNotFoundException> {
            service.applyStatus("order_missing", PaymentStatus.CAPTURED)
        }
    }
}
