package ai.pipecat.client.transport

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * An RTVI control message sent to the client.
 */
@Serializable
data class MsgServerToClient(
    val id: String? = null,
    val label: String,
    val type: String,
    val data: JsonElement = JsonNull
) {
    object Type {
        const val BotReady = "bot-ready"
        const val Error = "error"
        const val ErrorResponse = "error-response"
        const val UserTranscription = "user-transcription"
        const val BotTranscriptionLegacy = "tts-text"
        const val BotTranscription = "bot-transcription"
        const val UserStartedSpeaking = "user-started-speaking"
        const val UserStoppedSpeaking = "user-stopped-speaking"
        const val BotStartedSpeaking = "bot-started-speaking"
        const val BotStoppedSpeaking = "bot-stopped-speaking"
        const val ServerMessage = "server-message"
        const val ServerResponse = "server-response"
        const val Metrics = "metrics"
        const val LlmFunctionCall = "llm-function-call"
        const val AppendToContextResult = "append-to-context-result"
        const val BotLlmSearchResponse = "bot-llm-search-response"
        const val BotLlmText = "bot-llm-text" // Streaming chunk/word, directly after LLM
        const val BotLlmStarted = "bot-llm-started"
        const val BotLlmStopped = "bot-llm-stopped"
        const val BotTtsText = "bot-tts-text"
        const val BotTtsStarted = "bot-tts-started"
        const val BotTtsStopped = "bot-tts-stopped"
    }

    object Data {

        @Serializable
        data class Error(
            val error: String
        )

        @Serializable
        data class BotLLMTextData(
            val text: String
        )

        @Serializable
        data class BotTTSTextData(
            val text: String
        )
    }
}