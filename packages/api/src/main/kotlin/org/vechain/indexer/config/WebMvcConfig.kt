package org.vechain.indexer.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.format.FormatterRegistry
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.vechain.indexer.StringToAddressConverter

@Configuration
open class WebMvcConfig : WebMvcConfigurer {

    /** Enable parameter validation. */
    companion object {
        @Bean
        @JvmStatic
        fun methodValidationPostProcessor(): MethodValidationPostProcessor {
            return MethodValidationPostProcessor()
        }
    }

    /** Register custom data binders for query params */
    override fun addFormatters(registry: FormatterRegistry) {
        registry.addConverter(StringToAddressConverter())
    }

    override fun addCorsMappings(registry: CorsRegistry) {
        registry
            .addMapping("/**")
            .allowedOrigins("*") // Allow any origin
            .allowedMethods(
                "GET",
                "POST",
                "PUT",
                "DELETE",
                "OPTIONS"
            ) // Allow specified HTTP methods
            .allowedHeaders("*") // Allow any header
    }
}
