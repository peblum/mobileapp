package coredevices

import coredevices.util.CommonBuildKonfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExperimentalDevicesDisabledTest {
    @Test
    fun `disabled facade does not resolve Index dependencies`() = runTest {
        if (CommonBuildKonfig.INDEX_HARDWARE_ENABLED) return@runTest

        val devices = ExperimentalDevices(
            ringSyncProvider = unavailableDependency(),
            recordingStorageProvider = unavailableDependency(),
            ringDelegateProvider = unavailableDependency(),
            sandboxRepositoryProvider = unavailableDependency(),
            recordingRepositoryProvider = unavailableDependency(),
            conversationMessageDaoProvider = unavailableDependency(),
            preferencesProvider = unavailableDependency(),
            shortcutActionHandlerProvider = unavailableDependency(),
            libIndexProvider = unavailableDependency(),
            permissionRequesterProvider = unavailableDependency(),
            indexFeedSyncServiceProvider = unavailableDependency(),
            defaultListsBootstrapProvider = unavailableDependency(),
        )

        devices.appInit()
        devices.init()
        devices.onBackgroundSync()

        assertNull(devices.badCollectionsDir())
        assertTrue(devices.exportOutput("recording").isEmpty())
        assertTrue(devices.exportRecentRecordings().isEmpty())
        assertNull(devices.debugSummary())
    }

    private fun <T> unavailableDependency(): Lazy<T> =
        lazy { error("disabled Index dependency was resolved") }
}
