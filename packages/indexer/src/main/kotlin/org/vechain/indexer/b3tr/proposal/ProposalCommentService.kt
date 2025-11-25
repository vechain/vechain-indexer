package org.vechain.indexer.b3tr.proposal

import com.github.pemistahl.lingua.api.Language
import com.github.pemistahl.lingua.api.LanguageDetector
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getPower
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getProposalId
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getReason
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getSupport
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getVoter
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getWeight
import org.vechain.indexer.event.model.generic.IndexedEvent

@Profile("b3tr", "b3tr-proposal", "b3tr-proposal-comments")
@Service
open class ProposalCommentService(
    private val mongoTemplate: MongoTemplate,
    private val detector: LanguageDetector = LanguageDetectorBuilder.fromAllLanguages().build(),
    @param:Value("\${comments.min-length}") private val minLength: Int,
    @param:Value("\${comments.language.confidence}") private val confidenceThreshold: Double,
) {
    private val logger = LoggerFactory.getLogger(ProposalCommentService::class.java)

    open fun processEvents(matchedEvents: List<IndexedEvent>): List<ProposalComment> {
        val comments =
            matchedEvents
                .map { event -> parseEvent(event) }
                .filter { comment -> comment.reason.isNotEmpty() && comment.reason.isNotBlank() }

        return comments.filter { vote -> allowComment(vote.reason) }
    }

    fun parseEvent(event: IndexedEvent): ProposalComment =
        ProposalComment(
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            voter = getVoter(event),
            proposalId = getProposalId(event),
            support = getSupport(event),
            weight = getWeight(event),
            power = getPower(event),
            reason = getReason(event),
        )

    open fun save(comments: List<ProposalComment>) {
        if (comments.isNotEmpty()) {
            mongoTemplate.insert(comments, ProposalComment::class.java)
        }
    }

    /** Used to filter out spam or unwanted comments. */
    private fun allowComment(comment: String): Boolean = !isTooShort(comment) && isEnglish(comment)

    /** Comment must be at least 5 characters long after trimming. */
    private fun isTooShort(comment: String): Boolean = comment.trim().length < minLength

    private fun isEnglish(comment: String): Boolean {
        val confidenceValues = detector.computeLanguageConfidenceValues(comment)

        if (confidenceValues.isEmpty()) {
            logger.info("No language detected for comment: $comment")
            return false
        }

        // If the confidence value of English is less than 0.5, we consider it as non-English
        val confidence = confidenceValues[Language.ENGLISH] ?: 0.0

        logger.debug("English confidence value $confidence for: $comment")

        if (confidence < confidenceThreshold) {
            logger.info(
                "Failed to meet confidence threshold of $confidenceThreshold for English: $comment"
            )
            return false
        }

        return true
    }
}
