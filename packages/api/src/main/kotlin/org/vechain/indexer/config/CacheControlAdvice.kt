package org.vechain.indexer.config

import org.springframework.core.MethodParameter
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpResponse
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy

/** Writes the `Cache-Control` each handler's [CacheFor] declares, unless it granted its own. */
@RestControllerAdvice
open class CacheControlAdvice : ResponseBodyAdvice<Any> {

    override fun supports(
        returnType: MethodParameter,
        converterType: Class<out HttpMessageConverter<*>>,
    ): Boolean = returnType.hasMethodAnnotation(CacheFor::class.java)

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse,
    ): Any? {
        if (response.headers.containsKey(HttpHeaders.CACHE_CONTROL)) return body

        val declared = returnType.getMethodAnnotation(CacheFor::class.java)?.policy
        response.headers.set(
            HttpHeaders.CACHE_CONTROL,
            if (isSuccess(response)) declared?.headerValue ?: CachePolicy.VOLATILE.headerValue
            else CachePolicy.VOLATILE.headerValue,
        )
        return body
    }

    private fun isSuccess(response: ServerHttpResponse): Boolean {
        val status =
            (response as? ServletServerHttpResponse)?.servletResponse?.status ?: return true
        return HttpStatus.resolve(status)?.is2xxSuccessful ?: false
    }
}
