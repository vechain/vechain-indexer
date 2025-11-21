package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.core.annotation.AliasFor

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    `in` = ParameterIn.QUERY,
    name = "eventName",
    array =
        ArraySchema(
            schema =
                Schema(
                    type = "string",
                    allowableValues =
                        [
                            "B3TR_SWAP_VOT3_TO_B3TR",
                            "B3TR_SWAP_B3TR_TO_VOT3",
                            "B3TR_PROPOSAL_SUPPORT",
                            "B3TR_CLAIM_REWARD",
                            "B3TR_UPGRADE_GM",
                            "B3TR_ACTION",
                            "B3TR_PROPOSAL_VOTE",
                            "B3TR_XALLOCATION_VOTE",
                            "TRANSFER_VET",
                            "TRANSFER_FT",
                            "TRANSFER_NFT",
                            "TRANSFER_SF",
                            "SWAP_VET_TO_FT",
                            "SWAP_FT_TO_VET",
                            "SWAP_FT_TO_FT",
                            "UNKNOWN_TX",
                            "NFT_SALE",
                            "STARGATE_DELEGATE_LEGACY",
                            "STARGATE_CLAIM_REWARDS_BASE_LEGACY",
                            "STARGATE_CLAIM_REWARDS_DELEGATE_LEGACY",
                            "STARGATE_UNDELEGATE_LEGACY",
                            "STARGATE_STAKE",
                            "STARGATE_UNSTAKE",
                            "STARGATE_DELEGATE_ACTIVE",
                            "STARGATE_DELEGATE_REQUEST",
                            "STARGATE_DELEGATE_EXIT_REQUEST",
                            "STARGATE_DELEGATION_EXITED_VALIDATOR",
                            "STARGATE_DELEGATION_EXITED",
                            "STARGATE_DELEGATE_REQUEST_CANCELLED",
                            "STARGATE_CLAIM_REWARDS",
                            "STARGATE_BOOST",
                            "STARGATE_MANAGER_ADDED",
                            "STARGATE_MANAGER_REMOVED",
                            "VEVOTE_VOTE_CAST",
                        ],
                    description =
                        "Filter by specific transaction names. See HistoryEventName for the full list.",
                )
        ),
    description = "Filter by specific transaction names.",
    required = false,
)
annotation class EventNameParameter(
    @get:AliasFor(annotation = Parameter::class, attribute = "description")
    val description: String = "Filter by specific transaction names."
)
