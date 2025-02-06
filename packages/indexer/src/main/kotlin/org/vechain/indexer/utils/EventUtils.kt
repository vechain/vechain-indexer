package org.vechain.indexer.utils

import java.math.BigInteger
import org.slf4j.LoggerFactory
import org.vechain.indexer.contracts.specifications.ERC1155Contract
import org.vechain.indexer.contracts.specifications.Signatures
import org.vechain.indexer.contracts.specifications.VIP210Contract
import org.vechain.indexer.event.model.generic.GenericEventParameters
import org.vechain.indexer.model.HistoryEventName
import org.vechain.indexer.model.HistoryEventType
import org.vechain.indexer.model.TransferEventType
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
    val eventType: TransferEventType
)

object EventUtils {

    private val logger = LoggerFactory.getLogger(EventUtils::class.java)

    fun isNftTransferEvent(event: TxEvent): Boolean {
        return event.topics.size == 4 &&
            HexUtils.compare(event.topics[0], Signatures.Common.TRANSFER_EVENT)
    }

    fun isFungibleTransferEvent(event: TxEvent): Boolean {
        return event.topics.size == 3 &&
            HexUtils.compare(event.topics[0], Signatures.Common.TRANSFER_EVENT)
    }

    fun isTransferBatchEvent(event: TxEvent): Boolean {
        return isEvent(event, ERC1155Contract.TRANSFER_BATCH_EVENT) ||
            isEvent(event, VIP210Contract.TRANSFER_BATCH_EVENT)
    }

    fun isTransferSingleEvent(event: TxEvent): Boolean {
        return isEvent(event, ERC1155Contract.TRANSFER_SINGLE_EVENT) ||
            isEvent(event, VIP210Contract.TRANSFER_SINGLE_EVENT)
    }

    fun isEvent(event: TxEvent, eventSignature: String): Boolean {
        return event.topics.isNotEmpty() && HexUtils.compare(event.topics[0], eventSignature)
    }

    fun isTransferEvent(event: TxEvent): Boolean {
        return event.topics.isNotEmpty() &&
            TRANSFER_SIGNATURES.contains(HexUtils.removePrefix(event.topics[0]))
    }

    fun getEventParams(event: TxEvent): List<TransferParameters> {
        return when (true) {
            isFungibleTransferEvent(event) -> getFungibleParameters(event)
            isNftTransferEvent(event) -> getNFTParameters(event)
            isTransferSingleEvent(event) -> getSingleTransferParameters(event)
            isTransferBatchEvent(event) -> getBatchTransferParameters(event)
            else -> throw IllegalArgumentException("Event topics cannot be empty")
        }
    }

    fun getFungibleParameters(event: TxEvent): List<TransferParameters> {
        val amount = Numeric.decodeQuantity(event.data)

        return listOf(
            TransferParameters(
                from = AddressUtils.decode(event.topics[1]),
                to = AddressUtils.decode(event.topics[2]),
                tokenId = null,
                amount = amount,
                eventType = TransferEventType.FUNGIBLE_TOKEN
            )
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
                eventType = TransferEventType.NFT
            )
        )
    }

    fun getSingleTransferParameters(event: TxEvent): List<TransferParameters> {
        try {

            val ev =
                if (isEvent(event, ERC1155Contract.TRANSFER_SINGLE_EVENT)) ERC_TRANSFER_SINGLE_EVENT
                else if (isEvent(event, VIP210Contract.TRANSFER_SINGLE_EVENT))
                    VIP_TRANSFER_SINGLE_EVENT
                else throw IllegalArgumentException("Invalid event signature")

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
                    eventType = TransferEventType.SEMI_FUNGIBLE_TOKEN
                )
            )
        } catch (e: Exception) {
            logger.error("Error parsing single transfer event", e)
            throw e
        }
    }

    fun getBatchTransferParameters(event: TxEvent): List<TransferParameters> {

        try {

            val ev =
                if (isEvent(event, ERC1155Contract.TRANSFER_BATCH_EVENT)) ERC_TRANSFER_BATCH_EVENT
                else if (isEvent(event, VIP210Contract.TRANSFER_BATCH_EVENT))
                    VIP_TRANSFER_BATCH_EVENT
                else throw IllegalArgumentException("Invalid event signature")

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
                    eventType = TransferEventType.SEMI_FUNGIBLE_TOKEN
                )
            }
        } catch (e: Exception) {
            logger.error("Error parsing batch transfer event", e)
            throw e
        }
    }

    fun determineEventType(
        genericParams: GenericEventParameters
    ): Pair<HistoryEventName, HistoryEventType>? {
        return when (genericParams.getEventType()) {
            "B3TR_Vot3ToB3trSwap" ->
                Pair(HistoryEventName.B3TR_SWAP_VOT3_TO_B3TR, HistoryEventType.B3TR)
            "B3TR_B3trToVot3Swap" ->
                Pair(HistoryEventName.B3TR_SWAP_B3TR_TO_VOT3, HistoryEventType.B3TR)
            "B3TR_ProposalDeposit" ->
                Pair(HistoryEventName.B3TR_PROPOSAL_SUPPORT, HistoryEventType.B3TR)
            "B3TR_ClaimReward" -> Pair(HistoryEventName.B3TR_CLAIM_REWARD, HistoryEventType.B3TR)
            "B3TR_GMUpgrade" -> Pair(HistoryEventName.B3TR_UPGRADE_GM, HistoryEventType.B3TR)
            "B3TR_ActionReward" -> Pair(HistoryEventName.B3TR_ACTION, HistoryEventType.B3TR)
            "B3TR_ProposalVote" -> Pair(HistoryEventName.B3TR_PROPOSAL_VOTE, HistoryEventType.B3TR)
            "B3TR_XAllocationVote" ->
                Pair(HistoryEventName.B3TR_XALLOCATION_VOTE, HistoryEventType.B3TR)
            "Transfer" -> {
                when {
                    genericParams.params["value"] != null ->
                        Pair(HistoryEventName.TRANSFER_FT, HistoryEventType.TRANSFER)
                    genericParams.params["tokenId"] != null ->
                        Pair(HistoryEventName.TRANSFER_NFT, HistoryEventType.TRANSFER)
                    else -> null
                }
            }
            "TransferSingle",
            "TransferBatch" -> Pair(HistoryEventName.TRANSFER_SF, HistoryEventType.TRANSFER)
            "VET_TRANSFER" -> Pair(HistoryEventName.TRANSFER_VET, HistoryEventType.TRANSFER)
            "FT_VET_Swap" -> Pair(HistoryEventName.SWAP_FT_TO_VET, HistoryEventType.SWAP)
            "VET_FT_Swap" -> Pair(HistoryEventName.SWAP_VET_TO_FT, HistoryEventType.SWAP)
            "Token_FTSwap" -> Pair(HistoryEventName.SWAP_FT_TO_FT, HistoryEventType.SWAP)
            else -> null // Other events will not be labeled
        }
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
            object : TypeReference<DynamicArray<Uint256>>() {}
        )
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
            object : TypeReference<Utf8String>() {}
        )
    )

private val ERC_TRANSFER_SINGLE_EVENT =
    Event(
        "TransferSingle",
        listOf<TypeReference<*>>(
            object : TypeReference<Address>(true) {},
            object : TypeReference<Address>(true) {},
            object : TypeReference<Address>(true) {},
            object : TypeReference<Uint256>() {},
            object : TypeReference<Uint256>() {}
        )
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
            object : TypeReference<Utf8String>() {}
        )
    )

private val TRANSFER_SIGNATURES =
    listOf(
        Signatures.Common.TRANSFER_EVENT,
        VIP210Contract.TRANSFER_BATCH_EVENT,
        VIP210Contract.TRANSFER_SINGLE_EVENT,
        ERC1155Contract.TRANSFER_BATCH_EVENT,
        ERC1155Contract.TRANSFER_SINGLE_EVENT
    )
