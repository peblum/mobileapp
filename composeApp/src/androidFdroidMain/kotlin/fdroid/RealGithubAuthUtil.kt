package coredevices.coreapp.auth

import PlatformUiContext
import co.touchlab.kermit.Logger
import coredevices.util.auth.GitHubAuthUtil
import dev.gitlive.firebase.auth.AuthCredential

actual class RealGithubAuthUtil : GitHubAuthUtil {
    companion object {
        private val logger = Logger.withTag("RealGithubAuthUtil")
    }

    actual override suspend fun signInGithub(context: PlatformUiContext): AuthCredential? {
        logger.i { "GitHub sign-in is disabled in this build" }
        return null
    }
}
