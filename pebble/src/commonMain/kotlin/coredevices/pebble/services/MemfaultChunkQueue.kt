package coredevices.pebble.services

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import coredevices.database.MemfaultChunkDao
import coredevices.database.MemfaultChunkEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private sealed interface ChunkQueueItem

private data class PendingChunk(
    val serial: String,
    val bytes: ByteArray,
    val generation: Int,
) : ChunkQueueItem

private data class DiscardChunks(
    val generation: Int,
) : ChunkQueueItem

class MemfaultChunkQueue internal constructor(
    private val uploadChunkBatch: suspend (List<ByteArray>, String) -> Boolean,
    private val dao: MemfaultChunkDao,
    settings: Settings,
) {
    constructor(
        memfault: Memfault,
        dao: MemfaultChunkDao,
        settings: Settings,
    ) : this(memfault::uploadChunkBatch, dao, settings)

    private val logger = Logger.withTag("MemfaultChunkQueue")
    private val channel = Channel<ChunkQueueItem>(Channel.UNLIMITED)
    private val uploadMutex = Mutex()
    private val daoMutex = Mutex()
    private val consent = DiagnosticsConsentCoordinator(
        settings = settings,
        purgePendingKey = MEMFAULT_PURGE_PENDING_KEY,
    )

    // Non-suspending — safe to call from any context
    fun enqueue(serial: String, bytes: ByteArray) {
        val generation = consent.currentGeneration()
        if (!consent.uploadsEnabled()) {
            logger.d { "Not retaining Memfault chunks (uploads disabled in settings)" }
            return
        }
        channel.trySend(
            PendingChunk(
                serial = serial,
                bytes = bytes,
                generation = generation,
            )
        )
    }

    fun startProcessing(scope: CoroutineScope) {
        scope.launch {
            // Upload any chunks left over from a previous run first, before accepting new ones
            uploadPendingFromDb()

            while (true) {
                when (val item = channel.receive()) {
                    is DiscardChunks -> discardStored(item.generation)
                    is PendingChunk -> {
                        persistToDb(item)
                        var collected = 1

                        // Keep collecting until idle or the force-flush limit.
                        while (collected < MAX_COLLECT_BEFORE_FLUSH) {
                            when (val next = withTimeoutOrNull(IDLE_TIMEOUT) {
                                channel.receive()
                            }) {
                                null -> break
                                is DiscardChunks -> {
                                    discardStored(next.generation)
                                    collected = 0
                                    break
                                }
                                is PendingChunk -> {
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

    // Called externally (e.g. background sync) to retry any chunks that failed to upload.
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
        val serials = getPendingSerialsIfCurrent(generation)
        for (serial in serials) {
            val chunks = getChunksIfCurrent(serial, generation)
            for (batch in chunks.chunked(MAX_CHUNKS_PER_REQUEST)) {
                if (!consent.canUpload(generation)) return@uploadLock
                val success = uploadChunkBatch(batch.map { it.chunkData }, serial)
                if (!consent.canUpload(generation)) return@uploadLock
                if (success) {
                    if (!deleteIfCurrent(batch.map { it.id }, generation)) return@uploadLock
                } else {
                    logger.w { "uploadChunkBatch failed for serial=$serial; ${chunks.size} chunk(s) will be retried" }
                    return@uploadLock
                }
            }
        }
    }

    /**
     * Orders deletion before any chunks enqueued after a later opt-in. This command is
     * handled by the application-lived queue processor, not a screen-owned coroutine.
     */
    fun discardPending() {
        val generation = consent.requestPurge()
        channel.trySend(DiscardChunks(generation))
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

    private suspend fun getPendingSerialsIfCurrent(generation: Int): List<String> =
        daoMutex.withLock {
            if (!consent.canUpload(generation)) return@withLock emptyList()
            dao.getPendingSerials()
        }

    private suspend fun getChunksIfCurrent(
        serial: String,
        generation: Int,
    ): List<MemfaultChunkEntity> = daoMutex.withLock {
        if (!consent.canUpload(generation)) return@withLock emptyList()
        dao.getChunksForSerial(serial)
    }

    private suspend fun deleteIfCurrent(ids: List<Long>, generation: Int): Boolean =
        daoMutex.withLock {
            if (!consent.canUpload(generation)) return@withLock false
            dao.deleteByIds(ids)
            true
        }

    private suspend fun evictOldestIfCurrent(generation: Int): Boolean = daoMutex.withLock {
        if (!consent.canUpload(generation)) return@withLock false
        val count = dao.count()
        if (count > MAX_STORED_CHUNKS) {
            val excess = count - MAX_STORED_CHUNKS
            logger.w { "Chunk DB has $count entries (limit $MAX_STORED_CHUNKS), evicting $excess oldest" }
            dao.deleteOldest(excess)
        }
        true
    }

    private suspend fun persistToDb(chunk: PendingChunk) = daoMutex.withLock {
        if (!consent.uploadsEnabled() || !consent.isCurrent(chunk.generation)) return@withLock
        dao.insert(
            MemfaultChunkEntity(
                serial = chunk.serial,
                chunkData = chunk.bytes,
                createdAt = Clock.System.now().toEpochMilliseconds(),
            )
        )
    }

    companion object {
        private val IDLE_TIMEOUT = 10.seconds
        private const val MAX_COLLECT_BEFORE_FLUSH = 1000  // force flush if chunks never stop arriving
        private const val MAX_CHUNKS_PER_REQUEST = 100      // Memfault multipart limit per HTTP call
        private const val MAX_STORED_CHUNKS = 5000L          // evict oldest beyond this limit
        private const val MEMFAULT_PURGE_PENDING_KEY = "memfault_chunk_purge_pending"
    }
}
