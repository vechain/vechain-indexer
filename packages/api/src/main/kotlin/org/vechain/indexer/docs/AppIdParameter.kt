package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.core.annotation.AliasFor
import org.vechain.indexer.b3tr.AppId

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    name = "appId",
    description = "App ID to query by.",
    schema = Schema(type = "string", pattern = AppId.REGEX),
    example = "0x2fc30c2ad41a2994061efaf218f1d52dc92bc4a31a0f02a4916490076a7a393a",
)
annotation class AppIdParameter(
    @get:AliasFor(annotation = Parameter::class, attribute = "required")
    val required: Boolean = false,
    @get:AliasFor(annotation = Parameter::class, attribute = "in")
    val `in`: ParameterIn = ParameterIn.QUERY,
)
