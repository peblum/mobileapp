package coredevices.coreapp.auth

import PlatformUiContext
import android.content.Context
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import coredevices.util.auth.GoogleAuthUtil
import dev.gitlive.firebase.auth.AuthCredential

actual class RealGoogleAuthUtil(
    private val appContext: Context,
    private val settings: Settings,
) : GoogleAuthUtil {
    companion object {
        private val logger = Logger.withTag(RealGoogleAuthUtil::class.simpleName!!)
    }

    override suspend fun signInGoogle(context: PlatformUiContext): AuthCredential? {
        logger.i { "Google sign-in is disabled in this build" }
        return null
    }

    override suspend fun authorizeScopes(context: PlatformUiContext, scopes: List<String>): String? {
        logger.i { "Google OAuth scopes are disabled in this build" }
        return null
    }

    override suspend fun getAccessToken(scopes: List<String>): String? = null
}
