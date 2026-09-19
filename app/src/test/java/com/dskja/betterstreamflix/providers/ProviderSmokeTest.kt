package com.dskja.betterstreamflix.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProviderSmokeTest {

    @Before
    fun clearState() {
        // Success clears circuit state between tests.
        ProviderSmoke.noteHomeSuccess("CircuitProbe")
    }

    @Test
    fun circuitOpensAfterThresholdFailures() {
        val name = "CircuitProbe"
        repeat(ProviderSmoke.CIRCUIT_FAILURE_THRESHOLD) {
            ProviderSmoke.noteHomeFailure(name)
        }
        assertTrue(ProviderSmoke.isHomeCircuitOpen(name))
        assertTrue(ProviderSmoke.failureCount(name) >= ProviderSmoke.CIRCUIT_FAILURE_THRESHOLD)
        assertTrue(ProviderSmoke.circuitHint(name)!!.contains(name))
    }

    @Test
    fun successClearsCircuit() {
        val name = "CircuitProbe"
        repeat(ProviderSmoke.CIRCUIT_FAILURE_THRESHOLD) {
            ProviderSmoke.noteHomeFailure(name)
        }
        ProviderSmoke.noteHomeSuccess(name)
        assertFalse(ProviderSmoke.isHomeCircuitOpen(name))
        assertEquals(0, ProviderSmoke.failureCount(name))
        assertNull(ProviderSmoke.lastFailureAt(name))
    }

    @Test
    fun belowThresholdDoesNotOpenCircuit() {
        val name = "CircuitProbeSoft"
        ProviderSmoke.noteHomeSuccess(name)
        repeat(ProviderSmoke.CIRCUIT_FAILURE_THRESHOLD - 1) {
            ProviderSmoke.noteHomeFailure(name)
        }
        assertFalse(ProviderSmoke.isHomeCircuitOpen(name))
        assertTrue(ProviderSmoke.failureCount(name) > 0)
        ProviderSmoke.noteHomeSuccess(name)
    }
}
