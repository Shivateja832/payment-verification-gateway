package com.shiva.gateway.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitConfig {

    companion object {
        const val EXCHANGE = "payments.exchange"
        const val QUEUE = "payments.events"
        const val ROUTING_KEY = "payments.event"
        const val DLX = "payments.dlx"
        const val DLQ = "payments.events.dlq"
        const val DLQ_ROUTING_KEY = "payments.event.dead"
    }

    @Bean
    fun paymentsExchange() = DirectExchange(EXCHANGE)

    @Bean
    fun deadLetterExchange() = DirectExchange(DLX)

    // Messages rejected after all retries are routed to the DLQ by the broker.
    @Bean
    fun paymentsQueue(): Queue = QueueBuilder.durable(QUEUE)
        .withArgument("x-dead-letter-exchange", DLX)
        .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
        .build()

    @Bean
    fun deadLetterQueue(): Queue = QueueBuilder.durable(DLQ).build()

    @Bean
    fun paymentsBinding(): Binding =
        BindingBuilder.bind(paymentsQueue()).to(paymentsExchange()).with(ROUTING_KEY)

    @Bean
    fun deadLetterBinding(): Binding =
        BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DLQ_ROUTING_KEY)

    @Bean
    fun messageConverter(mapper: ObjectMapper): MessageConverter = Jackson2JsonMessageConverter(mapper)
}
