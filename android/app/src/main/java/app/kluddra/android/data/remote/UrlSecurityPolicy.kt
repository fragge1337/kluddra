package app.kluddra.android.data.remote

import java.net.URI

sealed interface UrlValidationResult {
    data class Valid(val normalizedUrl: String, val isLocalHttp: Boolean) : UrlValidationResult
    data class RequiresLocalHttpConsent(val normalizedUrl: String, val host: String) : UrlValidationResult
    data object PublicHttpBlocked : UrlValidationResult
    data object Invalid : UrlValidationResult
}

/**
 * Enforces Kluddra's transport security policy:
 * - HTTPS is always preferred and mandatory for public hosts/IPs.
 * - Cleartext HTTP is strictly limited to private RFC1918 / localhost / .local addresses
 *   and requires explicit user consent.
 */
object UrlSecurityPolicy {
    fun evaluate(rawInput: String, allowLocalHttpConsent: Boolean): UrlValidationResult {
        val trimmed = rawInput.trim().trimEnd('/')
        if (trimmed.isBlank()) return UrlValidationResult.Invalid

        val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }

        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return UrlValidationResult.Invalid
        if (uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null) {
            return UrlValidationResult.Invalid
        }
        val scheme = uri.scheme?.lowercase() ?: return UrlValidationResult.Invalid
        val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() } ?: return UrlValidationResult.Invalid
        if (uri.port < -1 || uri.port > 65535) return UrlValidationResult.Invalid

        val normalized = buildString {
            append(scheme).append("://").append(host)
            if (uri.port != -1) append(':').append(uri.port)
            val path = uri.rawPath?.trimEnd('/').orEmpty()
            if (path.isNotEmpty()) append(path)
        }

        return when (scheme) {
            "https" -> UrlValidationResult.Valid(normalizedUrl = normalized, isLocalHttp = false)
            "http" -> {
                if (!isPrivateLocalHost(host)) {
                    UrlValidationResult.PublicHttpBlocked
                } else if (!allowLocalHttpConsent) {
                    UrlValidationResult.RequiresLocalHttpConsent(normalizedUrl = normalized, host = host)
                } else {
                    UrlValidationResult.Valid(normalizedUrl = normalized, isLocalHttp = true)
                }
            }
            else -> UrlValidationResult.Invalid
        }
    }

    fun isPrivateLocalHost(host: String): Boolean {
        val h = host.lowercase().trim('[', ']')
        if (h == "localhost" || h == "127.0.0.1" || h == "::1" || h == "10.0.2.2") return true
        if (h.endsWith(".local") || h.endsWith(".home.arpa") || h.endsWith(".lan")) return true

        val parts = h.split('.')
        if (parts.size == 4) {
            val octets = parts.map { it.toIntOrNull() ?: return false }
            if (octets.any { it !in 0..255 }) return false
            val (a, b) = octets[0] to octets[1]
            if (a == 10) return true
            if (a == 192 && b == 168) return true
            if (a == 172 && b in 16..31) return true
        }
        return false
    }
}
