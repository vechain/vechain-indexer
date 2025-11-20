package org.vechain.indexer.docs

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import org.vechain.indexer.transaction.TransactionUtils

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(
    `in` = ParameterIn.PATH,
    name = "txId",
    schema = Schema(type = "string", pattern = TransactionUtils.REGEX),
    description = "A valid transaction ID",
    required = true,
    example = "0xacc8566c931235a43a775120d48680278d42fa12111aa3c4d4e3a7e8cfcd360a",
)
annotation class TransactionIdParameter
