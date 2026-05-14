package org.vechain.indexer.validators

import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigInteger
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.validator.DelegationStatusV2
import org.vechain.indexer.validator.DelegationV2

/**
 * Public API representation of a [DelegationV2] document, served at
 * `/api/v1/validators/delegations`. Storage-only fields (`transitionAtBlock`, `txId`, block
 * metadata, `version`) are intentionally omitted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DelegationResponse(
    val id: String,
    val validator: String,
    val tokenId: String,
    val owner: String,
    val status: DelegationStatusV2,
    val tokenLevel: TokenLevel,
    val stakedAmount: String,
    val totalRewardsClaimed: BigInteger,
) {
    companion object {
        fun from(d: DelegationV2): DelegationResponse =
            DelegationResponse(
                id = d.id,
                validator = d.validator,
                tokenId = d.tokenId,
                owner = d.owner,
                status = d.status,
                tokenLevel = d.tokenLevel,
                stakedAmount = d.stakedAmount,
                totalRewardsClaimed = d.totalRewardsClaimed,
            )
    }
}
