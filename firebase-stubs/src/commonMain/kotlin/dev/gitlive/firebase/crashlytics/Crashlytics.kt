package dev.gitlive.firebase.crashlytics

import dev.gitlive.firebase.Firebase

val Firebase.crashlytics: FirebaseCrashlytics
    get() = FirebaseCrashlytics

object FirebaseCrashlytics {
    fun log(message: String) = Unit
    fun recordException(throwable: Throwable) = Unit
    fun setCrashlyticsCollectionEnabled(enabled: Boolean) = Unit
    fun setCustomKey(key: String, value: String) = Unit
    fun setCustomKey(key: String, value: Int) = Unit
    fun setCustomKey(key: String, value: Long) = Unit
    fun setCustomKey(key: String, value: Boolean) = Unit
    fun didCrashOnPreviousExecution(): Boolean = false
}
