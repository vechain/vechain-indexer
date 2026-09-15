package org.vechain.indexer.accounts

import java.math.BigInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.config.ForkConfig
import org.vechain.indexer.config.NetworkDetectionService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.HexUtils.normalise
import org.vechain.indexer.thor.HexUtils.toBigInteger
import org.vechain.indexer.thor.VTHO_CONTRACT_ADDRESS
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.Transaction
import org.vechain.indexer.utils.NumberUtils.hexToBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

/** Rolls each block into the overview of every address it touches, VTHO earnings included. */
@Profile("accounts")
@Service
open class AccountOverviewService(
    private val repository: AccountsWriteRepository,
    private val forkConfig: ForkConfig,
    private val networkDetectionService: NetworkDetectionService,
    private val thorClient: ThorClient,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /** The overviews a block changed and the VET balances that moved with them. */
    data class Update(val overviews: List<AccountOverview>, val balances: List<VetBalance>)

    private val hayabusaBlock: Long by lazy {
        forkConfig.getHayabusaBlock(networkDetectionService.detectBlocking().network)
    }

    open suspend fun processBlock(block: Block, events: List<IndexedEvent>): Update {
        val vetTransfers = events.filter { it.eventType == VET_TRANSFER }
        val addresses =
            buildSet<String> {
                block.transactions.forEach {
                    add(normalise(it.origin))
                    add(normalise(it.gasPayer))
                }
                vetTransfers.forEach {
                    add(sender(it))
                    add(recipient(it))
                }
                if (block.number > 0L) add(normalise(block.beneficiary))
            }
        val stored = repository.findCurrentOverviews(addresses).associateBy { it.address }
        val touched = linkedMapOf<String, AccountOverview>()
        val resolve = { address: String -> forMutation(address, block, stored, touched) }

        block.transactions.forEach { transactionRules(it, resolve) }
        if (vetTransfers.isNotEmpty()) {
            // Passive VTHO settles on the balance before this block's transfers move it.
            passiveGenerationRule(block, vetTransfers, resolve)
            vetTransfers.forEach { transferRule(it, resolve) }
        }
        blockRewardsRule(block, events, stored, resolve)

        val balances =
            touched.values
                .filter { it.vetBalance != (stored[it.address]?.vetBalance ?: BigInteger.ZERO) }
                .map {
                    VetBalance(it.address, block.id, block.number, block.timestamp, it.vetBalance)
                }
        return Update(touched.values.toList(), balances)
    }

    open fun isHayabusaBlock(blockNumber: Long): Boolean = blockNumber == hayabusaBlock

    /** Closes passive generation for every holder at the fork, ahead of the block's own rules. */
    open fun settleHayabusa(block: Block) {
        logger.info("Starting Hayabusa VTHO settlement at block {} {}", block.number, block.id)
        val settled = repository.settlePassiveVtho(block.id, block.number, block.timestamp)
        logger.info("Completed Hayabusa VTHO settlement. Total accounts settled: {}", settled)
    }

    private fun transactionRules(tx: Transaction, resolve: (String) -> AccountOverview) {
        val origin = resolve(normalise(tx.origin))
        origin.transactionsSent += 1
        origin.clausesSent += tx.clauses.size.toLong()
        origin.gasUsed += BigInteger.valueOf(tx.gasUsed)

        val paid = toBigInteger(tx.paid)
        val payer = resolve(normalise(tx.gasPayer))
        payer.vthoBurned += paid
        if (origin.address != payer.address) payer.vthoDelegated += paid
    }

    private fun transferRule(event: IndexedEvent, resolve: (String) -> AccountOverview) {
        val value = event.params.getAsBigInteger("amount") ?: BigInteger.ZERO
        val from = resolve(sender(event))
        from.vetSent += value
        from.vetBalance -= value
        val to = resolve(recipient(event))
        to.vetReceived += value
        to.vetBalance += value
    }

    /**
     * 0.000432 VTHO per VET per day, credited to each party of a transfer since its last credit.
     */
    private fun passiveGenerationRule(
        block: Block,
        vetTransfers: List<IndexedEvent>,
        resolve: (String) -> AccountOverview,
    ) {
        if (block.number >= hayabusaBlock) return
        vetTransfers
            .flatMap { listOf(sender(it), recipient(it)) }
            .toSet()
            .forEach { address ->
                val account = resolve(address)
                val last = account.lastVthoSettlement
                if (
                    last != null && account.vetBalance > BigInteger.ZERO && last < block.timestamp
                ) {
                    account.vthoPassiveGeneration +=
                        passiveVtho(account.vetBalance, block.timestamp - last)
                }
                account.lastVthoSettlement = block.timestamp
            }
    }

    /** The reward is VTHO growth beyond transfers in, gas paid and pre-Hayabusa passive VTHO. */
    private suspend fun blockRewardsRule(
        block: Block,
        events: List<IndexedEvent>,
        stored: Map<String, AccountOverview>,
        resolve: (String) -> AccountOverview,
    ) {
        if (block.number == 0L) return
        val beneficiary = normalise(block.beneficiary)
        val (before, after) =
            coroutineScope {
                val parent = async {
                    thorClient.getAccountState(block.beneficiary, BlockRevision.Id(block.parentID))
                }
                val current = async {
                    thorClient.getAccountState(block.beneficiary, BlockRevision.Id(block.id))
                }
                parent.await() to current.await()
            }
        val passive =
            passiveVthoForBlock(before.balance.hexToBigInteger(), block.number, stored[beneficiary])
        val expected = before.energy.hexToBigInteger() + passive
        val used =
            block.transactions
                .filter { normalise(it.gasPayer) == beneficiary }
                .sumOf { toBigInteger(it.paid) }
        val reward =
            after.energy.hexToBigInteger() - vthoTransferDelta(beneficiary, events) + used -
                expected
        if (reward <= BigInteger.ZERO) return
        resolve(beneficiary).vthoBlockRewards += reward
    }

    /**
     * The block's ten seconds of passive VTHO on [vetBalance], for an account already generating.
     */
    internal fun passiveVthoForBlock(
        vetBalance: BigInteger,
        blockNumber: Long,
        account: AccountOverview?,
    ): BigInteger =
        if (blockNumber < hayabusaBlock && account?.lastVthoSettlement != null)
            passiveVtho(vetBalance, BLOCK_SECONDS)
        else BigInteger.ZERO

    internal fun passiveVtho(vetBalance: BigInteger, seconds: Long): BigInteger =
        vetBalance
            .multiply(BigInteger.valueOf(seconds))
            .multiply(BigInteger.valueOf(5))
            .divide(BigInteger.valueOf(1_000_000_000))

    /** VTHO in minus VTHO out for [address] over the block's VTHO transfers. */
    private fun vthoTransferDelta(address: String, events: List<IndexedEvent>): BigInteger =
        events
            .filter {
                it.eventType == "Transfer" && it.address.equals(VTHO_CONTRACT_ADDRESS, true)
            }
            .fold(BigInteger.ZERO) { delta, event ->
                val value = event.params.getAsBigInteger("value") ?: BigInteger.ZERO
                delta + (if (recipient(event) == address) value else BigInteger.ZERO) -
                    (if (sender(event) == address) value else BigInteger.ZERO)
            }

    private fun forMutation(
        address: String,
        block: Block,
        stored: Map<String, AccountOverview>,
        touched: MutableMap<String, AccountOverview>,
    ): AccountOverview =
        touched.getOrPut(address) {
            stored[address]?.copy(
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                lastSeen = block.timestamp,
            )
                ?: AccountOverview(
                    address = address,
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    firstSeen = block.timestamp,
                    lastSeen = block.timestamp,
                )
        }

    private fun sender(event: IndexedEvent): String =
        normalise(
            event.params.getAsString("from")
                ?: error("Invalid ${event.eventType} event: missing 'from' param (${event.id})")
        )

    private fun recipient(event: IndexedEvent): String =
        normalise(
            event.params.getAsString("to")
                ?: error("Invalid ${event.eventType} event: missing 'to' param (${event.id})")
        )

    companion object {
        const val VET_TRANSFER = "VET_TRANSFER"
        private const val BLOCK_SECONDS = 10L
    }
}
