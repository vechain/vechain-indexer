package org.vechain.indexer.config

import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJacksonValue
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.AbstractMappingJacksonResponseBodyAdvice
import org.vechain.indexer.constants.TRANSACTIONS_PATH
import org.vechain.indexer.thor.model.Views

@RestControllerAdvice
open class ResponseMappingControllerAdvice : AbstractMappingJacksonResponseBodyAdvice() {

    override fun beforeBodyWriteInternal(
        bodyContainer: MappingJacksonValue,
        contentType: MediaType,
        returnType: MethodParameter,
        request: ServerHttpRequest,
        response: ServerHttpResponse,
    ) {
        val req = request as ServletServerHttpRequest

        if (request.servletRequest.requestURI.startsWith(TRANSACTIONS_PATH, ignoreCase = true)) {
            val isExpanded = req.servletRequest.getParameter("expanded").toBoolean()

            if (isExpanded) bodyContainer.serializationView = Views.Expanded::class.java
            else bodyContainer.serializationView = Views.Public::class.java
        }
    }
}
