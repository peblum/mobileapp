package coredevices.analytics

import PlatformContext

fun createAndroidAnalytics(platformContext: PlatformContext): AnalyticsBackend = NoopAnalyticsBackend

private object NoopAnalyticsBackend : AnalyticsBackend {
    override fun logEvent(name: String, parameters: Map<String, Any>?) = Unit
    override fun addGlobalProperty(name: String, value: String?) = Unit
    override fun setEnabled(enabled: Boolean) = Unit
}
