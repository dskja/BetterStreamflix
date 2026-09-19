package com.dskja.betterstreamflix.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.utils.DnsResolver
import com.dskja.betterstreamflix.utils.NetworkClient
import com.dskja.betterstreamflix.utils.TMDb3
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * High-end Connection & Services diagnostics (DNS, DoH, HTTP reachability, provider + integration sweep).
 */
object ConnectionDiagnostics {

    data class NetworkSnapshot(
        val typeLabel: String,
        val metered: Boolean,
        val validated: Boolean,
        val dohEnabled: Boolean,
        val dohProvider: String,
        val providerName: String?,
        val providerBaseUrl: String?,
    )

    data class ProbeReport(
        val ok: Boolean,
        val title: String,
        val lines: List<String>,
        val latencyMs: Long? = null,
    ) {
        fun asSummary(maxLines: Int = 4): String =
            lines.take(maxLines).joinToString(" · ")

        fun asClipboardText(): String =
            buildString {
                appendLine(title)
                lines.forEach { appendLine(it) }
            }.trimEnd()
    }

    private val lastReportRef = AtomicReference<ProbeReport?>(null)

    fun lastReport(): ProbeReport? = lastReportRef.get()

    fun remember(report: ProbeReport): ProbeReport {
        lastReportRef.set(report)
        return report
    }

    fun clearLastReport() {
        lastReportRef.set(null)
    }

    fun networkSnapshot(context: Context): NetworkSnapshot {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val type = when {
            caps == null -> context.getString(R.string.connection_net_offline)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                context.getString(R.string.connection_net_wifi)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                context.getString(R.string.connection_net_ethernet)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                context.getString(R.string.connection_net_cellular)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ->
                context.getString(R.string.connection_net_vpn)
            else -> context.getString(R.string.connection_net_other)
        }
        val dohUrl = UserPreferences.dohProviderUrl
        val dohEnabled = dohUrl.isNotBlank()
        val providerName = when {
            !dohEnabled -> context.getString(R.string.connection_doh_system)
            "cloudflare" in dohUrl -> "Cloudflare"
            "dns.google" in dohUrl -> "Google"
            "quad9" in dohUrl -> "Quad9"
            "opendns" in dohUrl -> "OpenDNS"
            "adguard" in dohUrl -> "AdGuard"
            "cleanbrowsing" in dohUrl -> "CleanBrowsing"
            "dns4eu" in dohUrl || "joindns4" in dohUrl -> "DNS4EU"
            "fdn.fr" in dohUrl -> "FDN"
            else -> dohUrl.removePrefix("https://").substringBefore("/")
        }
        val current = UserPreferences.currentProvider
        return NetworkSnapshot(
            typeLabel = type,
            metered = cm?.isActiveNetworkMetered == true,
            validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            dohEnabled = dohEnabled,
            dohProvider = providerName,
            providerName = current?.name,
            providerBaseUrl = current?.baseUrl?.takeIf { it.isNotBlank() },
        )
    }

    fun statusSummary(context: Context): String {
        val snap = networkSnapshot(context)
        val meter = if (snap.metered) {
            context.getString(R.string.connection_metered)
        } else {
            context.getString(R.string.connection_unmetered)
        }
        val doh = if (snap.dohEnabled) {
            context.getString(R.string.connection_doh_on, snap.dohProvider)
        } else {
            context.getString(R.string.connection_doh_off)
        }
        return context.getString(
            R.string.connection_status_summary,
            snap.typeLabel,
            meter,
            doh,
        )
    }

    fun validatedLabel(context: Context): String {
        val snap = networkSnapshot(context)
        return if (snap.validated) {
            context.getString(R.string.connection_validated_yes)
        } else {
            context.getString(R.string.connection_validated_no)
        }
    }

    fun providerStatusSummary(context: Context): String {
        val snap = networkSnapshot(context)
        val name = snap.providerName ?: return context.getString(R.string.connection_provider_none)
        val host = snap.providerBaseUrl
            ?.removePrefix("https://")
            ?.removePrefix("http://")
            ?.substringBefore("/")
            ?.takeIf { it.isNotBlank() }
        return if (host != null) {
            context.getString(R.string.connection_provider_status, name, host)
        } else {
            name
        }
    }

    suspend fun probeDns(host: String = "api.themoviedb.org"): IntegrationProbes.ProbeResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val start = System.nanoTime()
                val addrs = DnsResolver.lookup(host)
                val ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                if (addrs.isEmpty()) {
                    IntegrationProbes.ProbeResult(false, "DNS empty for $host")
                } else {
                    val sample = addrs.take(2).joinToString { it.hostAddress ?: "?" }
                    IntegrationProbes.ProbeResult(true, "DNS OK · ${ms}ms · $sample")
                }
            }.getOrElse {
                IntegrationProbes.ProbeResult(false, "DNS failed · ${it.message ?: "error"}")
            }
        }

    suspend fun probeHttp(url: String, label: String): IntegrationProbes.ProbeResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val start = System.nanoTime()
                val request = Request.Builder().url(url).head().build()
                NetworkClient.default.newCall(request).execute().use { response ->
                    val ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                    if (response.isSuccessful || response.code in 300..399 || response.code == 405) {
                        IntegrationProbes.ProbeResult(true, "$label OK · ${ms}ms · HTTP ${response.code}")
                    } else {
                        IntegrationProbes.ProbeResult(false, "$label HTTP ${response.code} · ${ms}ms")
                    }
                }
            }.getOrElse {
                // Some hosts reject HEAD — fall back to GET with short read.
                runCatching {
                    val start = System.nanoTime()
                    val request = Request.Builder().url(url).get().build()
                    NetworkClient.default.newCall(request).execute().use { response ->
                        val ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                        response.body?.close()
                        if (response.isSuccessful || response.code in 300..399) {
                            IntegrationProbes.ProbeResult(true, "$label OK · ${ms}ms · HTTP ${response.code}")
                        } else {
                            IntegrationProbes.ProbeResult(false, "$label HTTP ${response.code}")
                        }
                    }
                }.getOrElse { e ->
                    IntegrationProbes.ProbeResult(false, "$label failed · ${e.message ?: "error"}")
                }
            }
        }

    suspend fun probeDoh(): IntegrationProbes.ProbeResult = withContext(Dispatchers.IO) {
        val url = UserPreferences.dohProviderUrl
        if (url.isBlank()) {
            return@withContext IntegrationProbes.ProbeResult(
                true,
                "DoH disabled · using system DNS",
            )
        }
        runCatching {
            val start = System.nanoTime()
            val addrs = DnsResolver.lookup("cloudflare.com")
            val ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
            if (addrs.isEmpty()) {
                IntegrationProbes.ProbeResult(false, "DoH returned no addresses")
            } else {
                IntegrationProbes.ProbeResult(true, "DoH OK · ${ms}ms · ${addrs.size} addr")
            }
        }.getOrElse {
            IntegrationProbes.ProbeResult(false, "DoH failed · ${it.message ?: "error"}")
        }
    }

    suspend fun probeTmdb(): IntegrationProbes.ProbeResult = withContext(Dispatchers.IO) {
        runCatching {
            when {
                !UserPreferences.enableTmdb ->
                    IntegrationProbes.ProbeResult(false, "TMDb disabled in Settings")
                !TMDb3.hasApiKey() ->
                    IntegrationProbes.ProbeResult(false, "TMDb API key missing")
                TMDb3.ping() ->
                    IntegrationProbes.ProbeResult(true, "TMDb API OK")
                else ->
                    IntegrationProbes.ProbeResult(false, "TMDb ping failed")
            }
        }.getOrElse {
            IntegrationProbes.ProbeResult(false, it.message ?: "TMDb probe failed")
        }
    }

    suspend fun probeProvider(): IntegrationProbes.ProbeResult = withContext(Dispatchers.IO) {
        val provider = UserPreferences.currentProvider
            ?: return@withContext IntegrationProbes.ProbeResult(false, "No active provider")
        val base = provider.baseUrl.trim().ifBlank {
            return@withContext IntegrationProbes.ProbeResult(false, "${provider.name} has no base URL")
        }
        val url = if (base.endsWith("/")) base else "$base/"
        probeHttp(url, provider.name)
    }

    suspend fun probeSubdl(): IntegrationProbes.ProbeResult = withContext(Dispatchers.IO) {
        val key = UserPreferences.subdlApiKey.trim()
        if (key.isBlank()) {
            return@withContext IntegrationProbes.ProbeResult(false, "SubDL API key missing")
        }
        probeHttp("https://api.subdl.com", "SubDL")
    }

    suspend fun runFullDiagnostic(context: Context): ProbeReport = withContext(Dispatchers.IO) {
        coroutineScope {
            val snap = networkSnapshot(context)
            val started = System.nanoTime()
            val dns = async { probeDns() }
            val doh = async { probeDoh() }
            val tmdbHttp = async { probeHttp("https://api.themoviedb.org/3", "TMDb") }
            val github = async { probeHttp("https://api.github.com", "GitHub") }
            val cloudflare = async { probeHttp("https://1.1.1.1", "Cloudflare") }
            val tmdbApi = async { probeTmdb() }
            val provider = async { probeProvider() }

            val dnsR = dns.await()
            val dohR = doh.await()
            val httpTmdb = tmdbHttp.await()
            val httpGh = github.await()
            val httpCf = cloudflare.await()
            val tmdbR = tmdbApi.await()
            val providerR = provider.await()
            val totalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

            val lines = buildList {
                add("${snap.typeLabel}${if (snap.metered) " · metered" else ""}${if (snap.validated) " · validated" else ""}")
                add(if (snap.dohEnabled) "DoH ${snap.dohProvider}" else "System DNS")
                snap.providerName?.let { add("Provider · $it") }
                add(dnsR.message)
                add(dohR.message)
                add(httpTmdb.message)
                add(httpGh.message)
                add(httpCf.message)
                add(tmdbR.message)
                add(providerR.message)
                add("Sweep · ${totalMs}ms")
            }
            val ok = dnsR.ok && dohR.ok && (httpTmdb.ok || httpGh.ok || httpCf.ok)
            remember(
                ProbeReport(
                    ok = ok,
                    title = if (ok) {
                        context.getString(R.string.connection_diag_ok)
                    } else {
                        context.getString(R.string.connection_diag_fail)
                    },
                    lines = lines,
                    latencyMs = totalMs,
                ),
            )
        }
    }

    suspend fun probeAllIntegrations(): ProbeReport = withContext(Dispatchers.IO) {
        coroutineScope {
            val trakt = IntegrationProbes.traktLocal()
            val jellyfin = async { IntegrationProbes.jellyfin() }
            val plex = async { IntegrationProbes.plex() }
            val debrid = async { IntegrationProbes.debrid() }
            val simkl = async { IntegrationProbes.simkl() }
            val os = async { IntegrationProbes.openSubtitles() }
            val tmdb = async { probeTmdb() }
            val subdl = async { probeSubdl() }
            val provider = async { probeProvider() }

            val results = listOf(
                "Trakt" to trakt,
                "Jellyfin" to jellyfin.await(),
                "Plex" to plex.await(),
                "Debrid" to debrid.await(),
                "Simkl" to simkl.await(),
                "OpenSubtitles" to os.await(),
                "TMDb" to tmdb.await(),
                "SubDL" to subdl.await(),
                "Provider" to provider.await(),
            )
            val okCount = results.count { it.second.ok }
            val lines = results.map { (name, r) ->
                val mark = if (r.ok) "✓" else "✗"
                "$mark $name · ${r.message}"
            }
            remember(
                ProbeReport(
                    ok = okCount > 0,
                    title = "$okCount/${results.size} services OK",
                    lines = lines,
                ),
            )
        }
    }

    /** Prefer IPv4 sample for display in tests. */
    fun resolveSystem(host: String): List<String> =
        runCatching {
            InetAddress.getAllByName(host).mapNotNull { it.hostAddress }
        }.getOrDefault(emptyList())
}
