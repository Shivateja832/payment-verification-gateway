package com.shiva.gateway.web

import com.shiva.gateway.domain.Payment
import com.shiva.gateway.service.CreatePaymentCommand
import com.shiva.gateway.service.PaymentService
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class CreatePaymentRequest(
    @field:NotBlank val customerId: String,
    @field:Min(100) val amountPaise: Long,
    @field:Size(min = 3, max = 3) val currency: String = "INR",
)

data class PaymentResponse(
    val id: UUID,
    val customerId: String,
    val amountPaise: Long,
    val currency: String,
    val status: String,
    val providerOrderId: String?,
)

private fun Payment.toResponse() =
    PaymentResponse(id, customerId, amountPaise, currency, status.name, providerOrderId)

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(private val service: PaymentService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: CreatePaymentRequest,
    ): PaymentResponse =
        service.create(
            idempotencyKey.trim(),
            CreatePaymentCommand(request.customerId, request.amountPaise, request.currency),
        ).toResponse()

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): PaymentResponse = service.get(id).toResponse()
}
