package org.vechain.indexer.config

import java.util.Locale
import kotlin.system.measureTimeMillis
import org.slf4j.LoggerFactory
import org.springdoc.api.AbstractOpenApiResource
import org.springdoc.webmvc.api.OpenApiWebMvcResource
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.stereotype.Component

/** Builds the OpenAPI spec before the port opens, instead of on springdoc's first request. */
@Component
open class OpenApiPreloader(private val resource: OpenApiWebMvcResource) :
    SmartInitializingSingleton {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun afterSingletonsInstantiated() {
        try {
            // springdoc's pre-loading-enabled only fires in the constructor of a @Lazy bean.
            val build =
                AbstractOpenApiResource::class
                    .java
                    .getDeclaredMethod("getOpenApi", Locale::class.java)
                    .apply { isAccessible = true }
            val elapsed = measureTimeMillis { build.invoke(resource, Locale.ENGLISH) }
            logger.info("Built the OpenAPI spec in {} ms", elapsed)
        } catch (e: ReflectiveOperationException) {
            logger.error("Could not build the OpenAPI spec at startup", e)
        }
    }
}
