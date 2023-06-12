package org.vechain.indexer.repository

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.GroupOperation
import org.springframework.data.mongodb.core.aggregation.MatchOperation
import org.springframework.data.mongodb.core.query.Criteria
import org.vechain.indexer.AbstractIntegrationTest
import org.vechain.indexer.model.IndexedNFT
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal class CountRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var countRepository: CountRepository

    @Test
    fun `count all elements of collection`() {
        val collection: Class<*> = IndexedNFT::class.java
        val matchOperation: MatchOperation? = null
        val groupOperation: GroupOperation? = null

        val count = countRepository.getCount(collection, matchOperation, groupOperation)

        expectThat(count).isEqualTo(121L)
    }

    @Test
    fun `limited count of all elements of collection`() {
        val collection: Class<*> = IndexedNFT::class.java
        val matchOperation: MatchOperation? = null
        val groupOperation: GroupOperation? = null
        val countLimit = 100L

        val count = countRepository.getCount(collection, matchOperation, groupOperation, countLimit)

        expectThat(count).isEqualTo(countLimit + 1)
    }

    @Test
    fun `count elements of collection matching query`() {
        val collection: Class<*> = IndexedNFT::class.java
        val matchOperation: MatchOperation = Aggregation.match(
            Criteria.where(IndexedNFT::contractAddress.name).`is`("0xb44111d908ad0af0949a20a130429f92a4cc0dbf")
        )
        val groupOperation: GroupOperation? = null

        val count = countRepository.getCount(collection, matchOperation, groupOperation)

        expectThat(count).isEqualTo(61L)
    }

    @Test
    fun `count distinct elements matching query`() {
        val collection: Class<*> = IndexedNFT::class.java
        val matchOperation: MatchOperation = Aggregation.match(
            Criteria.where(IndexedNFT::owner.name).`is`("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")
        )
        val groupOperation: GroupOperation = Aggregation.group(IndexedNFT::contractAddress.name)

        val count = countRepository.getCount(collection, matchOperation, groupOperation)

        expectThat(count).isEqualTo(2L)
    }
}