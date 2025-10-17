package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsLong
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("vevote", "vevote-historic-proposals")
@Service
open class HistoricProposalsVoteService {
    fun processVotes(events: List<IndexedEvent>): List<HistoricProposalsVote> {
        return events
            .mapNotNull { event ->
                val proposalId = event.params.getAsString("proposalId") ?: return@mapNotNull null
                val voter =
                    event.params.getAsString("sender")
                        ?: event.params.getAsString("voter")
                        ?: return@mapNotNull null
                val encodedChoices = event.params.getAsLong("options") ?: return@mapNotNull null
                val smartContractAddress = event.address ?: return@mapNotNull null
                val choices = decodeChoices(encodedChoices)

                HistoricProposalsVote(
                    id = "$proposalId-$voter-$smartContractAddress",
                    proposalId = proposalId,
                    contract = smartContractAddress,
                    choices = choices,
                    blockNumber = event.blockNumber,
                    blockTimestamp = event.blockTimestamp,
                    blockId = event.blockId,
                )
            }
            .groupBy { it.id }
            .mapValues { (_, votes) -> votes.maxBy { it.blockNumber } }
            .values
            .toList()
    }

    fun decodeChoices(choiceValue: Long): List<Int> {
        if (choiceValue < 0) return emptyList()
        return choiceValue.toString(2).reversed().mapIndexedNotNull { index, bit ->
            if (bit == '1') index + 1 else null
        }
    }
}
