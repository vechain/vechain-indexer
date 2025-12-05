package org.vechain.indexer.performance.validator

import java.math.BigInteger
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.ValidatorArchive
import org.vechain.indexer.validator.ValidatorRepository
import org.vechain.indexer.validator.ValidatorService
import org.vechain.indexer.validator.domain.ValidatorDecoder.hasAbiData
import org.vechain.indexer.validator.logic.ValidatorAssembler

/**
 * Extended ValidatorService that profiles EVERY internal method call Tracks performance of:
 * - processBlock (main processing)
 * - save (MongoDB writes)
 * - loadExistingDocs (DB reads)
 * - applyEventChanges (event processing)
 * - applyChainUpdates (contract call processing)
 * - ValidatorAssembler.getLatestValidatorInfo (decode chain data)
 * - getTotalVETStaked (Thor API call)
 */
class ProfiledValidatorService(
    repository: ValidatorRepository,
    archiveService: ArchiveService<Validator, ValidatorArchive>,
    thorService: ThorService,
    statsStartThreshold: Long,
    stakerSC: String,
    private val profiler: DetailedProfiler,
) : ValidatorService(repository, archiveService, thorService, statsStartThreshold, stakerSC) {

    override fun processBlock(
        block: Block,
        matchedEvents: List<IndexedEvent>,
        callResponses: List<InspectionResult>,
    ): Pair<List<Validator>, List<Validator>> {
        return profiler.time("      ValidatorService.processBlock") {
            val threshold = profiler.time("        - getThreshold") { getThresholdInternal() }

            // Load docs once
            val existingDocs =
                profiler.time("        - loadExistingDocs") {
                    loadExistingDocsInternal(block, matchedEvents, threshold)
                }
            val working = existingDocs.toMutableMap()

            // Load ABIs if not cached
            profiler.time("        - loadAllValidatorAbiFunctions") {
                loadAllValidatorAbiFunctionsInternal(
                    listOf(
                        "getValidators",
                        "totalStake",
                        "vthoTotalSupply",
                        "getVetPriceUsd",
                        "getVthoPriceUsd",
                        "totalBurned",
                    )
                )
            }

            // Determine if old block
            val isOldBlock = block.number < threshold

            // Apply event changes from blockchain logs
            profiler.time("        - applyEventChanges") {
                applyEventChangesInternal(matchedEvents, working, callResponses, isOldBlock)
            }

            // For old blocks → only beneficiary changes matter or if responses have no ABI data
            if (isOldBlock || callResponses.none { it.hasAbiData() }) {
                return@time working.values.toList() to emptyList()
            }

            // If VTHO issued is zero, fetch total VET staked from chain to calculate rewards
            // properly
            var stakerVetBalance = BigInteger.ZERO
            if (ValidatorAssembler.totalVTHOIssuedBlock == BigInteger.ZERO) {
                stakerVetBalance =
                    profiler.time("        - getTotalVETStaked") { getTotalVETStaked(block.id) }
            }

            // Decode and calculate full validator updates
            val chainUpdates =
                profiler.time("        - ValidatorAssembler.getLatestValidatorInfo") {
                    getLatestValidatorInfoInternal(
                        callResponses,
                        existingDocs,
                        block.id,
                        block.number,
                        block.timestamp,
                        stakerVetBalance,
                    )
                }

            // Merge into working set
            profiler.time("        - applyChainUpdates") {
                applyChainUpdatesInternal(chainUpdates, working)
            }

            working.values.toList() to existingDocs.values.toList()
        }
    }

    override fun save(updates: List<Validator>, archive: List<Validator>) {
        profiler.time("      ValidatorService.save (MongoDB)") { super.save(updates, archive) }
    }

    // Private method accessors using reflection
    private fun getThresholdInternal(): Long {
        val method = ValidatorService::class.java.getDeclaredMethod("getThreshold")
        method.isAccessible = true
        return method.invoke(this) as Long
    }

    private fun loadExistingDocsInternal(
        block: Block,
        matchedEvents: List<IndexedEvent>,
        threshold: Long,
    ): Map<String, Validator> {
        val method =
            ValidatorService::class
                .java
                .getDeclaredMethod(
                    "loadExistingDocs",
                    Block::class.java,
                    List::class.java,
                    Long::class.java,
                )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(this, block, matchedEvents, threshold) as Map<String, Validator>
    }

    private fun loadAllValidatorAbiFunctionsInternal(functionNames: List<String>) {
        val method =
            ValidatorService::class
                .java
                .getDeclaredMethod("loadAllValidatorAbiFunctions", List::class.java)
        method.isAccessible = true
        method.invoke(this, functionNames)
    }

    private fun applyEventChangesInternal(
        events: List<IndexedEvent>,
        working: MutableMap<String, Validator>,
        responses: List<InspectionResult>,
        isOldBlock: Boolean,
    ) {
        val method =
            ValidatorService::class
                .java
                .getDeclaredMethod(
                    "applyEventChanges",
                    List::class.java,
                    MutableMap::class.java,
                    List::class.java,
                    Boolean::class.java,
                )
        method.isAccessible = true
        method.invoke(this, events, working, responses, isOldBlock)
    }

    private fun applyChainUpdatesInternal(
        chainUpdates: List<Validator>,
        working: MutableMap<String, Validator>,
    ) {
        val method =
            ValidatorService::class
                .java
                .getDeclaredMethod("applyChainUpdates", List::class.java, MutableMap::class.java)
        method.isAccessible = true
        method.invoke(this, chainUpdates, working)
    }

    private fun getLatestValidatorInfoInternal(
        responses: List<InspectionResult>,
        existingDocs: Map<String, Validator>,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        stakerVetBalance: BigInteger,
    ): List<Validator> {
        // Access the cached ABI field
        val abiField = ValidatorService::class.java.getDeclaredField("cachedGetValidatorsAbi")
        abiField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val validatorsAbi =
            abiField.get(this)
                as
                java.util.concurrent.ConcurrentHashMap<
                    String,
                    org.vechain.indexer.event.model.abi.AbiElement,
                >

        return ValidatorAssembler.getLatestValidatorInfo(
            responses = responses,
            validatorsAbi = validatorsAbi,
            existingDocs = existingDocs,
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            stakerVetBalance = stakerVetBalance,
        )
    }
}
