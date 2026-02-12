package org.vechain.indexer.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.annotations.Hidden
import jakarta.servlet.RequestDispatcher
import jakarta.servlet.http.HttpServletRequest
import java.nio.ByteBuffer
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.server.handler.ErrorHandler
import org.eclipse.jetty.util.Callback
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.boot.web.servlet.error.ErrorController
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.exception.ExceptionResponse

/**
 * Custom error controller that returns JSON responses for errors forwarded by the servlet container
 * (e.g. 404s, errors dispatched to /error). Hidden from OpenAPI to prevent the /error endpoint from
 * appearing in the API schema.
 */
@Hidden
@RestController
class CustomErrorController : ErrorController {

    @RequestMapping("/error", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun handleError(request: HttpServletRequest): ResponseEntity<ExceptionResponse> {
        val statusCode =
            request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) as? Int
                ?: HttpStatus.INTERNAL_SERVER_ERROR.value()

        val status = HttpStatus.resolve(statusCode) ?: HttpStatus.INTERNAL_SERVER_ERROR

        val message =
            if (status.is4xxClientError) {
                request.getAttribute(RequestDispatcher.ERROR_MESSAGE) as? String
                    ?: status.reasonPhrase
            } else {
                null
            }

        val path =
            request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) as? String
                ?: request.requestURI

        val response =
            ExceptionResponse(
                path = path,
                status = status.value(),
                error = status.reasonPhrase,
                message = message,
            )

        return ResponseEntity(response, status)
    }
}

/**
 * Configures Jetty's ErrorHandler to return JSON instead of HTML for errors rejected at the HTTP
 * layer before reaching Spring's dispatcher servlet (e.g. suspicious path characters, ambiguous URI
 * encoding).
 */
@Configuration
open class JettyErrorHandlerConfig {

    @Bean
    open fun jettyCustomizer(
        objectMapper: ObjectMapper
    ): WebServerFactoryCustomizer<JettyServletWebServerFactory> =
        WebServerFactoryCustomizer { factory ->
            factory.addServerCustomizers(
                org.springframework.boot.web.embedded.jetty.JettyServerCustomizer { server ->
                    server.setErrorHandler(JsonErrorHandler(objectMapper))
                }
            )
        }
}

/**
 * Jetty ErrorHandler that overrides [generateResponse] to return JSON responses for errors handled
 * at the HTTP layer before reaching Spring's dispatcher servlet (e.g. suspicious path characters,
 * ambiguous URI encoding).
 */
class JsonErrorHandler(private val objectMapper: ObjectMapper) : ErrorHandler() {

    override fun generateResponse(
        request: Request,
        response: Response,
        code: Int,
        message: String?,
        cause: Throwable?,
        callback: Callback,
    ) {
        val status = HttpStatus.resolve(code) ?: HttpStatus.INTERNAL_SERVER_ERROR

        val errorMessage = if (status.is4xxClientError) message ?: status.reasonPhrase else null

        val errorResponse =
            ExceptionResponse(
                path = Request.getPathInContext(request) ?: "/",
                status = code,
                error = status.reasonPhrase,
                message = errorMessage,
            )

        response.headers.put("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        response.write(
            true,
            ByteBuffer.wrap(objectMapper.writeValueAsBytes(errorResponse)),
            callback,
        )
    }
}
