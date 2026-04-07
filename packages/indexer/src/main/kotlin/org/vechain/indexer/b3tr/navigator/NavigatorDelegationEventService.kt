package org.vechain.indexer.b3tr.navigator

import java.math.BigDecimal
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsString

@Service
@Profile("b3tr", "b3tr-navigator")
open class NavigatorDelegationEventService(
    private val repository: NavigatorDelegationEventRepository,
    private val citizenRepository: NavigatorCitizenRepository,
) {

    open fun processEvents(events: List<IndexedEvent>): List<NavigatorDelegationEvent> =
        events.map { ev ->
            val id = DigestUtils.sha1Hex("${ev.txId}_${ev.id}_${ev.eventType}")
            val citizen = ev.params.getAsString("citizen")?.lowercase() ?: ""
            val previousAmount = getCitizenAmount(citizen)

            when (ev.eventType) {
                "B3TR_DelegationCreated" -> {
                    val amount =
                        ev.params.getAsString("amount")?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    NavigatorDelegationEvent(
                        id = id,
                        blockId = ev.blockId,
                        blockNumber = ev.blockNumber,
                        blockTimestamp = ev.blockTimestamp,
                        txId = ev.txId,
                        navigator = ev.params.getAsString("navigator")?.lowercase() ?: "",
                        citizen = citizen,
                        eventType = ev.eventType,
                        amount = amount,
                        delta = amount,
                    )
                }
                "B3TR_DelegationUpdated" -> {
                    val newAmount =
                        ev.params.getAsString("newAmount")?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    NavigatorDelegationEvent(
                        id = id,
                        blockId = ev.blockId,
                        blockNumber = ev.blockNumber,
                        blockTimestamp = ev.blockTimestamp,
                        txId = ev.txId,
                        navigator = ev.params.getAsString("navigator")?.lowercase() ?: "",
                        citizen = citizen,
                        eventType = ev.eventType,
                        amount = newAmount,
                        delta = newAmount - previousAmount,
                    )
                }
                "B3TR_DelegationRemoved" -> {
                    NavigatorDelegationEvent(
                        id = id,
                        blockId = ev.blockId,
                        blockNumber = ev.blockNumber,
                        blockTimestamp = ev.blockTimestamp,
                        txId = ev.txId,
                        navigator = ev.params.getAsString("navigator")?.lowercase() ?: "",
                        citizen = citizen,
                        eventType = ev.eventType,
                        amount = BigDecimal.ZERO,
                        delta = previousAmount.negate(),
                    )
                }
                else -> error("Unexpected event type: ${ev.eventType}")
            }
        }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(records: List<NavigatorDelegationEvent>) {
        if (records.isNotEmpty()) repository.saveAll(records)
    }

    private fun getCitizenAmount(citizenAddress: String): BigDecimal =
        if (citizenAddress.isBlank()) BigDecimal.ZERO
        else citizenRepository.findById(citizenAddress).orElse(null)?.amount ?: BigDecimal.ZERO
}
