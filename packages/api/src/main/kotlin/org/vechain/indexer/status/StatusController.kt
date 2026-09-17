package org.vechain.indexer.status

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.config.postgres.ConditionalOnPostgres
import org.vechain.indexer.constants.STATUS_PATH
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.postgres.IndexerCheckpoint
import org.vechain.indexer.rest.CacheFor
import org.vechain.indexer.rest.CachePolicy

@ConditionalOnPostgres
@Tag(name = "Status", description = "How far each indexer has read the chain")
@Validated
@RestController
@RequestMapping(STATUS_PATH)
open class StatusController(private val statusService: StatusService) {

    @GetMapping
    @Operation(
        summary = "Get each indexer's latest indexed block",
        description =
            """
            Returns one entry per indexer running against this database, newest committed block
            first seen from its own checkpoint. An indexer that has written nothing yet reports a
            null `blockNumber`.

            Indexers advance independently, so one lagging entry means that domain's endpoints are
            behind while the rest are current. Compare against a Thor node's best block for the
            gap, or against the other colour's entries before a blue/green switch.
        """,
    )
    @CommonApiResponses
    @CacheFor(CachePolicy.VOLATILE)
    open fun getStatus(): List<IndexerCheckpoint> = statusService.checkpoints()
}
