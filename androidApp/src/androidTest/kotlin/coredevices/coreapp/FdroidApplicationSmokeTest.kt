package coredevices.coreapp

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import coredevices.ExperimentalDevices
import coredevices.pebble.PebbleAppDelegate
import coredevices.util.CommonBuildKonfig
import coredevices.util.CoreConfigHolder
import coredevices.util.OAuthRedirectHandler
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.GlobalContext
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@MediumTest
class FdroidApplicationSmokeTest {
    private lateinit var context: Context
    private lateinit var packageManager: PackageManager

    @Before
    fun setUp() {
        assumeTrue("This smoke test applies only to the F-Droid build graph", CommonBuildKonfig.FDROID_BUILD)
        context = InstrumentationRegistry.getInstrumentation().targetContext
        packageManager = context.packageManager
    }

    @Test
    fun applicationStartsWithCompleteF_DroidDependencyGraph() {
        assertTrue(context.applicationContext is MainApplication)
        assertFalse(CommonBuildKonfig.INDEX_HARDWARE_ENABLED)
        assertFalse(CommonBuildKonfig.GOOGLE_AUTH_ENABLED)
        assertFalse(CommonBuildKonfig.APPLE_AUTH_ENABLED)
        assertFalse(CommonBuildKonfig.GITHUB_AUTH_ENABLED)

        val koin = GlobalContext.get()
        assertNotNull(koin.get<CoreConfigHolder>())
        assertNotNull(koin.get<ExperimentalDevices>())
        assertNotNull(koin.get<PebbleAppDelegate>())
        assertNotNull(koin.get<OAuthRedirectHandler>())
    }

    @Test
    fun mainActivityReachesResumedState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertTrue(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
        }
    }

    @Test
    fun mergedManifestExcludesUnavailableCapabilitiesAndKeepsPebbleLinks() {
        val packageInfo = packageInfo(
            PackageManager.GET_ACTIVITIES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_SERVICES or
                PackageManager.GET_PROVIDERS or
                PackageManager.GET_PERMISSIONS,
        )
        val requestedPermissions = packageInfo.requestedPermissions.orEmpty().toSet()
        val excludedPermissions = setOf(
            "android.permission.health.WRITE_STEPS",
            "android.permission.health.WRITE_HEART_RATE",
            "android.permission.health.WRITE_SLEEP",
            "android.permission.health.WRITE_EXERCISE",
            "android.permission.health.WRITE_EXERCISE_ROUTE",
            "android.permission.RECORD_AUDIO",
            "android.permission.SCHEDULE_EXACT_ALARM",
            "com.android.alarm.permission.SET_ALARM",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "com.beeper.android.permission.READ_PERMISSION",
            "com.beeper.android.permission.SEND_PERMISSION",
        )
        assertTrue(
            requestedPermissions.intersect(excludedPermissions).isEmpty(),
            "F-Droid manifest retained excluded permissions: " +
                requestedPermissions.intersect(excludedPermissions),
        )

        val packagedComponents = buildSet {
            packageInfo.activities.orEmpty().mapTo(this) { it.name }
            packageInfo.receivers.orEmpty().mapTo(this) { it.name }
            packageInfo.services.orEmpty().mapTo(this) { it.name }
        }
        val excludedComponents = setOf(
            "com.viktormykhailiv.kmp.health.HealthConnectPermissionActivity",
            "coredevices.coreapp.ViewPermissionUsageActivity",
            "coredevices.coreapp.HealthPermissionsRationaleActivity",
            "coredevices.ring.glance.VoiceWidgetReceiver",
            "coredevices.ring.reminders.ReminderReceiver",
            "coredevices.ring.reminders.ReminderBootReceiver",
            "coredevices.ring.service.InferenceForegroundService",
            "coredevices.ring.service.InferenceActivity",
            "coredevices.ring.ShareToIndexNoteActivity",
            "coredevices.ring.ShareToIndexReminderActivity",
        )
        assertTrue(
            packagedComponents.intersect(excludedComponents).isEmpty(),
            "F-Droid manifest retained excluded components: " +
                packagedComponents.intersect(excludedComponents),
        )
        assertTrue(MAIN_ACTIVITY in packagedComponents)
        assertTrue("coredevices.coreapp.PebbleService" in packagedComponents)

        val fileProvider = packageInfo.providers.orEmpty().singleOrNull {
            it.name == "androidx.core.content.FileProvider"
        }
        assertNotNull(fileProvider)
        assertTrue("${context.packageName}.fileprovider" in fileProvider.authority.orEmpty())

        val applicationInfo = applicationInfo(PackageManager.GET_META_DATA)
        assertFalse(
            applicationInfo.metaData?.containsKey("firebase_crashlytics_collection_enabled") == true,
        )

        assertTrue(MAIN_ACTIVITY in resolvingActivities("pebble://open"))
        assertTrue(MAIN_ACTIVITY in resolvingActivities("pebblejs://open"))
        assertFalse(MAIN_ACTIVITY in resolvingActivities("voiceapp://callback"))
        assertFalse(
            MAIN_ACTIVITY in resolvingActivities("https://cloud.repebble.com/githubAuth"),
        )
        assertFalse(
            MAIN_ACTIVITY in resolvingActivities(
                uri = "content://example/watch.pbw",
                mimeType = "application/octet-stream",
                browsable = false,
            ),
        )
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(flags: Int): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(flags.toLong()),
            )
        } else {
            packageManager.getPackageInfo(context.packageName, flags)
        }

    @Suppress("DEPRECATION")
    private fun applicationInfo(flags: Int): ApplicationInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.ApplicationInfoFlags.of(flags.toLong()),
            )
        } else {
            packageManager.getApplicationInfo(context.packageName, flags)
        }

    @Suppress("DEPRECATION")
    private fun resolvingActivities(
        uri: String,
        mimeType: String? = null,
        browsable: Boolean = true,
    ): Set<String> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setPackage(context.packageName)
            addCategory(Intent.CATEGORY_DEFAULT)
            if (browsable) addCategory(Intent.CATEGORY_BROWSABLE)
            if (mimeType == null) {
                data = Uri.parse(uri)
            } else {
                setDataAndType(Uri.parse(uri), mimeType)
            }
        }
        val flags = PackageManager.MATCH_DEFAULT_ONLY
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(flags.toLong()),
            )
        } else {
            packageManager.queryIntentActivities(intent, flags)
        }
        return resolved.mapTo(mutableSetOf()) { it.activityInfo.name }
    }

    private companion object {
        const val MAIN_ACTIVITY = "coredevices.coreapp.MainActivity"
    }
}
