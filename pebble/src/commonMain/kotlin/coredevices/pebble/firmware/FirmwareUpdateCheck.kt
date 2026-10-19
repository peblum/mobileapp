package coredevices.pebble.firmware

import co.touchlab.kermit.Logger
import coredevices.analytics.CoreAnalytics
import coredevices.pebble.services.EngDashOta
import coredevices.pebble.services.Memfault
import coredevices.util.CommonBuildKonfig
import coredevices.util.CoreConfigFlow
import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform.*
import io.rebble.libpebblecommon.services.WatchInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class FirmwareUpdateCheck internal constructor(
    private val getLatestFirmware: suspend (WatchInfo) -> FirmwareUpdateCheckResult,
    private val clock: Clock,
    private val automaticChecksAllowed: Boolean,
) {
    constructor(
        memfault: Memfault,
        engDashOta: EngDashOta,
        cohorts: Cohorts,
        coreConfig: CoreConfigFlow,
        coreAnalytics: CoreAnalytics,
        clock: Clock = Clock.System,
    ) : this(
        getLatestFirmware = { watch ->
            when {
                watch.platform == UNKNOWN ->
                    FirmwareUpdateCheckResult.UpdateCheckFailed("Unknown platform")
                watch.platform.isCoreDevice() -> coreDeviceFirmwareCheck(
                    watch = watch,
                    memfault = memfault,
                    engDashOta = engDashOta,
                    cohorts = cohorts,
                    coreConfig = coreConfig,
                    coreAnalytics = coreAnalytics,
                )
                else -> cohorts.getLatestFirmware(watch)
            }
        },
        clock = clock,
        automaticChecksAllowed = !CommonBuildKonfig.FDROID_BUILD,
    )

    private val logger = Logger.withTag("FirmwareUpdateCheck")

    private data class CacheKey(
        val platform: WatchHardwarePlatform,
        val serial: String,
    )

    /**
     * One entry per watch: the running version is an input to the check, so an entry only answers
     * for the version it was fetched for, and a version change evicts it rather than shadowing it.
     */
    private data class CacheEntry(
        val fwVersion: String,
        val isRecovery: Boolean,
        val result: FirmwareUpdateCheckResult,
        val expiresAt: Instant,
    )

    private val mutex = Mutex()
    private val cache = mutableMapOf<CacheKey, CacheEntry>()

    suspend fun checkForUpdates(watch: WatchInfo, force: Boolean): FirmwareUpdateCheckResult {
        val key = CacheKey(platform = watch.platform, serial = watch.serial)
        val fwVersion = watch.runningFwVersion.stringVersion
        val isRecovery = watch.runningFwVersion.isRecovery
        val now = clock.now()
        if (!automaticChecksAllowed && !force) {
            logger.v { "Skipping automatic firmware update check in F-Droid build" }
            return mutex.withLock {
                val cached = cache[key]
                    ?.takeIf { it.fwVersion == fwVersion && it.isRecovery == isRecovery }
                if (cached != null && cached.expiresAt <= now) {
                    cache.remove(key)
                }
                firmwareUpdateResultWhenSkipped(
                    cachedResult = cached?.result,
                    cacheExpiresAt = cached?.expiresAt,
                    now = now,
                )
            }
        }
        if (!force) {
            mutex.withLock {
                cache[key]
                    ?.takeIf { it.fwVersion == fwVersion && it.isRecovery == isRecovery }
                    ?.takeIf { it.expiresAt > now }
                    ?.let {
                        logger.v { "Serving FWUP from cache" }
                        return it.result
                    }
            }
        }
        val result = getLatestFirmware(watch)
        // Only cache definitive answers — transient failures (network, rate limit)
        // must retry on the next connect, not be locked in for the TTL.
        if (result !is FirmwareUpdateCheckResult.UpdateCheckFailed) {
            mutex.withLock {
                cache[key] = CacheEntry(fwVersion, isRecovery, result, now + CACHE_TTL)
            }
        }
        return result
    }

    companion object {
        private val CACHE_TTL: Duration = 15.minutes
    }
}

/** Prefer eng-dash when opted in, falling back to whichever source we'd otherwise have used. */
private suspend fun coreDeviceFirmwareCheck(
    watch: WatchInfo,
    memfault: Memfault,
    engDashOta: EngDashOta,
    cohorts: Cohorts,
    coreConfig: CoreConfigFlow,
    coreAnalytics: CoreAnalytics,
): FirmwareUpdateCheckResult {
    if (CommonBuildKonfig.BUG_URL != null && coreConfig.value.useEngDashOta) {
        val result = engDashOta.getLatestFirmware(watch)
        if (result !is FirmwareUpdateCheckResult.UpdateCheckFailed) {
            return result
        }
        Logger.withTag("FirmwareUpdateCheck")
            .w { "eng-dash OTA check failed (${result.error}); falling back" }
        coreAnalytics.logEvent("core_ota_failed")
    }
    return if (shouldUseMemfaultForFirmwareUpdates(
            platform = watch.platform,
            memfaultToken = CommonBuildKonfig.MEMFAULT_TOKEN,
        )
    ) {
        memfault.getLatestFirmware(watch)
    } else {
        cohorts.getLatestFirmware(watch)
    }
}

internal fun firmwareUpdateResultWhenSkipped(
    cachedResult: FirmwareUpdateCheckResult?,
    cacheExpiresAt: Instant?,
    now: Instant,
): FirmwareUpdateCheckResult =
    cachedResult?.takeIf { cacheExpiresAt != null && cacheExpiresAt > now }
        ?: FirmwareUpdateCheckResult.FoundNoUpdate

internal fun shouldUseMemfaultForFirmwareUpdates(
    platform: WatchHardwarePlatform,
    memfaultToken: String?,
): Boolean = platform.isCoreDevice() && memfaultToken != null

fun WatchHardwarePlatform.isCoreDevice(): Boolean = when (this) {
    UNKNOWN, PEBBLE_ONE_EV_1, PEBBLE_ONE_EV_2, PEBBLE_ONE_EV_2_3, PEBBLE_ONE_EV_2_4,
    PEBBLE_ONE_POINT_FIVE, PEBBLE_TWO_POINT_ZERO, PEBBLE_SNOWY_EVT_2, PEBBLE_SNOWY_DVT,
    PEBBLE_BOBBY_SMILES, PEBBLE_ONE_BIGBOARD_2, PEBBLE_ONE_BIGBOARD, PEBBLE_SNOWY_BIGBOARD,
    PEBBLE_SNOWY_BIGBOARD_2, PEBBLE_SPALDING_EVT, PEBBLE_SPALDING_PVT, PEBBLE_SPALDING_BIGBOARD,
    PEBBLE_SILK_EVT, PEBBLE_SILK, PEBBLE_SILK_BIGBOARD, PEBBLE_SILK_BIGBOARD_2_PLUS,
    PEBBLE_ROBERT_EVT, PEBBLE_ROBERT_BIGBOARD, PEBBLE_ROBERT_BIGBOARD_2 -> false
    else -> true
}
