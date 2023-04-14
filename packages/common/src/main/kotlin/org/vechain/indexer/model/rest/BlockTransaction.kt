package org.vechain.indexer.model.rest

import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.vechain.indexer.model.Clause
import org.vechain.indexer.model.TxOutputs


data class BlockTransaction @ConstructorBinding constructor(
    var id: String,
    var chainTag: Long,
    var blockRef: String,
    var expiration: Long,
    var clauses: List<Clause>,
    var gasPriceCoef: Long,
    var gas: Long,
    var origin: String,
    var delegator: String?,
    var nonce: String,
    var dependsOn: String?,
    var size: Long,
    var gasUsed: Long,
    var gasPayer: String,
    var paid: String,
    var reward: String,
    var reverted: Boolean,
    var outputs: List<TxOutputs>
)