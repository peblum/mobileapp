package coredevices.pebble.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import coredevices.util.CommonBuildKonfig

@Composable
fun rememberExternalWatchAppConsentRequester(): ((() -> Unit) -> Unit) {
    val pendingAction = remember { mutableStateOf<(() -> Unit)?>(null) }

    pendingAction.value?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction.value = null },
            title = { Text("Download third-party watch app?") },
            text = {
                Text(
                    "This app comes from a third-party store, and downloading it bypasses " +
                        "F-Droid's checks. It may include phone-side JavaScript that runs " +
                        "inside the Pebble app and can use network access. Continue only if you " +
                        "trust the app and its source."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingAction.value = null
                        action()
                    }
                ) {
                    Text("Download and run")
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
