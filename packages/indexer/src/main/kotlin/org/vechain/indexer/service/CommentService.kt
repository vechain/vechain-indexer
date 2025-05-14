package org.vechain.indexer.service

import com.github.pemistahl.lingua.api.Language
import com.github.pemistahl.lingua.api.LanguageDetector
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder
import java.math.BigInteger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.VevoteProposalComment
import org.vechain.indexer.model.generateId
import org.vechain.indexer.repository.VevoteCommentRepository
import org.vechain.indexer.utils.EventUtils.getChoice

@Profile("vevote-events")
@Service
class CommentService(
    private val repository: VevoteCommentRepository,
    @Value("\${comments.minLength}") private val minLength: Int,
    @Value("\${comments.language.confidence}") private val confidenceThreshold: String,
) {
    private val logger = LoggerFactory.getLogger(CommentService::class.java)
    private val detector: LanguageDetector = LanguageDetectorBuilder.fromAllLanguages().build()

    fun processComment(processedEvents: List<IndexedEvent>): List<VevoteProposalComment> {
        // Process events to extract Reason
        val potentialComment =
            processedEvents.mapNotNull { event -> extractVevoteCommentEvent(event) }
        return potentialComment
            .filter { vote -> vote.reason.isNotEmpty() }
            .filter { vote -> allowComment(vote.proposalId, vote.reason, repository, minLength) }
    }

    fun extractVevoteCommentEvent(event: IndexedEvent): VevoteProposalComment? {
        try {
            val params = event.params
            val voter = params.getReturnValues()["voter"] as? String ?: return null
            val proposalId = params.getReturnValues()["proposalId"]?.toString() ?: return null
            val reason = params.getReturnValues()["reason"] as? String
            val nonNullReasonForId = reason ?: ""
            // Get the raw choice value
            val choiceValue = (params.getReturnValues()["choices"] as? Number)?.toLong() ?: 0L
            val choicesList = getChoice(choiceValue)

            return VevoteProposalComment(
                id = generateId(proposalId, nonNullReasonForId),
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
                voter = voter,
                proposalId = proposalId,
                choices = choicesList,
                weight =
                    (params.getReturnValues()["weight"] as? Number)?.toLong()?.toBigInteger()
                        ?: BigInteger.ZERO,
                reason = reason ?: ""
            )
        } catch (e: Exception) {
            return null
        }
    }

    /** Used to filter out spam or unwanted comments. */
    fun allowComment(
        proposalId: String,
        comment: String,
        repository: VevoteCommentRepository,
        minLength: Int
    ): Boolean =
        !isTooShort(comment, minLength) &&
            !isSpam(proposalId, comment, repository) &&
            isEnglish(comment)

    /** Comment must be at least 5 characters long after trimming. */
    fun isTooShort(comment: String?, minLength: Int): Boolean =
        comment == null || comment.trim().length < minLength

    /** Only allow one comment per proposal with the same content. */
    fun isSpam(proposalId: String, comment: String, repository: VevoteCommentRepository): Boolean {
        val id = generateId(proposalId, comment)
        val isDuplicate = repository.existsById(id)

        if (isDuplicate) {
            logger.info("Duplicate comment detected for proposal $proposalId: $comment")
        }

        return isDuplicate
    }

    fun isEnglish(comment: String): Boolean {
        val confidenceValues = detector.computeLanguageConfidenceValues(comment)

        if (confidenceValues.isEmpty()) {
            logger.info("No language detected for comment: $comment")
            return false
        }

        // If the confidence value of English is less than 0.5, we consider it as non-English
        val confidence = confidenceValues[Language.ENGLISH] ?: 0.0

        logger.debug("English confidence value $confidence for: $comment")

        if (confidence < confidenceThreshold.toDouble()) {
            logger.info(
                "Failed to meet confidence threshold of $confidenceThreshold for English: $comment",
            )
            return false
        }

        return true
    }
}
