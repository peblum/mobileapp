package coredevices.pebble.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import coredevices.util.CommonBuildKonfig

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

fun fdroidExternalWatchAppDownloadWarning(): String =
    "This app comes from a third-party store, and downloading it bypasses " +
        "F-Droid's checks. It may include phone-side JavaScript that runs inside " +
        "the Pebble app and can use network access or any location access granted " +
        "to the Pebble app. Continue only if you trust the app and its source."

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
