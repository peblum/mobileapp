package coredevices.coreapp.util

import PlatformUiContext
import android.content.Context
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual data object AppUpdatePlatformContent

class AndroidAppUpdate(
    private val context: Context,
) : AppUpdate {
    private val logger = Logger.withTag("AndroidAppUpdate")

    override val updateAvailable: StateFlow<AppUpdateState> =
        MutableStateFlow(AppUpdateState.NoUpdateAvailable)

    override fun startUpdateFlow(uiContext: PlatformUiContext, update: AppUpdatePlatformContent) {
        logger.d { "Play Core update flow is not available for this build of ${context.packageName}" }
    }
}
