package org.vechain.indexer.config

import io.prometheus.metrics.exporter.httpserver.HTTPServer
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["monitoring.enabled"], havingValue = "true")
open class PrometheusServerConfig {

    @Value("\${monitoring.port:2112}") private var port: Int = 2112

    @Bean
    open fun prometheusServer(): HTTPServer {
        JvmMetrics.builder().register()
        return HTTPServer.builder().port(port).buildAndStart()
    }
}
