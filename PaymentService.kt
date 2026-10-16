package com.shiva.gateway.service

import com.shiva.gateway.domain.Payment
import com.shiva.gateway.domain.PaymentRepository
import com.shiva.gateway.domain.PaymentStatus
import com.shiva.gateway.provider.KycClient
import com.shiva.gateway.provider.PaymentProvider
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

class PaymentNotFoundException(message: String) : RuntimeException(message)
class IdempotencyConflictException(message: String) : RuntimeException(message)

data class CreatePaymentCommand(val customerId: String, val amountPaise: Long, val currency: String)

@Service
class PaymentService(
    private val repo: PaymentRepository,
    private val kyc: KycClient,
    private val provider: PaymentProvider,
) {

    /**
     * Idempotent create: the same Idempotency-Key always returns the same payment and never
     * triggers a second provider call. Reusing a key with a different body is rejected.
     */
    fun create(idempotencyKey: String, cmd: CreatePaymentCommand): Payment {
        repo.findByIdempotencyKey(idempotencyKey)?.let { return ensureSameRequest(it, cmd) }

        val payment = Payment(
            idempotencyKey = idempotencyKey,
            customerId = cmd.customerId,
            amountPaise = cmd.amountPaise,
            currency = cmd.currency,
        )

        val verdict = kyc.verify(cmd.customerId)
        if (!verdict.approved) {
            payment.status = PaymentStatus.REJECTED_KYC
        } else {
            payment.providerOrderId = provider.createOrder(payment.id, cmd.amountPaise, cmd.currency)
            payment.status = PaymentStatus.PENDING
        }

        return try {
            repo.saveAndFlush(payment)
        } catch (e: DataIntegrityViolationException) {
            // A concurrent request with the same key won the race - return its result.
            repo.findByIdempotencyKey(idempotencyKey)?.let { ensureSameRequest(it, cmd) } ?: throw e
        }
    }

    fun get(id: UUID): Payment =
        repo.findById(id).orElseThrow { PaymentNotFoundException("Payment $id not found") }

    /** Applies a provider status; a captured payment is never downgraded. */
    @Transactional
    fun applyStatus(providerOrderId: String, newStatus: PaymentStatus) {
        val payment = repo.findByProviderOrderId(providerOrderId)
            ?: throw PaymentNotFoundException("No payment for provider order $providerOrderId")
        if (payment.status == PaymentStatus.CAPTURED || payment.status == newStatus) return
        payment.status = newStatus
        payment.updatedAt = Instant.now()
        repo.save(payment)
    }

    private fun ensureSameRequest(existing: Payment, cmd: CreatePaymentCommand): Payment {
        if (existing.customerId != cmd.customerId || existing.amountPaise != cmd.amountPaise || existing.currency != cmd.currency) {
            throw IdempotencyConflictException("Idempotency-Key was already used with a different request")
        }
        return existing
    }
}
