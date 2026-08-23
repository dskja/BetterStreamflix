package com.betterstreamflix.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

/**
 * Sealed class representing classified network errors with user-friendly German messages.
 */
sealed class NetworkError {
    abstract fun getUserMessage(context: Context): String

    object NoConnection : NetworkError() {
        override fun getUserMessage(context: Context): String =
            "Keine Internetverbindung. Bitte überprüfe deine Verbindung."
    }

    object Timeout : NetworkError() {
        override fun getUserMessage(context: Context): String =
            "Zeitüberschreitung. Der Server braucht zu lange zum Antworten."
    }

    object DnsFailure : NetworkError() {
        override fun getUserMessage(context: Context): String =
            "Domain konnte nicht aufgelöst werden. Der Provider ist möglicherweise offline."
    }

    object SslError : NetworkError() {
        override fun getUserMessage(context: Context): String =
            "Sicherheitsfehler bei der Verbindung. Der Provider hat ein Zertifikatsproblem."
    }

    data class HttpError(val code: Int) : NetworkError() {
        override fun getUserMessage(context: Context): String = when (code) {
            in 400..499 -> "Fehler $code: Der Server hat die Anfrage abgelehnt."
            in 500..599 -> "Fehler $code: Der Server hat ein Problem. Versuche es später erneut."
            else -> "Netzwerkfehler (Code $code)."
        }
    }

    object ProviderBlocked : NetworkError() {
        override fun getUserMessage(context: Context): String =
            "Der Provider ist nicht erreichbar. Möglicherweise wurde die Seite gesperrt."
    }

    data class Unknown(val message: String) : NetworkError() {
        override fun getUserMessage(context: Context): String =
            "Ein unerwarteter Fehler ist aufgetreten: $message"
    }

    companion object {
        /**
         * Classify a throwable into a NetworkError type.
         */
        fun from(throwable: Throwable): NetworkError {
            return when (throwable) {
                is SocketTimeoutException -> Timeout
                is UnknownHostException -> DnsFailure
                is SSLHandshakeException -> SslError
                is SSLException -> SslError
                else -> {
                    val message = throwable.message.orEmpty()
                    when {
                        message.contains("timeout", ignoreCase = true) -> Timeout
                        message.contains("unable to resolve host", ignoreCase = true) -> DnsFailure
                        message.contains("Connection refused", ignoreCase = true) -> ProviderBlocked
                        message.contains("403", ignoreCase = true) -> HttpError(403)
                        message.contains("404", ignoreCase = true) -> HttpError(404)
                        message.contains("500", ignoreCase = true) -> HttpError(500)
                        message.contains("502", ignoreCase = true) -> HttpError(502)
                        message.contains("503", ignoreCase = true) -> HttpError(503)
                        else -> Unknown(message.ifEmpty { throwable::class.java.simpleName })
                    }
                }
            }
        }

        /**
         * Check if the device currently has an active network connection.
         */
        fun isOnline(context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return false
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }
}
