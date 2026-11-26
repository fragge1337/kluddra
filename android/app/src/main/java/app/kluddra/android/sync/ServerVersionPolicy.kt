package app.kluddra.android.sync

data class ServerVersionCheck(
    val serverVersion: String?,
    val minRecommendedVersion: String,
    val isOutdated: Boolean
)

/**
 * Pure local SemVer & API compatibility check (zero external network calls).
 * Flags the connected Kluddra Server as outdated when its version or API level falls
 * behind what this Android app expects.
 */
object ServerVersionPolicy {
    const val MIN_RECOMMENDED_SERVER_VERSION = "1.0.0"
    const val MIN_API_VERSION = 1

    private val semverRegex = Regex("""^v?(\d+)\.(\d+)(?:\.(\d+))?(?:-.*)?$""")

    data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {
        override fun compareTo(other: SemVer): Int {
            if (major != other.major) return major.compareTo(other.major)
            if (minor != other.minor) return minor.compareTo(other.minor)
            return patch.compareTo(other.patch)
        }
    }

    fun parseSemVer(raw: String?): SemVer? {
        val cleaned = raw?.trim().orEmpty()
        if (cleaned.isEmpty()) return null
        val match = semverRegex.matchEntire(cleaned) ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: return null
        val patch = match.groupValues[3].toIntOrNull() ?: 0
        return SemVer(major, minor, patch)
    }

    fun evaluate(
        serverVersion: String?,
        serverApiVersion: String? = null,
        minRecommended: String = MIN_RECOMMENDED_SERVER_VERSION
    ): ServerVersionCheck {
        val apiNum = serverApiVersion?.trim()?.toIntOrNull()
        val apiOutdated = apiNum != null && apiNum < MIN_API_VERSION

        val parsedServer = parseSemVer(serverVersion)
        val parsedMin = parseSemVer(minRecommended)
        val versionOutdated = parsedServer != null && parsedMin != null && parsedServer < parsedMin

        return ServerVersionCheck(
            serverVersion = serverVersion?.trim()?.takeIf { it.isNotEmpty() },
            minRecommendedVersion = minRecommended,
            isOutdated = apiOutdated || versionOutdated
        )
    }
}
