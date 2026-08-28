package com.mmk.kmpnotifier.notification

import com.mmk.kmpnotifier.notification.configuration.NotificationPlatformConfiguration

typealias PayloadData = Map<String, Any?>

object NotifierManager {
    interface Listener {
        fun onNewToken(token: String) = Unit
        fun onPushNotification(title: String?, body: String?) = Unit
        fun onPushNotificationWithPayloadData(
            title: String?,
            body: String?,
            data: PayloadData,
        ) = Unit
    }

    fun initialize(configuration: NotificationPlatformConfiguration) = Unit
    fun addListener(listener: Listener) = Unit
    fun setLogger(logger: (String) -> Unit) = Unit
    fun getPushNotifier(): PushNotifier = PushNotifier
    fun getLocalNotifier(): LocalNotifier = LocalNotifier
}

object PushNotifier {
    suspend fun getToken(): String? = null
}

object LocalNotifier {
    fun notify(title: String, body: String) = Unit
    fun notify(notificationId: Int, title: String, body: String) = Unit
    fun notify(block: LocalNotification.() -> Unit) = Unit
    fun remove(notificationId: Int) = Unit
}

class LocalNotification {
    var title: String = ""
    var body: String = ""
}
