package org.vechain.indexer.openapi

import org.springframework.test.context.ActiveProfilesResolver

class OpenApiActiveProfilesResolver : ActiveProfilesResolver {

    override fun resolve(testClass: Class<*>): Array<String> {
        return resolveProfiles().toTypedArray()
    }

    companion object {
        private const val openApiProfilesProperty = "openapi.profiles"
        private val defaultProfiles =
            listOf(
                    "accounts",
                    "b3tr",
                    "contracts",
                    "explorer",
                    "history",
                    "nfts",
                    "stargate",
                    "transactions",
                    "transfers",
                    "validator",
                    "vevote",
                )
                .sorted()

        fun resolveProfiles(): List<String> {
            return System.getProperty(openApiProfilesProperty)?.let(::parseProfiles)
                ?: defaultProfiles
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
