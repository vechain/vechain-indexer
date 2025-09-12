package org.vechain.indexer.validator

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.vechain.indexer.docs.PaginationParameters
import org.vechain.indexer.exception.ExceptionResponse
import org.vechain.indexer.rest.PaginatedResponse
import org.vechain.indexer.rest.paginatedResponse
import org.vechain.indexer.utils.PaginationUtils.toPageable
import org.vechain.indexer.utils.SortFieldUtils
import org.vechain.indexer.validation.ValidPageSize

@Profile("validator")
@Tag(name = "Validator", description = "Query validator documents")
@Validated
@RestController
@RequestMapping("/validators")
open class ValidatorController(private val repository: ValidatorRepository) {
    @GetMapping
    @Operation(
        summary = "Get validators with optional filters",
        description =
            """
            This endpoint retrieves validator stats.

            You can filter the results by:
            - `validatorId`: specific validator ID
            - `status`: validator status
            - `endorser`: endorser address

            You can also sort the results by one of the supported fields and paginate.

            - `sortBy`: Choose between `validatorTvl`, `totalTvl`, or `blockProbability`
            - `page` and `size`: Controls pagination
            - `direction`: Either `asc` or `desc`
            """,
    )
    @ApiResponses(
        value =
            [
                ApiResponse(responseCode = "200", description = "Success"),
                ApiResponse(
                    responseCode = "400",
                    description = "Validation errors occurred",
                    content =
                        [
                            Content(
                                mediaType = "application/json",
                                schema = Schema(implementation = ExceptionResponse::class),
                            )
                        ],
                ),
            ]
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "validatorId",
        schema = Schema(type = "string"),
        description = "Filter by validator ID",
        required = false,
        example = "0x62cdf7135910dcabe336a0cdfcc3c1b16b774713",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "endorser",
        schema = Schema(type = "string"),
        description = "Filter by endorser address",
        required = false,
        example = "0x06c01371bc54c59d5fd9e296c74880324b62c5fe",
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "status",
        schema = Schema(implementation = Status::class),
        required = false,
    )
    @Parameter(
        `in` = ParameterIn.QUERY,
        name = "sortBy",
        description = "The sort by field",
        required = false,
        schema =
            Schema(
                type = "string",
                allowableValues = ["validatorTvl", "totalTvl", "blockProbability", "delegatorTvl"],
            ),
    )
    @PaginationParameters
    open fun getValidators(
        @RequestParam(required = false) status: Status?,
        @RequestParam(required = false) endorser: String?,
        @RequestParam(required = false) validatorId: String?,
        @RequestParam(required = false) page: Int?,
        @ValidPageSize @RequestParam(required = false) size: Int?,
        @RequestParam(required = false) direction: String?,
        @RequestParam(required = false, defaultValue = "validatorTvl") sortBy: String,
    ): PaginatedResponse<Validator> {
        val sortField = SortFieldUtils.getSortFieldValidator(sortBy)
        val pageable = toPageable(page, size, direction, sortField)

        val results: Slice<Validator> =
            when {
                validatorId != null -> {
                    val validatorOpt = repository.findById(validatorId)
                    if (validatorOpt.isPresent) {
                        SliceImpl(listOf(validatorOpt.get()), pageable, false)
                    } else {
                        SliceImpl(emptyList(), pageable, false)
                    }
                }
                status != null -> repository.findByStatus(status, pageable)
                endorser != null -> repository.findByEndorser(endorser, pageable)
                else -> repository.findAll(pageable)
            }

        return paginatedResponse(results)
    }
}
