package io.rebble.libpebblecommon.js

import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.FakeAppMessages
import io.rebble.libpebblecommon.connection.FakeLibPebble
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.fakeWatch
import io.rebble.libpebblecommon.database.entity.LockerEntry
import io.rebble.libpebblecommon.metadata.pbw.appinfo.PbwAppInfo
import io.rebble.libpebblecommon.metadata.pbw.appinfo.Resources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

abstract class PKJSRunnerTests {
    protected abstract fun createJsRunner(
        libPebble: LibPebble,
        scope: CoroutineScope,
        appInfo: PbwAppInfo,
        lockerEntry: LockerEntry,
        jsPath: Path,
        device: CompanionAppDevice,
        urlOpenRequests: Channel<String>,
        logMessages: Channel<String>,
    ): JsRunner

    companion object {
        private val RUNNER_TIMEOUT = 5.seconds

        private val APPINFO = PbwAppInfo(
            uuid = Uuid.NIL.toString(),
            shortName = "Test App",
            versionLabel = "1.0",
            resources = Resources(emptyList()),
        )

        private val LOCKERENTRY = LockerEntry(
            id = Uuid.NIL,
            version = "1.0",
            title = "Test App",
            type = "watchapp",
            developerName = "Test Developer",
            configurable = false,
            pbwVersionCode = "1",
            platforms = emptyList(),
        )
    }

    private fun writeJS(js: String): Path {
        val tempDir = Path(SystemTemporaryDirectory, "pkjs-test")
        val jsPath = Path(tempDir, "${Uuid.random()}.js")
        SystemFileSystem.createDirectories(tempDir)
        SystemFileSystem.sink(jsPath).buffered().use {
            it.writeString(js)
        }
        return jsPath
    }

    private fun makeRunner(
        js: String,
        uuid: Uuid,
        scope: CoroutineScope,
        appMessages: FakeAppMessages = FakeAppMessages()
    ): JsRunner {
        val libPebble = FakeLibPebble()
        val watch = fakeWatch(connected = true) as ConnectedPebbleDevice
        return createJsRunner(
            libPebble,
            scope,
            APPINFO.copy(uuid = uuid.toString()),
            LOCKERENTRY.copy(id = uuid),
            writeJS(js),
            CompanionAppDevice(
                watch.identifier,
                watch.watchInfo,
                appMessages
            ),
            Channel(Channel.UNLIMITED),
            Channel(
                capacity = 2,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            ),
        )
    }

    private suspend fun <T> withRunner(
        js: String,
        uuid: Uuid,
        block: suspend (JsRunner) -> T,
    ): T {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val runner = makeRunner(js, uuid, scope)
        return try {
            runner.start()
            withTimeout(RUNNER_TIMEOUT) {
                runner.readyState.first { it }
            }
            block(runner)
        } finally {
            try {
                runner.stop()
            } finally {
                scope.cancel()
                runCatching {
                    SystemFileSystem.delete(runner.jsPath, mustExist = false)
                }
            }
        }
    }

    private fun jsPrimitiveContent(value: Any?): String? = when (value) {
        null -> null
        is Boolean -> value.toString()
        is String -> runCatching {
            Json.decodeFromString<JsonElement>(value)
        }.getOrNull()?.let {
            if (it is JsonNull) null else it.jsonPrimitive.content
        } ?: value
        else -> error("Unexpected result type: ${value::class}")
    }

    private suspend fun JsRunner.assertJsValue(
        expression: String,
        expected: String?,
        message: String? = null,
    ) {
        var actual: String? = null
        var evaluated = false
        val matched = withTimeoutOrNull(RUNNER_TIMEOUT) {
            while (true) {
                actual = jsPrimitiveContent(evalWithResult(expression))
                evaluated = true
                if (actual == expected) return@withTimeoutOrNull true
                delay(10.milliseconds)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } ?: false
        if (!matched) {
            val detail = if (evaluated) {
                "last value was <$actual>"
            } else {
                "the expression did not complete"
            }
            fail("${message ?: "Unexpected result for: $expression"}; expected <$expected>, $detail")
        }
    }

    open fun testJSExecution() {
        runBlocking {
            withRunner("", Uuid.random()) { runner ->
                runner.eval("window.test = true;")
                runner.assertJsValue("window.test;", "true")
            }
        }
    }

    open fun testJSReady() {
        runBlocking {
            withRunner(
                """
                Pebble.addEventListener('ready', function() {
                  window.readyConfirmed = true;
                });
                """.trimIndent(),
                Uuid.random(),
            ) { runner ->
                runner.assertJsValue(
                    "window.readyConfirmed;",
                    "true",
                    "ready event handler should set window.readyConfirmed to true",
                )
            }
        }
    }

    open fun testLocalStoragePersistence() {
        val js = """
            Pebble.addEventListener('ready', function() {
                localStorage.setItem('testKey', 'testValue');
                window.localStorage.testPropKey = 'testPropValue';
            });
        """.trimIndent()
        val uuid = Uuid.random()

        runBlocking {
            withRunner(js, uuid) { runner ->
                runner.assertJsValue("localStorage.getItem('testKey');", "testValue")
                runner.assertJsValue("localStorage.testPropKey;", "testPropValue")
            }

            withRunner("", uuid) { runner ->
                runner.assertJsValue("localStorage.getItem('testKey');", "testValue")
                runner.assertJsValue("localStorage.testPropKey;", "testPropValue")
            }
        }
    }

    open fun testLocalStorageSandbox() {
        val js = """
            Pebble.addEventListener('ready', function() {
                localStorage.setItem('testKey', 'testValue');
                window.localStorage.testPropKey = 'testPropValue';
            });
        """.trimIndent()

        runBlocking {
            withRunner(js, Uuid.random()) { runner ->
                runner.assertJsValue("localStorage.getItem('testKey');", "testValue")
                runner.assertJsValue("localStorage.testPropKey;", "testPropValue")
            }

            withRunner("", Uuid.random()) { runner ->
                runner.assertJsValue("localStorage.getItem('testKey') === null;", "true")
                runner.assertJsValue(
                    "typeof window.localStorage.testPropKey === 'undefined';",
                    "true",
                )
            }
        }
    }

    /**
     * Testing for reproduction of undefined behaviour from the old app where localStorage is accessible
     * before the 'ready' event is fired.
     * see MOB-2364
     */
    open fun testLocalStorageEarlyExecution() {
        val uuid = Uuid.random()

        runBlocking {
            withRunner(
                """
                console.log(window.__localStorageShimmed);
                window.overrideAtScriptTime = window.__localStorageShimmed;
                localStorage.setItem('testKey', 'testValue');
                """.trimIndent(),
                uuid,
            ) { runner ->
                runner.assertJsValue(
                    "localStorage.getItem('testKey');",
                    "testValue",
                    "Early localStorage.setItem should save data to shimmed persistent storage",
                )
                runner.assertJsValue(
                    "window.overrideAtScriptTime;",
                    "true",
                    "window.__localStorageShimmed should be true at script execution time",
                )
            }

            withRunner("window.result = localStorage.getItem('testKey');", uuid) { runner ->
                runner.assertJsValue(
                    "window.result;",
                    "testValue",
                    "Early localStorage.getItem should return persisted data from shimmed storage",
                )
            }
        }
    }
}
