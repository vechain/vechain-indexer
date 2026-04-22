package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.core.annotation.AliasFor

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    schema =
        Schema(
            type = "string",
            allowableValues =
                ["NeededAction", "MyChallenges", "OpenToJoin", "OthersActive", "History"],
        )
)
annotation class ChallengeFilterParameter(
    @get:AliasFor(annotation = Parameter::class, attribute = "name") val name: String = "filter",
    @get:AliasFor(annotation = Parameter::class, attribute = "description")
    val description: String =
        "- `NeededAction`: challenges that require a wallet-level action — outstanding invites you have not accepted or declined, claimable prizes on Completed MaxActions challenges you won, finalizable MaxActions challenges past their endRound, or reclaimable stake on Cancelled or Invalid challenges you were a participant in.\n" +
            "- `MyChallenges`: Pending or Active challenges you created or have joined.\n" +
            "- `OpenToJoin`: public Pending challenges you are not yet involved in.\n" +
            "- `OthersActive`: public Active challenges you are not involved in (observation only).\n" +
            "- `History`: terminal-state (Completed, Cancelled, Invalid) challenges you have been involved in.",
    @get:AliasFor(annotation = Parameter::class, attribute = "in")
    val `in`: ParameterIn = ParameterIn.QUERY,
    @get:AliasFor(annotation = Parameter::class, attribute = "required")
    val required: Boolean = true,
)
