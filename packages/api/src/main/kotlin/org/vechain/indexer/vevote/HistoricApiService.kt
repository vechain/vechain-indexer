package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.vechain.indexer.thor.Address

@Profile("vevote", "vevote-historic-proposals")
@Service
open class HistoricApiService(
    private val historicProposalsRepository: HistoricProposalsRepository
) {
    fun findAll(
        proposalId: String?,
        contractAddress: Address?,
        pageable: Pageable,
    ): Slice<HistoricProposals> =
        if (proposalId != null && contractAddress != null) {
            val id = "${contractAddress.value.lowercase()}-$proposalId"
            val proposal = historicProposalsRepository.findByIdOrNull(id)
            val content: List<HistoricProposals> = proposal?.let { listOf(it) } ?: emptyList()
            SliceImpl(content, pageable, false)
        } else if (proposalId != null) {
            historicProposalsRepository.findByProposalId(proposalId, pageable)
        } else if (contractAddress != null) {
            historicProposalsRepository.findByContractAddress(
                contractAddress.value.lowercase(),
                pageable,
            )
        } else {
            historicProposalsRepository.findAll(pageable)
        }
}
