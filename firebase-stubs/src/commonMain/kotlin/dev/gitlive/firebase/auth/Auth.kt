package dev.gitlive.firebase.auth

import dev.gitlive.firebase.Firebase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

val Firebase.auth: FirebaseAuth
    get() = FdroidFirebaseAuth

object FdroidFirebaseAuth : FirebaseAuth

interface FirebaseAuth {
    val currentUser: FirebaseUser? get() = null
    val authStateChanged: Flow<FirebaseUser?> get() = MutableStateFlow(null)
    val idTokenChanged: Flow<FirebaseUser?> get() = MutableStateFlow(null)

    suspend fun signInAnonymously(): AuthResult = AuthResult(null)
    suspend fun signInWithCredential(credential: AuthCredential): AuthResult = AuthResult(null)
    suspend fun signInWithCustomToken(token: String): AuthResult = AuthResult(null)
    suspend fun signOut() = Unit
}

data class AuthResult(
    val user: FirebaseUser?,
)

open class AuthCredential(
    val nativeCredential: Any? = null,
) {
    open val providerId: String = "fdroid-disabled"
}

class OAuthCredential(
    nativeCredential: Any? = null,
) : AuthCredential(nativeCredential)

class FirebaseUser(
    val uid: String,
    val email: String? = null,
    val displayName: String? = null,
    val isAnonymous: Boolean = false,
    val providerData: List<UserInfo> = emptyList(),
) {
    suspend fun getIdToken(forceRefresh: Boolean): String? = null
    suspend fun linkWithCredential(credential: AuthCredential): AuthResult = AuthResult(this)
}

class UserInfo(
    val providerId: String,
)

class FirebaseAuthException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
class FirebaseAuthUserCollisionException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

object GoogleAuthProvider {
    fun credential(idToken: String?, accessToken: String?): AuthCredential = AuthCredential()
}
