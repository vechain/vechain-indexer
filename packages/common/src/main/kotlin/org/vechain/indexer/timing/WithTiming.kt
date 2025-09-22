package org.vechain.indexer.timing

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class WithTiming(val value: String = "")
