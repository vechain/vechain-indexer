package org.vechain.indexer.utils

import java.math.BigInteger
import org.slf4j.LoggerFactory
import org.vechain.indexer.contracts.specifications.ERC1155Contract
import org.vechain.indexer.contracts.specifications.Signatures
import org.vechain.indexer.contracts.specifications.VIP210Contract
import org.vechain.indexer.model.TransferEventType
import org.vechain.indexer.thor.model.TxEvent
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.Event
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256
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

            val eventParameters = Contract.staticExtractEventParameters(ev, event.toLog())

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

            val eventParameters = Contract.staticExtractEventParameters(ev, event.toLog())

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
