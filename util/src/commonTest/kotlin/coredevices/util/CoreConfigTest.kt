package coredevices.util

import coredevices.util.models.CactusSTTMode
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class CoreConfigTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun explicitWeatherUnitsSurvivesRoundTrip() {
        WeatherUnit.entries.forEach { unit ->
            val encoded = json.encodeToString(CoreConfig(weatherUnits = unit))
            val decoded = json.decodeFromString<CoreConfig>(encoded)
            assertEquals(unit, decoded.weatherUnits)
            assertEquals(unit, decoded.resolvedWeatherUnits)
        }
    }

    @Test
    fun unsetWeatherUnitsFallsBackToDeviceDefault() {
        val decoded = json.decodeFromString<CoreConfig>("{}")
        assertEquals(null, decoded.weatherUnits)
        assertEquals(deviceDefaultWeatherUnit(), decoded.resolvedWeatherUnits)
    }

    @Test
    fun speechRecognitionDefaultsToLocalOnlyWithoutACloudService() {
        assertEquals(CactusSTTMode.LocalOnly, defaultSttMode(wisprAuthUrl = null, kirinkiUrl = null))
        assertEquals(CactusSTTMode.RemoteOnly, defaultSttMode(wisprAuthUrl = "https://wispr.example", kirinkiUrl = null))
        assertEquals(CactusSTTMode.RemoteOnly, defaultSttMode(wisprAuthUrl = null, kirinkiUrl = "https://kirinki.example"))
    }

    @Test
    fun speechRecognitionModeIsStoredEvenWhenItIsTheDefault() {
        CactusSTTMode.entries.forEach { mode ->
            val encoded = json.encodeToString(STTConfig(mode = mode))
            assertEquals(mode, json.decodeFromString<STTConfig>(encoded).mode)
            assertEquals(true, encoded.contains("\"mode\""))
        }
    }
}
