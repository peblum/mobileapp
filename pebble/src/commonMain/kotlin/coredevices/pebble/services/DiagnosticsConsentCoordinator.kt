package coredevices.pebble.services

import com.russhwolf.settings.Settings
import coredevices.pebble.ui.SettingsKeys.KEY_ENABLE_MEMFAULT_UPLOADS
import coredevices.util.thirdPartyDiagnosticsEnabledByDefault
import kotlinx.atomicfu.atomic

internal class DiagnosticsConsentCoordinator(
    private val settings: Settings,
    private val purgePendingKey: String,
) {
    private val requestedPurgeGeneration = atomic(0)
    private val appliedPurgeGeneration = atomic(0)

    fun uploadsEnabled(): Boolean =
        settings.getBoolean(
            KEY_ENABLE_MEMFAULT_UPLOADS,
            thirdPartyDiagnosticsEnabledByDefault(),
        )

    fun currentGeneration(): Int = requestedPurgeGeneration.value

    fun requestPurge(): Int {
        val generation = requestedPurgeGeneration.incrementAndGet()
        settings.putBoolean(purgePendingKey, true)
        return generation
    }

    fun markPurgeApplied(generation: Int) {
        var applied = appliedPurgeGeneration.value
        while (
            generation > applied &&
            !appliedPurgeGeneration.compareAndSet(applied, generation)
        ) {
            applied = appliedPurgeGeneration.value
        }
        if (generation >= requestedPurgeGeneration.value) {
            settings.putBoolean(purgePendingKey, false)
            if (requestedPurgeGeneration.value > generation) {
                settings.putBoolean(purgePendingKey, true)
            }
        }
    }

    fun hasPendingPurge(): Boolean = settings.getBoolean(purgePendingKey, false)

    fun isCurrent(generation: Int): Boolean =
        requestedPurgeGeneration.value == generation &&
            appliedPurgeGeneration.value == generation &&
            !hasPendingPurge()

    fun canUpload(generation: Int): Boolean =
        uploadsEnabled() && isCurrent(generation)
}
