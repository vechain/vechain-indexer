package org.vechain.indexer.utils

import org.junit.jupiter.api.Test
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.model.TransferEventType
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.*

class BlockUtilsTest {

    @Test
    fun `getNftTransferEventsFromTopics should return all NFT transfer events`() {
        val block = BlockFixtures.BLOCK_NFT_MINT
        val nftTransferEvents = BlockUtils.getNftTransferEventsFromTopics(block, null)

        expect {
            that(nftTransferEvents.size).isEqualTo(1)
            that(nftTransferEvents[0].eventType).isEqualTo(TransferEventType.NFT)
            that(nftTransferEvents[0].tokenAddress)
                .isEqualTo("0x755a8cada7bbdc22b7413f40931389fdfe2b9089")
            that(nftTransferEvents[0].from).isEqualTo("0x0000000000000000000000000000000000000000")
            that(nftTransferEvents[0].to).isEqualTo("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")
            that(nftTransferEvents[0].tokenId).isEqualTo("0")
            that(nftTransferEvents[0].value).isEqualTo("1")
            that(nftTransferEvents[0].blockNumber).isEqualTo(block.number)
            that(nftTransferEvents[0].blockTimestamp).isEqualTo(block.timestamp)
            that(nftTransferEvents[0].txId).isEqualTo(block.transactions[0].id)
            that(nftTransferEvents[0].topics.size).isEqualTo(4)
        }
    }

    @Test
    fun `getNftTransferEventsFromTopics should return multiple NFT transfer events`() {
        val block = BlockFixtures.BLOCK_NFT_MINT_2
        val nftTransferEvents = BlockUtils.getNftTransferEventsFromTopics(block, null)

        expect {
            that(nftTransferEvents.size).isEqualTo(2)
            that(nftTransferEvents[0].eventType).isEqualTo(TransferEventType.NFT)
            that(nftTransferEvents[0].tokenAddress)
                .isEqualTo("0xed324c3628923d9816012cb5bc10c4d817e824a5")
            that(nftTransferEvents[0].from).isEqualTo("0x0000000000000000000000000000000000000000")
            that(nftTransferEvents[0].to).isEqualTo("0x435933c8064b4ae76be665428e0307ef2ccfbd68")
            that(nftTransferEvents[0].tokenId).isEqualTo("0")
            that(nftTransferEvents[0].value).isEqualTo("1")
            that(nftTransferEvents[0].blockNumber).isEqualTo(block.number)
            that(nftTransferEvents[0].blockTimestamp).isEqualTo(block.timestamp)
            that(nftTransferEvents[0].txId).isEqualTo(block.transactions[0].id)
            that(nftTransferEvents[0].topics.size).isEqualTo(4)
        }

        expect {
            that(nftTransferEvents[1].eventType).isEqualTo(TransferEventType.NFT)
            that(nftTransferEvents[1].tokenAddress)
                .isEqualTo("0xed324c3628923d9816012cb5bc10c4d817e824a5")
            that(nftTransferEvents[1].from).isEqualTo("0x0000000000000000000000000000000000000000")
            that(nftTransferEvents[1].to).isEqualTo("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")
            that(nftTransferEvents[1].tokenId).isEqualTo("1")
            that(nftTransferEvents[1].value).isEqualTo("1")
            that(nftTransferEvents[1].blockNumber).isEqualTo(block.number)
            that(nftTransferEvents[1].blockTimestamp).isEqualTo(block.timestamp)
            that(nftTransferEvents[1].txId).isEqualTo(block.transactions[1].id)
            that(nftTransferEvents[1].topics.size).isEqualTo(4)
        }
    }

    @Test
    fun `getNftTransferEventsFromTopics filter by correct address should return NFT transfer events`() {
        val block = BlockFixtures.BLOCK_NFT_MINT
        val nftTransferEvents =
            BlockUtils.getNftTransferEventsFromTopics(
                block,
                "0x755a8cada7bbdc22b7413f40931389fdfe2b9089"
            )

        expectThat(nftTransferEvents.size).isEqualTo(1)

        expect {
            that(nftTransferEvents[0].eventType).isEqualTo(TransferEventType.NFT)
            that(nftTransferEvents[0].tokenAddress)
                .isEqualTo("0x755a8cada7bbdc22b7413f40931389fdfe2b9089")
            that(nftTransferEvents[0].from).isEqualTo("0x0000000000000000000000000000000000000000")
            that(nftTransferEvents[0].to).isEqualTo("0xf077b491b355e64048ce21e3a6fc4751eeea77fa")
            that(nftTransferEvents[0].tokenId).isEqualTo("0")
            that(nftTransferEvents[0].value).isEqualTo("1")
            that(nftTransferEvents[0].blockNumber).isEqualTo(block.number)
            that(nftTransferEvents[0].blockTimestamp).isEqualTo(block.timestamp)
            that(nftTransferEvents[0].txId).isEqualTo(block.transactions[0].id)
            that(nftTransferEvents[0].topics.size).isEqualTo(4)
        }

        expectThat(nftTransferEvents).map(IndexedTransferEvent::tokenAddress).all {
            isEqualTo("0x755a8cada7bbdc22b7413f40931389fdfe2b9089")
        }
    }

    @Test
    fun `getNftTransferEventsFromTopics filter by incorrect address should return empty list`() {
        val block = BlockFixtures.BLOCK_NFT_MINT
        val nftTransferEvents =
            BlockUtils.getNftTransferEventsFromTopics(
                block,
                "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"
            )

        expectThat(nftTransferEvents).isEmpty()
    }

    @Test
    fun `getOutputs should return all outputs`() {
        val block = BlockFixtures.BLOCK_NFT_MINT
        val outputs = BlockUtils.getOutputs(block)

        expectThat(outputs.size).isEqualTo(1)
        expect {
            that(outputs[0].first.events.size).isEqualTo(2)
            that(outputs[0].second.id).isEqualTo(block.transactions[0].id)
        }
    }

    @Test
    fun `getOutputs should return all outputs from multiple transactions in the order the transactions appear in the block`() {
        val block = BlockFixtures.BLOCK_NFT_MINT_2
        val outputs = BlockUtils.getOutputs(block)

        expectThat(outputs.size).isEqualTo(2)

        expect {
            that(outputs[0].first.events.size).isEqualTo(2)
            that(outputs[0].first.events[0].address)
                .isEqualTo("0xed324c3628923d9816012cb5bc10c4d817e824a5")
            that(outputs[0].first.events[0].topics.size).isEqualTo(2)
            that(outputs[0].first.events[0].topics[0])
                .isEqualTo("0x26e57b75d51bb4535260f2bbcf7f5ca9f9612af5e3b52e82d20852b529f03290")
            that(outputs[0].first.events[0].topics[1])
                .isEqualTo("0x000000000000000000000000435933c8064b4ae76be665428e0307ef2ccfbd68")
            that(outputs[0].first.events[0].data)
                .isEqualTo(
                    "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001"
                )
            that(outputs[0].first.events[1].address)
                .isEqualTo("0xed324c3628923d9816012cb5bc10c4d817e824a5")
            that(outputs[0].first.events[1].topics.size).isEqualTo(4)
            that(outputs[0].first.events[1].topics[0])
                .isEqualTo("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")
            that(outputs[0].first.events[1].topics[1])
                .isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000000")
            that(outputs[0].first.events[1].topics[2])
                .isEqualTo("0x000000000000000000000000435933c8064b4ae76be665428e0307ef2ccfbd68")
            that(outputs[0].first.events[1].topics[3])
                .isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000000")
            that(outputs[0].second.id).isEqualTo(block.transactions[0].id)
        }

        expect {
            that(outputs[1].first.events.size).isEqualTo(2)
            that(outputs[1].first.events[0].address)
                .isEqualTo("0xed324c3628923d9816012cb5bc10c4d817e824a5")
            that(outputs[1].first.events[0].topics.size).isEqualTo(2)
            that(outputs[1].first.events[0].topics[0])
                .isEqualTo("0x26e57b75d51bb4535260f2bbcf7f5ca9f9612af5e3b52e82d20852b529f03290")
            that(outputs[1].first.events[0].topics[1])
                .isEqualTo("0x000000000000000000000000f077b491b355e64048ce21e3a6fc4751eeea77fa")
            that(outputs[1].first.events[0].data)
                .isEqualTo(
                    "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001"
                )
            that(outputs[1].first.events[1].address)
                .isEqualTo("0xed324c3628923d9816012cb5bc10c4d817e824a5")
            that(outputs[1].first.events[1].topics.size).isEqualTo(4)
            that(outputs[1].first.events[1].topics[0])
                .isEqualTo("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")
            that(outputs[1].first.events[1].topics[1])
                .isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000000")
            that(outputs[1].first.events[1].topics[2])
                .isEqualTo("0x000000000000000000000000f077b491b355e64048ce21e3a6fc4751eeea77fa")
            that(outputs[1].first.events[1].topics[3])
                .isEqualTo("0x0000000000000000000000000000000000000000000000000000000000000001")
            that(outputs[1].second.id).isEqualTo(block.transactions[1].id)
        }
    }

    @Test
    fun `getOutputs should filter reverted`() {
        val block = BlockFixtures.BLOCK_NFT_MINT_REVERTED

        val outputs = BlockUtils.getOutputs(block)

        expectThat(outputs.size).isEqualTo(1)

        expect {
            that(outputs[0].first.events.size).isEqualTo(2)
            that(outputs[0].second.id).isEqualTo(block.transactions[1].id)
        }
    }
}
