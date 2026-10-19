import java.util.Properties

val properties = Properties()
if (file("local.properties").exists()) {
    file("local.properties").inputStream().use { properties.load(it) }
}

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "libpebbleroot"

val fdroidBuild = providers.gradleProperty("fdroidBuild").map(String::toBooleanStrict).orElse(false).get()

include(":libpebble3")
include(":blobdbgen")
include(":blobannotations")
include(":composeApp")
include(":androidApp")
include(":pebble")
include(":util")
include(":mcp")
include(":index-ai")
include(":resampler")
if (!fdroidBuild) {
    include(":cactus")
    include(":cactus-native")
} else {
    include(":cactus-stubs")
    include(":firebase-stubs")
    include(":haversine-stubs")
    include(":health-stubs")
    include(":notifier-stubs")
}
include(":libindex")
include(":experimental")
include(":krisp-stubs")
