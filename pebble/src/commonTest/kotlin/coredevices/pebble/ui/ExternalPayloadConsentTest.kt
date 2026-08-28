package coredevices.pebble.ui

import coredevices.util.CommonBuildKonfig
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ExternalPayloadConsentTest {
    @Test
    fun thirdPartyPayloadWarningsAreEnabledForFdroidBuilds() {
        assertEquals(
            CommonBuildKonfig.FDROID_BUILD,
            fdroidFirmwareDownloadWarning() != null,
        )
        assertEquals(
            CommonBuildKonfig.FDROID_BUILD,
            fdroidLanguagePackDownloadWarning() != null,
        )
        if (CommonBuildKonfig.FDROID_BUILD) {
            val watchAppWarning = fdroidExternalWatchAppDownloadWarning()
            assertContains(watchAppWarning, "network access")
            assertContains(watchAppWarning, "location access")
            val updateCheckWarning = requireNotNull(
                fdroidFirmwareUpdateCheckWarning(WatchHardwarePlatform.PEBBLE_SILK),
            )
            assertContains(updateCheckWarning, "cohorts.rebble.io")
            assertContains(updateCheckWarning, "mobile platform")
            assertContains(updateCheckWarning, "account access token")
            val firmwareWarning = requireNotNull(fdroidFirmwareDownloadWarning())
            assertContains(firmwareWarning, "F-Droid")
            assertContains(firmwareWarning, "run on your watch")
            assertContains(firmwareWarning, "bypasses F-Droid's checks")
        }
    }

    @Test
    fun firmwareCheckWarningsMatchTheSelectedService() {
        val cohortsWarning = firmwareUpdateCheckWarning(
            platform = WatchHardwarePlatform.PEBBLE_SILK,
            memfaultToken = "project-key",
        )
        assertContains(cohortsWarning, "cohorts.rebble.io")
        assertContains(cohortsWarning, "mobile platform")
        assertContains(cohortsWarning, "account access token")

        val memfaultWarning = firmwareUpdateCheckWarning(
            platform = WatchHardwarePlatform.CORE_ASTERIX,
            memfaultToken = "project-key",
        )
        assertContains(memfaultWarning, "api.memfault.com")
        assertContains(memfaultWarning, "serial number")
        assertContains(memfaultWarning, "Bluetooth-derived identifier")
        assertContains(memfaultWarning, "current PebbleOS version")
        assertContains(memfaultWarning, "Memfault project key")

        val unknownWarning = firmwareUpdateCheckWarning(
            platform = WatchHardwarePlatform.UNKNOWN,
            memfaultToken = "project-key",
        )
        assertContains(unknownWarning, "No firmware service will be contacted")
        assertFalse(unknownWarning.contains("api.memfault.com"))
        assertFalse(unknownWarning.contains("cohorts.rebble.io"))
    }
}
