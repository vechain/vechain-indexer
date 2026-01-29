package org.vechain.indexer.transfer

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.vechain.indexer.thor.Address

@Profile("transfers")
@Service
open class TransferEventService(
    private val transferEventRepository: TransferEventRepository,
    private val fungibleTokenInteractionsRepository: FungibleTokenInteractionsRepository,
    private val officialTokenService: OfficialTokenService,
) {

    fun find(
        address: Address,
        tokenAddress: Address,
        eventType: TransferEventType?,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent> {
        return if (eventType != null) {
            transferEventRepository.findByToOrFromAndTokenAddressAndEventType(
                address.value,
                tokenAddress.value,
                eventType,
                pageable,
            )
        } else {
            transferEventRepository.findByToOrFromAndTokenAddress(
                address.value,
                tokenAddress.value,
                pageable,
            )
        }
    }

    fun findByAddress(
        address: Address,
        eventType: TransferEventType?,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent> {
        return if (eventType != null) {
            transferEventRepository.findByToOrFromAndEventType(address.value, eventType, pageable)
        } else {
            transferEventRepository.findByToOrFrom(address.value, address.value, pageable)
        }
    }

    fun findByTokenAddress(
        tokenAddress: Address,
        eventType: TransferEventType?,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent> {
        return if (eventType != null) {
            transferEventRepository.findByTokenAddressAndEventType(
                tokenAddress.value,
                eventType,
                pageable,
            )
        } else {
            transferEventRepository.findByTokenAddress(tokenAddress.value, pageable)
        }
    }

    fun findByTo(
        to: Address,
        tokenAddress: Address?,
        eventType: TransferEventType?,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent> {
        return if (tokenAddress != null && eventType != null) {
            transferEventRepository.findByToAndTokenAddressAndEventType(
                to.value,
                tokenAddress.value,
                eventType,
                pageable,
            )
        } else if (tokenAddress != null) {
            transferEventRepository.findByToAndTokenAddress(to.value, tokenAddress.value, pageable)
        } else if (eventType != null) {
            transferEventRepository.findByToAndEventType(to.value, eventType, pageable)
        } else {
            transferEventRepository.findByTo(to.value, pageable)
        }
    }

    fun findByFrom(
        from: Address,
        tokenAddress: Address?,
        eventType: TransferEventType?,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent> {
        return if (tokenAddress != null && eventType != null) {
            transferEventRepository.findByFromAndTokenAddressAndEventType(
                from.value,
                tokenAddress.value,
                eventType,
                pageable,
            )
        } else if (tokenAddress != null) {
            transferEventRepository.findByFromAndTokenAddress(
                from.value,
                tokenAddress.value,
                pageable,
            )
        } else if (eventType != null) {
            transferEventRepository.findByFromAndEventType(from.value, eventType, pageable)
        } else {
            transferEventRepository.findByFrom(from.value, pageable)
        }
    }

    fun findByBlockNumber(
        blockNumber: Long,
        addresses: List<Address>,
        pageable: Pageable,
    ): Slice<IndexedTransferEvent> {
        return transferEventRepository.findByBlockNumberAndToOrFromIn(
            blockNumber,
            addresses.map { it.value },
            pageable,
        )
    }

    fun findFungibleTokensContractsByAddress(
        address: Address,
        officialTokensOnly: Boolean,
        pageable: Pageable,
    ): Slice<String> {
        val interactions =
            if (officialTokensOnly) {
                fungibleTokenInteractionsRepository.findAllByWalletAddressAndContractAddresses(
                    address.value,
                    officialTokenService.getOfficialTokenAddresses(),
                    pageable,
                )
            } else {
                fungibleTokenInteractionsRepository.findByWalletAddress(address.value, pageable)
            }
        return interactions.map { it.contractAddress }
    }
}
