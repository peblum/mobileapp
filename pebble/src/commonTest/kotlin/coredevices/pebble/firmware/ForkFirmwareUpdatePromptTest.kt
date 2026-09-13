package coredevices.pebble.firmware

import com.russhwolf.settings.MapSettings
import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import io.rebble.libpebblecommon.connection.PebbleSocketIdentifier
import io.rebble.libpebblecommon.metadata.WatchColor
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import io.rebble.libpebblecommon.services.FirmwareVersion
import io.rebble.libpebblecommon.services.WatchInfo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ForkFirmwareUpdatePromptTest {
    private class FakeReleases : ForkFirmwareReleases {
        var release: ForkFirmwareRelease? = null
        override suspend fun latestFor(platform: WatchHardwarePlatform) = release
    }

    private val settings = MapSettings()
    private val releases = FakeReleases()
    private val startedUpdates =
        mutableListOf<Pair<PebbleIdentifier, FirmwareUpdateCheckResult.FoundUpdate>>()
    private val prompt = RealForkFirmwareUpdatePrompt(settings, releases) { identifier, update ->
        startedUpdates.add(identifier to update)
    }

    private val identifier = PebbleSocketIdentifier("watch-1")

    private fun firmwareVersion(tag: String, isRecovery: Boolean = false) = FirmwareVersion.from(
        tag = tag,
        isRecovery = isRecovery,
        gitHash = "",
        timestamp = Instant.DISTANT_PAST,
        isDualSlot = false,
        isSlot0 = false,
    )!!

    private fun watchInfo(
        runningVersion: String = "v4.36.2",
        isRecovery: Boolean = false,
        platform: WatchHardwarePlatform = WatchHardwarePlatform.CORE_ASTERIX,
        serial: String = "SERIAL1",
    ) = WatchInfo(
        runningFwVersion = firmwareVersion(runningVersion, isRecovery),
        recoveryFwVersion = null,
        platform = platform,
        bootloaderTimestamp = Instant.DISTANT_PAST,
        board = "board",
        serial = serial,
        btAddress = "00:11:22:33:44:55",
        resourceCrc = 0,
        resourceTimestamp = Instant.DISTANT_PAST,
        language = "en_US",
        languageVersion = 2,
        capabilities = emptySet(),
        isUnfaithful = false,
        healthInsightsVersion = null,
        javascriptVersion = null,
        color = WatchColor.ClassicFlyBlue,
    )

    private fun forkRelease(tag: String = "v4.36.9") = ForkFirmwareRelease(
        version = firmwareVersion(tag),
        url = "https://example.com/normal_asterix_$tag.pbz",
        notes = "Notes",
    )

    private suspend fun maybeOffer(
        watchInfo: WatchInfo = watchInfo(),
        updateInProgress: Boolean = false,
    ) = prompt.maybeOffer(identifier, "Watch 1", watchInfo, updateInProgress)

    @Test
    fun offersWhenRunningDifferentVersion() = runTest {
        releases.release = forkRelease()
        maybeOffer()
        val offer = prompt.pendingOffer.value
        assertEquals("v4.36.9", offer?.release?.version?.stringVersion)
        assertEquals("Watch 1", offer?.watchName)
    }

    @Test
    fun noOfferWhenNoReleaseAvailable() = runTest {
        maybeOffer()
        assertNull(prompt.pendingOffer.value)
    }

    @Test
    fun noOfferWhenAlreadyRunningReleaseVersion() = runTest {
        releases.release = forkRelease("v4.36.9")
        maybeOffer(watchInfo(runningVersion = "4.36.9"))
        assertNull(prompt.pendingOffer.value)
    }

    @Test
    fun noOfferInRecovery() = runTest {
        releases.release = forkRelease()
        maybeOffer(watchInfo(isRecovery = true))
        assertNull(prompt.pendingOffer.value)
    }

    @Test
    fun noOfferWhileUpdateInProgress() = runTest {
        releases.release = forkRelease()
        maybeOffer(updateInProgress = true)
        assertNull(prompt.pendingOffer.value)
    }

    @Test
    fun acceptStartsUpdateWithReleaseArtifact() = runTest {
        releases.release = forkRelease()
        maybeOffer()
        prompt.accept()
        assertNull(prompt.pendingOffer.value)
        val (startedFor, update) = startedUpdates.single()
        assertEquals(identifier, startedFor)
        assertEquals("https://example.com/normal_asterix_v4.36.9.pbz", update.url)
        assertTrue(update.canDowngrade)
    }

    @Test
    fun declineIsRememberedAcrossInstances() = runTest {
        releases.release = forkRelease()
        maybeOffer()
        prompt.decline()
        assertNull(prompt.pendingOffer.value)

        val newPrompt = RealForkFirmwareUpdatePrompt(settings, releases) { _, _ -> }
        newPrompt.maybeOffer(identifier, "Watch 1", watchInfo(), updateInProgress = false)
        assertNull(newPrompt.pendingOffer.value)
    }

    @Test
    fun dismissClearsOfferWithoutPersistingDecline() = runTest {
        releases.release = forkRelease()
        maybeOffer()
        prompt.dismiss()
        assertNull(prompt.pendingOffer.value)
        maybeOffer()
        assertNull(prompt.pendingOffer.value)

        val newPrompt = RealForkFirmwareUpdatePrompt(settings, releases) { _, _ -> }
        newPrompt.maybeOffer(identifier, "Watch 1", watchInfo(), updateInProgress = false)
        assertEquals(
            "v4.36.9",
            newPrompt.pendingOffer.value?.release?.version?.stringVersion,
        )
    }

    @Test
    fun doesNotReofferSameVersionInSameSession() = runTest {
        releases.release = forkRelease()
        maybeOffer()
        prompt.decline()
        maybeOffer()
        assertNull(prompt.pendingOffer.value)
    }

    @Test
    fun offersAgainForNewerVersionAfterDecline() = runTest {
        releases.release = forkRelease("v4.36.9")
        maybeOffer()
        prompt.decline()
        releases.release = forkRelease("v4.37.0")
        maybeOffer()
        assertEquals(
            "v4.37.0",
            prompt.pendingOffer.value?.release?.version?.stringVersion,
        )
    }

    @Test
    fun onlyOnePendingOfferAtATime() = runTest {
        releases.release = forkRelease()
        maybeOffer()
        val firstOffer = prompt.pendingOffer.value
        maybeOffer(watchInfo(serial = "SERIAL2"))
        assertEquals(firstOffer, prompt.pendingOffer.value)
    }
}
