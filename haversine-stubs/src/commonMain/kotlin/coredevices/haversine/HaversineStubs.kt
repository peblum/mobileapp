package coredevices.haversine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Instant

/**
 * Free, no-op API compatibility layer for builds that do not ship the
 * Haversine Index transport. Index hardware is disabled in those builds.
 */
class KMPHaversineSatelliteManager(
    pairedSatelliteIdProvider: () -> String?,
    debugDelegate: KMPHaversineDebugDelegate,
    hacksDelegate: KMPHaversineHacksDelegate,
    collectionIndexStorage: CollectionIndexStorage,
    context: Any? = null,
    hwVersion: Pair<Int, Int>,
    scope: CoroutineScope,
) {
    val lastRing: StateFlow<KMPHaversineSatellite?> = MutableStateFlow(null)

    suspend fun awaitBluetoothReady() = Unit

    suspend fun startScanning(): Flow<SatelliteStatus> = emptyFlow()

    suspend fun programSatelliteWithUserID(
        satelliteId: String,
        userId: String,
    ): Result<Unit> = Result.failure(
        UnsupportedOperationException("Index hardware is unavailable in this build"),
    )

    fun restartPreemptiveTransfer() = Unit

    suspend fun getSatelliteById(id: String): KMPHaversineSatellite? = null
}

class KMPHaversineSatellite(
    val id: String,
    val name: String,
    val state: StateFlow<KMPHaversineSatelliteState?> = MutableStateFlow(null),
    val lastAdvertisement: KMPHaversineAdvertisement? = null,
) {
    suspend fun panic(): Nothing =
        throw UnsupportedOperationException("Index hardware is unavailable in this build")

    suspend fun eraseCollections(): Nothing =
        throw UnsupportedOperationException("Index hardware is unavailable in this build")
}

data class KMPHaversineSatelliteState(
    val isInCollectionState: Boolean = false,
    val rxRSSI: Float? = null,
    val isNearby: Boolean = false,
    val isInFailsafeMode: Boolean = false,
    val firmwareVersion: String = "",
    val truncatedCollectionCount: Int = 0,
    val serialNumber: String = "",
    val programmedSerialNumber: String? = null,
    val applicationDataUserId: String? = null,
)

data class KMPHaversineAdvertisement(
    val id: String,
    val name: String,
    val timestamp: Instant,
    val rssi: Float? = null,
    val cacheableStateFingerprint: Long? = null,
) {
    companion object {
        fun parseToStateFingerprint(data: ByteArray): Long? = null
    }
}

fun fingerprintMatchesFailsafe(fingerprint: Long): Boolean = false

fun fingerprintMatchesUserId(fingerprint: Long, userId: String): Boolean = false

sealed interface SatelliteStatus {
    val satellite: KMPHaversineSatellite

    data class Transferring(
        override val satellite: KMPHaversineSatellite,
        val transferStatus: TransferStatus,
    ) : SatelliteStatus

    sealed class FirmwareUpdating : SatelliteStatus {
        data class Started(
            override val satellite: KMPHaversineSatellite,
            val newVersion: String,
        ) : FirmwareUpdating()

        data class Success(
            override val satellite: KMPHaversineSatellite,
            val newVersion: String,
        ) : FirmwareUpdating()

        data class Failed(
            override val satellite: KMPHaversineSatellite,
            val newVersion: String,
        ) : FirmwareUpdating()

        data class NotStarted(
            override val satellite: KMPHaversineSatellite,
            val newVersion: String,
        ) : FirmwareUpdating()
    }

    sealed class ProgrammingUserId : SatelliteStatus {
        data class InProgress(
            override val satellite: KMPHaversineSatellite,
        ) : ProgrammingUserId()

        data class Completed(
            override val satellite: KMPHaversineSatellite,
        ) : ProgrammingUserId()

        data class Failed(
            override val satellite: KMPHaversineSatellite,
            val error: Throwable,
        ) : ProgrammingUserId()
    }

    data class BluetoothFailure(
        override val satellite: KMPHaversineSatellite,
        val reason: BluetoothFailureReason,
    ) : SatelliteStatus
}

enum class BluetoothFailureReason {
    PeerRemovedPairingInformation,
    AuthenticationFailure,
    Unknown,
}

sealed class TransferStatus(
    open val satellite: KMPHaversineSatellite,
) {
    class TransferStarted(
        override val satellite: KMPHaversineSatellite,
        val willTransferRange: IntRange,
        val rollover: Boolean,
    ) : TransferStatus(satellite)

    class TransferTypeDetermined(
        override val satellite: KMPHaversineSatellite,
        val isAudio: Boolean,
        val buttonSequence: String?,
        val collectionStartIndex: Int?,
        val collectionIndex: Int,
        val final: Boolean,
        val advertisementReceivedTimestamp: Instant,
        val lifetimeCollectionCount: UInt?,
    ) : TransferStatus(satellite)

    class TransferInProgress(
        override val satellite: KMPHaversineSatellite,
        val collectionStartIndex: Int,
        val currentCollectionIndex: Int,
    ) : TransferStatus(satellite)

    class TransferComplete(
        override val satellite: KMPHaversineSatellite,
        val collectionStartCount: Long,
        val buttonSequence: String?,
        val collectionIndex: Int,
        val samples: ShortArray,
        val sampleRate: Long,
        val buttonReleaseTimestamp: Instant,
        val transferCompleteTimestamp: Instant,
        val isContiguous: Boolean,
    ) : TransferStatus(satellite)

    class TransferFailed(
        override val satellite: KMPHaversineSatellite,
        val exception: Exception?,
        val collectionIndex: Int,
    ) : TransferStatus(satellite)

    class IrrecoverableDataDetected(
        override val satellite: KMPHaversineSatellite,
        val exception: Exception,
        val collection: MultipartCollection?,
    ) : TransferStatus(satellite)
}

data class MultipartCollection(
    val startIndex: Int,
    val indices: Set<Int> = emptySet(),
)

interface CollectionIndexStorage {
    val lastSuccessfulCollectionIndex: StateFlow<Int?>
    fun setLastSuccessfulCollectionIndex(index: Int?)
}

interface KMPHaversineDebugDelegate {
    fun handleHaversineDebugInfo(info: KMPHaversineDebugInfo)
    fun shouldReadRxRSSI(satellite: KMPHaversineSatellite): Boolean
    fun handleRxRSSI(rssi: Float, satellite: KMPHaversineSatellite)
}

interface KMPHaversineHacksDelegate {
    fun shouldWipeCollectionsBeforeTransfer(satellite: KMPHaversineSatellite): Boolean
    fun wipedCollectionsBeforeTransfer(satellite: KMPHaversineSatellite)
}

data class KMPHaversineDebugInfo(
    val timestamp: Instant,
    val satelliteId: String,
    val satelliteName: String,
    val satelliteVersion: String,
    val satelliteSerial: String,
    val satelliteProgrammedSerial: String?,
    val dump: KMPHaversineDebugDump,
)

data class KMPHaversineDebugDump(
    val coreDump: ByteArray?,
    val rebootReasons: List<KMPHaversineDebugRebootReason>,
)

data class KMPHaversineDebugRebootReason(
    val code: UInt,
    val context: UInt,
    val description: String?,
)

class DataDecodeException(
    override val cause: Throwable,
    val data: ByteArray,
) : Exception(cause)

fun removeDCBias(samples: ShortArray) = Unit
