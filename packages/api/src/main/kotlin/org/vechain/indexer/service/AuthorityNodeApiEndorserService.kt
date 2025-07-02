package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.controller.AuthorityNodeEndorserResponse
import org.vechain.indexer.controller.AuthorityNodeInfo
import org.vechain.indexer.repository.AuthorityNodeRepository

@Profile("authority-nodes")
@Service
class AuthorityNodeApiEndorserService(
    private val authorityNodeRepository: AuthorityNodeRepository
) {

    fun checkUserIsEndorser(user: String): AuthorityNodeEndorserResponse {
        val endorsedNodes =
            authorityNodeRepository
                .findAll()
                .filter { it.endorser?.equals(user, ignoreCase = true) == true }
                .map { node ->
                    AuthorityNodeInfo(
                        nodeMaster = node.nodeMaster,
                        blockNumber = node.blockNumber,
                        blockTimestamp = node.blockTimestamp,
                    )
                }

        return AuthorityNodeEndorserResponse(
            user = user,
            isEndorser = endorsedNodes.isNotEmpty(),
            endorsedNodes = endorsedNodes,
            totalEndorsedNodes = endorsedNodes.size,
        )
    }
}
