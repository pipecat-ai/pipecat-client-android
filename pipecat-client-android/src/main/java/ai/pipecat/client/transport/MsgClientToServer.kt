package ai.pipecat.client.transport

import ai.pipecat.client.types.DTMFInputData
import ai.pipecat.client.types.DataMessage
import ai.pipecat.client.types.KeypadEntry
import ai.pipecat.client.types.LLMContextMessage
import ai.pipecat.client.types.LLMFunctionCallResult
import ai.pipecat.client.types.RTVIFile
import ai.pipecat.client.types.SendFileData
import ai.pipecat.client.types.SendFileOptions
import ai.pipecat.client.types.SendTextOptions
import ai.pipecat.client.utils.JSON_INSTANCE
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import java.util.UUID

/**
 * An RTVI control message sent to the backend.
 */
@Serializable
@ConsistentCopyVisibility
data class MsgClientToServer private constructor(
    val id: String,
    val label: String,
    val type: String,
    val data: JsonElement?
) {

    constructor(
        type: String,
        data: JsonElement?,
        id: String = UUID.randomUUID().toString()
    ) : this(
        id = id,
        label = "rtvi-ai",
        type = type,
        data = data
    )

    object Type {
        const val ClientReady = "client-ready"
        const val DisconnectBot = "disconnect-bot"
        const val ClientMessage = "client-message"
        const val AppendToContext = "append-to-context"
        const val SendText = "send-text"
        const val LlmFunctionCallResult = "llm-function-call-result"
        const val SendFile = "send-file"
        const val Dtmf = "dtmf"
    }

    object Data {
        @Serializable
        data class ClientReady(
            val version: String,
            val about: ClientAbout
        )

        @Serializable
        data class ClientAbout(
            val library: String,
            @SerialName("library_version")
            val libraryVersion: String,
            val platform: String,
            @SerialName("platform_version")
            val platformVersion: String
        )

        @Serializable
        data class SendText(
            val content: String,
            val options: SendTextOptions
        )
    }

    companion object {
        fun ClientReady(
            rtviVersion: String,
            library: String,
            libraryVersion: String,
            platform: String,
            platformVersion: String
        ) = MsgClientToServer(
            type = Type.ClientReady,
            data = JSON_INSTANCE.encodeToJsonElement(
                Data.ClientReady.serializer(),
                Data.ClientReady(
                    version = rtviVersion,
                    about = Data.ClientAbout(
                        library = library,
                        libraryVersion = libraryVersion,
                        platform = platform,
                        platformVersion = platformVersion
                    )
                )
            )
        )

        fun ClientMessage(
            id: String,
            msgType: String,
            data: JsonElement? = null
        ) = MsgClientToServer(
            id = id,
            type = Type.ClientMessage,
            data = JSON_INSTANCE.encodeToJsonElement(
                DataMessage.serializer(),
                DataMessage(
                    msgType = msgType,
                    data = data ?: JsonNull
                )
            )
        )

        fun AppendToContext(
            msg: LLMContextMessage
        ) = MsgClientToServer(
            type = Type.AppendToContext,
            data = JSON_INSTANCE.encodeToJsonElement(LLMContextMessage.serializer(), msg)
        )

        fun SendText(
            content: String,
            options: SendTextOptions
        ) = MsgClientToServer(
            type = Type.SendText,
            data = JSON_INSTANCE.encodeToJsonElement(Data.SendText.serializer(), Data.SendText(
                content = content,
                options = options
            ))
        )

        fun SendFile(
            file: RTVIFile,
            content: String,
            options: SendFileOptions? = null
        ) = MsgClientToServer(
            type = Type.SendFile,
            data = JSON_INSTANCE.encodeToJsonElement(
                SendFileData.serializer(),
                SendFileData(content = content, file = file, options = options)
            )
        )

        /**
         * DTMF keys, for a bot of RTVI 2.1.0 or later.
         */
        fun Dtmf(buttons: List<KeypadEntry>) = MsgClientToServer(
            type = Type.Dtmf,
            data = JSON_INSTANCE.encodeToJsonElement(DTMFInputData.serializer(), DTMFInputData(buttons = buttons))
        )

        /**
         * A DTMF key, for a bot of RTVI 2.0.x, which takes one key a message.
         */
        internal fun Dtmf(button: KeypadEntry) = MsgClientToServer(
            type = Type.Dtmf,
            data = JsonObject(mapOf("button" to JsonPrimitive(button.value)))
        )

        fun DisconnectBot() = MsgClientToServer(
            type = Type.DisconnectBot,
            data = null
        )

        fun LlmFunctionCallResult(
            msgId: String,
            data: LLMFunctionCallResult
        ) = MsgClientToServer(
            id = msgId,
            type = Type.LlmFunctionCallResult,
            data = JSON_INSTANCE.encodeToJsonElement(data)
        )
    }
}