package com.shiva.gateway.service

import com.shiva.gateway.config.GatewayProperties
import com.shiva.gateway.domain.PaymentRepository
import com.shiva.gateway.domain.PaymentStatus
import com.shiva.gateway.provider.PaymentProvider
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Background job: re-checks payments stuck in PENDING (e.g. a missed webhook) against the provider. */
@Component
class ReconciliationJob(
    private val repo: PaymentRepository,
    private val provider: PaymentProvider,
    private val service: PaymentService,
    private val props: GatewayProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${gateway.reconciliation.fixed-delay-ms:60000}")
    fun reconcile() {
        val cutoff = Instant.now().minus(props.reconciliation.staleAfterMinutes, ChronoUnit.MINUTES)
        val stale = repo.findByStatusAndUpdatedAtBefore(PaymentStatus.PENDING, cutoff)
        stale.forEach { payment ->
            val orderId = payment.providerOrderId ?: return@forEach
            try {
                val status = provider.fetchOrderStatus(orderId)
                if (status != PaymentStatus.PENDING) {
                    service.applyStatus(orderId, status)
                    log.info("Reconciled payment {} -> {}", payment.id, status)
                }
            } catch (e: Exception) {
                log.warn("Reconciliation failed for payment {}: {}", payment.id, e.message)
            }
        }
    }
}
