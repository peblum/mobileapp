package coredevices.pebble.ui

import coredevices.util.CommonBuildKonfig
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

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
            val firmwareWarning = requireNotNull(fdroidFirmwareDownloadWarning())
            assertContains(firmwareWarning, "F-Droid")
            assertContains(firmwareWarning, "run on your watch")
            assertContains(firmwareWarning, "bypasses F-Droid's checks")
        }
    }
}
