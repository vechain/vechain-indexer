package org.vechain.e2e

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.vechain.indexer.model.Contract
import org.vechain.indexer.model.NFT
import org.vechain.indexer.model.Transaction
import org.vechain.indexer.model.TransferEvent
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.*
import java.math.BigInteger

class SimpleAPICallsTest {

    @Nested
    inner class HealthcheckTest {
        @Test
        fun `infrastructure and apps should start`() {
            assertDoesNotThrow {
                VeWorldAPIClient.performHealthCheck()
            }
        }
    }

    @Nested
    inner class BlockTest {
        @Test
        fun `get block by number`() {
            val block = VeWorldAPIClient.getBlock("1")

            expectThat(block.blockNumber).isEqualTo(1)
        }

        @Test
        fun `get best block`() {
            assertDoesNotThrow {
                VeWorldAPIClient.getBlock("best")
            }
        }

        @Test
        fun `get block by invalid id`() {
            assertThrows<Exception> {
                VeWorldAPIClient.getBlock("invalid")
            }
        }
    }

    @Nested
    inner class ClauseTest {
        @Test
        fun `get clauses for address`() {
            val clauses = VeWorldAPIClient.getClauses(
                "0x435933c8064b4ae76be665428e0307ef2ccfbd68"
            )
            expectThat(clauses).hasSize(9)
        }

        @Test
        fun `get clauses for address pagination`() {
            val clauses = VeWorldAPIClient.getClauses(
                "0x435933c8064b4ae76be665428e0307ef2ccfbd68",
                page = 0,
                size = 1
            )
            expectThat(clauses).hasSize(1)
        }
    }

    @Nested
    inner class ContractTest {

        @Test
        fun `get contracts for creator`() {
            val contracts = VeWorldAPIClient.getContractForCreator("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")

            expectThat(contracts).hasSize(8)

            contracts.forEach { contract ->
                assertValidContract(contract)
            }

            // Get contract by address
            val contract = VeWorldAPIClient.getContract(contracts[0].address)

            assertValidContract(contract)
        }

        @Test
        fun `get contracts for creator paginated`() {
            val contracts = VeWorldAPIClient.getContractForCreator("0xf077b491b355e64048ce21e3a6fc4751eeea77fa", 0, 1)

            expectThat(contracts).hasSize(1)

            contracts.forEach { contract ->
                assertValidContract(contract)
            }
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
    }

    @Nested
    inner class NFTTest {
        @Test
        fun `get NFTs for address`() {
            val nfts = VeWorldAPIClient.getNfts("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")

            expectThat(nfts).hasSize(2)

            nfts.forEach { nft ->
                assertValidNft(nft)
            }
        }

        @Test
        fun `get NFTs for address with pagination`() {
            val nfts = VeWorldAPIClient.getNfts(address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa", size = 1)

            expectThat(nfts).hasSize(1)

            nfts.forEach { nft ->
                assertValidNft(nft)
            }

        }

        @Test
        fun `get filtered NFTS`() {
            //Perform regular call to get contract addresses
            val nfts = VeWorldAPIClient.getNfts("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")
            expectThat(nfts).hasSize(2)


            //Quick sanity check
            expectThat(nfts[0].contractAddress).isNotEqualTo(nfts[1].contractAddress)

            //Get filtered NFTs
            val nftsWithQuery = VeWorldAPIClient.getNfts(
                "0xf077b491b355e64048ce21e3a6fc4751eeea77fa",
                nfts[0].contractAddress
            )

            expectThat(nftsWithQuery).hasSize(1)
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

    @Nested
    inner class TransactionTest {
        @Test
        fun `get transactions for origin`() {
            val transactions = VeWorldAPIClient.getTransactionsByOrigin("0x435933c8064b4ae76be665428e0307ef2ccfbd68")

            expectThat(transactions).hasSize(8)

            transactions.forEach { transaction ->
                assertValidTransaction(transaction)
            }

            // Get transaction by id
            val transaction = VeWorldAPIClient.getTransactionById(transactions[0].id)

            assertValidTransaction(transaction)

        }

        @Test
        fun `get transactions for origin with pagination`() {
            val transactions =
                VeWorldAPIClient.getTransactionsByOrigin("0x435933c8064b4ae76be665428e0307ef2ccfbd68", size = 1)

            expectThat(transactions).hasSize(1)

            transactions.forEach { transaction ->
                assertValidTransaction(transaction)
            }

        }

        @Test
        fun `get delegated transactions`() {
            val transactions = VeWorldAPIClient.getDelegatedTransactions("0x435933c8064b4ae76be665428e0307ef2ccfbd68")

            expectThat(transactions).hasSize(1)

            transactions.forEach { transaction ->
                assertValidTransaction(transaction)
            }
        }

        @Test
        fun `get sent and delegated transactions`() {
            val transactions = VeWorldAPIClient.getTransactionsByOrigin(
                address = "0x435933c8064b4ae76be665428e0307ef2ccfbd68",
                includeDelegated = true
            )

            expectThat(transactions).hasSize(9)

            transactions.forEach { transaction ->
                assertValidTransaction(transaction)
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
    }

    @Nested
    inner class TransferEventTest {
        @Test
        fun `get transfer events for address`() {
            val transferEvents =
                VeWorldAPIClient.getTransferEvents(address = "0x435933c8064b4ae76be665428e0307ef2ccfbd68")

            expectThat(transferEvents).hasSize(10)

            transferEvents.forEach { transferEvent ->
                assertValidTransferEvent(transferEvent)
            }

            val tokenAddress = transferEvents.find { it.tokenAddress != null }!!.tokenAddress

            // Get transfer event by token address
            val transferEventsForToken = VeWorldAPIClient.getTransferEvents(
                tokenAddress = tokenAddress,
            )

            expectThat(transferEventsForToken.size).isGreaterThan(0)

            transferEventsForToken.forEach { transferEvent ->
                assertValidTransferEvent(transferEvent)
            }
        }

        @Test
        fun `get transfer events for address with pagination`() {
            val transferEvents =
                VeWorldAPIClient.getTransferEvents("0x435933c8064b4ae76be665428e0307ef2ccfbd68", size = 1)

            expectThat(transferEvents).hasSize(1)

            transferEvents.forEach { transferEvent ->
                assertValidTransferEvent(transferEvent)
            }
        }

        fun assertValidTransferEvent(transferEvent: TransferEvent) {
            expect {
                that(transferEvent.id).isNotEmpty()
                that(transferEvent.from).isNotEmpty()
                that(transferEvent.to).isNotEmpty()
                that(transferEvent.blockNumber).isGreaterThan(0)
                that(transferEvent.txId).isNotEmpty()
            }
        }
    }

}