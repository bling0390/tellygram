package tv.telegram.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal const val DownloadPageUrl = "https://tellygram.app/personal/"

internal sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck

    data class Available(val version: String, val notes: String) : UpdateCheck

    data class Failed(val kind: UpdateFailure) : UpdateCheck
}

internal enum class UpdateFailure { Network, Http, Parse }

private const val ReleasesUrl =
    "https://api.github.com/repos/bling0390/tellygram/releases/latest"

internal suspend fun fetchLatestRelease(currentVersion: String): UpdateCheck =
    withContext(Dispatchers.IO) {
        val body = try {
            val connection = (URL(ReleasesUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
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
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) {
            return@withContext UpdateCheck.UpToDate
        }
        val version = releaseVersion(json.optString("tag_name"))
            ?: return@withContext UpdateCheck.Failed(UpdateFailure.Parse)
        if (!isNewerVersion(version, currentVersion)) {
            return@withContext UpdateCheck.UpToDate
        }
        UpdateCheck.Available(version, plainChangelog(json.optString("body")))
    }
