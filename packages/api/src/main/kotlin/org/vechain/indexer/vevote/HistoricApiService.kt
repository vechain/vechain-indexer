package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
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
        testProposals: Boolean? = false,
        pageable: Pageable,
    ): Slice<HistoricProposals> {
        val address = contractAddress?.value?.lowercase()

        // Case 1: Filter by both proposalId and contractAddress
        if (proposalId != null && address != null) {
            val id = "$address-$proposalId"
            val proposal = historicProposalsRepository.findById(id)
            val content = proposal?.let { listOf(it) } ?: emptyList()
            return SliceImpl(content, pageable, false)
        }

        // Case 2: Filter by proposalId only
        if (proposalId != null) {
            return historicProposalsRepository.findByProposalId(proposalId, pageable)
        }

        // Case 3: Filter by contractAddress (with or without test flag)
        if (address != null) {
            return if (testProposals == true) {
                historicProposalsRepository.findByContractAddressAndTest(address, true, pageable)
            } else {
                historicProposalsRepository.findByContractAddress(address, pageable)
            }
        }

        // Case 4: Filter by test flag only
        if (testProposals != null) {
            return historicProposalsRepository.findByTest(testProposals, pageable)
        }

        // Default: return all
        return historicProposalsRepository.findAll(pageable)
    }
}
