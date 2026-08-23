package com.betterstreamflix.providers

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProviderHealthMonitorTest {

    @BeforeEach
    fun setup() {
        ProviderHealthMonitor.resetAll()
    }

    @Test
    fun `provider should be healthy initially`() {
        assertTrue(ProviderHealthMonitor.isHealthy("TestProvider"))
    }

    @Test
    fun `recordSuccess should reset consecutive failures`() {
        ProviderHealthMonitor.recordFailure("TestProvider", "error")
        ProviderHealthMonitor.recordFailure("TestProvider", "error")
        ProviderHealthMonitor.recordSuccess("TestProvider")
        val stats = ProviderHealthMonitor.getStats("TestProvider")
        assertFalse(stats == null)
        assertTrue(stats!!.consecutiveFailures == 0)
    }

    @Test
    fun `provider should be unhealthy after max consecutive failures`() {
        repeat(5) { i ->
            ProviderHealthMonitor.recordFailure("TestProvider", "error $i")
        }
        assertFalse(ProviderHealthMonitor.isHealthy("TestProvider"))
    }

    @Test
    fun `getStats should return correct counts`() {
        ProviderHealthMonitor.recordSuccess("TestProvider")
        ProviderHealthMonitor.recordSuccess("TestProvider")
        ProviderHealthMonitor.recordFailure("TestProvider", "fail")
        val stats = ProviderHealthMonitor.getStats("TestProvider")
        assertTrue(stats != null)
        assertTrue(stats!!.totalRequests == 3)
        assertTrue(stats.totalFailures == 1)
    }

    @Test
    fun `reset should clear provider state`() {
        ProviderHealthMonitor.recordFailure("TestProvider", "error")
        ProviderHealthMonitor.reset("TestProvider")
        assertTrue(ProviderHealthMonitor.getStats("TestProvider") == null)
    }
}
