package org.vechain.indexer.openapi

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.containsExactly

class OpenApiActiveProfilesResolverTest {

    @Test
    fun `resolveProfiles returns code owned defaults when no override is set`() {
        val previous = System.getProperty(OPENAPI_PROFILES_PROPERTY)
        System.clearProperty(OPENAPI_PROFILES_PROPERTY)

        try {
            expectThat(OpenApiActiveProfilesResolver.resolveProfiles())
                .containsExactly(
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
        } finally {
            restoreProperty(previous)
        }
    }

    @Test
    fun `resolveProfiles prefers explicit override`() {
        val previous = System.getProperty(OPENAPI_PROFILES_PROPERTY)
        System.setProperty(OPENAPI_PROFILES_PROPERTY, " validator ,contracts,validator ")

        try {
            expectThat(OpenApiActiveProfilesResolver.resolveProfiles())
                .containsExactly("contracts", "validator")
        } finally {
            restoreProperty(previous)
        }
    }

    private fun restoreProperty(previous: String?) {
        if (previous == null) {
            System.clearProperty(OPENAPI_PROFILES_PROPERTY)
        } else {
            System.setProperty(OPENAPI_PROFILES_PROPERTY, previous)
        }
    }

    companion object {
        private const val OPENAPI_PROFILES_PROPERTY = "openapi.profiles"
    }
}
