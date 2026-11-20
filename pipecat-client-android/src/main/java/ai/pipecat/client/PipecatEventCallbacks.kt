package ai.pipecat.client

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
import ai.pipecat.client.types.MediaDeviceInfo
import ai.pipecat.client.types.Participant
import ai.pipecat.client.types.PipecatMetrics
import ai.pipecat.client.types.STTRawTextData
import ai.pipecat.client.types.SystemLog
import ai.pipecat.client.types.TTSRawText
import ai.pipecat.client.types.Tracks
import ai.pipecat.client.types.Transcript
import ai.pipecat.client.types.TransportState
import ai.pipecat.client.types.UserInputData
import ai.pipecat.client.types.UserLLMText
import ai.pipecat.client.types.Value

/**
 * Callbacks invoked when changes occur in the session.
 */
@Suppress("unused")
abstract class PipecatEventCallbacks {

    /**
     * Invoked when the underlying transport has connected.
     */
    open fun onConnected() {}

    /**
     * Invoked when the underlying transport has disconnected.
     */
    open fun onDisconnected() {}

    /**
     * Invoked when the session state has changed.
     */
    open fun onTransportStateChanged(state: TransportState) {}

    /**
     * Invoked when the bot has connected to the session.
     */
    open fun onBotConnected(participant: Participant) {}

    /**
     * Invoked when the bot has indicated it is ready for commands.
     */
    open fun onBotReady(data: BotReadyData) {}

    /**
     * An error has occurred in the RTVI backend.
     */
    abstract fun onBackendError(message: String)

    /**
     * Invoked when the bot reports an error, with whether it stopped because of it. It comes
     * after [onBackendError], which carries only the error's text.
     */
    open fun onBotError(data: ErrorData) {}

    /**
     * Invoked when the bot has disconnected from the session.
     */
    open fun onBotDisconnected(participant: Participant) {}

    /**
     * Invoked when a participant has joined the session.
     */
    open fun onParticipantJoined(participant: Participant) {}

    /**
     * Invoked when a participant has left the session.
     */
    open fun onParticipantLeft(participant: Participant) {}

    /**
     * Invoked when the list of available cameras has changed.
     */
    open fun onAvailableCamsUpdated(cams: List<MediaDeviceInfo>) {}

    /**
     * Invoked when the list of available microphones has updated.
     */
    open fun onAvailableMicsUpdated(mics: List<MediaDeviceInfo>) {}

    /**
     * Invoked regularly with the volume of the locally captured audio, as the transport measures
     * it.
     *
     * Its default calls the deprecated [onUserAudioLevel] taking a `Float`, so an app overriding
     * that one still hears the level.
     */
    @Suppress("DEPRECATION")
    open fun onLocalAudioLevel(level: Float) = onUserAudioLevel(level)

    /**
     * Invoked regularly with the volume of the locally captured audio.
     */
    @Deprecated(
        "Use onLocalAudioLevel instead. This callback will be removed in a future version.",
        ReplaceWith("onLocalAudioLevel(level)")
    )
    open fun onUserAudioLevel(level: Float) {}

    /**
     * Invoked regularly with the audio volume of each remote participant.
     */
    open fun onRemoteAudioLevel(level: Float, participant: Participant) {}

    /**
     * Invoked when the bot starts talking.
     */
    open fun onBotStartedSpeaking() {}

    /**
     * Invoked when the bot stops talking.
     */
    open fun onBotStoppedSpeaking() {}

    /**
     * Invoked when the local user starts talking.
     */
    open fun onUserStartedSpeaking() {}

    /**
     * Invoked when the local user stops talking.
     */
    open fun onUserStoppedSpeaking() {}

    /**
     * Invoked when session metrics are received.
     */
    open fun onMetrics(data: PipecatMetrics) {}

    /**
     * Invoked when user transcript data is available.
     */
    @Deprecated("Use onUserInput() to show what the user said, or onSTTRawText() to inspect what the STT produced. This callback will be removed in a future version.")
    open fun onUserTranscript(data: Transcript) {}

    /**
     * Invoked when bot transcript data is available.
     */
    @Deprecated("Use onBotOutput() instead. This callback will be removed in a future version.")
    open fun onBotTranscript(text: String) {}

    /**
     * Invoked when the state of the input devices changes.
     */
    open fun onInputsUpdated(camera: Boolean, mic: Boolean) {}

    /**
     * Invoked when the set of available cam/mic tracks changes.
     */
    open fun onTracksUpdated(tracks: Tracks) {}

    /**
     * Invoked when text is generated by the bot LLM.
     */
    @Deprecated("Use onBotOutput() to show the bot's text, or onLLMRawText() to inspect what the LLM produced. This callback will be removed in a future version.")
    @Suppress("DEPRECATION")
    open fun onBotLLMText(data: MsgServerToClient.Data.BotLLMTextData) {}

    /**
     * Invoked when the bot emits output.
     */
    open fun onBotOutput(data: BotOutputData) {}

    /**
     * Invoked when the bot performs a sound, such as a laugh, or a pause, in expressive
     * conversation (RTVI 2.3.0+). Derived from [onBotOutput].
     */
    open fun onBotExpression(data: BotExpressionData) {}

    /**
     * Invoked when the bot's delivery changes, in expressive conversation (RTVI 2.3.0+). Derived
     * from [onBotOutput]: invoked only when it changes, and with a null delivery once the bot
     * stops speaking or the client disconnects.
     */
    open fun onBotDeliveryUpdated(data: BotDeliveryUpdatedData) {}

    /**
     * Invoked when the bot was interrupted and stopped what it was saying. The bot's text that
     * hadn't been spoken yet won't be.
     */
    open fun onBotInterrupted() {}

    /**
     * Invoked when the bot stops listening to the user, for example while it says something that
     * must not be interrupted.
     */
    open fun onUserMuteStarted() {}

    /**
     * Invoked when the bot listens to the user again.
     */
    open fun onUserMuteStopped() {}

    /**
     * Invoked with what the user said or typed: a transcription of their speech, text the client
     * sent, or a short acknowledgment the user said while the bot talks.
     */
    open fun onUserInput(data: UserInputData) {}

    /**
     * Invoked with the user's message as the bot's LLM receives it.
     */
    open fun onUserLLMText(data: UserLLMText) {}

    /**
     * Invoked when voice activity detection hears the user start speaking. It reacts to any
     * voice, sooner than [onUserStartedSpeaking], which comes when the bot decides the user's
     * turn started. The bot sends it only when enabled, with Pipecat's
     * `vad_user_speaking_enabled` setting.
     */
    open fun onVADUserStartedSpeaking() {}

    /**
     * Invoked when voice activity detection hears the user stop speaking. The bot sends it only
     * when enabled, with Pipecat's `vad_user_speaking_enabled` setting.
     */
    open fun onVADUserStoppedSpeaking() {}

    /**
     * Invoked regularly with how loud the user's audio is, from 0 (quiet) to 1 (loud), as the bot
     * received it. Unlike [onLocalAudioLevel], which the transport measures, the bot measures it.
     * The bot sends it only when enabled, with Pipecat's `user_audio_level_enabled` setting.
     */
    open fun onUserAudioLevel(data: AudioLevelData) {}

    /**
     * Invoked regularly with how loud the bot's audio is, from 0 (quiet) to 1 (loud), as the bot
     * produced it. Unlike [onRemoteAudioLevel], which the transport measures, the bot measures
     * it. The bot sends it only when enabled, with Pipecat's `bot_audio_level_enabled` setting.
     */
    open fun onBotAudioLevel(data: AudioLevelData) {}

    /**
     * Invoked with a line of the bot's log, for debugging. The bot sends it only when enabled,
     * with Pipecat's `system_logs_enabled` setting.
     */
    open fun onSystemLog(data: SystemLog) {}

    /**
     * Invoked with the marker the bot's LLM put in a response, outside the text it speaks, such
     * as one saying whether the user's turn was complete. The bot sends it only when enabled,
     * with Pipecat's `bot_llm_marker_enabled` setting.
     */
    open fun onBotLLMMarker(data: BotLLMMarkerData) {}

    /**
     * Invoked with a transcription of the user's speech as the bot's STT produces it, including
     * interim transcriptions that may still change (RTVI 2.2.0+). The bot sends it only when
     * enabled, with Pipecat's `stt_raw_text_enabled` setting.
     */
    open fun onSTTRawText(data: STTRawTextData) {}

    /**
     * Invoked with text exactly as the bot's LLM streams it, a chunk at a time (RTVI 2.2.0+). The
     * bot sends it only when enabled, with Pipecat's `llm_raw_text_enabled` setting.
     */
    open fun onLLMRawText(data: LLMRawText) {}

    /**
     * Invoked with text the bot's TTS speaks, as it speaks it (RTVI 2.2.0+). The bot sends it
     * only when enabled, with Pipecat's `tts_raw_text_enabled` setting.
     */
    open fun onTTSRawText(data: TTSRawText) {}

    /**
     * Invoked when text is spoken by the bot.
     */
    @Deprecated("Use onBotOutput() to show the bot's text, or onTTSRawText() to inspect what the TTS produced. This callback will be removed in a future version.")
    @Suppress("DEPRECATION")
    open fun onBotTTSText(data: MsgServerToClient.Data.BotTTSTextData) {}

    /**
     * Invoked when the bot starts generating LLM text.
     */
    open fun onBotLLMStarted() {}

    /**
     * Invoked when the bot stops generating LLM text.
     */
    open fun onBotLLMStopped() {}

    /**
     * Invoked when the bot starts generating TTS output.
     */
    open fun onBotTTSStarted() {}

    /**
     * Invoked when the bot stops generating TTS output.
     */
    open fun onBotTTSStopped() {}

    /**
     * Invoked when we receive a server message from the bot.
     */
    open fun onServerMessage(data: Value) {}

    /**
     * Invoked when the bot performs a web search.
     */
    open fun onBotLLMSearchResponse(data: BotLLMSearchResponseData) {}

    /**
     * Invoked when the LLM starts calling a function. It carries the function's name when the bot
     * reports it.
     */
    open fun onLLMFunctionCallStarted(data: LLMFunctionCallStartedData) {}

    /**
     * Invoked when the LLM is calling a function. How much it carries depends on the bot's report
     * level for the function: only the call's id, or also the function's name and arguments.
     *
     * To run the function in the app and answer the bot with its result, register a handler with
     * [PipecatClient.registerFunctionCallHandler].
     */
    open fun onLLMFunctionCallInProgress(data: LLMFunctionCallInProgressData) {}

    /**
     * Invoked when a function call finishes, or is cancelled.
     */
    open fun onLLMFunctionCallStopped(data: LLMFunctionCallStoppedData) {}

    /**
     * Invoked when the bot makes a function call request to the client.
     *
     * Only bots that send the deprecated `llm-function-call` message invoke it.
     */
    @Deprecated("Use registerFunctionCallHandler() or onLLMFunctionCallInProgress() instead. This callback will be removed in a future version.")
    open fun onLLMFunctionCall(functionCallData: LLMFunctionCallData) {}
}
