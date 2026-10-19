package com.mmk.kmpnotifier.notification.configuration

sealed class NotificationPlatformConfiguration {
    data class Android(
        val notificationIconResId: Int,
        val showPushNotification: Boolean = true,
    ) : NotificationPlatformConfiguration()

    data class Ios(
        val showPushNotification: Boolean = true,
    ) : NotificationPlatformConfiguration()
}
