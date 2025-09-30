package ai.pipecat.client

import ai.pipecat.client.result.Future
import ai.pipecat.client.result.Promise
import ai.pipecat.client.result.RTVIError
import ai.pipecat.client.result.catchExceptions
import ai.pipecat.client.result.resolvedPromiseErr
import ai.pipecat.client.result.resolvedPromiseOk
import ai.pipecat.client.transport.MsgClientToServer
import ai.pipecat.client.transport.MsgServerToClient
import ai.pipecat.client.transport.Transport
import ai.pipecat.client.transport.TransportContext
import ai.pipecat.client.types.APIRequest
import ai.pipecat.client.types.AppendToContextResultData
import ai.pipecat.client.types.BotReadyData
import ai.pipecat.client.types.DataMessage
import ai.pipecat.client.types.LLMContextMessage
import ai.pipecat.client.types.LLMFunctionCallData
import ai.pipecat.client.types.LLMFunctionCallHandler
import ai.pipecat.client.types.LLMFunctionCallResult
import ai.pipecat.client.types.MediaDeviceId
import ai.pipecat.client.types.SendTextOptions
import ai.pipecat.client.types.Transcript
import ai.pipecat.client.types.TransportState
import ai.pipecat.client.types.Value
import ai.pipecat.client.utils.JSON_INSTANCE
import ai.pipecat.client.utils.ResponseWaiters
import ai.pipecat.client.utils.ThreadRef
import ai.pipecat.client.utils.post
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

internal const val RTVI_PROTOCOL_VERSION = "1.0.0"

/**
 * A Pipecat client. Connects to an RTVI backend and handles bidirectional audio and video
 * streaming.
 *
 * The client must be cleaned up using the [release] method when it is no longer required.
 *
 * @param transport Transport for media streaming.
 * @param callbacks Callbacks invoked when changes occur in the voice session.
 * @param options Additional options for configuring the client and backend.
 */
@Suppress("unused")
open class PipecatClient<TransportType : Transport<ConnectParams>, ConnectParams>(
    private val transport: TransportType,
    private val options: PipecatClientOptions,
) {
    companion object {
        private const val TAG = "PipecatClient"
    }

    /**
     * The thread used by the PipecatClient for callbacks and other operations.
     */
    val thread = ThreadRef.forCurrent()

    private val responseWaiters = ResponseWaiters(thread)
    private val functionCallHandlers = mutableMapOf<String, LLMFunctionCallHandler>()

    private val transportCtx = object : TransportContext {

        override val options
            get() = this@PipecatClient.options

        override val callbacks
            get() = options.callbacks

        override val thread = this@PipecatClient.thread

        override fun onConnectionEnd() {
            thread.runOnThread {
                responseWaiters.clearAll()
                connection?.ready?.resolveErr(RTVIError.OperationCancelled)
                connection = null
            }
        }

        override fun onMessage(msg: MsgServerToClient) = thread.runOnThread {

            try {
                when (msg.type) {
                    MsgServerToClient.Type.BotReady -> {

                        val data =
                            JSON_INSTANCE.decodeFromJsonElement<BotReadyData>(msg.data)

                        this@PipecatClient.transport.setState(TransportState.Ready)

                        connection?.ready?.resolveOk(Unit)

                        callbacks.onBotReady(data)
                    }

                    MsgServerToClient.Type.Error -> {
                        val data =
                            JSON_INSTANCE.decodeFromJsonElement<MsgServerToClient.Data.Error>(msg.data)
                        callbacks.onBackendError(data.error)
                    }

                    MsgServerToClient.Type.ServerResponse,
                    MsgServerToClient.Type.AppendToContextResult -> {
                        try {
                            responseWaiters.resolve(id = msg.id!!, data = msg.data)
                        } catch (e: Exception) {
                            Log.e(TAG, "Got exception handling server response", e)
                            callbacks.onBackendError("Got exception while handling server response, see log (id = ${msg.id})")
                        }
                    }

                    MsgServerToClient.Type.ErrorResponse -> {

                        val data =
                            JSON_INSTANCE.decodeFromJsonElement<MsgServerToClient.Data.Error>(
                                msg.data
                            )

                        try {
                            responseWaiters.reject(
                                id = msg.id!!,
                                error = RTVIError.ErrorResponse(data.error)
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Got exception handling error response", e)
                            callbacks.onBackendError(data.error)
                        }
                    }

                    MsgServerToClient.Type.UserTranscription -> {
                        val data = JSON_INSTANCE.decodeFromJsonElement<Transcript>(msg.data)
                        callbacks.onUserTranscript(data)
                    }

                    MsgServerToClient.Type.BotTranscription,
                    MsgServerToClient.Type.BotTranscriptionLegacy -> {
                        val text = (msg.data.jsonObject.get("text") as JsonPrimitive).content
                        callbacks.onBotTranscript(text)
                    }

                    MsgServerToClient.Type.UserStartedSpeaking -> {
                        callbacks.onUserStartedSpeaking()
                    }

                    MsgServerToClient.Type.UserStoppedSpeaking -> {
                        callbacks.onUserStoppedSpeaking()
                    }

                    MsgServerToClient.Type.BotStartedSpeaking -> {
                        callbacks.onBotStartedSpeaking()
                    }

                    MsgServerToClient.Type.BotStoppedSpeaking -> {
                        callbacks.onBotStoppedSpeaking()
                    }

                    MsgServerToClient.Type.BotLlmText -> {
                        val data: MsgServerToClient.Data.BotLLMTextData =
                            JSON_INSTANCE.decodeFromJsonElement(msg.data)

                        callbacks.onBotLLMText(data)
                    }

                    MsgServerToClient.Type.BotTtsText -> {
                        val data: MsgServerToClient.Data.BotTTSTextData =
                            JSON_INSTANCE.decodeFromJsonElement(msg.data)

                        callbacks.onBotTTSText(data)
                    }

                    MsgServerToClient.Type.BotLlmStarted -> callbacks.onBotLLMStarted()
                    MsgServerToClient.Type.BotLlmStopped -> callbacks.onBotLLMStopped()

                    MsgServerToClient.Type.BotTtsStarted -> callbacks.onBotTTSStarted()
                    MsgServerToClient.Type.BotTtsStopped -> callbacks.onBotTTSStopped()

                    MsgServerToClient.Type.ServerMessage -> {
                        callbacks.onServerMessage(JSON_INSTANCE.decodeFromJsonElement(msg.data))
                    }

                    MsgServerToClient.Type.Metrics -> {
                        callbacks.onMetrics(JSON_INSTANCE.decodeFromJsonElement(msg.data))
                    }

                    MsgServerToClient.Type.LlmFunctionCall -> {

                        val functionCallData =
                            JSON_INSTANCE.decodeFromJsonElement<LLMFunctionCallData>(msg.data)

                        callbacks.onLLMFunctionCall(functionCallData)

                        val handler = functionCallHandlers[functionCallData.functionName]

                        if (handler != null) {
                            val activeConnection = connection

                            handler.handleFunctionCall(functionCallData) { resultData ->

                                thread.runOnThread {
                                    if (activeConnection == connection) {
                                        sendMessage(
                                            MsgClientToServer.LlmFunctionCallResult(
                                                msgId = msg.id ?: UUID.randomUUID().toString(),
                                                data = LLMFunctionCallResult(
                                                    functionName = functionCallData.functionName,
                                                    toolCallID = functionCallData.toolCallID,
                                                    arguments = functionCallData.args,
                                                    result = JSON_INSTANCE.encodeToJsonElement<Value>(
                                                        resultData
                                                    )
                                                )
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    MsgServerToClient.Type.BotLlmSearchResponse -> {
                        callbacks.onBotLLMSearchResponse(JSON_INSTANCE.decodeFromJsonElement(msg.data))
                    }

                    else -> {
                        Log.w(TAG, "Unexpected message type '${msg.type}'")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while handling VoiceMessage", e)
            }
        }
    }

    private inner class Connection {
        val ready = Promise<Unit, RTVIError>(thread)
    }

    private var connection: Connection? = null

    init {
        transport.initialize(transportCtx)
    }

    /**
     * Initialize local media devices such as camera and microphone.
     *
     * @return A Future, representing the asynchronous result of this operation.
     */
    fun initDevices(): Future<Unit, RTVIError> = transport.initDevices()

    fun startBot(startBotParams: APIRequest): Future<ConnectParams, RTVIError> =
        thread.runOnThreadReturningFuture {

            when (transport.state()) {
                TransportState.Authorizing,
                TransportState.Connecting,
                TransportState.Connected,
                TransportState.Ready -> return@runOnThreadReturningFuture resolvedPromiseErr(
                    thread,
                    RTVIError.InvalidState(
                        expected = TransportState.Initialized,
                        actual = transport.state()
                    )
                )

                else -> {
                    // Continue
                }
            }

            transport.setState(TransportState.Authorizing)

            val postResult = post(
                thread = thread,
                url = startBotParams.endpoint,
                body = JSON_INSTANCE.encodeToString(startBotParams.requestData)
                    .toByteArray() // Needed to avoid adding charset (which PCC doesn't support)
                    .toRequestBody("application/json".toMediaType()),
                customHeaders = startBotParams.headers.toList(),
                timeoutMs = startBotParams.timeoutMs
            )

            postResult.mapError<RTVIError> { RTVIError.HttpError(it) }.chain {
                try {
                    resolvedPromiseOk(thread, transport.deserializeConnectParams(it))
                } catch (e: Exception) {
                    resolvedPromiseErr(thread, RTVIError.ExceptionThrown(e))
                }
            }.withCallback {
                transport.setState(
                    if (it.ok) {
                        TransportState.Authorized
                    } else {
                        TransportState.Disconnected
                    }
                )
            }
        }

    /**
     * Initiate an RTVI session, connecting to the backend.
     */
    fun connect(transportParams: ConnectParams): Future<Unit, RTVIError> =
        thread.runOnThreadReturningFuture {

            if (connection != null) {
                return@runOnThreadReturningFuture resolvedPromiseErr(
                    thread,
                    RTVIError.PreviousConnectionStillActive
                )
            }

            connection = Connection()
            return@runOnThreadReturningFuture transport.connect(transportParams)
        }

    /**
     * Performs bot start request and connection in a single operation.
     *
     * This convenience method combines `startBot()` and `connect()` into a single call,
     * handling the complete flow from authentication to established connection.
     */
    fun startBotAndConnect(startBotParams: APIRequest): Future<Unit, RTVIError> =
        startBot(startBotParams).chain { connect(it) }

    /**
     * Disconnect an active RTVI session.
     *
     * @return A Future, representing the asynchronous result of this operation.
     */
    fun disconnect(): Future<Unit, RTVIError> {
        return transport.disconnect()
    }

    /**
     * Directly send a message to the bot via the transport.
     */
    private fun sendMessage(msg: MsgClientToServer) = transport.sendMessage(msg)

    /**
     * Sends a one-way message to the bot without expecting a response.
     *
     * Use this method to send fire-and-forget messages or notifications to the bot.
     */
    fun sendClientMessage(msgType: String, data: Value = Value.Null): Future<Unit, RTVIError> =
        sendMessage(
            MsgClientToServer.ClientMessage(
                id = UUID.randomUUID().toString(),
                msgType = msgType,
                data = JSON_INSTANCE.encodeToJsonElement(data)
            )
        )

    /**
     * Sends a request message to the bot and waits for a response.
     *
     * Use this method for request-response communication patterns with the bot.
     */
    fun sendClientRequest(
        msgType: String,
        data: Value = Value.Null
    ): Future<DataMessage, RTVIError> = thread.runOnThreadReturningFuture {

        val idUuid = UUID.randomUUID()
        val id = idUuid.toString()

        val future = responseWaiters.waitFor(id)

        sendMessage(
            MsgClientToServer.ClientMessage(
                id = id,
                msgType = msgType,
                data = JSON_INSTANCE.encodeToJsonElement<Value>(data)
            )
        )
            .withErrorCallback { responseWaiters.reject(id, it) }
            .chain { future }
            .mapToResult { catchExceptions { JSON_INSTANCE.decodeFromJsonElement(it) } }
    }

    /**
     * Appends a message to the bot's LLM conversation context.
     *
     * This method programmatically adds a message to the Large Language Model's conversation
     * history, allowing you to inject user context, assistant responses, or other relevant
     * information that will influence the bot's subsequent responses.
     *
     * The context message becomes part of the LLM's memory for the current session and will be
     * considered when generating future responses.
     *
     * Note: this method is deprecated. Use sendText() instead.
     */
    @Deprecated("appendToContext() is deprecated. Use sendText() instead.")
    fun appendToContext(
        message: LLMContextMessage
    ): Future<AppendToContextResultData, RTVIError> = thread.runOnThreadReturningFuture {
        val idUuid = UUID.randomUUID()
        val id = idUuid.toString()

        val future = responseWaiters.waitFor(id)

        sendMessage(MsgClientToServer.AppendToContext(message))
            .withErrorCallback { responseWaiters.reject(id, it) }
            .chain { future }
            .mapToResult { catchExceptions { JSON_INSTANCE.decodeFromJsonElement(it) } }
    }

    /**
     * Appends the specified message to the active conversation with the bot.
     *
     * The bot's response may be controlled using the values in `SendTextOptions`.
     */
    fun sendText(
        content: String,
        options: SendTextOptions = SendTextOptions()
    ): Future<Unit, RTVIError> {
        return sendMessage(MsgClientToServer.SendText(
            content = content,
            options = options
        ))
    }

    /**
     * Registers a function call handler for a specific function name.
     *
     * When the bot calls a function with the specified name, the registered callback
     * will be invoked instead of the delegate's `onLLMFunctionCall` method.
     */
    fun registerFunctionCallHandler(
        functionName: String,
        callback: LLMFunctionCallHandler
    ) = thread.runOnThread {
        functionCallHandlers[functionName] = callback
    }

    /**
     * Unregisters a function call handler for a specific function name.
     */
    fun unregisterFunctionCallHandler(
        functionName: String,
    ) = thread.runOnThread {
        functionCallHandlers.remove(functionName)
    }

    /**
     * Unregisters all function call handlers.
     */
    fun unregisterFunctionCallHandler() = thread.runOnThread {
        functionCallHandlers.clear()
    }

    /**
     * Sends a disconnect signal to the bot while maintaining the transport connection.
     *
     * This method instructs the bot to gracefully end the current conversation session
     * and clean up its internal state, but keeps the underlying transport connection
     * (WebRTC, WebSocket, etc.) active. This is different from `disconnect()` which
     * closes the entire connection.
     */
    fun disconnectBot() = thread.runOnThread {
        sendMessage(MsgClientToServer.DisconnectBot()).logError(TAG, "disconnectBot")
    }

    /**
     * The current state of the session.
     */
    val state
        get() = transport.state()

    /**
     * Returns a list of available audio input devices.
     */
    fun getAllMics() = transport.getAllMics()

    /**
     * Returns a list of available video input devices.
     */
    fun getAllCams() = transport.getAllCams()

    /**
     * Returns the selected audio input device.
     */
    val selectedMic
        get() = transport.selectedMic()

    /**
     * Returns the selected video input device.
     */
    val selectedCam
        get() = transport.selectedCam()

    /**
     * Use the specified audio input device.
     *
     * @return A Future, representing the asynchronous result of this operation.
     */
    fun updateMic(micId: MediaDeviceId) = transport.updateMic(micId)

    /**
     * Use the specified video input device.
     *
     * @return A Future, representing the asynchronous result of this operation.
     */
    fun updateCam(camId: MediaDeviceId) = transport.updateCam(camId)

    /**
     * Enables or disables the audio input device.
     *
     * @return A Future, representing the asynchronous result of this operation.
     */
    fun enableMic(enable: Boolean) = transport.enableMic(enable)

    /**
     * Enables or disables the video input device.
     *
     * @return A Future, representing the asynchronous result of this operation.
     */
    fun enableCam(enable: Boolean) = transport.enableCam(enable)

    /**
     * Returns true if the microphone is enabled, false otherwise.
     */
    val isMicEnabled
        get() = transport.isMicEnabled()

    /**
     * Returns true if the camera is enabled, false otherwise.
     */
    val isCamEnabled
        get() = transport.isCamEnabled()

    /**
     * Returns a list of participant media tracks.
     */
    val tracks
        get() = transport.tracks()

    /**
     * Destroys this PipecatClient and cleans up any allocated resources.
     */
    fun release() {
        thread.assertCurrent()
        responseWaiters.clearAll()
        transport.release()
    }
}