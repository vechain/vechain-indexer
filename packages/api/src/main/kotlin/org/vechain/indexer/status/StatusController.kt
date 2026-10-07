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
@Tag(name = "Status", description = "How up to date the data is.")
@Validated
@RestController
@RequestMapping(STATUS_PATH)
open class StatusController(private val statusService: StatusService) {

    @GetMapping
    @Operation(
        summary = "Get the latest block each indexer has saved",
        description =
            "The latest block each indexer has saved. `blockNumber` is null for an indexer " +
                "that hasn't saved anything yet. Indexers run independently, so if one is behind," +
                " only the endpoints that use it are out of date. Compare with a Thor node's best" +
                " block to see how far behind.",
    )
    @CommonApiResponses
    @CacheFor(CachePolicy.VOLATILE)
    open fun getStatus(): List<IndexerCheckpoint> = statusService.checkpoints()
}
