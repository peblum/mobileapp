package coredevices.coreapp.auth

import PlatformUiContext
import co.touchlab.kermit.Logger
import coredevices.util.auth.AppleAuthUtil
import dev.gitlive.firebase.auth.AuthCredential

actual class RealAppleAuthUtil : AppleAuthUtil {
    companion object {
        private val logger = Logger.withTag("RealAppleAuthUtil")
    }

    actual override suspend fun signInApple(context: PlatformUiContext): AuthCredential? {
        logger.i { "Apple sign-in is disabled in this build" }
        return null
    }
}
