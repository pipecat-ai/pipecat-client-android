package ai.pipecat.client.types

/**
 * The user's message as the bot's LLM receives it, as `user-llm-text` reports it.
 */
typealias UserLLMText = TextMessageData

/**
 * A chunk of text as the bot's LLM streams it, as `llm-raw-text` reports it (RTVI 2.2.0+).
 */
typealias LLMRawText = TextMessageData

/**
 * Text the bot's TTS speaks, as it speaks it, as `tts-raw-text` reports it (RTVI 2.2.0+).
 */
typealias TTSRawText = TextMessageData

/**
 * A line of the bot's log, as `system-log` reports it.
 */
typealias SystemLog = TextMessageData
