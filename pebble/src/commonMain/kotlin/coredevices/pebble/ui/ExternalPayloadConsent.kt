package coredevices.pebble.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import coredevices.pebble.firmware.shouldUseMemfaultForFirmwareUpdates
import coredevices.util.CommonBuildKonfig
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform

@Composable
private fun rememberFdroidConsentRequester(
    title: String,
    message: String,
    confirmLabel: String,
    requestKey: Any? = Unit,
): ((() -> Unit) -> Unit) {
    val pendingAction = remember(message, requestKey) {
        mutableStateOf<(() -> Unit)?>(null)
    }

    pendingAction.value?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction.value = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingAction.value = null
                        action()
                    }
                ) {
                    Text(confirmLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction.value = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    return { action ->
        if (CommonBuildKonfig.FDROID_BUILD) {
            pendingAction.value = action
        } else {
            action()
        }
    }
}

@Composable
fun rememberExternalWatchAppConsentRequester(): ((() -> Unit) -> Unit) =
    rememberFdroidConsentRequester(
        title = "Download third-party watch app?",
        message = fdroidExternalWatchAppDownloadWarning(),
        confirmLabel = "Download and run",
    )

@Composable
fun rememberFirmwareDownloadConsentRequester(
    requestKey: Any? = Unit,
): ((() -> Unit) -> Unit) =
    rememberFdroidConsentRequester(
        title = "Download third-party firmware?",
        message = fdroidFirmwareDownloadWarning().orEmpty(),
        confirmLabel = "Download and install",
        requestKey = requestKey,
    )

@Composable
fun rememberFirmwareUpdateCheckConsentRequester(
    platform: WatchHardwarePlatform,
    requestKey: Any?,
): ((() -> Unit) -> Unit) =
    rememberFdroidConsentRequester(
        title = "Check a third-party service?",
        message = fdroidFirmwareUpdateCheckWarning(platform).orEmpty(),
        confirmLabel = "Check",
        requestKey = requestKey,
    )

fun fdroidExternalWatchAppDownloadWarning(): String =
    "This app comes from a third-party store, and downloading it bypasses " +
        "F-Droid's checks. It may include phone-side JavaScript that runs inside " +
        "the Pebble app and can use network access or any location access granted " +
        "to the Pebble app. Continue only if you trust the app and its source."

fun fdroidFirmwareUpdateCheckWarning(
    platform: WatchHardwarePlatform,
): String? = if (CommonBuildKonfig.FDROID_BUILD) {
    firmwareUpdateCheckWarning(
        platform = platform,
        memfaultToken = CommonBuildKonfig.MEMFAULT_TOKEN,
    )
} else {
    null
}

internal fun firmwareUpdateCheckWarning(
    platform: WatchHardwarePlatform,
    memfaultToken: String?,
): String = when {
    platform == WatchHardwarePlatform.UNKNOWN ->
        "The app cannot check for updates because this watch's hardware platform is " +
            "unknown. No firmware service will be contacted."
    shouldUseMemfaultForFirmwareUpdates(platform, memfaultToken) ->
        "This check contacts api.memfault.com and sends your watch model, serial number, " +
            "or a Bluetooth-derived identifier for prototype watches, and current " +
            "PebbleOS version when available. It also sends the app's Memfault project " +
            "key. Any offered firmware is not reviewed by F-Droid."
    else ->
        "This check contacts cohorts.rebble.io and sends your watch model, mobile " +
            "platform, and app version. If you are signed in to a Pebble service, " +
            "it also sends your account access token. Any offered firmware is not " +
            "reviewed by F-Droid."
}

fun fdroidFirmwareDownloadWarning(): String? =
    if (CommonBuildKonfig.FDROID_BUILD) {
        "The Pebble app will download PebbleOS from an external service. F-Droid did not " +
            "build or review this firmware; it may contain non-free hardware components " +
            "and will run on your watch. Installing it bypasses F-Droid's checks. " +
            "Continue only if you trust the firmware source."
    } else {
        null
    }

fun fdroidLanguagePackDownloadWarning(): String? =
    if (CommonBuildKonfig.FDROID_BUILD) {
        "Language packs are downloaded from third-party sources and installed on your " +
            "watch. They are not reviewed by F-Droid. Continue only if you trust the source."
    } else {
        null
    }
