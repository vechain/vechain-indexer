package org.vechain.indexer

import com.fasterxml.jackson.core.type.TypeReference
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import org.vechain.indexer.nft.IndexedNft
import org.vechain.indexer.nft.NftRepository
import org.vechain.indexer.transaction.IndexedTransaction
import org.vechain.indexer.transaction.TransactionRepository
import org.vechain.indexer.transfer.IndexedTransferEvent
import org.vechain.indexer.transfer.TransferEventRepository
import org.vechain.indexer.utils.JsonUtils

/**
 * To be used for local testing only. Does not spin up mongo container, you can do this manually to
 * speed up tests.
 */
@RunWith(SpringRunner::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [VeWorldIndexerApiApplication::class],
)
@ContextConfiguration(initializers = [AbstractIntegrationTest.Initializer::class])
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class FastAbstractIntegrationTest {
    protected val TX_TYPE = object : TypeReference<List<IndexedTransaction>>() {}
    protected val NFT_TYPE = object : TypeReference<List<IndexedNft>>() {}
    protected val TRANSFER_EVENT_TYPE = object : TypeReference<List<IndexedTransferEvent>>() {}

    protected val objectMapper = JsonUtils.mapper

    @Autowired lateinit var transactionRepository: TransactionRepository

    @Autowired lateinit var nftRepository: NftRepository

    @Autowired lateinit var transferEventRepository: TransferEventRepository

    @BeforeAll
    fun setup() {

        val transactions: List<IndexedTransaction> =
            loadDataFromResources("/transactions.json", TX_TYPE)
        val nfts: List<IndexedNft> = loadDataFromResources("/nfts.json", NFT_TYPE)
        val transferEvents: List<IndexedTransferEvent> =
            loadDataFromResources("/transfers.json", TRANSFER_EVENT_TYPE)

        transactionRepository.saveAll(transactions)
        nftRepository.saveAll(nfts)
        transferEventRepository.saveAll(transferEvents)
    }

    /** Load json files from resources */
    private fun <T> loadDataFromResources(path: String, type: TypeReference<T>): T {
        val file =
            AbstractIntegrationTest::class.java.getResource(path)
                ?: throw Exception("File not found: $path")

        val rawJson: String = file.readText()

        if (rawJson.isEmpty()) throw Exception("Empty json file: $path")

        return objectMapper.readValue(rawJson, type)
    }
}
