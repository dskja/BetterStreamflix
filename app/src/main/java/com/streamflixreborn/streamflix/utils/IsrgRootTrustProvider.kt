package com.streamflixreborn.streamflix.utils

import android.os.Build
import android.util.Log
import com.streamflixreborn.streamflix.R
import com.streamflixreborn.streamflix.StreamFlixApp
import java.security.KeyStore
import java.security.Provider
import java.security.cert.CertificateFactory
import javax.net.ssl.ManagerFactoryParameters
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.TrustManagerFactorySpi
import javax.net.ssl.X509TrustManager

/**
 * Security Provider that wraps the default TrustManagerFactory to include
 * the ISRG Root X1 certificate (Let's Encrypt) on older Android versions
 * where the system CA store doesn't include it.
 *
 * Registered BEFORE Conscrypt so that ALL OkHttpClient instances
 * automatically trust Let's Encrypt without code changes per provider.
 */
class IsrgRootTrustProvider : Provider("IsrgRootTrust", 1.0, "Adds ISRG Root X1 to TrustManagerFactory") {

    companion object {
        private const val TAG = "IsrgRootTrust"

        fun install() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return

            try {
                java.security.Security.insertProviderAt(IsrgRootTrustProvider(), 1)
                Log.i(TAG, "ISRG Root X1 TrustProvider installed for API ${Build.VERSION.SDK_INT}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to install ISRG Root X1 TrustProvider: ${e.message}")
            }
        }

        /** Merged KeyStore: system CAs + ISRG Root X1. Built once and cached. */
        internal val mergedKeyStore: KeyStore by lazy {
            val algorithm = TrustManagerFactory.getDefaultAlgorithm()

            // Load system certificates via default factory (skips our provider by using explicit provider name)
            val systemTmf = getSystemTrustManagerFactory(algorithm)
            systemTmf.init(null as KeyStore?)
            val systemTm = systemTmf.trustManagers.first { it is X509TrustManager } as X509TrustManager

            // Create merged keystore
            val ks = KeyStore.getInstance(KeyStore.getDefaultType())
            ks.load(null, null)

            systemTm.acceptedIssuers.forEachIndexed { i, cert ->
                ks.setCertificateEntry("system_$i", cert)
            }

            // Add ISRG Root X1
            val cf = CertificateFactory.getInstance("X.509")
            val isrgCert = StreamFlixApp.instance.resources.openRawResource(R.raw.isrg_root_x1).use {
                cf.generateCertificate(it)
            }
            ks.setCertificateEntry("isrg_root_x1", isrgCert)

            Log.d(TAG, "Merged KeyStore built: ${ks.size()} certificates")
            ks
        }

        internal fun getSystemTrustManagerFactory(algorithm: String): TrustManagerFactory {
            val providers = java.security.Security.getProviders()
            val provider = providers.firstOrNull {
                it.name != "IsrgRootTrust" && it.getService("TrustManagerFactory", algorithm) != null
            } ?: throw java.security.NoSuchAlgorithmException("No system provider found for TrustManagerFactory.$algorithm")
            
            return TrustManagerFactory.getInstance(algorithm, provider)
        }
    }

    init {
        val factoryClass = IsrgTrustManagerFactorySpi::class.java.name
        put("TrustManagerFactory.${TrustManagerFactory.getDefaultAlgorithm()}", factoryClass)
        put("TrustManagerFactory.PKIX", factoryClass)
    }

    /**
     * TrustManagerFactory implementation that returns trust managers
     * from Conscrypt but with ISRG Root X1 injected into the trust store.
     */
    class IsrgTrustManagerFactorySpi : TrustManagerFactorySpi() {
        private lateinit var delegate: TrustManagerFactory

        override fun engineInit(ks: KeyStore?) {
            val algorithm = TrustManagerFactory.getDefaultAlgorithm()
            delegate = getSystemTrustManagerFactory(algorithm)

            if (ks == null) {
                // OkHttp calls init(null) to get system defaults — inject ISRG Root X1
                delegate.init(mergedKeyStore)
            } else {
                // Explicit KeyStore provided — use as-is
                delegate.init(ks)
            }
        }

        override fun engineInit(spec: ManagerFactoryParameters?) {
            delegate = getSystemTrustManagerFactory(TrustManagerFactory.getDefaultAlgorithm())
            delegate.init(spec)
        }

        override fun engineGetTrustManagers(): Array<TrustManager> = delegate.trustManagers
    }
}
