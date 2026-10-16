package com.shiva.gateway.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class PaymentStatus { PENDING, CAPTURED, FAILED, REJECTED_KYC }

@Entity
@Table(name = "payments")
class Payment(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    var idempotencyKey: String = "",

    @Column(name = "customer_id", nullable = false, length = 100)
    var customerId: String = "",

    @Column(name = "amount_paise", nullable = false)
    var amountPaise: Long = 0,

    @Column(nullable = false, length = 3)
    var currency: String = "INR",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: PaymentStatus = PaymentStatus.PENDING,

    @Column(name = "provider_order_id", length = 100)
    var providerOrderId: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

@Entity
@Table(name = "processed_events")
class ProcessedEvent(
    @Id
    @Column(name = "event_id", length = 100)
    var eventId: String = "",

    @Column(name = "processed_at", nullable = false)
    var processedAt: Instant = Instant.now(),
)
