package org.vechain.indexer.validator

import kotlin.collections.set
import org.bson.types.Decimal128
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("validator", "validator-stats")
@Service
open class ValidatorService(
    private val repository: ValidatorRepository,
    private val archiveService: ArchiveService<Validator, ValidatorArchive>,
    private val thorService: ThorService,
) {
    private val cachedGetValidatorsAbi: MutableMap<String, AbiElement> = mutableMapOf()
    private val cachedValidators = LRUCache<String, List<Validator>>(100)

    open fun processBlock(
        block: Block,
        matchedEvents: List<IndexedEvent>,
        callResponses: List<InspectionResult>,
    ): Pair<List<Validator>, List<Validator>> {
        val threshold = getThreshold()

        // Load docs once
        val existingDocs = loadExistingDocs(block, matchedEvents, threshold)
        val working = existingDocs.toMutableMap()

        // Apply beneficiary changes directly into the working map
        applyBeneficiaryChanges(matchedEvents, working)

        // Fetch ABIs for decoding
        loadAllValidatorAbiFunctions(
            listOf(
                "getValidators",
                "totalStake",
                "vthoTotalSupply",
                "getVetPriceUsd",
                "getVthoPriceUsd",
            )
        )

        // Decode into validators + delete list
        val chainUpdates =
            ValidatorUtils.getLatestValidatorInfo(
                responses = callResponses,
                validatorsAbi = cachedGetValidatorsAbi,
                existingDocs = existingDocs,
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
            )

        cachedValidators[block.id] = chainUpdates
        val parentValidators = cachedValidators[block.parentID]

        // TODO: If parentValidators is null, fetch from chain

        // If no changes detected, skip
        val updates = chainUpdates.updated(parentValidators)
        for (u in updates) {
            // TODO: validator state updated, save to DB
            println(u.id)
        }

        applyChainUpdates(chainUpdates, working)

        return working.values.toList() to existingDocs.values.toList()
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updates: List<Validator>, archive: List<Validator>) {
        // Persist once
        repository.saveAll(updates)

        // Archive old state
        if (archive.isNotEmpty()) {
            archiveService.saveAll(archive)
        }
    }

    private fun applyChainUpdates(
        chainUpdates: List<Validator>,
        working: MutableMap<String, Validator>,
    ) {
        chainUpdates.forEach { v ->
            val existing = working[v.id]
            working[v.id] =
                if (existing != null) {
                    v.copy(beneficiary = existing.beneficiary) // keep latest beneficiary change
                } else {
                    v
                }
        }
    }

    private fun loadExistingDocs(
        block: Block,
        matchedEvents: List<IndexedEvent>,
        threshold: Long,
    ): Map<String, Validator> =
        if (block.number < threshold) {
            // For old blocks → only fetch docs for validators in events
            val ids = matchedEvents.mapNotNull { it.params.getAsString("validator") }.distinct()

            if (ids.isEmpty()) {
                emptyMap()
            } else {
                repository.findAllById(ids).associateBy { it.id }
            }
        } else {
            // For recent blocks → load all validators once
            repository.findByStatusNot(Status.EXITED).associateBy { it.id }
        }

    private fun applyBeneficiaryChanges(
        events: List<IndexedEvent>,
        working: MutableMap<String, Validator>,
    ) {
        events.forEach { ev ->
            val validatorId = ev.params.getAsString("validator")!!
            val beneficiary = ev.params.getAsString("beneficiary")!!

            val base =
                working[validatorId]
                    ?: Validator(
                        id = validatorId,
                        blockId = ev.blockId,
                        blockNumber = ev.blockNumber,
                        blockTimestamp = ev.blockTimestamp,
                        beneficiary = beneficiary,
                        totalVTHOSupply = Decimal128(0),
                        version = 0,
                    )

            working[validatorId] =
                base.copy(
                    blockId = ev.blockId,
                    blockNumber = ev.blockNumber,
                    blockTimestamp = ev.blockTimestamp,
                    beneficiary = beneficiary,
                )
        }
    }

    private fun getThreshold(): Long {
        val bestBlock = thorService.getBestBlock()
        return bestBlock.number - 25
    }

    private fun loadAllValidatorAbiFunctions(functionNames: List<String>) {
        if (cachedGetValidatorsAbi.isNotEmpty()) return // already loaded

        val abis = AbiLoader.load(basePath = "abis/stargate", names = functionNames)

        abis.forEach { abi -> cachedGetValidatorsAbi[abi.name!!] = abi }
    }
}

class LRUCache<K, V>(private val maxSize: Int) : LinkedHashMap<K, V>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
        return size > maxSize
    }
}

// TODO: this always returns the full list even when. Some `equals` function is not working as
// expected
fun List<Validator>.updated(other: List<Validator>?): List<Validator> {
    if (other == null) return this

    val updates = mutableListOf<Validator>()
    val mapA = this.associateBy { it.id }
    val mapB = other.associateBy { it.id }

    mapA.forEach { (id, vA) ->
        val vB =
            mapB[id]
                ?: run {
                    updates.add(vA)
                    return@forEach
                }

        if (!vA.equals(vB)) {
            vA.printChanges(vB)
            updates.add(vA)
        }
    }

    mapB.forEach { (id, vB) ->
        if (!mapA.containsKey(id)) {
            // vB was removed in vA
            val exited = vB.copy(status = Status.EXITED)
            updates.add(exited)
        }
    }

    return updates
}
