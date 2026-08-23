package com.betterstreamflix.network

import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

/**
 * DNS-over-HTTPS (DoH) configuration helper.
 * Provides encrypted DNS resolution to bypass DNS-based blocking.
 */
object DnsOverHttpsConfig {

    /**
     * Known DoH providers.
     */
    val DOH_PROVIDERS = mapOf(
        "cloudflare" to "https://cloudflare-dns.com/dns-query",
        "google" to "https://dns.google/dns-query",
        "quad9" to "https://dns.quad9.net/dns-query",
        "adguard" to "https://dns.adguard.com/dns-query",
        "mullvad" to "https://doh.mullvad.net/dns-query",
    )

    /**
     * Configure DoH on an OkHttpClient.
     */
    fun configureDoh(client: OkHttpClient.Builder, dohUrl: String): OkHttpClient.Builder {
        val dns = DnsOverHttps.Builder()
            .client(client.build())
            .url(HttpUrl.parse(dohUrl)!!)
            .includeIPv6(true)
            .build()

        return client.dns(dns)
    }

    /**
     * Get the DoH URL from user preferences.
     */
    fun getConfiguredDohUrl(): String? {
        val url = com.betterstreamflix.utils.UserPreferences.dohProviderUrl
        return if (!url.isNullOrBlank()) url else null
    }

    /**
     * Get a fallback DNS resolver.
     */
    fun getFallbackDns(): Dns = Dns.SYSTEM
}
