package coredevices.pebble.services

import com.russhwolf.settings.MapSettings
import coredevices.database.AnalyticsHeartbeatDao
import coredevices.database.AnalyticsHeartbeatEntity
import coredevices.database.MemfaultChunkDao
import coredevices.database.MemfaultChunkEntity
import coredevices.pebble.ui.SettingsKeys.KEY_ENABLE_MEMFAULT_UPLOADS
import coredevices.util.CommonBuildKonfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsQueueTest {
    @Test
    fun disabledQueuesRejectNewDataAndDeleteRetainedData() = runTest {
        val settings = MapSettings().apply {
            if (!CommonBuildKonfig.FDROID_BUILD) {
                putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, false)
            }
        }
        val analyticsDao = FakeAnalyticsHeartbeatDao(listOf(analyticsRow(1)))
        val memfaultDao = FakeMemfaultChunkDao(listOf(memfaultChunk(1)))
        var analyticsUploads = 0
        var memfaultUploads = 0
        val analyticsQueue = AnalyticsHeartbeatQueue(
            uploadHeartbeat = {
                analyticsUploads++
                true
            },
            dao = analyticsDao,
            settings = settings,
        )
        val memfaultQueue = MemfaultChunkQueue(
            uploadChunkBatch = { _, _ ->
                memfaultUploads++
                true
            },
            dao = memfaultDao,
            settings = settings,
        )

        analyticsQueue.enqueue("new", null, byteArrayOf(2))
        memfaultQueue.enqueue("new", byteArrayOf(2))
        analyticsQueue.startProcessing(backgroundScope)
        memfaultQueue.startProcessing(backgroundScope)
        runCurrent()

        assertTrue(analyticsDao.rows.isEmpty())
        assertTrue(memfaultDao.rows.isEmpty())
        assertEquals(0, analyticsDao.insertCount)
        assertEquals(0, memfaultDao.insertCount)
        assertEquals(0, analyticsUploads)
        assertEquals(0, memfaultUploads)
    }

    @Test
    fun rapidReenablePurgesOldDataButPreservesNewData() = runTest {
        val settings = MapSettings().apply {
            putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, true)
        }
        val analyticsDao = FakeAnalyticsHeartbeatDao()
        val memfaultDao = FakeMemfaultChunkDao()
        val analyticsQueue = AnalyticsHeartbeatQueue(
            uploadHeartbeat = { true },
            dao = analyticsDao,
            settings = settings,
        )
        val memfaultQueue = MemfaultChunkQueue(
            uploadChunkBatch = { _, _ -> true },
            dao = memfaultDao,
            settings = settings,
        )

        analyticsQueue.enqueue("watch", null, byteArrayOf(1))
        memfaultQueue.enqueue("watch", byteArrayOf(1))
        analyticsQueue.startProcessing(backgroundScope)
        memfaultQueue.startProcessing(backgroundScope)
        runCurrent()
        assertEquals(1, analyticsDao.rows.size)
        assertEquals(1, memfaultDao.rows.size)

        settings.putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, false)
        analyticsQueue.discardPending()
        memfaultQueue.discardPending()
        settings.putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, true)
        analyticsQueue.enqueue("new", null, byteArrayOf(2))
        memfaultQueue.enqueue("new", byteArrayOf(2))
        runCurrent()

        assertEquals(listOf("new"), analyticsDao.rows.map { it.serial })
        assertEquals(listOf("new"), memfaultDao.rows.map { it.serial })
    }

    @Test
    fun backgroundRetriesApplyPendingPurge() = runTest {
        val settings = MapSettings().apply {
            putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, true)
        }
        val analyticsDao = FakeAnalyticsHeartbeatDao(listOf(analyticsRow(1)))
        val memfaultDao = FakeMemfaultChunkDao(listOf(memfaultChunk(1)))
        var analyticsUploads = 0
        var memfaultUploads = 0
        val analyticsQueue = AnalyticsHeartbeatQueue(
            uploadHeartbeat = {
                analyticsUploads++
                true
            },
            dao = analyticsDao,
            settings = settings,
        )
        val memfaultQueue = MemfaultChunkQueue(
            uploadChunkBatch = { _, _ ->
                memfaultUploads++
                true
            },
            dao = memfaultDao,
            settings = settings,
        )

        settings.putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, false)
        analyticsQueue.discardPending()
        memfaultQueue.discardPending()
        settings.putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, true)

        analyticsQueue.uploadPendingFromDb()
        memfaultQueue.uploadPendingFromDb()

        assertEquals(0, analyticsUploads)
        assertEquals(0, memfaultUploads)
        assertTrue(analyticsDao.rows.isEmpty())
        assertTrue(memfaultDao.rows.isEmpty())

        analyticsQueue.startProcessing(backgroundScope)
        memfaultQueue.startProcessing(backgroundScope)
        runCurrent()

        assertTrue(analyticsDao.rows.isEmpty())
        assertTrue(memfaultDao.rows.isEmpty())
        assertEquals(0, analyticsUploads)
        assertEquals(0, memfaultUploads)
    }

    @Test
    fun pendingPurgeSurvivesQueueRecreation() = runTest {
        val settings = MapSettings().apply {
            putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, true)
        }
        val analyticsDao = FakeAnalyticsHeartbeatDao(listOf(analyticsRow(1)))
        val memfaultDao = FakeMemfaultChunkDao(listOf(memfaultChunk(1)))
        val oldAnalyticsQueue = AnalyticsHeartbeatQueue(
            uploadHeartbeat = { true },
            dao = analyticsDao,
            settings = settings,
        )
        val oldMemfaultQueue = MemfaultChunkQueue(
            uploadChunkBatch = { _, _ -> true },
            dao = memfaultDao,
            settings = settings,
        )

        settings.putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, false)
        oldAnalyticsQueue.discardPending()
        oldMemfaultQueue.discardPending()
        settings.putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, true)

        var analyticsUploads = 0
        var memfaultUploads = 0
        val restartedAnalyticsQueue = AnalyticsHeartbeatQueue(
            uploadHeartbeat = {
                analyticsUploads++
                false
            },
            dao = analyticsDao,
            settings = settings,
        )
        val restartedMemfaultQueue = MemfaultChunkQueue(
            uploadChunkBatch = { _, _ ->
                memfaultUploads++
                false
            },
            dao = memfaultDao,
            settings = settings,
        )

        restartedAnalyticsQueue.uploadPendingFromDb()
        restartedMemfaultQueue.uploadPendingFromDb()

        assertTrue(analyticsDao.rows.isEmpty())
        assertTrue(memfaultDao.rows.isEmpty())
        assertEquals(0, analyticsUploads)
        assertEquals(0, memfaultUploads)

        restartedAnalyticsQueue.enqueue("new", null, byteArrayOf(2))
        restartedMemfaultQueue.enqueue("new", byteArrayOf(2))
        restartedAnalyticsQueue.startProcessing(backgroundScope)
        restartedMemfaultQueue.startProcessing(backgroundScope)
        runCurrent()

        assertEquals(listOf("new"), analyticsDao.rows.map { it.serial })
        assertEquals(listOf("new"), memfaultDao.rows.map { it.serial })
    }

    @Test
    fun analyticsOptOutStopsAnActiveBatch() = runTest {
        val settings = MapSettings().apply {
            putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, true)
        }
        val dao = FakeAnalyticsHeartbeatDao(
            listOf(analyticsRow(1), analyticsRow(2)),
        )
        val uploadedIds = mutableListOf<Long>()
        lateinit var queue: AnalyticsHeartbeatQueue
        queue = AnalyticsHeartbeatQueue(
            uploadHeartbeat = {
                uploadedIds += it.id
                settings.putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, false)
                queue.discardPending()
                true
            },
            dao = dao,
            settings = settings,
        )

        queue.startProcessing(backgroundScope)
        runCurrent()

        assertEquals(listOf(1L), uploadedIds)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun memfaultOptOutStopsAnActiveBatch() = runTest {
        val settings = MapSettings().apply {
            putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, true)
        }
        val dao = FakeMemfaultChunkDao(
            (1L..101L).map(::memfaultChunk),
        )
        var uploadCalls = 0
        lateinit var queue: MemfaultChunkQueue
        queue = MemfaultChunkQueue(
            uploadChunkBatch = { _, _ ->
                uploadCalls++
                settings.putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, false)
                queue.discardPending()
                true
            },
            dao = dao,
            settings = settings,
        )

        queue.startProcessing(backgroundScope)
        runCurrent()

        assertEquals(1, uploadCalls)
        assertTrue(dao.rows.isEmpty())
    }
}

private fun analyticsRow(id: Long) = AnalyticsHeartbeatEntity(
    id = id,
    serial = "watch",
    fwVersion = null,
    tzOffsetMinutes = 0,
    payload = byteArrayOf(id.toByte()),
    createdAt = id,
)

private class FakeAnalyticsHeartbeatDao(
    initialRows: List<AnalyticsHeartbeatEntity> = emptyList(),
) : AnalyticsHeartbeatDao {
    val rows = initialRows.toMutableList()
    var insertCount = 0
        private set
    private var nextId = (rows.maxOfOrNull { it.id } ?: 0) + 1

    override suspend fun insert(row: AnalyticsHeartbeatEntity): Long {
        insertCount++
        val id = row.id.takeIf { it != 0L } ?: nextId++
        rows += row.copy(id = id)
        return id
    }

    override suspend fun getBatch(limit: Int): List<AnalyticsHeartbeatEntity> =
        rows.sortedBy { it.id }.take(limit)

    override suspend fun deleteByIds(ids: List<Long>) {
        rows.removeAll { it.id in ids }
    }

    override suspend fun deleteAll() {
        rows.clear()
    }

    override suspend fun count(): Long = rows.size.toLong()

    override suspend fun deleteOldest(count: Long) {
        val ids = rows.sortedBy { it.id }.take(count.toInt()).map { it.id }
        deleteByIds(ids)
    }
}

private fun memfaultChunk(id: Long) = MemfaultChunkEntity(
    id = id,
    serial = "watch",
    chunkData = byteArrayOf(id.toByte()),
    createdAt = id,
)

private class FakeMemfaultChunkDao(
    initialRows: List<MemfaultChunkEntity> = emptyList(),
) : MemfaultChunkDao {
    val rows = initialRows.toMutableList()
    var insertCount = 0
        private set
    private var nextId = (rows.maxOfOrNull { it.id } ?: 0) + 1

    override suspend fun insert(chunk: MemfaultChunkEntity): Long {
        insertCount++
        val id = chunk.id.takeIf { it != 0L } ?: nextId++
        rows += chunk.copy(id = id)
        return id
    }

    override suspend fun getPendingSerials(): List<String> =
        rows.sortedBy { it.id }.map { it.serial }.distinct()

    override suspend fun getChunksForSerial(serial: String): List<MemfaultChunkEntity> =
        rows.filter { it.serial == serial }.sortedBy { it.id }

    override suspend fun deleteByIds(ids: List<Long>) {
        rows.removeAll { it.id in ids }
    }

    override suspend fun deleteAll() {
        rows.clear()
    }

    override suspend fun count(): Long = rows.size.toLong()

    override suspend fun deleteOldest(count: Long) {
        val ids = rows.sortedBy { it.id }.take(count.toInt()).map { it.id }
        deleteByIds(ids)
    }
}
