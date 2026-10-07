package org.vechain.indexer.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import org.vechain.indexer.exception.AbstractHttpException
import org.vechain.indexer.exception.ExceptionResponse
import org.vechain.indexer.exception.PriceFeedUnavailableException
import org.vechain.indexer.exception.WindowNotIndexedException
import org.vechain.indexer.rest.CachePolicy

@RestControllerAdvice
open class ExceptionResponseConfig : ResponseEntityExceptionHandler() {

    /** Handles our custom HTTP exceptions. */
    @ExceptionHandler(value = [AbstractHttpException::class])
    protected fun handleHttpException(
        req: HttpServletRequest,
        ex: AbstractHttpException,
    ): ResponseEntity<ExceptionResponse> {

        /** If a client error, return the message from the exception. */
        val message = if (ex.status.is4xxClientError) ex.message else null
        val path = req.requestURI ?: req.servletPath

        val response =
            ExceptionResponse(
                path = path,
                status = ex.status.value(),
                error = ex.status.reasonPhrase,
                message = message,
            )

        this.logger.warn(
            "HTTP ${ex.status.value()} ($path) ${ex.status.reasonPhrase}: (id=${response.id}) - ${ex.message}"
        )

        return ResponseEntity(response, ex.status)
    }

    /** Handles constraint violations. Eg Invalid address */
    @ExceptionHandler(value = [ConstraintViolationException::class])
    protected fun handleConstrainViolation(
        req: HttpServletRequest,
        ex: ConstraintViolationException,
    ): ResponseEntity<ExceptionResponse> {

        val status = HttpStatus.BAD_REQUEST
        val message = ex.constraintViolations.joinToString { it.message }
        val path = req.requestURI ?: req.servletPath

        val response =
            ExceptionResponse(
                path = path,
                status = status.value(),
                error = status.reasonPhrase,
                message = message,
            )

        this.logger.warn(
            "HTTP ${status.value()} ($path) ${status.reasonPhrase}: (id=${response.id}) - $message"
        )

        return ResponseEntity(response, status)
    }

    /** Handles type mismatch on method arguments, eg invalid enum values. */
    @ExceptionHandler(value = [MethodArgumentTypeMismatchException::class])
    protected fun handleMethodArgumentTypeMismatch(
        req: HttpServletRequest,
        ex: MethodArgumentTypeMismatchException,
    ): ResponseEntity<ExceptionResponse> {

        val status = HttpStatus.BAD_REQUEST
        val path = req.requestURI ?: req.servletPath
        val message = "Invalid value '${ex.value}' for parameter '${ex.name}'"

        val response =
            ExceptionResponse(
                path = path,
                status = status.value(),
                error = status.reasonPhrase,
                message = message,
            )

        this.logger.warn(
            "HTTP ${status.value()} ($path) ${status.reasonPhrase}: (id=${response.id}) - $message"
        )

        return ResponseEntity(response, status)
    }

    /**
     * Maps upstream price-oracle failures to 503 so callers see an explicit unavailability signal.
     */
    @ExceptionHandler(value = [PriceFeedUnavailableException::class])
    protected fun handlePriceFeedUnavailable(
        req: HttpServletRequest,
        ex: PriceFeedUnavailableException,
    ): ResponseEntity<ExceptionResponse> {

        val status = HttpStatus.SERVICE_UNAVAILABLE
        val path = req.requestURI ?: req.servletPath

        val response =
            ExceptionResponse(
                path = path,
                status = status.value(),
                error = status.reasonPhrase,
                message = null,
            )

        this.logger.warn(
            "HTTP ${status.value()} ($path) ${status.reasonPhrase}: (id=${response.id}) - ${ex.message}"
        )

        return ResponseEntity(response, status)
    }

    /** Too early is routine, so a 4xx that says how far the index is, held at the edge briefly. */
    @ExceptionHandler(value = [WindowNotIndexedException::class])
    protected fun handleWindowNotIndexed(
        req: HttpServletRequest,
        ex: WindowNotIndexedException,
    ): ResponseEntity<ExceptionResponse> {

        val path = req.requestURI ?: req.servletPath

        val response =
            ExceptionResponse(
                path = path,
                status = ex.status.value(),
                error = ex.status.reasonPhrase,
                message = ex.message,
            )

        this.logger.warn(
            "HTTP ${ex.status.value()} ($path) ${ex.status.reasonPhrase}: (id=${response.id}) - ${ex.message}"
        )

        val headers = HttpHeaders()
        headers.set(HttpHeaders.CACHE_CONTROL, CachePolicy.VOLATILE.headerValue)
        ex.indexedThrough?.let {
            headers.set(WindowNotIndexedException.INDEXED_THROUGH_HEADER, it.toString())
        }
        return ResponseEntity(response, headers, ex.status)
    }

    /** Handles all other exceptions. */
    @ExceptionHandler(value = [Exception::class])
    protected fun handleException(
        req: HttpServletRequest,
        ex: Exception,
    ): ResponseEntity<ExceptionResponse> {

        val status = HttpStatus.INTERNAL_SERVER_ERROR
        val path = req.requestURI ?: req.servletPath

        val response =
            ExceptionResponse(
                path = path,
                status = status.value(),
                error = status.reasonPhrase,
                message = null,
            )

        this.logger.warn(
            "HTTP ${status.value()} ($path) ${status.reasonPhrase}: (id=${response.id}) - ${ex.message}",
            ex,
        )

        return ResponseEntity(response, status)
    }
}
