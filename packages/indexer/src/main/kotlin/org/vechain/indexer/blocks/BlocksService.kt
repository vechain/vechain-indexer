package org.vechain.indexer.blocks

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.blocks.repository.BlockRepository
import org.vechain.indexer.thor.model.Block

@Profile("blocks")
@Service
open class BlocksService(private val repository: BlockRepository) {

    /**
     * Projects a block header onto [IndexedBlock]. `isTrunk` and `isFinalized` are dropped: both
     * are node-local, time-varying properties rather than block contents.
     */
    open fun processBlock(block: Block): IndexedBlock =
        IndexedBlock(
            blockNumber = block.number,
            blockId = block.id,
            blockTimestamp = block.timestamp,
            size = block.size,
            parentID = block.parentID,
            gasLimit = block.gasLimit,
            gasUsed = block.gasUsed,
            beneficiary = block.beneficiary,
            totalScore = block.totalScore,
            txsRoot = block.txsRoot,
            txsFeatures = block.txsFeatures,
            stateRoot = block.stateRoot,
            receiptsRoot = block.receiptsRoot,
            com = block.com,
            signer = block.signer,
            baseFeePerGas = block.baseFeePerGas,
            transactions = block.transactions.map { it.id },
        )

    // No @Transactional needed: single-document writes are always atomic in MongoDB.
    open fun save(block: IndexedBlock) {
        repository.save(block)
    }
}
