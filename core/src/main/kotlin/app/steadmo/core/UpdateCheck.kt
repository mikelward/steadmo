package app.steadmo.core

/**
 * Checks whether GitHub Releases has a newer Steadmo build than the installed one.
 *
 * GitHub's `/releases/latest` responds with a 302 redirect whose `Location` ends in
 * `/releases/tag/v<versionCode>` (matching `scripts/publish-apk-release.sh`), so a
 * single HEAD request without following redirects avoids API rate limits and downloads
 * no response body.
 */
object UpdateCheck {
    const val LATEST_RELEASE_URL = "https://github.com/mikelward/steadmo/releases/latest"

    private val TAG_PATTERN = Regex("""(?:^|/)releases/tag/v([0-9]+)$""")

    /**
     * Extracts the positive `versionCode` from a GitHub `/releases/latest` redirect
     * `Location` header (full URL or path), or returns `null` if none is present.
     */
    fun parseVersionCode(locationHeader: String?): Int? {
        if (locationHeader.isNullOrBlank()) return null
        val path = locationHeader.trim()
            .substringBefore('#')
            .substringBefore('?')
            .trimEnd('/')
        val match = TAG_PATTERN.find(path) ?: return null
        val code = match.groupValues[1].toIntOrNull() ?: return null
        return code.takeIf { it > 0 }
    }

    /** Returns true when [locationHeader] points to a release newer than [currentVersionCode]. */
    fun isUpdateAvailable(currentVersionCode: Int, locationHeader: String?): Boolean {
        val latest = parseVersionCode(locationHeader) ?: return false
        return latest > currentVersionCode
    }
}
