package org.vechain.indexer.performance.validatorBlock

import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.ValidatorBlock
import org.vechain.indexer.validator.ValidatorBlockRepository
import org.vechain.indexer.validator.ValidatorBlockService
import org.vechain.indexer.validator.domain.ValidatorDecoder.decodeResponseInfo
import org.vechain.indexer.validator.models.DecodedValidatorInfo

/**
 * Extended ValidatorBlockService that profiles EVERY internal method call Tracks performance of:
 * - processBlock (main processing)
 * - save (MongoDB writes)
 * - loadAllValidatorAbiFunctions (ABI loading)
 * - decodeResponseInfo (response decoding)
 * - getValidationInfo (validation info calculation)
 * - getValidatorsWithMissedSlots (missed slots calculation)
 */
class ProfiledValidatorBlockService(
    repository: ValidatorBlockRepository,
    thorService: ThorService,
    private val profiler: DetailedProfiler,
) : ValidatorBlockService(repository, thorService) {

    override fun processBlock(
        block: Block,
        callResponses: List<InspectionResult>,
    ): List<ValidatorBlock> {
        return profiler.time("      ValidatorBlockService.processBlock") {
            // Fetch ABIs for decoding
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

            val decodedInfo =
                profiler.time("        - decodeResponseInfo") {
                    decodeResponseInfoInternal(callResponses)
                }

            val validationInfo =
                profiler.time("        - getValidationInfo") {
                    getValidationInfo(block, decodedInfo)
                }

            val missedSlots =
                profiler.time("        - getValidatorsWithMissedSlots") {
                    getValidatorsWithMissedSlots(decodedInfo, block)
                }

            listOfNotNull(validationInfo) + missedSlots
        }
    }

    override fun save(records: List<ValidatorBlock>) {
        profiler.time("      ValidatorBlockService.save (MongoDB)") { super.save(records) }
    }

    // Private method accessors using reflection
    private fun loadAllValidatorAbiFunctionsInternal(functionNames: List<String>) {
        val method =
            ValidatorBlockService::class
                .java
                .getDeclaredMethod("loadAllValidatorAbiFunctions", List::class.java)
        method.isAccessible = true
        method.invoke(this, functionNames)
    }

    private fun decodeResponseInfoInternal(
        callResponses: List<InspectionResult>
    ): DecodedValidatorInfo? {
        // Access the cached ABI field
        val abiField = ValidatorBlockService::class.java.getDeclaredField("cachedGetValidatorsAbi")
        abiField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val cachedGetValidatorsAbi =
            abiField.get(this)
                as
                java.util.concurrent.ConcurrentHashMap<
                    String,
                    org.vechain.indexer.event.model.abi.AbiElement,
                >

        return decodeResponseInfo(callResponses, cachedGetValidatorsAbi)
    }
}
