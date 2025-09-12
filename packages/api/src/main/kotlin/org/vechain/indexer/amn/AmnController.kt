package org.vechain.indexer.amn

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.vechain.indexer.constants.AUTHORITY_NODES_PATH
import org.vechain.indexer.docs.CommonApiResponses
import org.vechain.indexer.thor.Address
import org.vechain.indexer.validation.ValidAddress

@Profile("authority-nodes")
@Tag(
    name = "Authority Nodes",
    description = "Indexer API for querying Authority Master Node endorsement information.",
)
@Validated
@RestController
@RequestMapping(AUTHORITY_NODES_PATH)
open class AmnController(private val amnApiEndorserService: AmnApiEndorserService) {
    @GetMapping("endorsers/{user}")
    @Operation(summary = "Check if a user is an endorser of any Authority Master Node.")
    @Parameter(
        `in` = ParameterIn.PATH,
        name = "user",
        description = "User address to check if they are an endorser.",
        required = true,
        schema = Schema(type = "string"),
    )
    @CommonApiResponses
    open fun checkUserIsEndorser(@ValidAddress @PathVariable user: Address): AmnEndorser? =
        amnApiEndorserService.findByEndorser(user.value)
}
