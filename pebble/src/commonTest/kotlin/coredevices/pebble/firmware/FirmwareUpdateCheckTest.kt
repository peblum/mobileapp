package coredevices.pebble.firmware

import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.metadata.WatchColor
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import io.rebble.libpebblecommon.services.FirmwareVersion
import io.rebble.libpebblecommon.services.WatchInfo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class FirmwareUpdateCheckTest {
    @Test
    fun firmwareServiceSelectionRequiresACoreWatchAndToken() {
        assertTrue(
            shouldUseMemfaultForFirmwareUpdates(
                platform = WatchHardwarePlatform.CORE_ASTERIX,
                memfaultToken = "project-key",
            )
        )
        assertFalse(
            shouldUseMemfaultForFirmwareUpdates(
                platform = WatchHardwarePlatform.CORE_ASTERIX,
                memfaultToken = null,
            )
        )
        assertFalse(
            shouldUseMemfaultForFirmwareUpdates(
                platform = WatchHardwarePlatform.PEBBLE_SILK,
                memfaultToken = "project-key",
            )
        )
    }

    @Test
    fun skippedCheckPreservesPreviouslyFoundUpdate() {
        val update = foundUpdate()
        val now = Instant.fromEpochSeconds(10)
        assertSame(
            update,
            firmwareUpdateResultWhenSkipped(
                cachedResult = update,
                cacheExpiresAt = now + 15.minutes,
                now = now,
            ),
        )
        assertSame(
            FirmwareUpdateCheckResult.FoundNoUpdate,
            firmwareUpdateResultWhenSkipped(
                cachedResult = update,
                cacheExpiresAt = now,
                now = now,
            ),
        )
        assertSame(
            FirmwareUpdateCheckResult.FoundNoUpdate,
            firmwareUpdateResultWhenSkipped(
                cachedResult = null,
                cacheExpiresAt = null,
                now = now,
            ),
        )
    }

    @Test
    fun skippedCheckDoesNotServeAnExpiredManualResult() = runTest {
        val clock = MutableClock(Instant.fromEpochSeconds(10))
        val update = foundUpdate()
        var checkCount = 0
        val checker = FirmwareUpdateCheck(
            getLatestFirmware = {
                checkCount++
                update
            },
            clock = clock,
            automaticChecksAllowed = false,
        )
        val watch = watchInfo()

        assertSame(update, checker.checkForUpdates(watch, force = true))
        clock.current += 14.minutes
        assertSame(update, checker.checkForUpdates(watch, force = false))
        clock.current += 2.minutes
        assertSame(
            FirmwareUpdateCheckResult.FoundNoUpdate,
            checker.checkForUpdates(watch, force = false),
        )
        assertEquals(1, checkCount)
    }
}

private fun foundUpdate() = FirmwareUpdateCheckResult.FoundUpdate(
    version = FirmwareVersion(
        stringVersion = "1.2.3",
        timestamp = Instant.fromEpochSeconds(1),
        major = 1,
        minor = 2,
        patch = 3,
        suffix = null,
        gitHash = "abc",
        isRecovery = false,
        isDualSlot = false,
        isSlot0 = false,
    ),
    url = "https://example.invalid/update.pbz",
    notes = "",
)

private fun watchInfo() = WatchInfo(
    runningFwVersion = FirmwareVersion(
        stringVersion = "1.0.0",
        timestamp = Instant.fromEpochSeconds(1),
        major = 1,
        minor = 0,
        patch = 0,
        suffix = null,
        gitHash = "abc",
        isRecovery = false,
        isDualSlot = false,
        isSlot0 = false,
    ),
    recoveryFwVersion = FirmwareVersion(
        stringVersion = "1.0.0",
        timestamp = Instant.fromEpochSeconds(1),
        major = 1,
        minor = 0,
        patch = 0,
        suffix = null,
        gitHash = "abc",
        isRecovery = true,
        isDualSlot = false,
        isSlot0 = false,
    ),
    platform = WatchHardwarePlatform.CORE_ASTERIX,
    bootloaderTimestamp = Instant.fromEpochSeconds(1),
    board = "asterix",
    serial = "TEST12345678",
    btAddress = "00:11:22:33:44:55",
    resourceCrc = 0,
    resourceTimestamp = Instant.fromEpochSeconds(1),
    language = "en_US",
    languageVersion = 1,
    capabilities = emptySet(),
    isUnfaithful = false,
    healthInsightsVersion = 1,
    javascriptVersion = 1,
    color = WatchColor.ClassicFlyBlue,
)

private class MutableClock(
    var current: Instant,
) : Clock {
    override fun now(): Instant = current
}
