package org.vechain.e2e

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.vechain.indexer.model.Contract
import org.vechain.indexer.model.NFT
import org.vechain.indexer.model.WrappedTransaction

class SimpleAPICallsTest {
    @Test
    fun `infrastructure and apps should start`() {
        assertDoesNotThrow {
            VeWorldAPIClient.performHealthCheck()
        }
    }

    @Test
    fun `get transactions`() {
        val transactions = VeWorldAPIClient.getTransactions("0x435933c8064b4ae76be665428e0307ef2ccfbd68")

        assert(transactions.size == 8)

        transactions.forEach { transaction ->
            assertValidTransaction(transaction)
        }
    }

    @Test
    fun `get delegated transactions`() {
        val transactions = VeWorldAPIClient.getDelegatedTransactions("0x435933c8064b4ae76be665428e0307ef2ccfbd68")

        assert(transactions.size == 1)

        transactions.forEach { transaction ->
            assertValidTransaction(transaction)
        }
    }

    @Test
    fun `get sent and delegated transactions`() {
        val transactions = VeWorldAPIClient.getTransactions(
            address = "0x435933c8064b4ae76be665428e0307ef2ccfbd68",
            includeDelegated = true
        )

        assert(transactions.size == 9)

        transactions.forEach { transaction ->
            assertValidTransaction(transaction)
        }
    }

    @Test
    fun `get contracts`() {
        val contracts = VeWorldAPIClient.getContracts("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")

        assert(contracts.size == 8)

        contracts.forEach { contract ->
            assertValidContract(contract)
        }
    }

    @Test
    fun `get NFTs`() {
        val nfts = VeWorldAPIClient.getNfts("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")

        assert(nfts.size == 2)

        nfts.forEach { nft ->
            assertValidNft(nft)
        }
    }

    @Test
    fun `get filtered NFTS`() {
        //Perform regular call to get contract addresses
        val nfts = VeWorldAPIClient.getNfts("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")
        assert(nfts.size == 2)

        //Quick sanity check
        assert(nfts[0].contractAddress != nfts[1].contractAddress)

        //Get filtered NFTs
        val nftsWithQuery = VeWorldAPIClient.getNfts(
            "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
            listOf(nfts[0].contractAddress!!)
        )

        assert(nftsWithQuery.size == 1)
    }

    fun assertValidContract(contract: Contract) {
        assert(contract.txId != null)
        assert(contract.blockId != null)
        assert(contract.blockNumber != null)
        assert(contract.creator != null)
        assert(contract.rawData != null)
    }

    fun assertValidTransaction(transaction: WrappedTransaction) {
        assert(transaction.id != null)
        assert(transaction.origin != null)
        assert(transaction.nonce != null)
        assert(transaction.gasUsed != null)
        assert(transaction.clauses.isNotEmpty())
        assert(transaction.outputs.isNotEmpty())
    }

    fun assertValidNft(nft: NFT) {
        assert(nft.tokenId != null)
        assert(nft.contractAddress != null)
        assert(nft.blockNumber != null)
        assert(nft.txId != null)
        assert(nft.owner != null)
        assert(nft.id != null)
    }

}