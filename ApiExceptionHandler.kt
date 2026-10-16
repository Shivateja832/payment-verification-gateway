package com.shiva.gateway.web

import com.shiva.gateway.service.IdempotencyConflictException
import com.shiva.gateway.service.PaymentNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException::class)
    fun notFound(e: PaymentNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to (e.message ?: "not found")))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun conflict(e: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to (e.message ?: "conflict")))
}
