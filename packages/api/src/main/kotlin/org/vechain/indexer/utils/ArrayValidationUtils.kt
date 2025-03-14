package org.vechain.indexer.utils

object ArrayValidationUtils {

    /**
     * Validates that an array contains only allowed values and has no duplicates.
     *
     * @param input The array to validate (nullable).
     * @param allowedValues The set of allowed values.
     * @param fieldName The name of the field being validated (used for error messages).
     * @param defaultValues Optional default values to return if input is null or empty.
     * @return A list of validated values.
     * @throws IllegalArgumentException if validation fails.
     */
    fun validateArray(
        input: List<String>?,
        allowedValues: Set<String>,
        fieldName: String,
        defaultValues: List<String>? = null
    ): List<String> {
        // If the input is null or empty, return default values if provided
        if (input.isNullOrEmpty()) {
            return defaultValues ?: emptyList()
        }

        // Ensure all input values are in the allowed set
        val invalidValues = input.filterNot { it in allowedValues }
        if (invalidValues.isNotEmpty()) {
            throw IllegalArgumentException(
                "Invalid $fieldName: $invalidValues. Allowed values: $allowedValues."
            )
        }

        // Check for duplicates
        val duplicates = input.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
        if (duplicates.isNotEmpty()) {
            throw IllegalArgumentException("Duplicate values found in $fieldName: $duplicates.")
        }

        return input
    }
}
