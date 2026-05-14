package org.vechain.indexer.validators

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.validator.DelegationStatusV2
import org.vechain.indexer.validator.DelegationV2
import org.vechain.indexer.validator.DelegationV2Repository

@Profile("delegation")
@Service
open class DelegationService(private val delegationRepository: DelegationV2Repository) {

    open fun getDelegations(
        validator: String?,
        tokenId: String?,
        statuses: List<DelegationStatusV2>?,
        pageable: Pageable,
    ): Slice<DelegationV2> {
        val normalisedValidator = validator?.let(HexUtils::normalise)

        return when {
            normalisedValidator != null && tokenId != null && statuses != null ->
                delegationRepository.findByValidatorAndTokenIdAndStatusIn(
                    normalisedValidator,
                    tokenId,
                    statuses,
                    pageable,
                )
            normalisedValidator != null && tokenId != null ->
                delegationRepository.findByValidatorAndTokenId(
                    normalisedValidator,
                    tokenId,
                    pageable,
                )
            normalisedValidator != null && statuses != null ->
                delegationRepository.findByValidatorAndStatusIn(
                    normalisedValidator,
                    statuses,
                    pageable,
                )
            tokenId != null && statuses != null ->
                delegationRepository.findByTokenIdAndStatusIn(tokenId, statuses, pageable)
            normalisedValidator != null ->
                delegationRepository.findByValidator(normalisedValidator, pageable)
            tokenId != null -> delegationRepository.findByTokenId(tokenId, pageable)
            statuses != null -> delegationRepository.findByStatusIn(statuses, pageable)
            else -> delegationRepository.findAll(pageable)
        }
    }

    open fun getDelegationCounts(validator: Address?): List<DelegationCountsResponse> {
        val results =
            if (validator != null) {
                delegationRepository.aggregateDelegationCountsByValidator(
                    validator.value.lowercase()
                )
            } else {
                delegationRepository.aggregateDelegationCountsByValidator()
            }

        return results.map { result ->
            val countsByStatus = result.counts.associateBy { it.status }
            DelegationCountsResponse(
                validator = result._id,
                queued = countsByStatus["QUEUED"]?.count ?: 0L,
                active = countsByStatus["ACTIVE"]?.count ?: 0L,
                exiting = countsByStatus["EXITING"]?.count ?: 0L,
            )
        }
    }
}
