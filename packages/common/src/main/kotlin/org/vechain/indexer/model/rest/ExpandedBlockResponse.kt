package org.vechain.indexer.model.rest

import org.springframework.boot.context.properties.bind.ConstructorBinding


data class ExpandedBlockResponse @ConstructorBinding constructor(
    var number: Long,
    var id: String,
    var size: Long,
    var parentID: String,
    var timestamp: Long,
    var gasLimit: Long,
    var beneficiary: String,
    var gasUsed: Long,
    var totalScore: Long,
    var txsRoot: String,
    var txsFeatures: Long,
    var stateRoot: String,
    var receiptsRoot: String,
    var com: Boolean,
    var signer: String,
    var isTrunk: Boolean,
    var isFinalized: Boolean,
    var transactions: List<BlockTransaction>
)

