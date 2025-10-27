package org.vechain.indexer.stargate.nftHolders

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.stargate.token.LevelledValue
import org.vechain.indexer.stargate.token.TokenLevel

@Document(collection = "stargate_total_nft_holders_by_block")
data class NftHoldersByBlock
@ConstructorBinding
constructor(
    @JsonIgnore override val blockId: String,
    @JsonIgnore @Id override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    override val total: Long,
    override val byLevel: Map<TokenLevel, Long>,
) : IndexedDocument, LevelledValue<Long> {
    override fun valueForLevel(level: TokenLevel?): Long =
        if (level == null) total else byLevel[level] ?: 0L
}
