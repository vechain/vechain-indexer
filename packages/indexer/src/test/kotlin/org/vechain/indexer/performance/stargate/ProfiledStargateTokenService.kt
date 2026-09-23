package org.vechain.indexer.performance.stargate

import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.stargate.token.StargateEventService
import org.vechain.indexer.stargate.token.StargateToken
import org.vechain.indexer.stargate.token.StargateTokenService
import org.vechain.indexer.stargate.token.StargateTokenWriteRepository

/** StargateTokenService with its event processing and Postgres writes timed. */
class ProfiledStargateTokenService(
    repository: StargateTokenWriteRepository,
    eventService: StargateEventService,
    private val profiler: DetailedProfiler,
) : StargateTokenService(repository, eventService) {

    override fun processEvents(events: List<IndexedEvent>): List<StargateToken> =
        profiler.time("      StargateTokenService.processEvents") { super.processEvents(events) }

    override fun save(tokens: List<StargateToken>) {
        profiler.time("      StargateTokenService.save (Postgres)") { super.save(tokens) }
    }
}
