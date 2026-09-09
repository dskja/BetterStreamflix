package com.dskja.betterstreamflix.providers

/**
 * Provider health / hygiene: quarantine chronically brittle sources and define
 * the smoke-test priority set for CI / manual checks.
 */
object ProviderHealth {

    /**
     * Providers hidden from the default picker until the user enables
     * "Show quarantined providers" in settings. Names must match [Provider.name].
     */
    val quarantinedNames: Set<String> = setOf(
        "Fanpelis",
        "Kidraz",
        "Einschalten",
        "Series Turcas",
        "FrenchManga",
        "Poseidonhd2",
        "SeriesFlix",
    )

    /** High-traffic providers that smoke harnesses should prioritize. */
    val topSmokeNames: List<String> = listOf(
        "SerienStream",
        "StreamingCommunity",
        "StreamingCommunity (EN)",
        "GuardaFlix",
        "GuardaSerie",
        "HDFilme",
        "Frembed",
        "AniWorld",
        "SFlix",
        "Cine24h",
        "SoloLatino",
        "MEGAKino",
    )

    fun isQuarantined(provider: Provider): Boolean =
        quarantinedNames.contains(provider.name)

    fun isQuarantinedName(name: String): Boolean =
        quarantinedNames.contains(name)

    fun activeProviders(includeQuarantined: Boolean): List<Provider> {
        return Provider.providers.keys.filter { provider ->
            includeQuarantined || !isQuarantined(provider)
        }
    }

    fun smokeTargets(includeQuarantined: Boolean = false): List<Provider> {
        val active = activeProviders(includeQuarantined).associateBy { it.name }
        return topSmokeNames.mapNotNull { active[it] }
    }
}
