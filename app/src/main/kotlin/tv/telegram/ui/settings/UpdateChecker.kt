package tv.telegram.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal const val DownloadPageUrl = "https://tellygram.app/personal/"

/** What the settings screen learns from one check. */
internal sealed interface UpdateCheck {
    /** The release is not newer than what is installed. */
    data object UpToDate : UpdateCheck

    /** A newer release: [notes] are the release body with its Markdown reduced to text. */
    data class Available(val version: String, val notes: String) : UpdateCheck

    data class Failed(val kind: UpdateFailure) : UpdateCheck
}

internal enum class UpdateFailure { Network, Http, Parse }

private const val ReleasesUrl =
    "https://api.github.com/repos/bling0390/tellygram/releases/latest"

/**
 * One unauthenticated GET against the public repository's latest release. GitHub allows 60 of
 * these per hour per IP, which is plenty for a manual "Check for updates".
 *
 * Only the fields the dialog needs are used (`tag_name`, `body`, plus the draft/prerelease
 * guards); the repository currently publishes no assets, and the download stays on the site.
 */
internal suspend fun fetchLatestRelease(currentVersion: String): UpdateCheck =
    withContext(Dispatchers.IO) {
        val body = try {
            val connection = (URL(ReleasesUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                // GitHub rejects requests that carry no User-Agent.
                setRequestProperty("User-Agent", "tellygram")
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext UpdateCheck.Failed(UpdateFailure.Http)
                }
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            return@withContext UpdateCheck.Failed(UpdateFailure.Network)
        }

        val json = try {
            JSONObject(body)
        } catch (e: Exception) {
            return@withContext UpdateCheck.Failed(UpdateFailure.Parse)
        }
        // /releases/latest already skips drafts and prereleases; this guards a future flip.
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) {
            return@withContext UpdateCheck.UpToDate
        }
        val version = releaseVersion(json.optString("tag_name"))
            ?: return@withContext UpdateCheck.Failed(UpdateFailure.Parse)
        // upstream and counts segments numerically, so 1.0.100 beats 1.0.99.
        if (!isNewerVersion(version, currentVersion)) {
            return@withContext UpdateCheck.UpToDate
        }
        UpdateCheck.Available(version, plainChangelog(json.optString("body")))
    }
