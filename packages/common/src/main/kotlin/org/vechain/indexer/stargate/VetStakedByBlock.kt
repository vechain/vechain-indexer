package org.vechain.indexer.stargate

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument

@Document(collection = "stargate_total_vet_staked_by_block")
data class VetStakedByBlock
@ConstructorBinding
constructor(
    @JsonIgnore override val blockId: String,
    @JsonIgnore @Id override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    override val total: BigInteger,
    override val byLevel: Map<TokenLevel, BigInteger>,
) : IndexedDocument, LevelledValue<BigInteger> {
    override fun valueForLevel(level: TokenLevel?): BigInteger {
        return if (level == null) total else byLevel[level] ?: BigInteger.ZERO
    }
}
