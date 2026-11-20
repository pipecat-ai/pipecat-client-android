package ai.pipecat.client

import ai.pipecat.client.support.ClientTestCase
import ai.pipecat.client.support.same
import ai.pipecat.client.transport.MsgClientToServer
import ai.pipecat.client.types.LLMFunctionCallData
import ai.pipecat.client.types.LLMFunctionCallHandler
import ai.pipecat.client.types.Value
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FunctionCallTests : ClientTestCase() {

    private val handled = mutableListOf<LLMFunctionCallData>()

    @Before
    fun registerHandler() {
        client.registerFunctionCallHandler("get_weather", LLMFunctionCallHandler { data, onResult ->
            handled.add(data)
            onResult(Value.Object("sunny" to Value.Bool(true)))
        })
    }

    private val results: List<JsonElement>
        get() = transport.sent.filter { it.type == MsgClientToServer.Type.LlmFunctionCallResult }
            .mapNotNull { it.data }

    @Test
    fun aHandlerAnswersACallInProgress() {
        receive("llm-function-call-in-progress", buildJsonObject {
            put("tool_call_id", "call_1")
            put("function_name", "get_weather")
            put("arguments", buildJsonObject { put("city", "Lisbon") })
        })

        assertEquals(listOf("call_1"), handled.map { it.toolCallID })
        assertEquals(1, results.size)
        assertTrue(same(results[0], buildJsonObject {
            put("function_name", "get_weather")
            put("tool_call_id", "call_1")
            put("arguments", buildJsonObject { put("city", "Lisbon") })
            put("result", buildJsonObject { put("sunny", true) })
        }))
        assertEquals(listOf("onLLMFunctionCallInProgress"), callbacks.calls.map { it.name })
    }

    @Test
    fun aHandlerAnswersACallInProgressWithoutArguments() {
        receive("llm-function-call-in-progress", buildJsonObject {
            put("tool_call_id", "call_1")
            put("function_name", "get_weather")
        })

        assertEquals(listOf(JsonObject(emptyMap())), results.map { it.jsonObject["arguments"] })
    }

    @Test
    fun noHandlerRunsForACallWithoutItsFunctionName() {
        receive("llm-function-call-in-progress", buildJsonObject { put("tool_call_id", "call_1") })

        assertEquals(0, handled.size)
        assertEquals(emptyList<JsonElement>(), results)
        assertEquals(listOf("onLLMFunctionCallInProgress"), callbacks.calls.map { it.name })
    }

    @Test
    fun aHandlerAnswersTheDeprecatedFunctionCall() {
        receive("llm-function-call", buildJsonObject {
            put("function_name", "get_weather")
            put("tool_call_id", "call_1")
            put("args", buildJsonObject { put("city", "Lisbon") })
        })

        assertEquals(listOf("call_1"), handled.map { it.toolCallID })
        assertEquals(listOf("call_1"), results.map { it.jsonObject["tool_call_id"]?.jsonPrimitive?.content })
        assertEquals(listOf("onLLMFunctionCall"), callbacks.calls.map { it.name })
    }

    @Test
    fun theCallsLifecycleReachesTheApp() {
        receive("llm-function-call-started", buildJsonObject { put("function_name", "get_weather") })
        receive("llm-function-call-stopped", buildJsonObject {
            put("tool_call_id", "call_1")
            put("cancelled", false)
            put("function_name", "get_weather")
        })

        assertEquals(
            listOf("onLLMFunctionCallStarted", "onLLMFunctionCallStopped"),
            callbacks.calls.map { it.name }
        )
    }
}
