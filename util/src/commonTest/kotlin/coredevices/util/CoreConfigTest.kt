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
            assertEquals(unit, json.decodeFromString<CoreConfig>(encoded).weatherUnits)
        }
    }

    @Test
    fun unsetWeatherUnitsDecodesAsNull() {
        assertEquals(null, json.decodeFromString<CoreConfig>("{}").weatherUnits)
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
