package coredevices.pebble.firmware

import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ForkFirmwareReleasesTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun release(
        tag: String,
        vararg assetNames: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
        body: String? = null,
    ) = GithubRelease(
        tagName = tag,
        draft = draft,
        prerelease = prerelease,
        body = body,
        assets = assetNames.map { GithubReleaseAsset(it, "https://example.com/$it") },
    )

    @Test
    fun picksAssetMatchingPlatformRevision() {
        val releases = listOf(
            release(
                "v4.36.2",
                "normal_asterix_v4.36.2.pbz",
                "normal_obelix_pvt_v4.36.2.pbz",
                "normal_obelix_pvt_v4.36.2_slot0.pbz",
                "normal_obelix_pvt_v4.36.2_slot1.pbz",
                "recovery_obelix_pvt_v4.36.2.pbz",
                body = "Notes",
            ),
        )
        val selected = selectForkFirmware(releases, WatchHardwarePlatform.CORE_OBELIX_PVT)
        assertEquals("https://example.com/normal_obelix_pvt_v4.36.2.pbz", selected?.url)
        assertEquals("v4.36.2", selected?.version?.stringVersion)
        assertEquals("Notes", selected?.notes)
    }

    @Test
    fun returnsNullWhenNoAssetForPlatform() {
        val releases = listOf(release("v4.36.2", "normal_asterix_v4.36.2.pbz"))
        assertNull(selectForkFirmware(releases, WatchHardwarePlatform.CORE_OBELIX_PVT))
    }

    @Test
    fun returnsNullWhenNoReleases() {
        assertNull(selectForkFirmware(emptyList(), WatchHardwarePlatform.CORE_ASTERIX))
    }

    @Test
    fun skipsDraftsAndPrereleases() {
        val releases = listOf(
            release("v4.36.3", "normal_asterix_v4.36.3.pbz", draft = true),
            release("v4.36.2", "normal_asterix_v4.36.2.pbz", prerelease = true),
            release("v4.36.1", "normal_asterix_v4.36.1.pbz"),
        )
        val selected = selectForkFirmware(releases, WatchHardwarePlatform.CORE_ASTERIX)
        assertEquals("v4.36.1", selected?.version?.stringVersion)
    }

    @Test
    fun skipsReleasesWithoutFirmwareAssets() {
        val releases = listOf(
            release("pr-assets", "battery-series.csv", "gauge-soc.png"),
            release("v4.36.1", "normal_asterix_v4.36.1.pbz"),
        )
        val selected = selectForkFirmware(releases, WatchHardwarePlatform.CORE_ASTERIX)
        assertEquals("v4.36.1", selected?.version?.stringVersion)
    }

    @Test
    fun picksNewestMatchingRelease() {
        val releases = listOf(
            release("v4.36.2", "normal_asterix_v4.36.2.pbz"),
            release("v4.36.1", "normal_asterix_v4.36.1.pbz"),
        )
        val selected = selectForkFirmware(releases, WatchHardwarePlatform.CORE_ASTERIX)
        assertEquals("v4.36.2", selected?.version?.stringVersion)
    }

    @Test
    fun matchesAssetForTagWithoutVersionPrefix() {
        val releases = listOf(release("4.36.2", "normal_asterix_v4.36.2.pbz"))
        val selected = selectForkFirmware(releases, WatchHardwarePlatform.CORE_ASTERIX)
        assertEquals("https://example.com/normal_asterix_v4.36.2.pbz", selected?.url)
    }

    @Test
    fun parsesGithubReleasesJson() {
        val payload = """
            [
              {
                "url": "https://api.github.com/repos/peblum/Peblum/releases/1",
                "tag_name": "v4.36.2",
                "name": "v4.36.2",
                "draft": false,
                "prerelease": false,
                "body": "Release notes",
                "assets": [
                  {
                    "name": "normal_asterix_v4.36.2.pbz",
                    "browser_download_url": "https://github.com/peblum/Peblum/releases/download/v4.36.2/normal_asterix_v4.36.2.pbz",
                    "size": 123456
                  }
                ]
              }
            ]
        """.trimIndent()
        val releases = json.decodeFromString<List<GithubRelease>>(payload)
        val selected = selectForkFirmware(releases, WatchHardwarePlatform.CORE_ASTERIX)
        assertEquals(
            "https://github.com/peblum/Peblum/releases/download/v4.36.2/normal_asterix_v4.36.2.pbz",
            selected?.url,
        )
        assertEquals("Release notes", selected?.notes)
    }
}
