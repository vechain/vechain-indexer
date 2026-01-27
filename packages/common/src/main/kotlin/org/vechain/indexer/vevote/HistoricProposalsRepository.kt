package org.vechain.indexer.vevote

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface HistoricProposalsRepository : PostgresIndexedRepository {
    fun saveAll(proposals: List<HistoricProposals>)

    fun findById(id: String): HistoricProposals?

    fun findAllById(ids: List<String>): List<HistoricProposals>

    fun updateVoteTallies(id: String, voteTallies: List<Long>, totalVotes: Long)

    fun findAll(pageable: Pageable): Slice<HistoricProposals>

    fun findByProposalId(proposalId: String, pageable: Pageable): Slice<HistoricProposals>

    fun findByContractAddress(contractAddress: String, pageable: Pageable): Slice<HistoricProposals>

    fun findByContractAddressAndTest(
        contractAddress: String,
        test: Boolean,
        pageable: Pageable,
    ): Slice<HistoricProposals>

    fun findByTest(test: Boolean, pageable: Pageable): Slice<HistoricProposals>
}
