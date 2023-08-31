package org.vechain.indexer

import org.vechain.indexer.thor.model.Block

class BlockTestBuilder {
    companion object {
        fun buildBlock(num: Long, parentId: String = "0x${maxOf(num - 1, 0)}"): Block {
            return Block(
                id = "0x${num}",
                number = num,
                timestamp = num,
                size = 0,
                gasUsed = 0,
                gasLimit = 0,
                parentID = parentId,
                beneficiary = "",
                totalScore = 0,
                txsRoot = "",
                stateRoot = "",
                receiptsRoot = "",
                txsFeatures = 0,
                com = false,
                signer = "0x995711ADca070C8f6cC9ca98A5B9C5A99b8350b1",
                isTrunk = false,
                isFinalized = false,
                transactions = emptyList()
            )
        }
    }
}
