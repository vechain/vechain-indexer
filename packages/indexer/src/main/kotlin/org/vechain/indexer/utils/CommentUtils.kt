package org.vechain.indexer.utils

import org.slf4j.LoggerFactory
import org.vechain.indexer.model.generateId
import org.vechain.indexer.repository.VevoteCommentRepository

object CommentUtils {
    private val logger = LoggerFactory.getLogger(CommentUtils::class.java)

    /** Used to filter out spam or unwanted comments. */
    fun allowComment(
        proposalId: String,
        comment: String,
        repository: VevoteCommentRepository,
        minLength: Int
    ): Boolean = !isTooShort(comment, minLength) && !isSpam(proposalId, comment, repository)

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
}
