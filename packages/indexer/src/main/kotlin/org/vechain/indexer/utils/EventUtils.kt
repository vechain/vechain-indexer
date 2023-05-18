package org.vechain.indexer.utils

import org.slf4j.LoggerFactory
import org.vechain.indexer.model.TransferEventType
import org.vechain.indexer.model.TxEvent
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.Event
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.tx.Contract
import org.web3j.utils.Numeric
import java.math.BigInteger

object EventUtils {

    private val logger = LoggerFactory.getLogger(EventUtils::class.java)

    data class TransferParameters(
        val from: String,
        val to: String,
        val tokenId: BigInteger?,
        val amount: BigInteger,
        val eventType: TransferEventType
    )

    private val TRANSFER_BATCH_EVENT = Event(
        "TransferBatch",
        listOf(
            object : TypeReference<Address>(true) {},
            object : TypeReference<Address>(true) {},
            object : TypeReference<Address>(true) {},
            object : TypeReference<DynamicArray<Uint256>>() {},
            object : TypeReference<DynamicArray<Uint256>>() {})
    )

    private val TRANSFER_SINGLE_EVENT = Event(
        "TransferSingle",
        listOf<TypeReference<*>>(
            object : TypeReference<Address>(true) {},
            object : TypeReference<Address>(true) {},
            object : TypeReference<Address>(true) {},
            object : TypeReference<Uint256>() {},
            object : TypeReference<Uint256>() {})
    )

    /**
     * Transfer Event for both NFT and Fungible
     */
    private val TRANSFER_EVENT_SIGNATURE = ContractUtils.getEventSignature("Transfer(address,address,uint256)")

    /**
     * Transfer Event for semi-fungible
     */
    private val TRANSFER_SINGLE_EVENT_SIGNATURE =
        ContractUtils.getEventSignature("TransferSingle(address,address,address,uint256,uint256)")

    /**
     * Transfer Event for semi-fungible
     */
    private val TRANSFER_BATCH_EVENT_SIGNATURE =
        ContractUtils.getEventSignature("TransferBatch(address,address,address,uint256[],uint256[])")

    fun isNftTransferEvent(event: TxEvent): Boolean {
        return event.topics.size == 4 && HexUtil.removePrefix(event.topics[0]) == TRANSFER_EVENT_SIGNATURE
    }

    fun isFungibleTransferEvent(event: TxEvent): Boolean {
        return event.topics.size == 3 && HexUtil.removePrefix(event.topics[0]) == TRANSFER_EVENT_SIGNATURE
    }

    fun isTransferSingleEvent(event: TxEvent): Boolean {
        return event.topics.isNotEmpty() && HexUtil.removePrefix(event.topics[0]) == TRANSFER_SINGLE_EVENT_SIGNATURE
    }

    fun isTransferBatchEvent(event: TxEvent): Boolean {
        return event.topics.isNotEmpty() && HexUtil.removePrefix(event.topics[0]) == TRANSFER_BATCH_EVENT_SIGNATURE
    }

    fun isTransferEvent(event: TxEvent): Boolean {
        return isNftTransferEvent(event)
                || isFungibleTransferEvent(event)
                || isTransferSingleEvent(event)
                || isTransferBatchEvent(event)
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
                from = AddressUtil.decode(event.topics[1]),
                to = AddressUtil.decode(event.topics[2]),
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
                from = AddressUtil.decode(event.topics[1]),
                to = AddressUtil.decode(event.topics[2]),
                tokenId = tokenId,
                amount = BigInteger.ONE,
                eventType = TransferEventType.NFT
            )
        )
    }


    fun getSingleTransferParameters(event: TxEvent): List<TransferParameters> {
        try {
            val eventParameters = Contract.staticExtractEventParameters(TRANSFER_SINGLE_EVENT, event.toLog())

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
            logger.warn("Error parsing single transfer event", e)
            return emptyList()
        }
    }


    fun getBatchTransferParameters(event: TxEvent): List<TransferParameters> {

        try {
            val eventParameters = Contract.staticExtractEventParameters(TRANSFER_BATCH_EVENT, event.toLog())

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
            logger.warn("Error parsing batch transfer event", e)
            return emptyList()
        }
    }

}