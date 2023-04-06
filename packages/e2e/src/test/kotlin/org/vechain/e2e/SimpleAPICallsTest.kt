package org.vechain.e2e

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.vechain.indexer.model.Contract
import org.vechain.indexer.model.NFT
import org.vechain.indexer.model.WrappedTransaction
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotEqualTo
import strikt.assertions.isNotNull

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

        expectThat(transactions.size).isEqualTo(8)

        transactions.forEach { transaction ->
            assertValidTransaction(transaction)
        }
    }

    @Test
    fun `get delegated transactions`() {
        val transactions = VeWorldAPIClient.getDelegatedTransactions("0x435933c8064b4ae76be665428e0307ef2ccfbd68")

        expectThat(transactions.size).isEqualTo(1)

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

        expectThat(transactions.size).isEqualTo(9)

        transactions.forEach { transaction ->
            assertValidTransaction(transaction)
        }
    }

    @Test
    fun `get contracts`() {
        val contracts = VeWorldAPIClient.getContracts("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")

        expectThat(contracts.size).isEqualTo(8)

        contracts.forEach { contract ->
            assertValidContract(contract)
        }
    }

    @Test
    fun `get NFTs`() {
        val nfts = VeWorldAPIClient.getNfts("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")

        expectThat(nfts.size).isEqualTo(2)

        nfts.forEach { nft ->
            assertValidNft(nft)
        }
    }

    @Test
    fun `get filtered NFTS`() {
        //Perform regular call to get contract addresses
        val nfts = VeWorldAPIClient.getNfts("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")
        expectThat(nfts.size).isEqualTo(2)


        //Quick sanity check
        expectThat(nfts[0].contractAddress).isNotEqualTo(nfts[1].contractAddress)

        //Get filtered NFTs
        val nftsWithQuery = VeWorldAPIClient.getNfts(
            "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
            listOf(nfts[0].contractAddress!!)
        )

        expectThat(nftsWithQuery.size).isEqualTo(1)
    }

    fun assertValidContract(contract: Contract) {

        expect {
            that(contract.txId).isNotNull()
            that(contract.blockId).isNotNull()
            that(contract.blockNumber).isNotNull()
            that(contract.creator).isNotNull()
            that(contract.rawData).isNotNull()
        }
    }

    fun assertValidTransaction(transaction: WrappedTransaction) {

        expect {
            that(transaction.id).isNotNull()
            that(transaction.origin).isNotNull()
            that(transaction.nonce).isNotNull()
            that(transaction.gasUsed).isNotNull()
            that(transaction.clauses).isNotEmpty()
            that(transaction.outputs).isNotEmpty()
        }
    }

    fun assertValidNft(nft: NFT) {
        expect {
            that(nft.tokenId).isNotNull()
            that(nft.contractAddress).isNotNull()
            that(nft.blockNumber).isNotNull()
            that(nft.txId).isNotNull()
            that(nft.owner).isNotNull()
            that(nft.id).isNotNull()
        }
    }

}