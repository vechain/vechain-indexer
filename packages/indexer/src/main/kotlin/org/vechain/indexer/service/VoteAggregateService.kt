package org.vechain.indexer.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.FilterCriteria
import org.vechain.indexer.repository.VoteAggregateRepository

@Profile("vevote-events")
@Service
class VoteAggregateService(
    @Value("\${veworld.contract.vevote.address}") private val contractAddress: String,
    private val repository: VoteAggregateRepository
) {

    fun getFileCriteria(): FilterCriteria {
        return FilterCriteria(
            contractAddresses = listOf(contractAddress),
            eventNames = listOf("VoteCast"),
        )
    }
}
