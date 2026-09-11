package org.vechain.indexer.history

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.domain.Sort.Direction.ASC
import org.springframework.data.domain.Sort.Direction.DESC
import org.vechain.indexer.b3tr.action.SustainabilityProofV2
import org.vechain.indexer.history.HistoryEventName.B3TR_ACTION
import org.vechain.indexer.history.HistoryEventName.NFT_SALE
import org.vechain.indexer.history.HistoryEventName.STARGATE_STAKE
import org.vechain.indexer.history.HistoryEventName.TRANSFER_NFT
import org.vechain.indexer.history.HistoryEventName.TRANSFER_VET
import org.vechain.indexer.history.HistoryEventName.UNKNOWN_TX
import org.vechain.indexer.history.HistoryFixtures.address
import org.vechain.indexer.history.HistoryFixtures.hash
import org.vechain.indexer.history.HistoryReadRepository.SearchField
import org.vechain.indexer.nft.NftBlacklistState
import org.vechain.indexer.nft.NftBlacklistWriteRepository
import org.vechain.indexer.postgres.PostgresTestDatabase

/** One test per API query on a seeded timeline; [seed] says who did what and what is flagged. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HistoryReadRepositoryTest {

    private val database = PostgresTestDatabase()
    private lateinit var repository: HistoryReadRepository

    private val alice = address(1)
    private val bob = address(2)
    private val carol = address(3)
    private val art = "0x" + "1".repeat(40)
    private val spam = "0x" + "3".repeat(40)
    private val stargate = "0x" + "5".repeat(40)
    private val appX = hash(0x77)
    private val appY = hash(0x78)
    private val events = mutableMapOf<String, IndexedHistoryEvent>()

    @BeforeAll
    fun start() {
        database.start()
        repository = HistoryReadRepository(database.jdbc)
        seed()
    }

    @AfterAll fun stop() = database.close()

    private fun seed() {
        fun e(
            key: String,
            block: Long,
            name: HistoryEventName,
            vararg fields: Pair<String, String?>,
        ) {
            val f = fields.toMap()
            events[key] =
                IndexedHistoryEvent(
                    id = key.padStart(40, '0'),
                    blockId = hash(block.toInt()),
                    blockNumber = block,
                    blockTimestamp = block * 10,
                    txId = hash(1000 + block.toInt()),
                    eventName = name,
                    origin = f["origin"],
                    gasPayer = f["gasPayer"],
                    to = f["to"],
                    from = f["from"],
                    owner = f["owner"],
                    contractAddress = f["contract"],
                    tokenId = f["tokenId"],
                    appId = f["appId"],
                    value = f["value"],
                    proof = if (name == B3TR_ACTION) SustainabilityProofV2(2, "walked") else null,
                )
        }
        e("e1", 10, TRANSFER_VET, "origin" to alice, "to" to bob, "value" to "5")
        e(
            "e2",
            11,
            TRANSFER_NFT,
            "origin" to alice,
            "from" to alice,
            "to" to bob,
            "contract" to art,
            "tokenId" to "7",
        )
        e(
            "e3",
            12,
            TRANSFER_NFT,
            "origin" to alice,
            "from" to alice,
            "to" to bob,
            "contract" to spam,
            "tokenId" to "1",
        )
        e(
            "e4",
            13,
            NFT_SALE,
            "origin" to carol,
            "from" to bob,
            "to" to carol,
            "contract" to art,
            "tokenId" to "7",
            "value" to "9",
        )
        e(
            "e5",
            14,
            B3TR_ACTION,
            "origin" to address(9),
            "from" to address(4),
            "to" to alice,
            "appId" to appX,
            "value" to "5000000000000000000",
        )
        e(
            "e6",
            15,
            B3TR_ACTION,
            "origin" to address(9),
            "from" to address(4),
            "to" to bob,
            "appId" to appX,
            "value" to "1",
        )
        e(
            "e7",
            16,
            B3TR_ACTION,
            "origin" to address(9),
            "from" to address(4),
            "to" to alice,
            "appId" to appY,
            "value" to "2",
        )
        e(
            "e8",
            17,
            STARGATE_STAKE,
            "origin" to alice,
            "owner" to alice,
            "contract" to stargate,
            "tokenId" to "42",
        )
        e(
            "e9",
            18,
            TRANSFER_NFT,
            "origin" to alice,
            "from" to alice,
            "to" to bob,
            "contract" to stargate,
            "tokenId" to "42",
        )
        e(
            "e10",
            19,
            TRANSFER_NFT,
            "origin" to carol,
            "from" to carol,
            "to" to alice,
            "contract" to art,
            "tokenId" to "42",
        )
        e("e11", 20, UNKNOWN_TX, "origin" to alice, "gasPayer" to bob)
        e(
            "e12",
            21,
            TRANSFER_VET,
            "origin" to bob,
            "gasPayer" to alice,
            "to" to carol,
            "value" to "1",
        )
        HistoryWriteRepository(database.jdbc).save(events.values.toList())
        NftBlacklistWriteRepository(database.jdbc)
            .save(listOf(NftBlacklistState(spam, true, hash(30), 30, 300)))
    }

    private fun keys(rows: List<IndexedHistoryEvent>) = rows.map { row ->
        events.entries.single { it.value.id == row.id }.key
    }

    private val stargateNames =
        HistoryEventName.entries.filter { it.name.startsWith("STARGATE_") }.map { it.name }
    private val nftNames = listOf("TRANSFER_NFT", "NFT_SALE", "VEVOTE_VOTE_CAST")

    @Test
    fun `an account's history spans every address field, newest first, minus flagged transfers`() {
        val page = repository.findByAccount(alice, null, null, null, null, 0, 20, DESC)

        assertEquals(listOf("e12", "e11", "e10", "e9", "e8", "e7", "e5", "e2", "e1"), keys(page))
        assertEquals(events["e5"], page[6], "rows assemble with their structured fields")
    }

    @Test
    fun `account history pages with offset, limit and direction`() {
        assertEquals(
            listOf("e2", "e5"),
            keys(repository.findByAccount(alice, null, null, null, null, 1, 2, ASC)),
        )
    }

    @Test
    fun `account history filters by event name, window and collection`() {
        assertEquals(
            listOf("e10", "e9", "e2"),
            keys(
                repository.findByAccount(
                    alice,
                    listOf("TRANSFER_NFT"),
                    null,
                    null,
                    null,
                    0,
                    20,
                    DESC,
                )
            ),
        )
        assertEquals(
            listOf("e8", "e7", "e5"),
            keys(repository.findByAccount(alice, null, null, 140, 170, 0, 20, DESC)),
        )
        assertEquals(
            listOf("e12", "e11"),
            keys(repository.findByAccount(alice, null, null, 200, null, 0, 20, DESC)),
        )
        assertEquals(
            listOf("e10", "e2"),
            keys(repository.findByAccount(alice, null, art, null, null, 0, 20, DESC)),
        )
    }

    @Test
    fun `event names the enum does not know match nothing`() {
        assertTrue(
            repository
                .findByAccount(
                    alice,
                    listOf("STARGATE_DELEGATION_REMOVED_LEGACY"),
                    null,
                    null,
                    null,
                    0,
                    20,
                    DESC,
                )
                .isEmpty()
        )
        assertEquals(
            listOf("e12", "e1"),
            keys(
                repository.findByAccount(
                    alice,
                    listOf("TRANSFER_VET", "NOT_A_NAME"),
                    null,
                    null,
                    null,
                    0,
                    20,
                    DESC,
                )
            ),
        )
    }

    @Test
    fun `searchBy unions the named fields once per event and pages the union`() {
        val fields = listOf(SearchField.TO, SearchField.ORIGIN)
        assertEquals(
            listOf("e11", "e10", "e9", "e8", "e7", "e5", "e2", "e1"),
            keys(repository.findBySearchFields(alice, fields, null, null, null, null, 0, 20, DESC)),
        )
        assertEquals(
            listOf("e8", "e7"),
            keys(repository.findBySearchFields(alice, fields, null, null, null, null, 3, 2, DESC)),
        )
        assertEquals(
            listOf("e12"),
            keys(
                repository.findBySearchFields(
                    alice,
                    listOf(SearchField.GAS_PAYER),
                    null,
                    null,
                    null,
                    null,
                    0,
                    20,
                    DESC,
                )
            ),
        )
        assertEquals(
            listOf("e2"),
            keys(
                repository.findBySearchFields(
                    alice,
                    fields,
                    listOf("TRANSFER_NFT"),
                    art,
                    null,
                    150,
                    0,
                    20,
                    DESC,
                )
            ),
        )
    }

    @Test
    fun `a token's history is its transfers and sales unless the collection is flagged`() {
        assertEquals(
            listOf("e4", "e2"),
            keys(
                repository.findTokenHistory(
                    art,
                    "7",
                    listOf("TRANSFER_NFT", "NFT_SALE"),
                    null,
                    null,
                    0,
                    20,
                    DESC,
                )
            ),
        )
        assertEquals(
            listOf("e4"),
            keys(
                repository.findTokenHistory(art, "7", listOf("NFT_SALE"), null, null, 0, 20, DESC)
            ),
        )
        assertEquals(
            listOf("e2"),
            keys(
                repository.findTokenHistory(
                    art,
                    "7",
                    listOf("TRANSFER_NFT", "NFT_SALE"),
                    null,
                    120,
                    0,
                    20,
                    DESC,
                )
            ),
        )
        assertTrue(
            repository
                .findTokenHistory(spam, "1", listOf("TRANSFER_NFT"), null, null, 0, 20, DESC)
                .isEmpty()
        )
    }

    @Test
    fun `a stargate token's history is protocol events plus NFT events on the stargate contract`() {
        assertEquals(
            listOf("e9", "e8"),
            keys(
                repository.findStargateTokenHistory(
                    "42",
                    null,
                    stargateNames,
                    nftNames,
                    stargate,
                    null,
                    null,
                    0,
                    20,
                    DESC,
                )
            ),
        )
        assertEquals(
            listOf("e8"),
            keys(
                repository.findStargateTokenHistory(
                    "42",
                    listOf("STARGATE_STAKE"),
                    stargateNames,
                    nftNames,
                    stargate,
                    null,
                    null,
                    0,
                    20,
                    DESC,
                )
            ),
        )
    }

    @Test
    fun `actions filter by receiver and app, with exclusive single-sided windows`() {
        assertEquals(
            listOf("e7", "e5"),
            keys(repository.findActions(alice, null, null, null, 0, 20, DESC)),
        )
        assertEquals(
            listOf("e5"),
            keys(repository.findActions(alice, appX, null, null, 0, 20, DESC)),
        )
        assertEquals(
            listOf("e6", "e5"),
            keys(repository.findActions(null, appX, null, null, 0, 20, DESC)),
        )
        assertEquals(
            listOf("e7"),
            keys(repository.findActions(alice, null, 140, null, 0, 20, DESC)),
        )
        assertEquals(
            listOf("e5"),
            keys(repository.findActions(alice, null, null, 160, 0, 20, DESC)),
        )
        assertEquals(
            listOf("e5", "e7"),
            keys(repository.findActions(alice, null, 140, 160, 0, 20, ASC)),
        )
    }
}
