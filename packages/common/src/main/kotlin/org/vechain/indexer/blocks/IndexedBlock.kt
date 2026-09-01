package org.vechain.indexer.blocks

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonView
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.model.Views

/**
 * A collapsed VeChainThor block header. Named `IndexedBlock` to avoid clashing with indexer-core's
 * `org.vechain.indexer.thor.model.Block`.
 *
 * The `@get:JsonProperty` renames reproduce Thor's wire names, so the serialised document is a
 * collapsed `GET /blocks/{revision}` response plus two totals, minus `isTrunk`/`isFinalized` — see
 * `BlockController` for why those two are omitted. Mongo field names are unaffected: Spring Data
 * maps on property names, so range queries still filter and sort on `blockNumber`.
 */
@Document(collection = IndexerNames.BLOCKS.COLLECTION)
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class IndexedBlock
@ConstructorBinding
@JsonCreator
constructor(
    @get:JsonProperty("number") override val blockNumber: Long,
    @get:JsonProperty("id") override val blockId: String,
    @get:JsonProperty("timestamp") override val blockTimestamp: Long,
    val size: Long,
    val parentID: String,
    val gasLimit: Long,
    val gasUsed: Long,
    val beneficiary: String,
    val totalScore: Long,
    val txsRoot: String,
    val txsFeatures: Int,
    val stateRoot: String,
    val receiptsRoot: String,
    val com: Boolean,
    val signer: String,
    // omitempty in Thor — absent on pre-GALACTICA blocks.
    val baseFeePerGas: String? = null,
    // Totals over the block's transactions; `totalVthoPaid` is hex wei, like Thor's own `paid`.
    val clauseCount: Int = 0,
    val totalVthoPaid: String = "0x0",
    val transactions: List<String> = emptyList(),
    @JsonIgnore @Id val id: String = blockNumber.toString(),
) : IndexedDocument
