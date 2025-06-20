package org.vechain.indexer.utils

import org.springframework.core.io.support.PathMatchingResourcePatternResolver

object FileUtils {
    fun getJsonFilePaths(directory: String): List<String> {
        val resolver = PathMatchingResourcePatternResolver()
        val resources = resolver.getResources("classpath*:$directory/*.json")
        return resources
            .mapNotNull {
                it.url.path.substringAfterLast("$directory/").takeIf { name ->
                    name.endsWith(".json")
                }
            }
            .map { "$directory/$it" }
    }
}
