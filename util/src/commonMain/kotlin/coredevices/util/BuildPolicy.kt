package coredevices.util

fun cloudAccountAuthEnabled(): Boolean =
    CommonBuildKonfig.GOOGLE_AUTH_ENABLED ||
        CommonBuildKonfig.APPLE_AUTH_ENABLED ||
        CommonBuildKonfig.GITHUB_AUTH_ENABLED

fun thirdPartyDiagnosticsEnabledByDefault(): Boolean = !CommonBuildKonfig.FDROID_BUILD
