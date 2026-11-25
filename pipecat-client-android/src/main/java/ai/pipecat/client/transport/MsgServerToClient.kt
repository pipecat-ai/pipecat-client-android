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
        /** Deprecated: bots send [UserInput] and [SttRawText] instead. */
        const val UserTranscription = "user-transcription"
        const val BotTranscriptionLegacy = "tts-text"

        /** Deprecated: bots send [BotOutput] instead. */
        const val BotTranscription = "bot-transcription"
        const val UserStartedSpeaking = "user-started-speaking"
        const val UserStoppedSpeaking = "user-stopped-speaking"
        const val BotStartedSpeaking = "bot-started-speaking"
        const val BotStoppedSpeaking = "bot-stopped-speaking"
        const val ServerMessage = "server-message"
        const val ServerResponse = "server-response"
        const val Metrics = "metrics"
        const val LlmFunctionCallStarted = "llm-function-call-started"
        const val LlmFunctionCallInProgress = "llm-function-call-in-progress"
        const val LlmFunctionCallStopped = "llm-function-call-stopped"

        /** Deprecated: bots send [LlmFunctionCallInProgress] instead. */
        const val LlmFunctionCall = "llm-function-call"
        const val AppendToContextResult = "append-to-context-result"
        const val BotLlmSearchResponse = "bot-llm-search-response"
        /** Deprecated: bots send [LlmRawText] instead. */
        const val BotLlmText = "bot-llm-text"
        const val BotLlmStarted = "bot-llm-started"
        const val BotLlmStopped = "bot-llm-stopped"
        const val BotOutput = "bot-output"
        /** Deprecated: bots send [TtsRawText] instead. */
        const val BotTtsText = "bot-tts-text"
        const val BotTtsStarted = "bot-tts-started"
        const val BotTtsStopped = "bot-tts-stopped"

        /** The bot was interrupted and stopped what it was saying. */
        const val BotInterrupted = "bot-interrupted"

        /** The bot stopped listening to the user. */
        const val UserMuteStarted = "user-mute-started"

        /** The bot listens to the user again. */
        const val UserMuteStopped = "user-mute-stopped"

        /** What the user said or typed. */
        const val UserInput = "user-input"

        /** The user's message as the bot's LLM receives it. */
        const val UserLlmText = "user-llm-text"

        /** Voice activity detection heard the user start speaking. */
        const val VadUserStartedSpeaking = "vad-user-started-speaking"

        /** Voice activity detection heard the user stop speaking. */
        const val VadUserStoppedSpeaking = "vad-user-stopped-speaking"

        /** How loud the user's audio is, as the bot measures it. */
        const val UserAudioLevel = "user-audio-level"

        /** How loud the bot's audio is, as the bot measures it. */
        const val BotAudioLevel = "bot-audio-level"

        /** A line of the bot's log. */
        const val SystemLog = "system-log"

        /** The marker the bot's LLM put in a response. */
        const val BotLlmMarker = "bot-llm-marker"

        /** A transcription as the bot's STT produces it. */
        const val SttRawText = "stt-raw-text"

        /** Text as the bot's LLM streams it. */
        const val LlmRawText = "llm-raw-text"

        /** Text the bot's TTS speaks, as it speaks it. */
        const val TtsRawText = "tts-raw-text"
    }

    object Data {

        @Deprecated("Use ErrorData or ErrorResponseData instead. This type will be removed in a future version.")
        @Serializable
        data class Error(
            val error: String
        )

        /** The data of `bot-llm-text`, which only the deprecated [ai.pipecat.client.PipecatEventCallbacks.onBotLLMText] takes. */
        @Deprecated("Use TextMessageData instead. This type will be removed in a future version.")
        @Serializable
        data class BotLLMTextData(
            val text: String
        )

        /** The data of `bot-tts-text`, which only the deprecated [ai.pipecat.client.PipecatEventCallbacks.onBotTTSText] takes. */
        @Deprecated("Use TextMessageData instead. This type will be removed in a future version.")
        @Serializable
        data class BotTTSTextData(
            val text: String
        )

    }
}