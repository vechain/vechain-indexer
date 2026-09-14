package org.vechain.indexer.validators

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.utils.BigIntegerUtils
import org.vechain.indexer.utils.PaginationUtils.offsetSlice
import org.vechain.indexer.validator.Delegation
import org.vechain.indexer.validator.DelegationReadRepository
import org.vechain.indexer.validator.DelegationStatus

@Profile("delegation")
@Service
open class DelegationService(private val delegationRepository: DelegationReadRepository) {

    open fun getDelegations(
        validator: String?,
        tokenId: String?,
        statuses: List<DelegationStatus>?,
        pageable: Pageable,
    ): Slice<Delegation> {
        val normalisedValidator = validator?.let(HexUtils::normalise)
        val decimalTokenId = tokenId?.let { BigIntegerUtils.fromHexOrDecimal(it).toString(10) }
        return offsetSlice(pageable, Delegation::blockNumber.name) { offset, limit, direction ->
            delegationRepository.find(
                normalisedValidator,
                decimalTokenId,
                statuses,
                direction,
                offset,
                limit,
            )
        }
    }

    open fun getDelegationCounts(validator: Address?): List<DelegationCountsResponse> =
        delegationRepository.countsByValidator(validator?.value?.lowercase()).map {
            DelegationCountsResponse(
                validator = it.validator,
                queued = it.queued,
                active = it.active,
                exiting = it.exiting,
            )
        }
}
