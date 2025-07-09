package io.hhplus.tdd

import io.hhplus.tdd.dto.CommonResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

data class ErrorResponse(val code: String, val message: String)

@RestControllerAdvice
class ApiControllerAdvice : ResponseEntityExceptionHandler() {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(e: IllegalArgumentException): ResponseEntity<CommonResponse<Nothing>> {
        logger.warn("IllegalArgumentException occurred: ${e.message}")
        return ResponseEntity.badRequest().body(
            CommonResponse.error(e.message ?: "잘못된 요청입니다.")
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<CommonResponse<Nothing>> {
        logger.error("Unexpected error occurred", e)
        return ResponseEntity.internalServerError().body(
            CommonResponse.error("서버 오류가 발생했습니다.")
        )
    }
}