package org.vechain.indexer.utils

import java.math.BigInteger
import org.slf4j.LoggerFactory
import org.vechain.indexer.contracts.specifications.ERC1155Contract
import org.vechain.indexer.contracts.specifications.Signatures
import org.vechain.indexer.contracts.specifications.VIP210Contract
import org.vechain.indexer.event.model.generic.GenericEventParameters
import org.vechain.indexer.model.TransferEventType
import org.vechain.indexer.model.history.HistoryEventName
import org.vechain.indexer.thor.model.TxEvent
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.Event
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.core.methods.response.Log
import org.web3j.tx.Contract
import org.web3j.utils.Numeric

data class TransferParameters(
    val from: String,
    val to: String,
    val tokenId: BigInteger?,
    val amount: BigInteger,
    val eventType: TransferEventType,
)

object EventUtils {
    private val logger = LoggerFactory.getLogger(EventUtils::class.java)

    fun isNftTransferEvent(event: TxEvent): Boolean =
        event.topics.size == 4 &&
            HexUtils.compare(event.topics[0], Signatures.Common.TRANSFER_EVENT)

    fun isFungibleTransferEvent(event: TxEvent): Boolean =
        event.topics.size == 3 &&
            HexUtils.compare(event.topics[0], Signatures.Common.TRANSFER_EVENT)

    fun isTransferBatchEvent(event: TxEvent): Boolean =
        isEvent(event, ERC1155Contract.TRANSFER_BATCH_EVENT) ||
            isEvent(event, VIP210Contract.TRANSFER_BATCH_EVENT)

    fun isTransferSingleEvent(event: TxEvent): Boolean =
        isEvent(event, ERC1155Contract.TRANSFER_SINGLE_EVENT) ||
            isEvent(event, VIP210Contract.TRANSFER_SINGLE_EVENT)

    fun isEvent(
        event: TxEvent,
        eventSignature: String,
    ): Boolean = event.topics.isNotEmpty() && HexUtils.compare(event.topics[0], eventSignature)

    fun isTransferEvent(event: TxEvent): Boolean =
        event.topics.isNotEmpty() &&
            TRANSFER_SIGNATURES.contains(HexUtils.removePrefix(event.topics[0]))

    fun getEventParams(event: TxEvent): List<TransferParameters> =
        when (true) {
            isFungibleTransferEvent(event) -> getFungibleParameters(event)
            isNftTransferEvent(event) -> getNFTParameters(event)
            isTransferSingleEvent(event) -> getSingleTransferParameters(event)
            isTransferBatchEvent(event) -> getBatchTransferParameters(event)
            else -> throw IllegalArgumentException("Event topics cannot be empty")
        }

    fun getFungibleParameters(event: TxEvent): List<TransferParameters> {
        val amount = Numeric.decodeQuantity(event.data)

        return listOf(
            TransferParameters(
                from = AddressUtils.decode(event.topics[1]),
                to = AddressUtils.decode(event.topics[2]),
                tokenId = null,
                amount = amount,
                eventType = TransferEventType.FUNGIBLE_TOKEN,
            ),
        )
    }

    fun getNFTParameters(event: TxEvent): List<TransferParameters> {
        val tokenId = Numeric.decodeQuantity(event.topics[3])

        return listOf(
            TransferParameters(
                from = AddressUtils.decode(event.topics[1]),
                to = AddressUtils.decode(event.topics[2]),
                tokenId = tokenId,
                amount = BigInteger.ONE,
                eventType = TransferEventType.NFT,
            ),
        )
    }

    fun getSingleTransferParameters(event: TxEvent): List<TransferParameters> {
        try {
            val ev =
                if (isEvent(event, ERC1155Contract.TRANSFER_SINGLE_EVENT)) {
                    ERC_TRANSFER_SINGLE_EVENT
                } else if (isEvent(event, VIP210Contract.TRANSFER_SINGLE_EVENT)) {
                    VIP_TRANSFER_SINGLE_EVENT
                } else {
                    throw IllegalArgumentException("Invalid event signature")
                }

            val eventParameters = Contract.staticExtractEventParameters(ev, buildEventLog(event))

            val tokenId = eventParameters.nonIndexedValues[0] as Uint256
            val amount = eventParameters.nonIndexedValues[1] as Uint256
            val from = eventParameters.indexedValues[1] as Address
            val to = eventParameters.indexedValues[2] as Address

            return listOf(
                TransferParameters(
                    from = from.value,
                    to = to.value,
                    tokenId = tokenId.value,
                    amount = amount.value,
                    eventType = TransferEventType.SEMI_FUNGIBLE_TOKEN,
                ),
            )
        } catch (e: Exception) {
            logger.error("Error parsing single transfer event", e)
            throw e
        }
    }

    fun getBatchTransferParameters(event: TxEvent): List<TransferParameters> {
        try {
            val ev =
                if (isEvent(event, ERC1155Contract.TRANSFER_BATCH_EVENT)) {
                    ERC_TRANSFER_BATCH_EVENT
                } else if (isEvent(event, VIP210Contract.TRANSFER_BATCH_EVENT)) {
                    VIP_TRANSFER_BATCH_EVENT
                } else {
                    throw IllegalArgumentException("Invalid event signature")
                }

            val eventParameters = Contract.staticExtractEventParameters(ev, buildEventLog(event))

            val tokenIds = eventParameters.nonIndexedValues[0] as DynamicArray<*>
            val amounts = eventParameters.nonIndexedValues[1] as DynamicArray<*>

            val from = eventParameters.indexedValues[1] as Address
            val to = eventParameters.indexedValues[2] as Address

            return tokenIds.value.mapIndexed { index, tokenId ->
                TransferParameters(
                    from = from.value,
                    to = to.value,
                    tokenId = tokenId.value as BigInteger,
                    amount = amounts.value[index].value as BigInteger,
                    eventType = TransferEventType.SEMI_FUNGIBLE_TOKEN,
                )
            }
        } catch (e: Exception) {
            logger.error("Error parsing batch transfer event", e)
            throw e
        }
    }

    fun determineEventType(genericParams: GenericEventParameters): HistoryEventName? =
        when (genericParams.getEventType()) {
            "B3TR_Vot3ToB3trSwap" -> HistoryEventName.B3TR_SWAP_VOT3_TO_B3TR
            "B3TR_B3trToVot3Swap" -> HistoryEventName.B3TR_SWAP_B3TR_TO_VOT3
            "B3TR_ProposalDeposit" -> HistoryEventName.B3TR_PROPOSAL_SUPPORT
            "B3TR_ClaimReward" -> HistoryEventName.B3TR_CLAIM_REWARD
            "B3TR_GMUpgrade" -> HistoryEventName.B3TR_UPGRADE_GM
            "B3TR_ActionReward" -> HistoryEventName.B3TR_ACTION
            "B3TR_ProposalVote" -> HistoryEventName.B3TR_PROPOSAL_VOTE
            "B3TR_XAllocationVote" -> HistoryEventName.B3TR_XALLOCATION_VOTE
            "Transfer" -> {
                when {
                    genericParams.params["value"] != null -> HistoryEventName.TRANSFER_FT
                    genericParams.params["tokenId"] != null -> HistoryEventName.TRANSFER_NFT
                    else -> null
                }
            }
            "TransferSingle",
            "TransferBatch", -> HistoryEventName.TRANSFER_SF
            "VET_TRANSFER" -> HistoryEventName.TRANSFER_VET
            "FT_VET_Swap" -> HistoryEventName.SWAP_FT_TO_VET
            "VET_FT_Swap" -> HistoryEventName.SWAP_VET_TO_FT
            "Token_FTSwap" -> HistoryEventName.SWAP_FT_TO_FT
            else -> null // Other events will not be labeled
        }

    fun determineTransferType(genericParams: GenericEventParameters): TransferEventType? =
        when (genericParams.getEventType()) {
            "Transfer" -> {
                when {
                    genericParams.params["value"] != null -> TransferEventType.FUNGIBLE_TOKEN
                    genericParams.params["tokenId"] != null -> TransferEventType.NFT
                    else -> null
                }
            }
            "TransferSingle",
            "TransferBatch", -> TransferEventType.SEMI_FUNGIBLE_TOKEN
            "VET_TRANSFER" -> TransferEventType.VET
            else -> null // Other events will not be labeled
        }

    /** This can be used for decoding events with Web3J */
    private fun buildEventLog(txEvent: TxEvent): Log {
        val log = Log()
        log.address = txEvent.address
        log.topics = txEvent.topics
        log.data = txEvent.data
        return log
    }
}

private val ERC_TRANSFER_BATCH_EVENT =
    Event(
        "TransferBatch",
        listOf(
            object : TypeReference<Address>(true) {},
            object : TypeReference<Address>(true) {},
            object : TypeReference<Address>(true) {},
            object : TypeReference<DynamicArray<Uint256>>() {},
            object : TypeReference<DynamicArray<Uint256>>() {},
        ),
    )

private val VIP_TRANSFER_BATCH_EVENT =
    Event(
        "TransferBatch",
        listOf(
            object : TypeReference<Address>(true) {},
            object : TypeReference<Address>(true) {},
            object : TypeReference<Address>(true) {},
            object : TypeReference<DynamicArray<Uint256>>() {},
            object : TypeReference<DynamicArray<Uint256>>() {},
            object : TypeReference<Utf8String>() {},
        ),
    )

private val ERC_TRANSFER_SINGLE_EVENT =
    Event(
        "TransferSingle",
        listOf<TypeReference<*>>(
            object : TypeReference<Address>(true) {},
            object : TypeReference<Address>(true) {},
            object : TypeReference<Address>(true) {},
            object : TypeReference<Uint256>() {},
            object : TypeReference<Uint256>() {},
        ),
    )

private val VIP_TRANSFER_SINGLE_EVENT =
    Event(
        "TransferSingle",
        listOf<TypeReference<*>>(
            object : TypeReference<Address>(true) {},
            object : TypeReference<Address>(true) {},
            object : TypeReference<Address>(true) {},
            object : TypeReference<Uint256>() {},
            object : TypeReference<Uint256>() {},
            object : TypeReference<Utf8String>() {},
        ),
    )

private val TRANSFER_SIGNATURES =
    listOf(
        Signatures.Common.TRANSFER_EVENT,
        VIP210Contract.TRANSFER_BATCH_EVENT,
        VIP210Contract.TRANSFER_SINGLE_EVENT,
        ERC1155Contract.TRANSFER_BATCH_EVENT,
        ERC1155Contract.TRANSFER_SINGLE_EVENT,
    )
