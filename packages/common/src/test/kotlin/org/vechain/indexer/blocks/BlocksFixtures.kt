package org.vechain.indexer.blocks

import java.math.BigInteger
import org.vechain.indexer.thor.DecodedEvent
import org.vechain.indexer.thor.DecodedOutputs
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.TxTransfer
import org.vechain.indexer.transaction.IndexedTransaction

object BlocksFixtures {
    fun address(n: Int) = "0x" + n.toString(16).padStart(40, '0')

    fun hash(n: Int) = "0x" + n.toString(16).padStart(64, '0')

    fun block(number: Long, transactions: List<IndexedTransaction> = emptyList()) =
        IndexedBlock(
            blockNumber = number,
            blockId = hash(number.toInt()),
            blockTimestamp = 1_700_000_000L + number * 10,
            size = 361,
            parentID = hash(number.toInt() - 1),
            gasLimit = 40_000_000,
            gasUsed = 21_000L * transactions.size,
            beneficiary = address(1),
            totalScore = number * 2,
            txsRoot = hash(1000),
            txsFeatures = 1,
            stateRoot = hash(1001),
            receiptsRoot = hash(1002),
            com = true,
            signer = address(2),
            baseFeePerGas = if (number % 2 == 0L) "0x2540be400" else null,
            clauseCount = transactions.sumOf { it.clauses.size },
            totalVthoPaid = "0x" + BigInteger.valueOf(21_000L * transactions.size).toString(16),
            transactions = transactions.map { it.id },
        )

    fun transaction(
        block: IndexedBlock,
        index: Int,
        origin: String = address(10),
        gasPayer: String = origin,
        reverted: Boolean = false,
        clauses: List<Clause> = listOf(Clause(address(20), "0x0", "0x")),
        events: List<DecodedEvent> = emptyList(),
        transfers: List<TxTransfer> = emptyList(),
    ) =
        IndexedTransaction(
            id = hash(block.blockNumber.toInt() * 100 + index),
            blockId = block.blockId,
            blockNumber = block.blockNumber,
            blockTimestamp = block.blockTimestamp,
            transactionIndex = index.toLong(),
            type = if (index % 2 == 0) null else 81L,
            size = 200L + index,
            chainTag = 74L,
            blockRef = "0x00000000aabbccdd",
            expiration = 720L,
            clauses = clauses,
            gasPriceCoef = if (index % 2 == 0) 128L else null,
            gas = 21_000L,
            maxFeePerGas = if (index % 2 == 0) null else "0x9184e72a000",
            maxPriorityFeePerGas = if (index % 2 == 0) null else "0x0",
            dependsOn = if (index == 0) null else hash(1),
            nonce = "0xb8a9e3f6f7f7f47c",
            gasUsed = 21_000L,
            gasPayer = gasPayer,
            paid = "0x1c6bf52634000",
            reward = "0x0",
            reverted = reverted,
            origin = origin,
            outputs =
                if (reverted) emptyList()
                else
                    clauses.map {
                        DecodedOutputs(
                            contractAddress = origin,
                            events = events,
                            transfers = transfers,
                        )
                    },
        )

    fun decodedEvent(address: String = address(30)) =
        DecodedEvent(
            address = address,
            topics = listOf(hash(2), hash(3)),
            data = "0x" + "00".repeat(31) + "01",
            name = "Transfer",
            params =
                mapOf(
                    "from" to address(10),
                    "to" to address(11),
                    "value" to "1000000000000000000",
                    "ids" to listOf("1", "2"),
                    "flag" to true,
                ),
        )

    fun rawEvent(address: String = address(31)) =
        DecodedEvent(address = address, topics = emptyList(), data = "0x")

    fun transfer() = TxTransfer(address(10), address(11), "0xde0b6b3a7640000")
}
