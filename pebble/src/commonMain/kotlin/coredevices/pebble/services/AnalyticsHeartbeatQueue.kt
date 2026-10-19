package coredevices.pebble.services

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import coredevices.database.AnalyticsHeartbeatDao
import coredevices.database.AnalyticsHeartbeatEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private sealed interface HeartbeatQueueItem

private data class PendingHeartbeat(
    val serial: String,
    val fwVersion: String?,
    val tzOffsetMinutes: Int,
    val payload: ByteArray,
    val generation: Int,
) : HeartbeatQueueItem

private data class DiscardHeartbeats(
    val generation: Int,
) : HeartbeatQueueItem

class AnalyticsHeartbeatQueue internal constructor(
    private val uploadHeartbeat: suspend (AnalyticsHeartbeatEntity) -> Boolean,
    private val dao: AnalyticsHeartbeatDao,
    settings: Settings,
) {
    constructor(
        ingest: AnalyticsIngest,
        dao: AnalyticsHeartbeatDao,
        settings: Settings,
    ) : this(ingest::uploadHeartbeat, dao, settings)

    private val logger = Logger.withTag("AnalyticsHeartbeatQueue")
    private val channel = Channel<HeartbeatQueueItem>(Channel.UNLIMITED)
    private val uploadMutex = Mutex()
    private val daoMutex = Mutex()
    private val consent = DiagnosticsConsentCoordinator(
        settings = settings,
        purgePendingKey = ANALYTICS_PURGE_PENDING_KEY,
    )

    // Non-suspending — safe to call from any context
    fun enqueue(serial: String, fwVersion: String?, payload: ByteArray) {
        val generation = consent.currentGeneration()
        if (!consent.uploadsEnabled()) {
            logger.d { "Not uploading watch analytics (disabled in settings)" }
            return
        }
        logger.v { "enqueue heartbeat: serial=$serial fwVersion=$fwVersion" }
        val tzOffsetMinutes = TimeZone.currentSystemDefault()
            .offsetAt(Clock.System.now())
            .totalSeconds / 60
        channel.trySend(
            PendingHeartbeat(
                serial = serial,
                fwVersion = fwVersion,
                tzOffsetMinutes = tzOffsetMinutes,
                payload = payload,
                generation = generation,
            )
        )
    }

    fun startProcessing(scope: CoroutineScope) {
        scope.launch {
            // Drain any heartbeats left over from a previous run before accepting new ones
            uploadPendingFromDb()

            while (true) {
                when (val item = channel.receive()) {
                    is DiscardHeartbeats -> discardStored(item.generation)
                    is PendingHeartbeat -> {
                        persistToDb(item)
                        var collected = 1

                        // Keep collecting until idle or the force-flush limit.
                        while (collected < MAX_COLLECT_BEFORE_FLUSH) {
                            when (val next = withTimeoutOrNull(IDLE_TIMEOUT) {
                                channel.receive()
                            }) {
                                null -> break
                                is DiscardHeartbeats -> {
                                    discardStored(next.generation)
                                    collected = 0
                                    break
                                }
                                is PendingHeartbeat -> {
                                    persistToDb(next)
                                    collected++
                                }
                            }
                        }
                        if (collected > 0) uploadPendingFromDb()
                    }
                }
            }
        }
    }

    // Called externally (e.g. background sync) to retry any rows that failed to upload.
    // The mutex ensures this doesn't run concurrently with the processing loop's own upload pass.
    suspend fun uploadPendingFromDb() = uploadMutex.withLock uploadLock@ {
        if (consent.hasPendingPurge()) {
            discardStored(consent.currentGeneration())
        }
        val generation = consent.currentGeneration()
        if (!consent.uploadsEnabled()) {
            discardStoredIfStillDisabled(generation)
            return@uploadLock
        }
        if (!evictOldestIfCurrent(generation)) return@uploadLock
        while (true) {
            val batch = getBatchIfCurrent(generation)
            if (batch.isEmpty()) return@uploadLock
            var uploaded = 0
            for (row in batch) {
                if (!consent.canUpload(generation)) return@uploadLock
                val success = uploadHeartbeat(row)
                if (!consent.canUpload(generation)) return@uploadLock
                if (success) {
                    if (!deleteIfCurrent(row.id, generation)) return@uploadLock
                    uploaded++
                } else {
                    logger.w { "uploadHeartbeat failed for serial=${row.serial}; will retry later" }
                    return@uploadLock
                }
            }
            logger.d { "Uploaded $uploaded heartbeats of ${batch.size} from DB" }
        }
    }

    /**
     * Orders deletion before any records enqueued after a later opt-in. This command is
     * handled by the application-lived queue processor, not a screen-owned coroutine.
     */
    fun discardPending() {
        val generation = consent.requestPurge()
        channel.trySend(DiscardHeartbeats(generation))
    }

    private suspend fun discardStored(generation: Int) = daoMutex.withLock {
        dao.deleteAll()
        consent.markPurgeApplied(generation)
    }

    private suspend fun discardStoredIfStillDisabled(generation: Int) = daoMutex.withLock {
        if (!consent.uploadsEnabled() && consent.isCurrent(generation)) {
            dao.deleteAll()
        }
    }

    private suspend fun getBatchIfCurrent(
        generation: Int,
    ): List<AnalyticsHeartbeatEntity> = daoMutex.withLock {
        if (!consent.canUpload(generation)) return@withLock emptyList()
        dao.getBatch(UPLOAD_BATCH_SIZE)
    }

    private suspend fun deleteIfCurrent(id: Long, generation: Int): Boolean =
        daoMutex.withLock {
            if (!consent.canUpload(generation)) return@withLock false
            dao.deleteByIds(listOf(id))
            true
        }

    private suspend fun evictOldestIfCurrent(generation: Int): Boolean = daoMutex.withLock {
        if (!consent.canUpload(generation)) return@withLock false
        val count = dao.count()
        if (count > MAX_STORED_HEARTBEATS) {
            val excess = count - MAX_STORED_HEARTBEATS
            logger.w { "Heartbeat DB has $count entries (limit $MAX_STORED_HEARTBEATS), evicting $excess oldest" }
            dao.deleteOldest(excess)
        }
        true
    }

    private suspend fun persistToDb(item: PendingHeartbeat) = daoMutex.withLock {
        if (!consent.uploadsEnabled() || !consent.isCurrent(item.generation)) return@withLock
        dao.insert(
            AnalyticsHeartbeatEntity(
                serial = item.serial,
                fwVersion = item.fwVersion,
                tzOffsetMinutes = item.tzOffsetMinutes,
                payload = item.payload,
                createdAt = Clock.System.now().toEpochMilliseconds(),
            )
        )
    }

    companion object {
        private val IDLE_TIMEOUT = 10.seconds
        private const val MAX_COLLECT_BEFORE_FLUSH = 50
        private const val UPLOAD_BATCH_SIZE = 100
        private const val MAX_STORED_HEARTBEATS = 500L
        private const val ANALYTICS_PURGE_PENDING_KEY =
            "analytics_heartbeat_purge_pending"
    }
}
