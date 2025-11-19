package org.vechain.indexer.amn

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.API_PATH
import org.vechain.indexer.docs.AccountParameter
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.exception.ResourceNotFoundException
import org.vechain.indexer.thor.Address
import org.vechain.indexer.validation.ValidAddress

@Profile("authority-nodes")
@Tag(
    name = "Authority Nodes",
    description = "Indexer API for querying Authority Master Node endorsement information.",
)
@Validated
@RestController
@Deprecated("This api is deprecated post hayabusa release")
@RequestMapping("$API_PATH/authority-endorsers")
open class AmnController(private val amnApiEndorserService: AmnApiEndorserService) {
    @GetMapping("endorsers/{user}")
    @Operation(summary = "Check if a user is an endorser of any Authority Master Node.")
    @AccountParameter(
        `in` = ParameterIn.PATH,
        name = "user",
        description = "User address to check if they are an endorser.",
        required = true,
    )
    @CommonApiResponses
    open fun checkUserIsEndorser(@ValidAddress @PathVariable user: Address): AmnEndorser =
        amnApiEndorserService.findByEndorser(user.value)
            ?: throw ResourceNotFoundException("No endorser found for ${user.value}")
}
