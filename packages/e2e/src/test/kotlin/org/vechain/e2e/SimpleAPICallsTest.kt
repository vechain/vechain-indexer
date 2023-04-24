package org.vechain.e2e

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.vechain.indexer.model.Contract
import org.vechain.indexer.model.NFT
import org.vechain.indexer.model.Transaction
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.*
import java.math.BigInteger

class SimpleAPICallsTest {
    @Test
    fun `infrastructure and apps should start`() {
        assertDoesNotThrow {
            VeWorldAPIClient.performHealthCheck()
        }
    }

    @Test
    fun `get transaction by id`() {
        val transaction =
            VeWorldAPIClient.getTransactionById("0x0569d985aff6e073af33415f5ca4e848742cb483533015486dd96779c6e8251d")

        expectThat(transaction.id).isEqualTo("0x0569d985aff6e073af33415f5ca4e848742cb483533015486dd96779c6e8251d")

    }

    @Test
    fun `get transactions for origin`() {
        val transactions = VeWorldAPIClient.getTransactionsByOrigin("0x435933c8064b4ae76be665428e0307ef2ccfbd68")

        expectThat(transactions.data?.size).isEqualTo(8)
        expectThat(transactions.pagination?.totalElements).isEqualTo(8)

        transactions.data?.forEach { transaction ->
            assertValidTransaction(transaction)
        }
    }

    @Test
    fun `get delegated transactions`() {
        val transactions = VeWorldAPIClient.getDelegatedTransactions("0x435933c8064b4ae76be665428e0307ef2ccfbd68")

        expectThat(transactions.data?.size).isEqualTo(1)
        expectThat(transactions.pagination?.totalElements).isEqualTo(1)

        transactions.data?.forEach { transaction ->
            assertValidTransaction(transaction)
        }
    }

    @Test
    fun `get sent and delegated transactions`() {
        val transactions = VeWorldAPIClient.getTransactionsByOrigin(
            address = "0x435933c8064b4ae76be665428e0307ef2ccfbd68",
            includeDelegated = true
        )

        expectThat(transactions.data?.size).isEqualTo(9)
        expectThat(transactions.pagination?.totalElements).isEqualTo(9)

        transactions.data?.forEach { transaction ->
            assertValidTransaction(transaction)
        }
    }

    @Test
    fun `get contracts`() {
        val contract = VeWorldAPIClient.getContract("0x1a772e9592f04250860e1416d33b5b8513f7384f")

        expectThat(contract.address).isEqualTo("0x1a772e9592f04250860e1416d33b5b8513f7384f")
    }

    @Test
    fun `get contracts for creator`() {
        val contract = VeWorldAPIClient.getContractForCreator("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")

        expectThat(contract.data?.size).isEqualTo(8)
    }

    @Test
    fun `get NFTs`() {
        val nfts = VeWorldAPIClient.getNfts("0xf077b491b355e64048ce21e3a6fc4751eeea77fa").data

        expectThat(nfts).isNotNull()
        expectThat(nfts!!.size).isEqualTo(2)

        nfts.forEach { nft ->
            assertValidNft(nft)
        }
    }

    @Test
    fun `get filtered NFTS`() {
        //Perform regular call to get contract addresses
        val nfts = VeWorldAPIClient.getNfts("0xf077b491b355e64048ce21e3a6fc4751eeea77fa").data
        expectThat(nfts!!.size).isEqualTo(2)


        //Quick sanity check
        expectThat(nfts[0].contractAddress).isNotEqualTo(nfts[1].contractAddress)

        //Get filtered NFTs
        val nftsWithQuery = VeWorldAPIClient.getNfts(
            "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
            nfts[0].contractAddress
        ).data

        expectThat(nftsWithQuery!!.size).isEqualTo(1)
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