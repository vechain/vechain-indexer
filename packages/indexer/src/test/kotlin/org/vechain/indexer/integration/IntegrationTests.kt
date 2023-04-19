package org.vechain.indexer.integration

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoOperations
import org.springframework.data.mongodb.core.query.Query
import org.vechain.indexer.repos.BaseIndexedRepo
import strikt.api.expect
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan

class IntegrationTests : AbstractIntegrationTest() {

    @Autowired
    lateinit var mongoOps: MongoOperations

    @Autowired
    lateinit var allRepos: List<BaseIndexedRepo<*>>

    @Test
    fun `repos should be created`() {
        val collectionsSize: Int = mongoOps.collectionNames.size
        val reposSize: Int = allRepos.size

        expect {
            that(collectionsSize).isEqualTo(reposSize)
        }
    }

    /**
     * This tests checks that ALL repos have been populated after indexing the thor script output.
     */
    @Test
    fun `repos are indexing`() {

        //Sleep while indexer catches chain
        waitForFullySynced()

        val repoNames = mongoOps.collectionNames

        repoNames.forEach { name ->
            mongoOps.count(Query(), name).let { count ->

                expect {
                    that(count)
                        .describedAs("Repo ($name) should have 1 or more documents")
                        .isGreaterThan(0)
                }
            }
        }
    }

    /**
     * This tests checks that ALL repos can be read from.
     */
    @Test
    fun `can read from repos`() {

        //Sleep while indexer catches chain
        waitForFullySynced()

        allRepos.forEach { repo ->
            assertDoesNotThrow {
                repo.findAll()
            }
        }
    }
}