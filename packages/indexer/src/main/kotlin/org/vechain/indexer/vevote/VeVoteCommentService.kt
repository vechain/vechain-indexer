package org.vechain.indexer.vevote

import com.github.pemistahl.lingua.api.Language
import com.github.pemistahl.lingua.api.LanguageDetector
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.b3tr.action.IdUtils.generateId
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("vevote", "vevote-comments")
@Service
class VeVoteCommentService(
    private val repository: VevoteCommentRepository,
    @Value("\${comments.min-length}") private val minLength: Int,
    @Value("\${comments.language.confidence}") private val confidenceThreshold: String,
) {
    private val logger = LoggerFactory.getLogger(VeVoteCommentService::class.java)

    // Only support English to reduce memory usage
    private val detector: LanguageDetector =
        LanguageDetectorBuilder.fromLanguages(
                Language.ENGLISH,
                Language.FRENCH,
                Language.GERMAN,
                Language.SPANISH,
                Language.ITALIAN,
                Language.PORTUGUESE,
                Language.DUTCH,
                Language.RUSSIAN,
                Language.POLISH,
                Language.SWEDISH,
            )
            .build()

    fun processComment(processedEvents: List<IndexedEvent>): List<VeVoteProposalComment> =
        processedEvents
            .mapNotNull { extractVeVoteCommentEvent(it) }
            .filter { it.reason.isNotBlank() }
            .filter { allowComment(it.proposalId, it.reason) }

    fun extractVeVoteCommentEvent(event: IndexedEvent): VeVoteProposalComment? {
        val params = event.params

        val reason = params.getAsString("reason") ?: return null
        if (reason.isBlank()) return null // Ignore if no comment provided

        val voter = params.getAsString("voter") ?: return null
        val proposalId = params.getAsString("proposalId") ?: return null
        val weight = params.getAsBigInteger("weight") ?: return null
        val supportRaw = params.getAsBigInteger("support") ?: return null

        val support = Support.map(supportRaw)

        return VeVoteProposalComment(
            id = generateId(proposalId, reason),
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            voter = voter,
            proposalId = proposalId,
            support = support,
            weight = weight,
            reason = reason,
        )
    }

    fun allowComment(proposalId: String, comment: String): Boolean =
        !isTooShort(comment) && !isSpam(proposalId, comment) && isEnglish(comment)

    fun isTooShort(comment: String?): Boolean = comment == null || comment.trim().length < minLength

    fun isSpam(proposalId: String, comment: String): Boolean {
        val id = generateId(proposalId, comment)
        val isDuplicate = repository.existsById(id)

        if (isDuplicate) {
            logger.info("Duplicate comment detected for proposal $proposalId: $comment")
        }

        return isDuplicate
    }

    fun isEnglish(comment: String): Boolean {
        val shortened = comment.take(400) // Using only first 400 chars to try avoid memory issues
        val confidenceValues = detector.computeLanguageConfidenceValues(shortened)

        if (confidenceValues.isEmpty()) {
            logger.info("No language detected for comment: $shortened")
            return false
        }

        val confidence = confidenceValues[Language.ENGLISH] ?: 0.0

        if (confidence < confidenceThreshold.toDouble()) {
            logger.info("Below threshold $confidenceThreshold: $shortened")
            return false
        }

        return true
    }
}
