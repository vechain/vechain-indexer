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
                            "TRANSFER_NFT",
                            "NFT_SALE",
                            "VEVOTE_VOTE_CAST",
                        ],
                )
        ),
    description = "Filter by Stargate token history event names.",
    required = false,
)
annotation class StargateTokenHistoryEventNameParameter(
    @get:AliasFor(annotation = Parameter::class, attribute = "description")
    val description: String = "Filter by Stargate token history event names."
)
