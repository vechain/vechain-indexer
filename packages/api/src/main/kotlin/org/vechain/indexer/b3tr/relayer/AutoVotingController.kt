package org.vechain.indexer.b3tr.relayer

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.B3TR_PATH
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.docs.RoundIdParameter

@Profile("b3tr", "b3tr-auto-voting-users")
@Tag(
    name = "B3TR - Relayer",
    description = "Endpoints for the VeBetterDAO relayer-node action queue.",
)
@Validated
@RestController
@RequestMapping("$B3TR_PATH/relayer/auto-voting-users")
open class AutoVotingController(private val service: AutoVotingService) {

    @GetMapping("/at-round/{roundId}")
    @Operation(
        summary = "List addresses with auto-voting enabled for a given round",
        description =
            """
            Returns the full set of addresses that had auto-voting enabled at the start of the
            given round.

            Toggles are checkpointed on-chain: a toggle emitted during round N is effective from
            round N+1 onward, because round N's auto-voting state was already snapshotted at its
            `voteStart` block before the event landed. This endpoint applies that semantic by
            filtering `AutoVotingToggled` history to `roundId < requestedRoundId` and returning the
            latest status per address.

            Returned in a single response (no pagination) — the relayer consumes the whole queue
            at once.
        """,
    )
    @RoundIdParameter(required = true, `in` = ParameterIn.PATH)
    @CommonApiResponses
    open fun getEnabledAtRound(@PathVariable(required = true) roundId: Int): List<String> =
        service.findEnabledAddressesAtRound(roundId = roundId)
}
