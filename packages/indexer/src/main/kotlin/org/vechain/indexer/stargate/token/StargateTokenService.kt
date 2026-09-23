package org.vechain.indexer.stargate.token

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.event.model.generic.IndexedEvent

/** Loads the snapshots a block's events touch, applies the events and keeps what changed. */
@Profile("stargate", "stargate-token")
@Service
open class StargateTokenService(
    private val stargateTokenRepository: StargateTokenWriteRepository,
    private val eventService: StargateEventService,
) {
    /** The snapshots this block's events modified or minted. */
    open fun processEvents(events: List<IndexedEvent>): List<StargateToken> {
        val tokenIds = events.mapNotNull(StargateEventService::tokenIdOf).toSet()
        if (tokenIds.isEmpty()) return emptyList()
        val loaded = stargateTokenRepository.findAllById(tokenIds).associateBy { it.tokenId }
        val latest = loaded.toMutableMap()
        eventService.handleStargateEvents(events, latest)
        return latest.values.filter { it != loaded[it.tokenId] }
    }

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(tokens: List<StargateToken>) {
        if (tokens.isEmpty()) return
        stargateTokenRepository.save(tokens)
    }
}
