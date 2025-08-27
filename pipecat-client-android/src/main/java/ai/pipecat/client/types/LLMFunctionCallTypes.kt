package ai.pipecat.client.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

fun interface LLMFunctionCallHandler {
    fun handleFunctionCall(
        data: LLMFunctionCallData,
        onResult: (Value) -> Unit
    )
}

@Serializable
data class LLMFunctionCallData(
    @SerialName("function_name")
    val functionName: String,
    @SerialName("tool_call_id")
    val toolCallID: String,
    val args: JsonElement
)

@Serializable
data class LLMFunctionCallResult(
    @SerialName("function_name")
    val functionName: String,
    @SerialName("tool_call_id")
    val toolCallID: String,
    val arguments: JsonElement,
    val result: JsonElement
)