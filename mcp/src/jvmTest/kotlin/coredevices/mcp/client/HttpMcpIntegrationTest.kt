package coredevices.mcp.client

import io.ktor.server.engine.EmbeddedServer
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class HttpMcpIntegrationTest {
    private val impl = Implementation(
        name = "TestClient",
        version = "1.0.0",
    )

    private fun buildUrl(port: Int, sse: Boolean): String {
        return "http://127.0.0.1:${port}/${if (sse) "sse" else ""}"
    }

    private fun startServer(): Pair<EmbeddedServer<*, *>, Int> {
        val server = runSseMcpServer(port = 0, wait = false)
        val port = runBlocking {
            server.engine.resolvedConnectors().single().port
        }
        return server to port
    }

    @Test
    fun basicClientConnectionTest() {
        val (server, port) = startServer()
        val integration = HttpMcpIntegration(
            "test",
            impl,
            buildUrl(port, true),
            HttpMcpProtocol.Sse,
        )

        try {
            val tools = runBlocking(Dispatchers.IO) {
                withTimeout(15.seconds) {
                    integration.connect()
                    integration.listTools()
                }
            }
            assertTrue(tools.isNotEmpty())
        } finally {
            try {
                runBlocking {
                    withTimeout(5.seconds) {
                        integration.close()
                    }
                }
            } finally {
                server.stop(100, 300)
            }
        }
    }

    // The test server advertises only the tools capability, so it stands in for any MCP
    // server that doesn't support prompts: listPrompts must return empty, not throw.
    @Test
    fun listPromptsEmptyWhenServerLacksPromptsCapability() {
        val (server, port) = startServer()
        val integration = HttpMcpIntegration(
            "test",
            impl,
            buildUrl(port, true),
            HttpMcpProtocol.Sse,
        )

        try {
            val prompts = runBlocking(Dispatchers.IO) {
                withTimeout(15.seconds) {
                    integration.connect()
                    integration.listPrompts()
                }
            }
            assertTrue(prompts.isEmpty())
        } finally {
            try {
                runBlocking {
                    withTimeout(5.seconds) {
                        integration.close()
                    }
                }
            } finally {
                server.stop(100, 300)
            }
        }
    }
}
