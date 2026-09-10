package org.vechain.indexer.nft

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.vechain.indexer.history.HistoryEventName
import org.vechain.indexer.history.IndexedHistoryEvent
import org.vechain.indexer.thor.Address
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isFalse
import strikt.assertions.isTrue

/** Runs the real `$lookup` pipeline against embedded MongoDB. */
@DataMongoTest
@ActiveProfiles("test")
@ContextConfiguration(classes = [NftBlacklistFilterMongoTest.App::class])
internal class NftBlacklistFilterMongoTest {
    @SpringBootApplication open class App

    @Autowired private lateinit var template: MongoTemplate

    private val account = "0x00000000000000000000000000000000000000aa"
    private val blacklisted = "0x1111111111111111111111111111111111111111"
    private val whitelistedAgain = "0x2222222222222222222222222222222222222222"
    private val neverListed = "0x3333333333333333333333333333333333333333"
    private val pageable = PageRequest.of(0, 2, Sort.by(Sort.Order.desc("blockTimestamp")))

    @BeforeEach
    fun setUp() {
        template.dropCollection(IndexedHistoryEvent::class.java)
        template.dropCollection(IndexedNft::class.java)
        template.dropCollection(NftBlacklist::class.java)
        template.insert(blacklistDoc(blacklisted, isBlacklisted = true))
        template.insert(blacklistDoc(whitelistedAgain, isBlacklisted = false))
    }

    @Test
    fun `history rows of blacklisted collections are dropped, everything else pages in order`() {
        listOf(
                historyRow("h1", 500, blacklisted),
                historyRow("h2", 400, neverListed),
                historyRow("h3", 300, blacklisted),
                historyRow("h4", 200, whitelistedAgain),
                historyRow("h5", 100, null),
            )
            .forEach { template.insert(it) }
        val criteria = Criteria.where(IndexedHistoryEvent.INVOLVED_ADDRESSES_FIELD).`is`(account)

        val first =
            NftBlacklistFilter.findPage(
                template,
                criteria,
                pageable,
                IndexedHistoryEvent::class.java,
            )
        val second =
            NftBlacklistFilter.findPage(
                template,
                criteria,
                pageable.next(),
                IndexedHistoryEvent::class.java,
            )

        expectThat(first.content.map { it.id }).containsExactly("h2", "h4")
        expectThat(first.hasNext()).isTrue()
        expectThat(second.content.map { it.id }).containsExactly("h5")
        expectThat(second.hasNext()).isFalse()
    }

    @Test
    fun `owned NFTs and owned collections skip blacklisted contracts`() {
        listOf(
                nft("1", blacklisted, 30),
                nft("2", neverListed, 20),
                nft("3", whitelistedAgain, 10),
            )
            .forEach { template.insert(it) }
        val service = NftService(template)
        val owner = Address(account)
        val nftPage = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("blockNumber")))

        val owned = service.findOwnedNfts(owner, null, null, null, nftPage)
        val contracts = service.findContractsByNftOwner(owner, null, nftPage)

        expectThat(owned.content.map { it.contractAddress })
            .containsExactly(neverListed, whitelistedAgain)
        expectThat(contracts.content).containsExactly(neverListed, whitelistedAgain)
    }

    private fun blacklistDoc(contract: String, isBlacklisted: Boolean) =
        NftBlacklist(
            id = contract,
            isBlacklisted = isBlacklisted,
            blockId = "0xblock",
            blockNumber = 1,
            blockTimestamp = 1,
            version = 1,
        )

    private fun historyRow(id: String, blockTimestamp: Long, contract: String?) =
        IndexedHistoryEvent(
            id = id,
            blockId = "block-$id",
            blockNumber = blockTimestamp,
            blockTimestamp = blockTimestamp,
            txId = "tx-$id",
            origin = account,
            contractAddress = contract,
            eventName = HistoryEventName.TRANSFER_NFT,
            involvedAddresses = listOf(account),
        )

    private fun nft(tokenId: String, contract: String, blockNumber: Long) =
        IndexedNft(
            id = "$contract-$tokenId",
            version = 1,
            tokenId = tokenId,
            contractAddress = contract,
            owner = account,
            txId = "tx-$tokenId",
            blockNumber = blockNumber,
            blockId = "block-$blockNumber",
            blockTimestamp = blockNumber,
        )
}
