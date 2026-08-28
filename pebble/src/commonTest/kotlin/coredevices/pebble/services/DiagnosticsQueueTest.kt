package coredevices.pebble.services

import com.russhwolf.settings.MapSettings
import coredevices.database.AnalyticsHeartbeatDao
import coredevices.database.AnalyticsHeartbeatEntity
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
    fun disabledAnalyticsQueueRejectsNewDataAndDeletesRetainedData() = runTest {
        val settings = MapSettings().apply {
            if (!CommonBuildKonfig.FDROID_BUILD) {
                putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, false)
            }
        }
        val analyticsDao = FakeAnalyticsHeartbeatDao(listOf(analyticsRow(1)))
        var analyticsUploads = 0
        val analyticsQueue = AnalyticsHeartbeatQueue(
            uploadHeartbeat = {
                analyticsUploads++
                true
            },
            dao = analyticsDao,
            settings = settings,
        )

        analyticsQueue.enqueue("new", null, byteArrayOf(2))
        analyticsQueue.startProcessing(backgroundScope)
        runCurrent()

        assertTrue(analyticsDao.rows.isEmpty())
        assertEquals(0, analyticsDao.insertCount)
        assertEquals(0, analyticsUploads)
    }

    @Test
    fun rapidReenablePurgesOldAnalyticsButPreservesNewData() = runTest {
        val settings = MapSettings().apply {
            putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, true)
        }
        val analyticsDao = FakeAnalyticsHeartbeatDao()
        val analyticsQueue = AnalyticsHeartbeatQueue(
            uploadHeartbeat = { true },
            dao = analyticsDao,
            settings = settings,
        )

        analyticsQueue.enqueue("watch", null, byteArrayOf(1))
        analyticsQueue.startProcessing(backgroundScope)
        runCurrent()
        assertEquals(1, analyticsDao.rows.size)

        settings.putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, false)
        analyticsQueue.discardPending()
        settings.putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, true)
        analyticsQueue.enqueue("new", null, byteArrayOf(2))
        runCurrent()

        assertEquals(listOf("new"), analyticsDao.rows.map { it.serial })
    }

    @Test
    fun backgroundRetryAppliesPendingAnalyticsPurge() = runTest {
        val settings = MapSettings().apply {
            putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, true)
        }
        val analyticsDao = FakeAnalyticsHeartbeatDao(listOf(analyticsRow(1)))
        var analyticsUploads = 0
        val analyticsQueue = AnalyticsHeartbeatQueue(
            uploadHeartbeat = {
                analyticsUploads++
                true
            },
            dao = analyticsDao,
            settings = settings,
        )

        settings.putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, false)
        analyticsQueue.discardPending()
        settings.putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, true)

        analyticsQueue.uploadPendingFromDb()

        assertEquals(0, analyticsUploads)
        assertTrue(analyticsDao.rows.isEmpty())

        analyticsQueue.startProcessing(backgroundScope)
        runCurrent()

        assertTrue(analyticsDao.rows.isEmpty())
        assertEquals(0, analyticsUploads)
    }

    @Test
    fun pendingAnalyticsPurgeSurvivesQueueRecreation() = runTest {
        val settings = MapSettings().apply {
            putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, true)
        }
        val analyticsDao = FakeAnalyticsHeartbeatDao(listOf(analyticsRow(1)))
        val oldAnalyticsQueue = AnalyticsHeartbeatQueue(
            uploadHeartbeat = { true },
            dao = analyticsDao,
            settings = settings,
        )

        settings.putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, false)
        oldAnalyticsQueue.discardPending()
        settings.putBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, true)

        var analyticsUploads = 0
        val restartedAnalyticsQueue = AnalyticsHeartbeatQueue(
            uploadHeartbeat = {
                analyticsUploads++
                false
            },
            dao = analyticsDao,
            settings = settings,
        )

        restartedAnalyticsQueue.uploadPendingFromDb()

        assertTrue(analyticsDao.rows.isEmpty())
        assertEquals(0, analyticsUploads)

        restartedAnalyticsQueue.enqueue("new", null, byteArrayOf(2))
        restartedAnalyticsQueue.startProcessing(backgroundScope)
        runCurrent()

        assertEquals(listOf("new"), analyticsDao.rows.map { it.serial })
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
