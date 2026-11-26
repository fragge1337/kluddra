package app.kluddra.android.data.remote

import android.os.Build
import app.kluddra.android.data.prefs.KluddraPreferences
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class ApiException(
    val statusCode: Int,
    val code: String,
    override val message: String
) : Exception(message)

class AuthExpiredException(message: String = "Session expired") : Exception(message)

class KluddraApiClient(
    private val preferences: KluddraPreferences
) {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val refreshMutex = Mutex()

    private val client = HttpClient(OkHttp) {
        followRedirects = false
        engine {
            config {
                // Never follow HTTP/HTTPS redirects automatically: prevents credential/token
                // leakage or HTTPS->HTTP downgrade via malicious 301/302 responses.
                followRedirects(false)
                followSslRedirects(false)
                // Enforce TLS 1.2+ (MODERN_TLS) for all HTTPS connections; CLEARTEXT is strictly
                // gated below by our network interceptor to RFC1918/local hosts only.
                connectionSpecs(
                    listOf(
                        okhttp3.ConnectionSpec.MODERN_TLS,
                        okhttp3.ConnectionSpec.CLEARTEXT
                    )
                )
                // Network-level transport guard runs right before bytes hit the socket.
                addNetworkInterceptor { chain ->
                    val req = chain.request()
                    if (!req.isHttps && !UrlSecurityPolicy.isPrivateLocalHost(req.url.host)) {
                        throw java.io.IOException("Blocked cleartext HTTP request to public host: ${req.url.host}")
                    }
                    chain.proceed(req)
                }
            }
        }
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 25_000
            socketTimeoutMillis = 25_000
        }
    }

    fun deviceName(): String =
        listOfNotNull(Build.MANUFACTURER?.takeIf { it.isNotBlank() }, Build.MODEL?.takeIf { it.isNotBlank() })
            .joinToString(" ")
            .ifBlank { "Android" }

    suspend fun fetchServerInfo(baseUrl: String, allowLocalHttp: Boolean): ServerInfoDto {
        val validUrl = requireAllowedUrl(baseUrl, allowLocalHttp)
        val resp = client.get("$validUrl/api/v1/server-info")
        return parseOrThrow(resp)
    }

    suspend fun fetchCurrentServerInfo(): ServerInfoDto? {
        val creds = preferences.getCredentials() ?: return null
        return runCatching { fetchServerInfo(creds.serverUrl, creds.allowLocalHttp) }.getOrNull()
    }

    suspend fun completeSetup(
        baseUrl: String,
        allowLocalHttp: Boolean,
        setupCode: String,
        username: String,
        password: String
    ): AuthTokensResponseDto {
        val validUrl = requireAllowedUrl(baseUrl, allowLocalHttp)
        val resp = client.post("$validUrl/api/v1/auth/setup") {
            contentType(ContentType.Application.Json)
            setBody(SetupRequestDto(setupCode.trim(), username.trim(), password, deviceName()))
        }
        return parseOrThrow(resp)
    }

    suspend fun login(
        baseUrl: String,
        allowLocalHttp: Boolean,
        username: String,
        password: String
    ): AuthTokensResponseDto {
        val validUrl = requireAllowedUrl(baseUrl, allowLocalHttp)
        val resp = client.post("$validUrl/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequestDto(username.trim(), password, deviceName()))
        }
        return parseOrThrow(resp)
    }

    suspend fun logout() {
        val creds = preferences.getCredentials() ?: return
        val validUrl = runCatching { requireAllowedUrl(creds.serverUrl, creds.allowLocalHttp) }.getOrNull() ?: return
        runCatching {
            val resp = client.post("$validUrl/api/v1/auth/logout") {
                header(HttpHeaders.Authorization, "Bearer ${creds.accessToken}")
            }
            if (resp.status == HttpStatusCode.Unauthorized) {
                val freshAccess = refreshAccessToken(validUrl)
                client.post("$validUrl/api/v1/auth/logout") {
                    header(HttpHeaders.Authorization, "Bearer $freshAccess")
                }
            }
        }
    }

    suspend fun syncPull(sinceSeq: Long, limit: Int = 200): SyncPullResponseDto =
        authorizedRequest { baseUrl, token ->
            client.get("$baseUrl/api/v1/sync") {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("since", sinceSeq)
                parameter("limit", limit)
            }
        }

    suspend fun syncPush(request: SyncPushRequestDto): SyncPushResponseDto =
        authorizedRequest { baseUrl, token ->
            client.post("$baseUrl/api/v1/sync") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    suspend fun listSessions(): List<SessionInfoDto> =
        authorizedRequest<SessionsResponseDto> { baseUrl, token ->
            client.get("$baseUrl/api/v1/auth/sessions") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }.sessions

    suspend fun revokeSession(sessionId: String) {
        authorizedEmptyRequest { baseUrl, token ->
            client.delete("$baseUrl/api/v1/auth/sessions/$sessionId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }
    }

    suspend fun revokeOtherSessions(): Int =
        authorizedRequest<RevokeOthersResponseDto> { baseUrl, token ->
            client.post("$baseUrl/api/v1/auth/sessions/revoke-others") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }.revokedCount

    private suspend inline fun <reified T> authorizedRequest(
        crossinline block: suspend (baseUrl: String, accessToken: String) -> HttpResponse
    ): T {
        val creds = preferences.getCredentials() ?: throw AuthExpiredException()
        val baseUrl = requireAllowedUrl(creds.serverUrl, creds.allowLocalHttp)

        var response = block(baseUrl, creds.accessToken)
        if (response.status == HttpStatusCode.Unauthorized) {
            val newAccess = refreshAccessToken(baseUrl)
            response = block(baseUrl, newAccess)
            if (response.status == HttpStatusCode.Unauthorized) {
                throw AuthExpiredException()
            }
        }
        return parseOrThrow(response)
    }

    private suspend fun authorizedEmptyRequest(
        block: suspend (baseUrl: String, accessToken: String) -> HttpResponse
    ) {
        val creds = preferences.getCredentials() ?: throw AuthExpiredException()
        val baseUrl = requireAllowedUrl(creds.serverUrl, creds.allowLocalHttp)

        var response = block(baseUrl, creds.accessToken)
        if (response.status == HttpStatusCode.Unauthorized) {
            val newAccess = refreshAccessToken(baseUrl)
            response = block(baseUrl, newAccess)
            if (response.status == HttpStatusCode.Unauthorized) {
                throw AuthExpiredException()
            }
        }
        if (response.status.value !in 200..299) {
            val text = runCatching { response.bodyAsText() }.getOrDefault("")
            val parsed = runCatching { json.decodeFromString<ApiErrorDto>(text) }.getOrNull()
            throw ApiException(
                statusCode = response.status.value,
                code = parsed?.code ?: "HTTP_${response.status.value}",
                message = parsed?.message ?: response.status.description
            )
        }
    }

    private suspend fun refreshAccessToken(baseUrl: String): String = refreshMutex.withLock {
        val creds = preferences.getCredentials() ?: throw AuthExpiredException()
        val resp = client.post("$baseUrl/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequestDto(creds.refreshToken, deviceName()))
        }
        if (resp.status == HttpStatusCode.Unauthorized) {
            throw AuthExpiredException()
        }
        val tokens: AuthTokensResponseDto = parseOrThrow(resp)
        preferences.updateTokens(tokens.accessToken, tokens.refreshToken, tokens.sessionId)
        tokens.accessToken
    }

    private fun requireAllowedUrl(url: String, allowLocalHttp: Boolean): String {
        return when (val result = UrlSecurityPolicy.evaluate(url, allowLocalHttp)) {
            is UrlValidationResult.Valid -> result.normalizedUrl
            UrlValidationResult.PublicHttpBlocked -> throw ApiException(0, "PUBLIC_HTTP_BLOCKED", "HTTPS is required for public servers")
            is UrlValidationResult.RequiresLocalHttpConsent -> throw ApiException(0, "LOCAL_HTTP_CONSENT_REQUIRED", "Local HTTP requires explicit consent")
            UrlValidationResult.Invalid -> throw ApiException(0, "INVALID_URL", "Invalid server address")
        }
    }

    private suspend inline fun <reified T> parseOrThrow(response: HttpResponse): T {
        if (response.status.value in 200..299) {
            return response.body()
        }
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        val parsed = runCatching { json.decodeFromString<ApiErrorDto>(text) }.getOrNull()
        throw ApiException(
            statusCode = response.status.value,
            code = parsed?.code ?: "HTTP_${response.status.value}",
            message = parsed?.message ?: response.status.description
        )
    }
}
