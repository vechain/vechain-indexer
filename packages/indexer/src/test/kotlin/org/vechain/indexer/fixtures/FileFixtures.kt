package org.vechain.indexer.fixtures

import java.io.File
import kotlin.collections.filter
import kotlin.collections.map
import kotlin.io.extension
import kotlin.jvm.javaClass

object FileFixtures {
    // Get a list of json file classpath-relative paths from the business-events directory
    val businessEventFiles: List<String> =
        File(
                javaClass.classLoader.getResource("business-events")?.toURI()
                    ?: error("Test resource dir not found")
            )
            .listFiles()
            ?.filter { it.extension == "json" }
            ?.map { "business-events/${it.name}" } ?: emptyList()

    // Get a list of json file classpath-relative paths from the abis directory
    val abiFiles: List<String> =
        File(
                javaClass.classLoader.getResource("abis")?.toURI()
                    ?: error("Test resource dir not found")
            )
            .listFiles()
            ?.filter { it.extension == "json" }
            ?.map { "abis/${it.name}" } ?: emptyList()
}
