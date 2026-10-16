package com.shiva.gateway.messaging

import com.shiva.gateway.config.RabbitConfig
import com.shiva.gateway.domain.PaymentStatus
import com.shiva.gateway.domain.ProcessedEvent
import com.shiva.gateway.domain.ProcessedEventRepository
import com.shiva.gateway.service.PaymentService
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class PaymentEvent(val eventId: String, val type: String, val providerOrderId: String)

@Component
class PaymentEventPublisher(private val rabbit: RabbitTemplate) {
    fun publish(event: PaymentEvent) =
        rabbit.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, event)
}

/**
 * Event consumer. Failures are retried (spring.rabbitmq.listener.simple.retry); once retries are
 * exhausted the message is rejected without requeue and the broker routes it to the dead-letter queue.
 * Processing is idempotent: an already-processed eventId is skipped.
 */
@Component
class PaymentEventConsumer(
    private val service: PaymentService,
    private val processed: ProcessedEventRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = [RabbitConfig.QUEUE])
    @Transactional
    fun handle(event: PaymentEvent) {
        if (processed.existsById(event.eventId)) {
            log.info("Skipping duplicate event {}", event.eventId)
            return
        }
        val status = when (event.type) {
            "payment.captured", "order.paid" -> PaymentStatus.CAPTURED
            "payment.failed" -> PaymentStatus.FAILED
            else -> null
        }
        if (status != null) service.applyStatus(event.providerOrderId, status)
        else log.info("Ignoring event type {}", event.type)
        processed.save(ProcessedEvent(event.eventId))
    }
}
