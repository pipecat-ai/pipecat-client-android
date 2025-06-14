package ai.pipecat.client.transport

import ai.pipecat.client.types.ActionDescription
import ai.pipecat.client.types.ServiceConfig
import ai.pipecat.client.types.ServiceConfigDescription
import ai.pipecat.client.types.Value
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
        const val DescribeConfigResponse = "config-available"
        const val GetOrUpdateConfigResponse = "config"
        const val DescribeActionsResponse = "actions-available"
        const val ActionResponse = "action-response"
        const val UserTranscription = "user-transcription"
        const val BotTranscriptionLegacy = "tts-text"
        const val BotTranscription = "bot-transcription"
        const val UserStartedSpeaking = "user-started-speaking"
        const val UserStoppedSpeaking = "user-stopped-speaking"
        const val BotStartedSpeaking = "bot-started-speaking"
        const val BotStoppedSpeaking = "bot-stopped-speaking"
        const val Config = "config"
        const val ServerMessage = "server-message"

        // Service-specific
        const val BotLlmText = "bot-llm-text" // Streaming chunk/word, directly after LLM
        const val BotLlmStarted = "bot-llm-started"
        const val BotLlmStopped = "bot-llm-stopped"
        const val BotTtsText = "bot-tts-text"
        const val BotTtsStarted = "bot-tts-started"
        const val BotTtsStopped = "bot-tts-stopped"

        // Storage
        const val StorageItemStored = "storage-item-stored" // Item was stored
    }

    object Data {

        @Serializable
        data class BotReady(
            val version: String,
            val config: List<ServiceConfig>
        )

        @Serializable
        data class Error(
            val error: String
        )

        @Serializable
        data class DescribeConfigResponse(
            val config: List<ServiceConfigDescription>
        )

        @Serializable
        data class GetOrUpdateConfigResponse(
            val config: List<ServiceConfig>
        )

        @Serializable
        data class DescribeActionsResponse(
            val actions: List<ActionDescription>
        )

        @Serializable
        data class ActionResponse(
            val result: Value
        )

        @Serializable
        data class BotLLMTextData(
            val text: String
        )

        @Serializable
        data class BotTTSTextData(
            val text: String
        )

        @Serializable
        data class StorageItemStoredData(
            val action: String,
            val items: Value
        )
    }
}