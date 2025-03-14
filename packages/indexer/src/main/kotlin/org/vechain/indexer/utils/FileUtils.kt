package org.vechain.indexer.utils

import java.io.InputStream
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

object FileUtils {
    /**
     * @param path The classpath directory containing JSON files (e.g., "abis", "business_events").
     * @return A map where the key is the file name (without `.json`) and the value is its
     *   InputStream.
     * @notice Loads all JSON files from a specified classpath directory.
     * @dev Works with any JSON file type, returning a map of file names to parsed objects.
     */
    fun loadFileStreams(path: String): Map<String, InputStream> {
        val files = mutableMapOf<String, InputStream>()

        return try {
            val resolver = PathMatchingResourcePatternResolver()
            val resources = resolver.getResources("classpath:$path/*.json")

            resources.forEach { resource ->
                resource.filename?.removeSuffix(".json")?.let { fileName ->
                    files[fileName] = resource.inputStream
                }
            }

            files
        } catch (e: Exception) {
            println("Failed to load files from classpath: ${e.message}")
            files
        }
    }
}
