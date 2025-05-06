package org.vechain.indexer.utils

import com.github.pemistahl.lingua.api.Language
import com.github.pemistahl.lingua.api.LanguageDetector
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder
import org.slf4j.LoggerFactory
import org.vechain.indexer.model.generateId
import org.vechain.indexer.repository.VevoteCommentRepository

object CommentUtils {
    private val logger = LoggerFactory.getLogger(CommentUtils::class.java)
    private val detector: LanguageDetector = LanguageDetectorBuilder.fromAllLanguages().build()
    private val confidenceThreshold = 0.9

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

        if (confidence < confidenceThreshold) {
            logger.info(
                "Failed to meet confidence threshold of $confidenceThreshold for English: $comment",
            )
            return false
        }

        return true
    }
}
