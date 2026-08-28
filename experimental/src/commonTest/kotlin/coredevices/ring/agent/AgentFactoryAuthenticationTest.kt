package coredevices.ring.agent

import com.russhwolf.settings.MapSettings
import coredevices.indexai.data.entity.mcp_sandbox.McpSandboxGroupEntity
import coredevices.indexai.data.entity.mcp_sandbox.SandboxModelType
import coredevices.ring.database.Preferences
import coredevices.ring.database.PreferencesImpl
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class AgentFactoryAuthenticationTest {
    @BeforeTest
    fun setUp() {
        stopKoin()
        startKoin {
            modules(
                module {
                    single<Preferences> {
                        PreferencesImpl(
                            MapSettings("llm_mode" to LlmMode.RemoteOnly.id),
                        )
                    }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun remoteOnlyRequiresAuthenticationBeforeResolvingRemoteAgent() {
        val error = assertFailsWith<AgentAuthenticationException> {
            AgentFactory(isSignedIn = { false }).createForChatMode(ChatMode.Normal)
        }

        assertEquals("User must be authenticated to use online LLM agent", error.message)
    }

    @Test
    fun searchRequiresAuthenticationBeforeResolvingSearchAgent() {
        val error = assertFailsWith<AgentAuthenticationException> {
            AgentFactory(isSignedIn = { false }).createForChatMode(ChatMode.Search)
        }

        assertEquals("User must be authenticated to use search mode", error.message)
    }

    @Test
    fun remoteMcpModesRequireAuthenticationBeforeResolvingMcpAgent() {
        listOf(SandboxModelType.Default, SandboxModelType.HighCapability).forEach { modelType ->
            val error = assertFailsWith<AgentAuthenticationException> {
                AgentFactory(isSignedIn = { false }).createForChatMode(
                    ChatMode.McpSandbox(
                        McpSandboxGroupEntity(
                            title = "Test",
                            modelType = modelType,
                        ),
                    ),
                )
            }

            assertEquals("User must be authenticated to use MCP sandbox mode", error.message)
        }
    }
}
