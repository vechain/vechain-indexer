package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import org.vechain.indexer.accounts.TimeFrame

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    name = "timeFrame",
    `in` = ParameterIn.QUERY,
    description = "Period to total over: DAY, WEEK, MONTH, YEAR or ALL.",
    required = false,
    schema = Schema(implementation = TimeFrame::class),
)
annotation class TimeFrameParameter
