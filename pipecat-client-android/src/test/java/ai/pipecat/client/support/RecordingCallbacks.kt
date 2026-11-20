package ai.pipecat.client.support

import ai.pipecat.client.PipecatEventCallbacks
import ai.pipecat.client.transport.MsgServerToClient
import ai.pipecat.client.types.AudioLevelData
import ai.pipecat.client.types.BotDeliveryUpdatedData
import ai.pipecat.client.types.BotExpressionData
import ai.pipecat.client.types.BotLLMMarkerData
import ai.pipecat.client.types.BotLLMSearchResponseData
import ai.pipecat.client.types.BotOutputData
import ai.pipecat.client.types.BotReadyData
import ai.pipecat.client.types.ErrorData
import ai.pipecat.client.types.LLMRawText
import ai.pipecat.client.types.LLMFunctionCallData
import ai.pipecat.client.types.LLMFunctionCallInProgressData
import ai.pipecat.client.types.LLMFunctionCallStartedData
import ai.pipecat.client.types.LLMFunctionCallStoppedData
import ai.pipecat.client.types.PipecatMetrics
import ai.pipecat.client.types.STTRawTextData
import ai.pipecat.client.types.SystemLog
import ai.pipecat.client.types.TTSRawText
import ai.pipecat.client.types.Transcript
import ai.pipecat.client.types.UserInputData
import ai.pipecat.client.types.UserLLMText
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

    /**
     * A call, with the JSON of what it was given, and what it was given when that isn't JSON.
     */
    data class Call(val name: String, val data: JsonElement?, val value: Any? = null)

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

    override fun onBotError(data: ErrorData) {
        record("onBotError", ErrorData.serializer(), data)
    }

    override fun onBotExpression(data: BotExpressionData) {
        calls.add(Call("onBotExpression", null, data))
    }

    override fun onBotDeliveryUpdated(data: BotDeliveryUpdatedData) {
        calls.add(Call("onBotDeliveryUpdated", null, data))
    }

    override fun onBotInterrupted() {
        record("onBotInterrupted")
    }

    override fun onUserMuteStarted() {
        record("onUserMuteStarted")
    }

    override fun onUserMuteStopped() {
        record("onUserMuteStopped")
    }

    override fun onUserInput(data: UserInputData) {
        record("onUserInput", UserInputData.serializer(), data)
    }

    override fun onUserLLMText(data: UserLLMText) {
        record("onUserLLMText", UserLLMText.serializer(), data)
    }

    override fun onVADUserStartedSpeaking() {
        record("onVADUserStartedSpeaking")
    }

    override fun onVADUserStoppedSpeaking() {
        record("onVADUserStoppedSpeaking")
    }

    override fun onUserAudioLevel(data: AudioLevelData) {
        record("onUserAudioLevel", AudioLevelData.serializer(), data)
    }

    override fun onBotAudioLevel(data: AudioLevelData) {
        record("onBotAudioLevel", AudioLevelData.serializer(), data)
    }

    override fun onSystemLog(data: SystemLog) {
        record("onSystemLog", SystemLog.serializer(), data)
    }

    override fun onBotLLMMarker(data: BotLLMMarkerData) {
        record("onBotLLMMarker", BotLLMMarkerData.serializer(), data)
    }

    override fun onSTTRawText(data: STTRawTextData) {
        record("onSTTRawText", STTRawTextData.serializer(), data)
    }

    override fun onLLMRawText(data: LLMRawText) {
        record("onLLMRawText", LLMRawText.serializer(), data)
    }

    override fun onTTSRawText(data: TTSRawText) {
        record("onTTSRawText", TTSRawText.serializer(), data)
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

    override fun onLLMFunctionCallStarted(data: LLMFunctionCallStartedData) {
        record("onLLMFunctionCallStarted", LLMFunctionCallStartedData.serializer(), data)
    }

    override fun onLLMFunctionCallInProgress(data: LLMFunctionCallInProgressData) {
        record("onLLMFunctionCallInProgress", LLMFunctionCallInProgressData.serializer(), data)
    }

    override fun onLLMFunctionCallStopped(data: LLMFunctionCallStoppedData) {
        record("onLLMFunctionCallStopped", LLMFunctionCallStoppedData.serializer(), data)
    }

    @Deprecated("Records the deprecated callback")
    override fun onLLMFunctionCall(functionCallData: LLMFunctionCallData) {
        record("onLLMFunctionCall", LLMFunctionCallData.serializer(), functionCallData)
    }
}
