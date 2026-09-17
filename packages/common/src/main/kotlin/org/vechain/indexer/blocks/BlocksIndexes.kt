package org.vechain.indexer.blocks

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** Three address b-trees on the busiest tables there are; the indexer writes and never reads. */
object BlocksIndexes {

    val SET =
        IndexSet(
            "blocks",
            listOf(
                // /transactions?origin= and ?gasPayer=
                DeferrableIndex(
                    "transaction_origin_idx",
                    "transaction",
                    "(origin, block_number DESC, id DESC)",
                ),
                DeferrableIndex(
                    "transaction_gas_payer_idx",
                    "transaction",
                    "(gas_payer, block_number DESC, id DESC)",
                ),
                // /transactions/contract
                DeferrableIndex(
                    "clause_recipient_idx",
                    "clause",
                    "(to_address, block_number DESC, tx_id DESC) WHERE to_address IS NOT NULL",
                ),
            ),
        )
}
