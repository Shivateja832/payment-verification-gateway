package com.shiva.gateway.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface PaymentRepository : JpaRepository<Payment, UUID> {
    fun findByIdempotencyKey(idempotencyKey: String): Payment?
    fun findByProviderOrderId(providerOrderId: String): Payment?
    fun findByStatusAndUpdatedAtBefore(status: PaymentStatus, before: Instant): List<Payment>
}

interface ProcessedEventRepository : JpaRepository<ProcessedEvent, String>
