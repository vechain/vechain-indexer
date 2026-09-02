package org.vechain.indexer.rest

import java.lang.reflect.Method
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.classreading.MetadataReader
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** Reads bytecode, not a Spring context, so an endpoint cannot hide behind its `@Profile`. */
internal class CacheControlCoverageTest {

    private val scanner =
        object : ClassPathScanningCandidateComponentProvider(false) {
            override fun isCandidateComponent(metadataReader: MetadataReader): Boolean =
                metadataReader.annotationMetadata.hasAnnotation(RestController::class.java.name)
        }

    private fun controllers(): List<Class<*>> =
        scanner
            .findCandidateComponents("org.vechain.indexer")
            .mapNotNull(BeanDefinition::getBeanClassName)
            .map { Class.forName(it) }
            .sortedBy { it.name }

    private fun endpoints(): List<Method> =
        controllers().flatMap { controller ->
            controller.declaredMethods.filter { it.isAnnotationPresent(GetMapping::class.java) }
        }

    @Test
    fun `the scan finds the controllers it is meant to police`() {
        // A broken scan would pass every other assertion here silently.
        assertTrue(controllers().size >= 25, "only found ${controllers().size} controllers")
        assertTrue(endpoints().size >= 90, "only found ${endpoints().size} endpoints")
    }

    @Test
    fun `every endpoint declares how long it may be cached`() {
        val undeclared =
            endpoints()
                .filterNot { it.isAnnotationPresent(CacheFor::class.java) }
                .map { "${it.declaringClass.simpleName}.${it.name}" }
                .sorted()

        assertEquals(
            emptyList<String>(),
            undeclared,
            "add @CacheFor to these endpoints — see AGENTS.md 'Endpoints Own Their Cache TTL'",
        )
    }
}
