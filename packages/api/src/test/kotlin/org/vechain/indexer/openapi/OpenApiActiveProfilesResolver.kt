package org.vechain.indexer.openapi

import java.nio.file.Files
import java.nio.file.Path
import org.springframework.test.context.ActiveProfilesResolver

class OpenApiActiveProfilesResolver : ActiveProfilesResolver {

    override fun resolve(testClass: Class<*>): Array<String> {
        return resolveProfiles().toTypedArray()
    }

    companion object {
        private const val openApiProfilesProperty = "openapi.profiles"
        private const val springProfilesActivePrefix = "SPRING_PROFILES_ACTIVE="

        fun resolveProfiles(): List<String> {
            return System.getProperty(openApiProfilesProperty)?.let(::parseProfiles)
                ?: loadProfilesFromEnvExample()
        }

        private fun loadProfilesFromEnvExample(): List<String> {
            val envExamplePath =
                listOf(Path.of("packages/api/.env.example"), Path.of(".env.example"))
                    .firstOrNull(Files::exists)
                    ?: error(
                        "Unable to find packages/api/.env.example for OpenAPI profile resolution"
                    )

            val springProfilesLine =
                Files.readAllLines(envExamplePath).firstOrNull { line ->
                    line.startsWith(springProfilesActivePrefix)
                } ?: error("SPRING_PROFILES_ACTIVE not found in ${envExamplePath.toAbsolutePath()}")

            return parseProfiles(springProfilesLine.removePrefix(springProfilesActivePrefix))
        }

        private fun parseProfiles(rawProfiles: String): List<String> {
            return rawProfiles
                .split(',')
                .map { profile -> profile.trim() }
                .filter { profile -> profile.isNotEmpty() }
                .distinct()
                .sorted()
        }
    }
}
