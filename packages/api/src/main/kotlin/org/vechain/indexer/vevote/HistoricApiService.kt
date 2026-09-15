package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.PaginationUtils.offsetSlice

@Profile("vevote", "vevote-historic")
@Service
open class HistoricApiService(private val repository: HistoricProposalsReadRepository) {
    fun findAll(
        proposalId: String?,
        contractAddress: Address?,
        testProposals: Boolean?,
        pageable: Pageable,
    ): Slice<HistoricProposals> =
        offsetSlice(pageable, HistoricProposals::blockNumber.name) { offset, limit, direction ->
            repository.find(
                proposalId,
                contractAddress?.value,
                testProposals,
                offset,
                limit,
                direction,
            )
        }
}
