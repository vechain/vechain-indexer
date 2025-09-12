package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import org.vechain.indexer.b3tr.AppId

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    name = "appId",
    description = "App ID to query by.",
    schema = Schema(type = "string", pattern = AppId.REGEX),
)
annotation class AppIdParameter(
    val required: Boolean = false,
    val `in`: ParameterIn = ParameterIn.QUERY,
)
