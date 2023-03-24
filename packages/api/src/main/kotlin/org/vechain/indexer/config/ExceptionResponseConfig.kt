package org.vechain.indexer.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import org.vechain.indexer.exception.AbstractHttpException
import org.vechain.indexer.exception.ExceptionResponse

@RestControllerAdvice
open class ExceptionResponseConfig : ResponseEntityExceptionHandler() {

    /**
     * Handles our custom HTTP exceptions.
     */
    @ExceptionHandler(value = [AbstractHttpException::class])
    protected fun handleHttpException(
        req: HttpServletRequest, ex: AbstractHttpException
    ): ResponseEntity<ExceptionResponse> {

        /**
         * If a client error, return the message from the exception.
         */
        val message = if (ex.status.is4xxClientError) ex.message
        else null


        val response = ExceptionResponse(
            path = req.servletPath, status = ex.status.value(), error = ex.status.reasonPhrase, message = message
        )

        this.logger.warn(
            "HTTP ${ex.status.value()} ${ex.status.reasonPhrase}: ${ex.message} (path: ${req.servletPath})", ex
        )

        return ResponseEntity(response, ex.status)
    }

    /**
     * Handles constraint violations
     */
    @ExceptionHandler(value = [ConstraintViolationException::class])
    protected fun handleConstrainViolation(
        req: HttpServletRequest, ex: ConstraintViolationException
    ): ResponseEntity<ExceptionResponse> {

        val status = HttpStatus.BAD_REQUEST
        val message = ex.constraintViolations.joinToString { it.message }

        val response = ExceptionResponse(
            path = req.servletPath, status = status.value(), error = status.reasonPhrase, message = message
        )

        this.logger.warn(
            "HTTP ${status.value()} ${status.reasonPhrase}: ${message} (path: ${req.servletPath})", ex
        )

        return ResponseEntity(response, status)
    }
}