package org.vechain.indexer.integration

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.CrudRepository

class IntegrationTests : AbstractIntegrationTest() {

    @Autowired
    lateinit var allRepos: List<CrudRepository<*, String>>

    /**
     * This tests checks that ALL repos have been populated after indexing the thor script output.
     */
    @Test
    fun `repos are indexing`() {

        //Sleep while indexer catches chain
        waitForFullySynced()

        allRepos.forEach { repo ->

            val repoCount = repo.count()

            if (repoCount <= 0) {
                throw Exception("Repo not updating")
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
            assertDoesNotThrow("should be able to read from the DB") {
                repo.findAll()
            }
        }
    }
}