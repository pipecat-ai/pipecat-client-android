package ai.pipecat.client.support

import ai.pipecat.client.PipecatEventCallbacks
import ai.pipecat.client.transport.MsgServerToClient
import ai.pipecat.client.types.BotLLMSearchResponseData
import ai.pipecat.client.types.BotOutputData
import ai.pipecat.client.types.BotReadyData
import ai.pipecat.client.types.LLMFunctionCallData
import ai.pipecat.client.types.PipecatMetrics
import ai.pipecat.client.types.Transcript
import ai.pipecat.client.types.Value
import ai.pipecat.client.utils.JSON_INSTANCE
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Callbacks that record each call a server message brings, with the JSON of what it was given.
 */
class RecordingCallbacks : PipecatEventCallbacks() {

    data class Call(val name: String, val data: JsonElement?)

    val calls = mutableListOf<Call>()

    private fun record(name: String, data: JsonElement? = null) {
        calls.add(Call(name, data))
    }

    private fun <T> record(name: String, serializer: SerializationStrategy<T>, data: T) {
        record(name, JSON_INSTANCE.encodeToJsonElement(serializer, data))
    }

    override fun onBackendError(message: String) {
        record("onBackendError", JsonObject(mapOf("error" to JsonPrimitive(message))))
    }

    override fun onBotReady(data: BotReadyData) {
        record("onBotReady", BotReadyData.serializer(), data)
    }

    override fun onMetrics(data: PipecatMetrics) {
        record("onMetrics", PipecatMetrics.serializer(), data)
    }

    override fun onServerMessage(data: Value) {
        record("onServerMessage", Value.serializer(), data)
    }

    override fun onBotStartedSpeaking() {
        record("onBotStartedSpeaking")
    }

    override fun onBotStoppedSpeaking() {
        record("onBotStoppedSpeaking")
    }

    override fun onUserStartedSpeaking() {
        record("onUserStartedSpeaking")
    }

    override fun onUserStoppedSpeaking() {
        record("onUserStoppedSpeaking")
    }

    override fun onUserTranscript(data: Transcript) {
        record("onUserTranscript", Transcript.serializer(), data)
    }

    @Deprecated("Records the deprecated callback")
    override fun onBotTranscript(text: String) {
        record("onBotTranscript", JsonObject(mapOf("text" to JsonPrimitive(text))))
    }

    override fun onBotLLMText(data: MsgServerToClient.Data.BotLLMTextData) {
        record("onBotLLMText", MsgServerToClient.Data.BotLLMTextData.serializer(), data)
    }

    override fun onBotOutput(data: BotOutputData) {
        record("onBotOutput", BotOutputData.serializer(), data)
    }

    override fun onBotTTSText(data: MsgServerToClient.Data.BotTTSTextData) {
        record("onBotTTSText", MsgServerToClient.Data.BotTTSTextData.serializer(), data)
    }

    override fun onBotLLMStarted() {
        record("onBotLLMStarted")
    }

    override fun onBotLLMStopped() {
        record("onBotLLMStopped")
    }

    override fun onBotTTSStarted() {
        record("onBotTTSStarted")
    }

    override fun onBotTTSStopped() {
        record("onBotTTSStopped")
    }

    override fun onBotLLMSearchResponse(data: BotLLMSearchResponseData) {
        record("onBotLLMSearchResponse", BotLLMSearchResponseData.serializer(), data)
    }

    override fun onLLMFunctionCall(functionCallData: LLMFunctionCallData) {
        record("onLLMFunctionCall", LLMFunctionCallData.serializer(), functionCallData)
    }
}
