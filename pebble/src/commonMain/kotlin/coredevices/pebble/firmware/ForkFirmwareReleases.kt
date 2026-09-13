package coredevices.pebble.firmware

import co.touchlab.kermit.Logger
import coredevices.pebble.services.ensureVersionPrefix
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.ContentConvertException
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import io.rebble.libpebblecommon.services.FirmwareVersion
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

object ForkFirmwareSource {
    const val NAME = "Peblum"
    const val GITHUB_OWNER = "peblum"
    const val GITHUB_REPO = "Peblum"
}

data class ForkFirmwareRelease(
    val version: FirmwareVersion,
    val url: String,
    val notes: String,
)

interface ForkFirmwareReleases {
    suspend fun latestFor(platform: WatchHardwarePlatform): ForkFirmwareRelease?
}

class GithubForkFirmwareReleases(
    private val httpClient: HttpClient,
    private val clock: Clock,
) : ForkFirmwareReleases {
    private val logger = Logger.withTag("ForkFirmwareReleases")
    private val mutex = Mutex()
    private var cached: CachedFetch? = null

    private data class CachedFetch(
        val releases: List<GithubRelease>?,
        val expiresAt: Instant,
    )

    override suspend fun latestFor(platform: WatchHardwarePlatform): ForkFirmwareRelease? {
        val releases = fetchReleases() ?: return null
        return selectForkFirmware(releases, platform)
    }

    private suspend fun fetchReleases(): List<GithubRelease>? = mutex.withLock {
        val now = clock.now()
        cached?.takeIf { it.expiresAt > now }?.let { return it.releases }
        val releases = doFetch()
        cached = CachedFetch(releases, now + FETCH_TTL)
        releases
    }

    private suspend fun doFetch(): List<GithubRelease>? {
        val url = "https://api.github.com/repos/" +
                "${ForkFirmwareSource.GITHUB_OWNER}/${ForkFirmwareSource.GITHUB_REPO}/releases"
        val response = try {
            httpClient.get(url) {
                parameter("per_page", RELEASES_PAGE_SIZE)
            }
        } catch (e: IOException) {
            logger.w(e) { "Error fetching fork firmware releases: ${e.message}" }
            return null
        }
        if (response.status != HttpStatusCode.OK) {
            logger.w { "Error fetching fork firmware releases: ${response.status}" }
            return null
        }
        return try {
            response.body<List<GithubRelease>>()
        } catch (e: IOException) {
            logger.w(e) { "Error reading fork firmware releases: ${e.message}" }
            null
        } catch (e: NoTransformationFoundException) {
            logger.w(e) { "Error parsing fork firmware releases: ${e.message}" }
            null
        } catch (e: ContentConvertException) {
            logger.w(e) { "Error parsing fork firmware releases: ${e.message}" }
            null
        }
    }

    companion object {
        private val FETCH_TTL: Duration = 15.minutes
        private const val RELEASES_PAGE_SIZE = 30
    }
}

@Serializable
data class GithubRelease(
    @SerialName("tag_name")
    val tagName: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val body: String? = null,
    val assets: List<GithubReleaseAsset> = emptyList(),
)

@Serializable
data class GithubReleaseAsset(
    val name: String,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String,
)

fun selectForkFirmware(
    releases: List<GithubRelease>,
    platform: WatchHardwarePlatform,
): ForkFirmwareRelease? = releases.asSequence()
    .filterNot { it.draft || it.prerelease }
    .mapNotNull { it.forkFirmwareFor(platform) }
    .firstOrNull()

private fun GithubRelease.forkFirmwareFor(platform: WatchHardwarePlatform): ForkFirmwareRelease? {
    val assetName = "normal_${platform.revision}_${ensureVersionPrefix(tagName)}.pbz"
    val asset = assets.firstOrNull { it.name == assetName } ?: return null
    val version = FirmwareVersion.from(
        tag = tagName,
        isRecovery = false,
        gitHash = "",
        timestamp = Instant.DISTANT_PAST,
        isDualSlot = false,
        isSlot0 = false,
    ) ?: return null
    return ForkFirmwareRelease(
        version = version,
        url = asset.browserDownloadUrl,
        notes = body.orEmpty(),
    )
}
