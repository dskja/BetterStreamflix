package com.betterstreamflix.testing

/**
 * Test assertions — custom assertion helpers for BetterStreamflix tests.
 */
object TestAssertions {

    /**
     * Assert that a list is not empty.
     */
    fun assertNotEmpty(list: List<*>, message: String = "List should not be empty") {
        assert(list.isNotEmpty()) { message }
    }

    /**
     * Assert that a list has a specific size.
     */
    fun assertSize(list: List<*>, expectedSize: Int, message: String = "List size mismatch") {
        assert(list.size == expectedSize) { "$message: expected $expectedSize, got ${list.size}" }
    }

    /**
     * Assert that a string is not blank.
     */
    fun assertNotBlank(value: String?, message: String = "String should not be blank") {
        assert(!value.isNullOrBlank()) { message }
    }

    /**
     * Assert that a value is in a range.
     */
    fun assertInRange(value: Number, min: Number, max: Number, message: String = "Value out of range") {
        val v = value.toDouble()
        assert(v >= min.toDouble() && v <= max.toDouble()) {
            "$message: expected [$min, $max], got $value"
        }
    }

    /**
     * Assert that two lists contain the same elements (order-independent).
     */
    fun <T> assertSameElements(actual: List<T>, expected: List<T>, message: String = "Lists do not contain same elements") {
        assert(actual.toSet() == expected.toSet()) {
            "$message: expected ${expected.toSet()}, got ${actual.toSet()}"
        }
    }

    /**
     * Assert that a condition is true within a timeout.
     */
    fun assertEventually(condition: () -> Boolean, timeoutMs: Long = 5000L, message: String = "Condition not met within timeout") {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (condition()) return
            Thread.sleep(100)
        }
        throw AssertionError(message)
    }

    /**
     * Assert that a result is successful.
     */
    fun <T> assertSuccess(result: com.betterstreamflix.architecture.OperationResult<T>, message: String = "Expected success") {
        assert(result is com.betterstreamflix.architecture.OperationResult.Success) { message }
    }

    /**
     * Assert that a result is a failure.
     */
    fun <T> assertFailure(result: com.betterstreamflix.architecture.OperationResult<T>, message: String = "Expected failure") {
        assert(result is com.betterstreamflix.architecture.OperationResult.Failure) { message }
    }
}
