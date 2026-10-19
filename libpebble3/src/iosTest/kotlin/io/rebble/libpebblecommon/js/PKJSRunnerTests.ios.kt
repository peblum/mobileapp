package io.rebble.libpebblecommon.js

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.rebble.libpebblecommon.NotificationConfigFlow
import io.rebble.libpebblecommon.WatchConfigFlow
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.TokenProvider
import io.rebble.libpebblecommon.database.Database
import io.rebble.libpebblecommon.database.DatabaseConstructor
import io.rebble.libpebblecommon.database.dao.FakeLockerEntryDao
import io.rebble.libpebblecommon.database.entity.LockerEntry
import io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class PKJSRunnerTestsIos : PKJSRunnerTests() {
    private lateinit var testDatabase: Database

    @BeforeTest
    fun setUpDatabase() {
        testDatabase = Room.inMemoryDatabaseBuilder<Database> {
            DatabaseConstructor.initialize()
        }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    }

    @AfterTest
    fun tearDownDatabase() {
        if (::testDatabase.isInitialized) testDatabase.close()
    }

    override fun createJsRunner(
        libPebble: LibPebble,
        scope: CoroutineScope,
        appInfo: PbwAppInfo,
        lockerEntry: LockerEntry,
        jsPath: Path,
        device: CompanionAppDevice,
        urlOpenRequests: Channel<String>,
        logMessages: Channel<String>,
    ): JsRunner {
        val appContext = AppContext()
        val watchConfigFlow = WatchConfigFlow(libPebble.config)
        val remoteTimelineEmulator = RemoteTimelineEmulator(
            watchConfigFlow = watchConfigFlow,
            json = Json,
            timelinePinRealDao = testDatabase.timelinePinDao(),
            timelineReminderRealDao = testDatabase.timelineReminderDao(),
        )
        return JavascriptCoreJsRunner(
            appContext = appContext,
            libPebble = libPebble,
            jsTokenUtil = JsTokenUtil(
                object : TokenProvider {
                    override suspend fun getDevToken(): String? = null
                },
                lockerEntryDao = FakeLockerEntryDao(),
                watchConfigFlow = watchConfigFlow,
            ),
            device = device,
            scope = scope,
            appInfo = appInfo,
            lockerEntry = lockerEntry,
            jsPath = jsPath,
            urlOpenRequests = urlOpenRequests,
            logMessages = logMessages,
            remoteTimelineEmulator = remoteTimelineEmulator,
            httpInterceptorManager = HttpInterceptorManager(
                timeline = remoteTimelineEmulator,
                injectedHttpInterceptors = InjectedPKJSHttpInterceptors(emptyList()),
            ),
            notificationConfigFlow = NotificationConfigFlow(libPebble.config),
        )
    }

    @Test
    override fun testJSExecution() {
        super.testJSExecution()
    }

    @Test
    override fun testJSReady() {
        super.testJSReady()
    }

    @Test
    override fun testLocalStoragePersistence() {
        super.testLocalStoragePersistence()
    }

    @Test
    override fun testLocalStorageSandbox() {
        super.testLocalStorageSandbox()
    }

    @Test
    override fun testLocalStorageEarlyExecution() {
        super.testLocalStorageEarlyExecution()
    }
}
