package com.betterstreamflix.providers

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class ProviderHealthMonitorTest {

    @Before
    fun reset() {
        ProviderHealthMonitor.reset("TestProvider")
    }

    @Test
    fun isHealthy_byDefault() {
        assertThat(ProviderHealthMonitor.isHealthy("TestProvider")).isTrue()
    }

    @Test
    fun recordFailure_thenSuccess_clearsConsecutive() {
        repeat(3) { ProviderHealthMonitor.recordFailure("TestProvider", "boom") }
        ProviderHealthMonitor.recordSuccess("TestProvider")
        val stats = ProviderHealthMonitor.getStats("TestProvider")
        assertThat(stats?.consecutiveFailures).isEqualTo(0)
        assertThat(ProviderHealthMonitor.isHealthy("TestProvider")).isTrue()
    }

    @Test
    fun becomesUnhealthy_afterMaxConsecutiveFailures() {
        repeat(5) { ProviderHealthMonitor.recordFailure("TestProvider", "down") }
        assertThat(ProviderHealthMonitor.isHealthy("TestProvider")).isFalse()
    }
}
