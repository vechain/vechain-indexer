package org.vechain.indexer.nft

import org.vechain.indexer.postgres.DeferrableIndex
import org.vechain.indexer.postgres.IndexSet

/** Both owner pages, random by address over every NFT transfer; the key takes a tokenId. */
object NftIndexes {

    val SET =
        IndexSet(
            "nft",
            listOf(
                current("ownership_owner_idx", "(owner, block_number, tx_id, id)"),
                current(
                    "ownership_owner_contract_idx",
                    "(owner, contract_address, block_number, tx_id, id)",
                ),
            ),
        )

    private fun current(name: String, columns: String) =
        DeferrableIndex(name, "ownership", "$columns WHERE superseded_at IS NULL")
}
