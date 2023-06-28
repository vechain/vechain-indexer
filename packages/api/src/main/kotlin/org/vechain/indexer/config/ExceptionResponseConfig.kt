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

    /** Handles our custom HTTP exceptions. */
    @ExceptionHandler(value = [AbstractHttpException::class])
    protected fun handleHttpException(
      req: HttpServletRequest,
      ex: AbstractHttpException
    ): ResponseEntity<ExceptionResponse> {

        /** If a client error, return the message from the exception. */
        val message = if (ex.status.is4xxClientError) ex.message else null

        val response =
          ExceptionResponse(
            path = req.servletPath,
            status = ex.status.value(),
            error = ex.status.reasonPhrase,
            message = message
          )

        this.logger.warn(
          "HTTP ${ex.status.value()} (${req.servletPath}) ${ex.status.reasonPhrase}: (id=${response.id}) - ${ex.message}"
        )

        return ResponseEntity(response, ex.status)
    }

    /** Handles constraint violations. Eg Invalid address */
    @ExceptionHandler(value = [ConstraintViolationException::class])
    protected fun handleConstrainViolation(
      req: HttpServletRequest,
      ex: ConstraintViolationException
    ): ResponseEntity<ExceptionResponse> {

        val status = HttpStatus.BAD_REQUEST
        val message = ex.constraintViolations.joinToString { it.message }

        val response =
          ExceptionResponse(
            path = req.servletPath,
            status = status.value(),
            error = status.reasonPhrase,
            message = message
          )

        this.logger.warn(
          "HTTP ${status.value()} (${req.servletPath}) ${status.reasonPhrase}: (id=${response.id}) - $message"
        )

        return ResponseEntity(response, status)
    }

    /** Handles all other exceptions. */
    @ExceptionHandler(value = [Exception::class])
    protected fun handleException(
      req: HttpServletRequest,
      ex: Exception
    ): ResponseEntity<ExceptionResponse> {

        val status = HttpStatus.INTERNAL_SERVER_ERROR

        val response =
          ExceptionResponse(
            path = req.servletPath,
            status = status.value(),
            error = status.reasonPhrase,
            message = null
          )

        this.logger.warn(
          "HTTP ${status.value()} (${req.servletPath}) ${status.reasonPhrase}: (id=${response.id}) - ${ex.message}",
          ex
        )

        return ResponseEntity(response, status)
    }
}
