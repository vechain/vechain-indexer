package org.vechain.indexer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.springframework.beans.factory.annotation.Autowired
import org.vechain.indexer.repos.BlockRepo

class IndexerApplicationTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var blockRepo: BlockRepo

    @Test
    fun contextLoads() {
        for (i in 1..100) {
            try {
                val blockCount = blockRepo.count()

                if (blockCount > 0) {
                    return
                }

                Thread.sleep(500)
            } catch (e: Exception) {
                println("Block count failed: $e")
                Thread.sleep(500)
            }
        }

        fail("Blocks not updating")
    }
}