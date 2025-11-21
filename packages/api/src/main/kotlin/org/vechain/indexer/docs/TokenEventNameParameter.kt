package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.core.annotation.AliasFor
import org.vechain.indexer.history.HistoryEventName

/**
 * Parameter annotation for filtering by token-specific event names.
 *
 * The allowableValues are a curated subset of HistoryEventName for token history queries. This
 * includes STARGATE_* events, VEVOTE_VOTE_CAST, NFT_SALE, TRANSFER_NFT, and B3TR_UPGRADE_GM.
 *
 * @see HistoryEventName
 * @see ValidTokenEventName
 */
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
                            "STARGATE_STAKE",
                            "STARGATE_DELEGATE_REQUEST",
                            "STARGATE_DELEGATE_ACTIVE",
                            "STARGATE_UNDELEGATE_LEGACY",
                            "STARGATE_DELEGATE_EXIT_REQUEST",
                            "STARGATE_DELEGATION_EXITED_VALIDATOR",
                            "STARGATE_DELEGATION_EXITED",
                            "STARGATE_CLAIM_REWARDS",
                            "STARGATE_BOOST",
                            "STARGATE_MANAGER_ADDED",
                            "STARGATE_MANAGER_REMOVED",
                            "VEVOTE_VOTE_CAST",
                            "NFT_SALE",
                            "TRANSFER_NFT",
                            "B3TR_UPGRADE_GM",
                        ],
                )
        ),
    description = "Filter by specific transaction names.",
    required = false,
)
annotation class TokenEventNameParameter(
    @get:AliasFor(annotation = Parameter::class, attribute = "description")
    val description: String = "Filter by specific transaction names."
)
