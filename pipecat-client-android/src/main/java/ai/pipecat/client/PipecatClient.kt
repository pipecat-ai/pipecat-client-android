package ai.pipecat.client

import ai.pipecat.client.result.Future
import ai.pipecat.client.result.Promise
import ai.pipecat.client.result.RTVIError
import ai.pipecat.client.result.Result
import ai.pipecat.client.result.catchExceptions
import ai.pipecat.client.result.resolvedPromiseErr
import ai.pipecat.client.result.resolvedPromiseOk
import ai.pipecat.client.transport.MsgClientToServer
import ai.pipecat.client.transport.MsgServerToClient
import ai.pipecat.client.transport.Transport
import ai.pipecat.client.transport.TransportContext
import ai.pipecat.client.types.APIRequest
import ai.pipecat.client.types.AppendToContextResultData
import ai.pipecat.client.types.AudioLevelData
import ai.pipecat.client.types.BotDeliveryUpdatedData
import ai.pipecat.client.types.BotExpressionData
import ai.pipecat.client.types.BotOutputData
import ai.pipecat.client.types.BotReadyData
import ai.pipecat.client.types.DataMessage
import ai.pipecat.client.types.ErrorData
import ai.pipecat.client.types.LLMContextMessage
import ai.pipecat.client.types.LLMFunctionCallData
import ai.pipecat.client.types.LLMFunctionCallHandler
import ai.pipecat.client.types.LLMFunctionCallInProgressData
import ai.pipecat.client.types.LLMFunctionCallResult
import ai.pipecat.client.types.MediaDeviceId
import ai.pipecat.client.types.FileBytes
import ai.pipecat.client.types.KeypadEntry
import ai.pipecat.client.types.RTVIFile
import ai.pipecat.client.types.SendFileOptions
import ai.pipecat.client.types.SendTextOptions
import ai.pipecat.client.types.Transcript
import ai.pipecat.client.types.TransportState
import ai.pipecat.client.types.Value
import ai.pipecat.client.utils.JSON_INSTANCE
import ai.pipecat.client.utils.ResponseWaiters
import ai.pipecat.client.utils.ThreadRef
import ai.pipecat.client.utils.post
import android.util.Base64
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

internal const val RTVI_PROTOCOL_VERSION = "2.3.0"

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
            thread.assertCurrent()
            responseWaiters.clearAll()
            connection?.ready?.resolveErr(RTVIError.OperationCancelled)
            connection = null
            botVersion = listOf(0, 0, 0)
            setBotDelivery(null)
        }

        override fun onMessage(msg: MsgServerToClient) = thread.runOnThread {

            try {
                when (msg.type) {
                    MsgServerToClient.Type.BotReady -> {

                        val data =
                            JSON_INSTANCE.decodeFromJsonElement<BotReadyData>(msg.data)

                        this@PipecatClient.transport.setState(TransportState.Ready)

                        connection?.ready?.resolveOk(Unit)

                        botVersion = data.version.split(".").map { it.toIntOrNull() ?: 0 }

                        val clientMajor = RTVI_PROTOCOL_VERSION.split(".").firstOrNull()?.toIntOrNull() ?: 0
                        if (botVersion[0] < clientMajor) {
                            Log.w(TAG, "Bot is running an older RTVI protocol version (${data.version}) than the client ($RTVI_PROTOCOL_VERSION). Some features may not be available.")
                        }

                        callbacks.onBotReady(data)
                    }

                    MsgServerToClient.Type.Error -> {
                        val data =
                            JSON_INSTANCE.decodeFromJsonElement<MsgServerToClient.Data.Error>(msg.data)
                        callbacks.onBackendError(data.error)

                        // An error without `fatal` still reaches onBackendError.
                        val errorData = catchExceptions {
                            JSON_INSTANCE.decodeFromJsonElement<ErrorData>(msg.data)
                        }
                        if (errorData is Result.Ok) {
                            callbacks.onBotError(errorData.value)
                        }
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
                        setBotDelivery(null)
                    }

                    MsgServerToClient.Type.BotLlmText -> {
                        val data: MsgServerToClient.Data.BotLLMTextData =
                            JSON_INSTANCE.decodeFromJsonElement(msg.data)

                        callbacks.onBotLLMText(data)
                    }

                    MsgServerToClient.Type.BotOutput -> {
                        val data: BotOutputData =
                            JSON_INSTANCE.decodeFromJsonElement(msg.data)

                        callbacks.onBotOutput(data)

                        BotExpressionData.from(data)?.let { callbacks.onBotExpression(it) }

                        // Only spoken words carry a delivery, as they're spoken.
                        data.delivery?.let { setBotDelivery(it) }
                    }

                    MsgServerToClient.Type.BotInterrupted -> callbacks.onBotInterrupted()

                    MsgServerToClient.Type.UserMuteStarted -> callbacks.onUserMuteStarted()
                    MsgServerToClient.Type.UserMuteStopped -> callbacks.onUserMuteStopped()

                    MsgServerToClient.Type.UserInput -> {
                        callbacks.onUserInput(JSON_INSTANCE.decodeFromJsonElement(msg.data))
                    }

                    MsgServerToClient.Type.UserLlmText -> {
                        callbacks.onUserLLMText(JSON_INSTANCE.decodeFromJsonElement(msg.data))
                    }

                    MsgServerToClient.Type.VadUserStartedSpeaking -> callbacks.onVADUserStartedSpeaking()
                    MsgServerToClient.Type.VadUserStoppedSpeaking -> callbacks.onVADUserStoppedSpeaking()

                    MsgServerToClient.Type.UserAudioLevel -> {
                        callbacks.onUserAudioLevel(JSON_INSTANCE.decodeFromJsonElement<AudioLevelData>(msg.data))
                    }

                    MsgServerToClient.Type.BotAudioLevel -> {
                        callbacks.onBotAudioLevel(JSON_INSTANCE.decodeFromJsonElement(msg.data))
                    }

                    MsgServerToClient.Type.SystemLog -> {
                        callbacks.onSystemLog(JSON_INSTANCE.decodeFromJsonElement(msg.data))
                    }

                    MsgServerToClient.Type.BotLlmMarker -> {
                        callbacks.onBotLLMMarker(JSON_INSTANCE.decodeFromJsonElement(msg.data))
                    }

                    MsgServerToClient.Type.SttRawText -> {
                        callbacks.onSTTRawText(JSON_INSTANCE.decodeFromJsonElement(msg.data))
                    }

                    MsgServerToClient.Type.LlmRawText -> {
                        callbacks.onLLMRawText(JSON_INSTANCE.decodeFromJsonElement(msg.data))
                    }

                    MsgServerToClient.Type.TtsRawText -> {
                        callbacks.onTTSRawText(JSON_INSTANCE.decodeFromJsonElement(msg.data))
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

                    MsgServerToClient.Type.LlmFunctionCallStarted -> {
                        callbacks.onLLMFunctionCallStarted(JSON_INSTANCE.decodeFromJsonElement(msg.data))
                    }

                    MsgServerToClient.Type.LlmFunctionCallInProgress -> {

                        val data =
                            JSON_INSTANCE.decodeFromJsonElement<LLMFunctionCallInProgressData>(msg.data)

                        // A handler can only run when the bot reports which function it calls.
                        if (data.functionName != null) {
                            runFunctionCallHandler(
                                msgId = msg.id,
                                functionCallData = LLMFunctionCallData(
                                    functionName = data.functionName,
                                    toolCallID = data.toolCallID,
                                    args = data.arguments?.let { JSON_INSTANCE.encodeToJsonElement(it) }
                                        ?: JsonObject(emptyMap())
                                )
                            )
                        }

                        callbacks.onLLMFunctionCallInProgress(data)
                    }

                    MsgServerToClient.Type.LlmFunctionCallStopped -> {
                        callbacks.onLLMFunctionCallStopped(JSON_INSTANCE.decodeFromJsonElement(msg.data))
                    }

                    MsgServerToClient.Type.LlmFunctionCall -> {

                        val functionCallData =
                            JSON_INSTANCE.decodeFromJsonElement<LLMFunctionCallData>(msg.data)

                        @Suppress("DEPRECATION")
                        callbacks.onLLMFunctionCall(functionCallData)

                        runFunctionCallHandler(msgId = msg.id, functionCallData = functionCallData)
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

    /**
     * The delivery of the words the bot is speaking, or null when it isn't speaking.
     */
    private var botDelivery: List<String>? = null

    /**
     * The RTVI protocol version the bot speaks, as its major, minor and patch numbers; zeros until
     * it's ready.
     */
    private var botVersion = listOf(0, 0, 0)

    /**
     * The request that started the bot, whose endpoint, headers and timeout file uploads use.
     */
    private var startBotParams: APIRequest? = null

    /**
     * The upload route the start-bot response advertised, as `fileUploadUrl`.
     */
    private var advertisedFileUploadUrl: String? = null

    /**
     * Whether the bot speaks an RTVI protocol at least as new as a major and minor version.
     */
    private fun botSupports(major: Int, minor: Int): Boolean {
        val botMajor = botVersion.getOrElse(0) { 0 }
        val botMinor = botVersion.getOrElse(1) { 0 }
        return botMajor > major || (botMajor == major && botMinor >= minor)
    }

    /**
     * Why the client can't send a message that needs the bot ready and speaking an RTVI protocol
     * at least as new as a major and minor version, or null when it can.
     */
    private fun unavailable(feature: String, major: Int, minor: Int): RTVIError? = when {
        transport.state() != TransportState.Ready ->
            RTVIError.InvalidState(expected = TransportState.Ready, actual = transport.state())

        !botSupports(major, minor) ->
            RTVIError.UnsupportedFeature(feature, "requires RTVI protocol $major.$minor.0+")

        else -> null
    }

    /**
     * Tracks the bot's delivery, reporting each change. A delivery is a set, so the same names in
     * another order are no change.
     */
    private fun setBotDelivery(delivery: List<String>?) {
        val previous = botDelivery
        if (delivery?.toSet() == previous?.toSet()) {
            return
        }
        botDelivery = delivery
        options.callbacks.onBotDeliveryUpdated(BotDeliveryUpdatedData(delivery = delivery, previous = previous))
    }

    /**
     * Runs the handler registered for the function the bot calls, which answers the bot with its
     * result.
     */
    private fun runFunctionCallHandler(msgId: String?, functionCallData: LLMFunctionCallData) {

        val handler = functionCallHandlers[functionCallData.functionName] ?: return
        val activeConnection = connection

        handler.handleFunctionCall(functionCallData) { resultData ->

            thread.runOnThread {
                if (activeConnection == connection) {
                    sendMessage(
                        MsgClientToServer.LlmFunctionCallResult(
                            msgId = msgId ?: UUID.randomUUID().toString(),
                            data = LLMFunctionCallResult(
                                functionName = functionCallData.functionName,
                                toolCallID = functionCallData.toolCallID,
                                arguments = functionCallData.args,
                                result = JSON_INSTANCE.encodeToJsonElement<Value>(resultData)
                            )
                        )
                    )
                }
            }
        }
    }

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
                    val connectParams = transport.deserializeConnectParams(it, startBotParams)
                    this.startBotParams = startBotParams
                    // Pipecat's runner and Pipecat Cloud advertise it in camelCase; snake_case is accepted too.
                    val fields = catchExceptions { JSON_INSTANCE.parseToJsonElement(it) as? JsonObject }
                        .let { result -> (result as? Result.Ok)?.value }
                    advertisedFileUploadUrl = ((fields?.get("fileUploadUrl") ?: fields?.get("file_upload_url"))
                            as? JsonPrimitive)?.takeIf { url -> url.isString }?.content
                    resolvedPromiseOk(thread, connectParams)
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
    fun disconnect(): Future<Unit, RTVIError> = thread.runOnThreadReturningFuture {
        setBotDelivery(null)
        botVersion = listOf(0, 0, 0)
        startBotParams = null
        advertisedFileUploadUrl = null
        transport.disconnect()
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
     * Sends a file to the bot, with text that goes with it, such as a question about it.
     *
     * The bot's LLM receives the file as part of the user's message. A file can be sent with its
     * content, or as a URL the bot fetches it from. A file whose message is larger than
     * [Transport.maxMessageSize] is uploaded first, to the `fileUploadUrl` the start-bot response
     * advertised or else [PipecatClientOptions.fileUploadEndpoint], and sent as the URL the upload
     * returns.
     *
     * The future fails with [RTVIError.InvalidState] when the bot isn't ready,
     * [RTVIError.UnsupportedFeature] when it speaks an RTVI protocol older than 2.2.0,
     * [RTVIError.OtherError] when the file must be uploaded and there is nowhere to upload it, and
     * [RTVIError.HttpError] when the upload fails.
     *
     * @param file The file, such as `RTVIFile(data, format, name)` or `RTVIFile(url, format, name)`.
     * @param content Text that goes with the file.
     * @param options How the bot handles the file.
     */
    fun sendFile(
        file: RTVIFile,
        content: String,
        options: SendFileOptions? = null
    ): Future<Unit, RTVIError> = thread.runOnThreadReturningFuture {

        unavailable("sendFile", major = 2, minor = 2)?.let {
            return@runOnThreadReturningFuture resolvedPromiseErr(thread, it)
        }

        val message = MsgClientToServer.SendFile(file = file, content = content, options = options)
        val bytes = file.source as? FileBytes
        val size = JSON_INSTANCE.encodeToString(MsgClientToServer.serializer(), message).toByteArray().size
        if (bytes == null || size <= transport.maxMessageSize) {
            return@runOnThreadReturningFuture sendMessage(message)
        }

        val data = try {
            Base64.decode(bytes.bytes, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            return@runOnThreadReturningFuture resolvedPromiseErr(thread, RTVIError.ExceptionThrown(e))
        }

        uploadFile(data = data, format = file.format, name = file.name).chain { uploaded ->
            sendMessage(
                MsgClientToServer.SendFile(
                    file = RTVIFile(format = file.format, source = uploaded.source, name = file.name ?: uploaded.name),
                    content = content,
                    options = options
                )
            )
        }
    }

    /**
     * Uploads a file with a multipart `POST`, which answers with the file as `send-file` takes it,
     * by its URL. The upload carries the start-bot request's headers, such as its authorization,
     * and its timeout.
     */
    private fun uploadFile(data: ByteArray, format: String, name: String?): Future<RTVIFile, RTVIError> {

        // The backend's advertisement wins over the option: only the backend can mint a session-scoped route.
        val endpoint = advertisedFileUploadUrl ?: options.fileUploadEndpoint
            ?: return resolvedPromiseErr(
                thread,
                RTVIError.OtherError(
                    "Unable to determine upload URL: the start-bot response did not advertise a fileUploadUrl " +
                            "and no fileUploadEndpoint is set in PipecatClientOptions (file uploads may be disabled " +
                            "on the server)"
                )
            )

        val startBotParams = startBotParams
        val url = startBotParams?.endpoint?.toHttpUrlOrNull()?.resolve(endpoint) ?: endpoint.toHttpUrlOrNull()
            ?: return resolvedPromiseErr(
                thread,
                RTVIError.OtherError("Unable to resolve upload URL \"$endpoint\" without a startBot endpoint")
            )

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", name ?: "uploaded_file", data.toRequestBody(format.toMediaTypeOrNull()))
            .build()

        return post(
            thread = thread,
            url = url.toString(),
            body = body,
            customHeaders = startBotParams?.headers?.toList() ?: emptyList(),
            timeoutMs = startBotParams?.timeoutMs ?: 30000
        )
            .mapError<RTVIError> { RTVIError.HttpError(it) }
            .mapToResult { catchExceptions { JSON_INSTANCE.decodeFromString(RTVIFile.serializer(), it) } }
    }

    /**
     * Sends DTMF keypresses to the bot, such as `"123#"`.
     *
     * A bot of RTVI 2.1.0 or later receives the keys in one message; a bot of 2.0.x receives a
     * message a key.
     *
     * The future fails with [RTVIError.InvalidState] when the bot isn't ready,
     * [RTVIError.OtherError] when `dtmf` is empty or has a character that isn't a key, and
     * [RTVIError.UnsupportedFeature] when the bot speaks an RTVI protocol older than 2.0.0.
     *
     * @param dtmf The keys, in order: `0`-`9`, `*` or `#`.
     */
    fun sendDTMF(dtmf: String): Future<Unit, RTVIError> = thread.runOnThreadReturningFuture {

        if (transport.state() != TransportState.Ready) {
            return@runOnThreadReturningFuture resolvedPromiseErr(
                thread,
                RTVIError.InvalidState(expected = TransportState.Ready, actual = transport.state())
            )
        }

        if (dtmf.isEmpty() || dtmf.any { it !in "0123456789*#" }) {
            return@runOnThreadReturningFuture resolvedPromiseErr(
                thread,
                RTVIError.OtherError("Invalid DTMF sequence \"$dtmf\". Only 0-9, * and # are allowed.")
            )
        }

        unavailable("DTMF", major = 2, minor = 0)?.let {
            return@runOnThreadReturningFuture resolvedPromiseErr(thread, it)
        }

        val buttons = dtmf.map { KeypadEntry(it.toString()) }
        if (botSupports(major = 2, minor = 1)) {
            sendMessage(MsgClientToServer.Dtmf(buttons))
        } else {
            buttons.drop(1).fold(sendMessage(MsgClientToServer.Dtmf(buttons[0]))) { sent, button ->
                sent.chain { sendMessage(MsgClientToServer.Dtmf(button)) }
            }
        }
    }

    /**
     * Registers a function call handler for a specific function name.
     *
     * When the bot calls a function with the specified name, the callback runs with the call's
     * arguments, and the result it's given is sent back to the bot. The bot reports a call's
     * function name and arguments only when its report level for the function allows it: in
     * Pipecat, at least `RTVIFunctionCallReportLevel.ARGUMENTS` in
     * `RTVIObserverParams.function_call_report_level`.
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