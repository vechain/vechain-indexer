package org.vechain.indexer.utils

import org.springframework.core.io.support.PathMatchingResourcePatternResolver

object FileUtils {
    /**
     * Returns a list of JSON file paths in the given directory and its subdirectories up to
     * [depth].
     *
     * @param directory The root directory to search.
     * @param depth The maximum subdirectory depth to search (default is 1).
     */
    fun getJsonFilePaths(directory: String, depth: Int = 1): List<String> {
        val resolver = PathMatchingResourcePatternResolver()
        val glob = buildGlob(directory, depth)
        val resources = resolver.getResources(glob)
        return resources
            .mapNotNull {
                // Extract path relative to the directory
                it.url.path.substringAfterLast("$directory/").takeIf { name ->
                    name.endsWith(".json")
                }
            }
            .map { "$directory/$it" }
    }

    private fun buildGlob(directory: String, depth: Int): String {
        // Build a glob pattern like "classpath*:dir/*/*.json" for depth=2
        val subdirs = if (depth <= 1) "" else (1 until depth).joinToString("") { "*/" }
        return "classpath*:$directory/$subdirs*.json"
    }
}
