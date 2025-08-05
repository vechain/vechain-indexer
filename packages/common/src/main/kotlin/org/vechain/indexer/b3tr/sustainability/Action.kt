package org.vechain.indexer.b3tr.sustainability

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.utils.scaleDown

/**
 * Sustainable action This model is used to track sustainable actions. We use the RewardDistributed
 * event on the X2EarnRewardsPool contract to track the sustainable actions.
 */
@Document(collection = "sustainable_actions")
data class Action
@ConstructorBinding
constructor(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val appId: String,
    val distributor: String,
    @JsonIgnore val amountRaw: BigInteger,
    val amount: Double = scaleDown(amountRaw, 18),
    val receiver: String,
    @JsonIgnore val proofRaw: String?,
    val proof: SustainabilityProofV2?,
) : IndexedDocument
