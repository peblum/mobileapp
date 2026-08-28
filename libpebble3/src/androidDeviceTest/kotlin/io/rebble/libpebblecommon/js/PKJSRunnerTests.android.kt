package io.rebble.libpebblecommon.js

import androidx.room.Room
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.rebble.libpebblecommon.NotificationConfigFlow
import io.rebble.libpebblecommon.WatchConfigFlow
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.TokenProvider
import io.rebble.libpebblecommon.database.Database
import io.rebble.libpebblecommon.database.dao.FakeLockerEntryDao
import io.rebble.libpebblecommon.database.entity.LockerEntry
import io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test

@MediumTest
class PKJSRunnerTestsAndroid : PKJSRunnerTests() {
    private lateinit var testDatabase: Database

    @Before
    fun setUpDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        testDatabase = Room.inMemoryDatabaseBuilder<Database>(context)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    @After
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
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appContext = AppContext(context)
        val watchConfigFlow = WatchConfigFlow(libPebble.config)
        val remoteTimelineEmulator = RemoteTimelineEmulator(
            watchConfigFlow = watchConfigFlow,
            json = Json,
            timelinePinRealDao = testDatabase.timelinePinDao(),
            timelineReminderRealDao = testDatabase.timelineReminderDao(),
        )
        return WebViewJsRunner(
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
            appInfo = appInfo,
            lockerEntry = lockerEntry,
            jsPath = jsPath,
            urlOpenRequests = urlOpenRequests,
            logMessages = logMessages,
            scope = scope,
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
