package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository

@Profile("vevote", "vevote-historic-proposals")
@Repository
interface HistoricProposalsRepository :
    BasePagingAndSortingIndexedRepository<HistoricProposals, String> {
    fun findByProposalId(proposalId: String, pageable: Pageable): Slice<HistoricProposals>

    fun findByContractAddress(contractAddress: String, pageable: Pageable): Slice<HistoricProposals>

    fun findByContractAddressAndTest(
        contractAddress: String,
        test: Boolean,
        pageable: Pageable,
    ): Slice<HistoricProposals>

    fun findByTest(test: Boolean, pageable: Pageable): Slice<HistoricProposals>
}
