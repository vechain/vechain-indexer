package org.vechain.indexer.integration

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.data.mongodb.core.query.Query
import org.vechain.indexer.repository.BasePagingAndSortingIndexedRepository
import strikt.api.expect
import strikt.assertions.isGreaterThan

class IntegrationTests : AbstractIntegrationTest() {

    @Autowired lateinit var mongoOps: MongoOperations

    @Autowired lateinit var allRepos: List<BasePagingAndSortingIndexedRepository<*, *>>

    /**
     * This tests checks that ALL repos have been populated after indexing the thor script output.
     */
    @Test
    fun `repos are indexing`() {

        // Sleep while indexer catches chain
        waitForFullySynced()

        // TODO: The current transaction script does not contain any stargate data
        val unsupportedCollections =
            listOf(
                "stargate_total_nft_holders_by_block",
                "stargate_total_vet_staked_by_block",
                "stargate_vtho_claimed_by_account",
                "stargate_vtho_claimed_by_block",
            )

        // Do not take into account mongock collections, archive collections
        val changeLogCollections = listOf("mongockChangeLog", "mongockLock")

        val collections = mongoOps.collectionNames

        collections
            .filter {
                !unsupportedCollections.contains(it) &&
                    !changeLogCollections.contains(it) &&
                    !it.endsWith("archives")
            }
            .forEach { collection ->
                mongoOps.count(Query(), collection).let { count ->
                    expect {
                        that(count)
                            .describedAs("Repo ($collection) should have 1 or more documents")
                            .isGreaterThan(0)
                    }
                }
            }
    }

    /** This tests checks that ALL repos can be read from. */
    @Test
    fun `can read from repos`() {

        // Sleep while indexer catches chain
        waitForFullySynced()

        allRepos.forEach { repo -> assertDoesNotThrow { repo.findAll() } }
    }
}
