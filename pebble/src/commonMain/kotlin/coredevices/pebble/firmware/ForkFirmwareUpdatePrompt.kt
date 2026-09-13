package coredevices.pebble.firmware

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import coredevices.pebble.services.ensureVersionPrefix
import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import io.rebble.libpebblecommon.services.WatchInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ForkFirmwareOffer(
    val identifier: PebbleIdentifier,
    val watchName: String,
    val release: ForkFirmwareRelease,
)

interface ForkFirmwareUpdatePrompt {
    val pendingOffer: StateFlow<ForkFirmwareOffer?>
    suspend fun maybeOffer(
        identifier: PebbleIdentifier,
        watchName: String,
        watchInfo: WatchInfo,
        updateInProgress: Boolean,
    )

    fun accept()
    fun decline()
    fun dismiss()
}

class RealForkFirmwareUpdatePrompt(
    private val settings: Settings,
    private val releases: ForkFirmwareReleases,
    private val startUpdate: (PebbleIdentifier, FirmwareUpdateCheckResult.FoundUpdate) -> Unit,
) : ForkFirmwareUpdatePrompt {
    private val logger = Logger.withTag("ForkFirmwareUpdatePrompt")
    private val offeredThisSession = mutableSetOf<String>()
    private val _pendingOffer = MutableStateFlow<ForkFirmwareOffer?>(null)
    override val pendingOffer: StateFlow<ForkFirmwareOffer?> = _pendingOffer.asStateFlow()

    override suspend fun maybeOffer(
        identifier: PebbleIdentifier,
        watchName: String,
        watchInfo: WatchInfo,
        updateInProgress: Boolean,
    ) {
        if (updateInProgress) return
        if (watchInfo.platform == WatchHardwarePlatform.UNKNOWN) return
        if (watchInfo.runningFwVersion.isRecovery) return
        if (_pendingOffer.value != null) return
        val release = releases.latestFor(watchInfo.platform) ?: return
        val releaseVersion = ensureVersionPrefix(release.version.stringVersion)
        if (releaseVersion == ensureVersionPrefix(watchInfo.runningFwVersion.stringVersion)) return
        val offerKey = "${watchInfo.serial}|$releaseVersion"
        if (offerKey in offeredThisSession) return
        if (isDeclined(releaseVersion)) return
        offeredThisSession += offerKey
        logger.d { "Offering ${ForkFirmwareSource.NAME} $releaseVersion for $watchName" }
        _pendingOffer.value = ForkFirmwareOffer(identifier, watchName, release)
    }

    override fun accept() {
        val offer = _pendingOffer.value ?: return
        _pendingOffer.value = null
        logger.d { "Starting ${ForkFirmwareSource.NAME} update for ${offer.watchName}" }
        startUpdate(
            offer.identifier,
            FirmwareUpdateCheckResult.FoundUpdate(
                version = offer.release.version,
                url = offer.release.url,
                notes = offer.release.notes,
                canDowngrade = true,
            ),
        )
    }

    override fun decline() {
        val offer = _pendingOffer.value ?: return
        _pendingOffer.value = null
        val version = ensureVersionPrefix(offer.release.version.stringVersion)
        logger.d { "Declined ${ForkFirmwareSource.NAME} $version" }
        settings.putBoolean(declineKey(version), true)
    }

    override fun dismiss() {
        _pendingOffer.value = null
    }

    private fun isDeclined(version: String): Boolean =
        settings.getBoolean(declineKey(version), false)

    private fun declineKey(version: String) = "$KEY_DECLINED_PREFIX$version"

    companion object {
        private const val KEY_DECLINED_PREFIX = "declined_fork_firmware_"
    }
}
