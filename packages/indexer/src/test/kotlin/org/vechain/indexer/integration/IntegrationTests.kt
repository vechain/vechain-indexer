package org.vechain.indexer.integration

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.data.mongodb.core.query.Query
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
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

        // TODO: The current transaction script is missing a lot of data:
        val unsupportedCollections =
            setOf(
                "stargate_total_nft_holders_by_block",
                "stargate_total_vet_staked_by_block",
                "stargate_vtho_claimed_by_account",
                "stargate_vtho_claimed_by_block",
                "stargate_vtho_generated_by_block",
                "stargate_total_vet_delegated_by_block",
                "stargate_tokens",
                "historic_proposals",
                "historic_proposals_votes",
                "b3tr_user_action_summaries_all_time",
                "b3tr_user_action_summaries_daily",
                "b3tr_user_action_summaries_round",
                "b3tr_app_action_summaries_all_time",
                "b3tr_app_action_summaries_daily",
                "b3tr_app_action_summaries_round",
                "b3tr_proposal_comments",
                "b3tr_proposal_results",
                "b3tr_gm_nfts",
                "b3tr_x_alloc_results",
                "vevote_proposal_results",
                "vevote_proposal_comments",
            )

        val collections = mongoOps.collectionNames

        collections
            .filter { !unsupportedCollections.contains(it) && !it.endsWith("archives") }
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
