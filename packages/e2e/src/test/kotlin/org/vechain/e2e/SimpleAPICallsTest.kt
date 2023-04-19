package org.vechain.e2e

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.vechain.indexer.model.Contract
import org.vechain.indexer.model.NFT
import org.vechain.indexer.model.Transaction
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotEqualTo
import java.math.BigInteger

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
            listOf(nfts[0].contractAddress)
        )

        expectThat(nftsWithQuery.size).isEqualTo(1)
    }

    fun assertValidContract(contract: Contract) {

        expect {
            that(contract.txId).isNotEmpty()
            that(contract.blockId).isNotEmpty()
            that(contract.blockNumber).isGreaterThan(0)
            that(contract.creator).isNotEmpty()
            that(contract.rawData).isNotEmpty()
        }
    }

    fun assertValidTransaction(transaction: Transaction) {

        expect {
            that(transaction.id).isNotEmpty()
            that(transaction.origin).isNotEmpty()
            that(transaction.nonce).isNotEmpty()
            that(transaction.gasUsed).isGreaterThan(0)
            that(transaction.clauses).isNotEmpty()
            that(transaction.outputs).isNotEmpty()
        }
    }

    fun assertValidNft(nft: NFT) {
        expect {
            that(nft.tokenId).isGreaterThan(BigInteger.valueOf(-1))
            that(nft.contractAddress).isNotEmpty()
            that(nft.blockNumber).isGreaterThan(0)
            that(nft.txId).isNotEmpty()
            that(nft.owner).isNotEmpty()
            that(nft.id).isNotEmpty()
        }
    }

}