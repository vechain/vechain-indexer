package org.vechain.indexer.transfer

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** Every b-tree on the transfer table but the one rollback reads; the key is replay alone. */
object TransferIndexes {

    val SET =
        IndexSet(
            "transfers",
            listOf(
                // /transfers/latest?eventType=
                DeferrableIndex(
                    "transfer_type_block_idx",
                    "transfer",
                    "(event_type, block_number DESC, transfer_index)",
                ),
                // /transfers, /transfers/to and /transfers/from
                DeferrableIndex(
                    "transfer_to_idx",
                    "transfer",
                    "(to_address, block_timestamp, transfer_index)",
                ),
                DeferrableIndex(
                    "transfer_from_idx",
                    "transfer",
                    "(from_address, block_timestamp, transfer_index)",
                ),
                DeferrableIndex(
                    "transfer_token_idx",
                    "transfer",
                    "(token_address, block_timestamp, transfer_index) " +
                        "WHERE token_address IS NOT NULL",
                ),
                // A sha1 no query names: it exists so a replayed block inserts nothing twice.
                DeferrableIndex("transfer_pkey", "transfer", "(id)", primaryKey = true),
            ),
        )
}
