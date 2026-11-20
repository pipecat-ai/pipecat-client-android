package ai.pipecat.client

import ai.pipecat.client.result.RTVIError
import ai.pipecat.client.result.Result
import ai.pipecat.client.support.ClientTestCase
import ai.pipecat.client.support.SchemaValidator
import ai.pipecat.client.support.carries
import ai.pipecat.client.support.same
import ai.pipecat.client.transport.MsgClientToServer
import ai.pipecat.client.types.DataMessage
import ai.pipecat.client.types.LLMFunctionCallHandler
import ai.pipecat.client.types.SendTextOptions
import ai.pipecat.client.types.Value
import ai.pipecat.client.utils.JSON_INSTANCE
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks that the client speaks the RTVI protocol as its document describes it: it hands every
 * message the server sends to the app, with all of the message's data, and builds every message
 * it sends as the document's schema says.
 */
class RTVIDocumentTests : ClientTestCase() {

    companion object {
        /** Server messages the client doesn't hand to the app, and why. */
        val ignored = mapOf(
            "bot-audio-level" to "not handled yet",
            "bot-interrupted" to "not handled yet",
            "bot-llm-marker" to "not handled yet",
            "llm-raw-text" to "not handled yet",
            "stt-raw-text" to "not handled yet",
            "system-log" to "not handled yet",
            "tts-raw-text" to "not handled yet",
            "ui-command" to "it drives the elements of a web page, which Android has no equivalent of yet",
            "ui-job-group" to "it reports jobs that UI commands started, which Android doesn't handle",
            "user-audio-level" to "not handled yet",
            "user-input" to "not handled yet",
            "user-llm-text" to "not handled yet",
            "user-mute-started" to "not handled yet",
            "user-mute-stopped" to "not handled yet",
            "vad-user-started-speaking" to "not handled yet",
            "vad-user-stopped-speaking" to "not handled yet",
        )

        /** Server messages that reach the app without all of their data, and why. */
        val incomplete = mapOf(
            "error" to "onBackendError() takes only the error's text, without whether it's fatal",
        )

        /** Server messages answering a request: they settle it. */
        val answers = setOf("server-response", "error-response")

        /** Message types the client knows that the document doesn't have, and why. */
        val notInDocument = mapOf(
            "append-to-context" to "deprecated; servers no longer handle it",
            "append-to-context-result" to "deprecated; servers no longer send it",
            "bot-llm-search-response" to "sent only by Pipecat's Google RTVI observer, outside the protocol",
            "tts-text" to "removed from the protocol; servers no longer send it",
        )

        /** Client messages the client doesn't send, and why. */
        val notSent = mapOf(
            "dtmf" to "not supported yet",
            "raw-audio" to "the transport carries the user's audio",
            "raw-audio-batch" to "the transport carries the user's audio",
            "send-file" to "not supported yet",
            "ui-cancel-job-group" to "Android doesn't handle UI commands",
            "ui-event" to "Android doesn't handle UI commands",
            "ui-snapshot" to "Android doesn't handle UI commands",
        )

        /** The message types the client's `Type` objects name. */
        fun knownMessageTypes(): Set<String> {
            val transport = File("src/main/java/ai/pipecat/client/transport")
            val constant = Regex("""const val \w+ = "([^"]+)"""")
            return listOf("MsgServerToClient.kt", "MsgClientToServer.kt").flatMap { file ->
                val source = File(transport, file).readText()
                val body = source.substringAfter("object Type {").substringBefore("}")
                constant.findAll(body).map { it.groupValues[1] }.toList()
            }.toSet()
        }
    }

    private val document = RTVIDocument.load(RTVI_PROTOCOL_VERSION)

    private val delivered
        get() = document.serverTypes.filter { it !in ignored && it !in answers }

    private fun receiveExample(type: String, adding: Map<String, JsonElement> = emptyMap()) {
        val payload = document.example(type).jsonObject.toMutableMap()
        val data = payload["data"]
        if (data is JsonObject && adding.isNotEmpty()) {
            payload["data"] = JsonObject(adding + data)
        }
        receive(JsonObject(payload))
    }

    /** Whether a call the message brought has all of its data. */
    private fun carries(type: String): Boolean {
        val data = document.example(type).jsonObject["data"]
            ?: return callbacks.calls.isNotEmpty()
        return callbacks.calls.any { it.data?.carries(data) ?: false }
    }

    // Messages from the server

    @Test
    fun serverMessagesReachTheAppOrAreIgnoredOnPurpose() {
        val unexpected = mutableListOf<String>()
        for (type in document.serverTypes.filter { it !in answers }) {
            setUpClient()
            receiveExample(type)
            val reached = callbacks.calls.isNotEmpty()
            if (reached == (type in ignored)) {
                unexpected += if (reached) "$type reaches the app, but is ignored" else "$type doesn't reach the app"
            }
        }
        assertEquals(emptyList<String>(), unexpected)
    }

    @Test
    fun serverMessagesReachTheAppWithTheirData() {
        val unexpected = mutableListOf<String>()
        for (type in delivered) {
            setUpClient()
            receiveExample(type)
            if (!carries(type)) {
                unexpected += type
            }
        }
        assertEquals(incomplete.keys.sorted(), unexpected.sorted())
    }

    @Test
    fun serverMessagesWithAFieldTheClientDoesNotKnowReachTheApp() {
        val unexpected = mutableListOf<String>()
        for (type in delivered.filter { document.example(it).jsonObject["data"] is JsonObject }) {
            setUpClient()
            receiveExample(type, adding = mapOf("x_unknown" to JsonPrimitive(1)))
            if (callbacks.calls.isEmpty()) {
                unexpected += type
            }
        }
        assertEquals(emptyList<String>(), unexpected)
    }

    @Test
    fun theMessageTypesTheClientKnowsAreTheDocuments() {
        val documented = (document.serverTypes + document.clientTypes).toSet()
        assertEquals(notInDocument.keys.sorted(), (knownMessageTypes() - documented).sorted())
    }

    // Messages to the server

    @Test
    fun messagesTheClientSendsMatchTheirSchemas() {
        becomeReady()
        client.registerFunctionCallHandler("get_weather", LLMFunctionCallHandler { _, onResult ->
            onResult(Value.Object("sunny" to Value.Bool(true)))
        })

        transport.sendMessage(
            MsgClientToServer.ClientReady(
                rtviVersion = RTVI_PROTOCOL_VERSION,
                library = "pipecat-client-android",
                libraryVersion = "1.2.0",
                platform = "Android",
                platformVersion = "34"
            )
        )
        client.sendClientMessage("set-volume", Value.Object("level" to Value.Number(5.0)))
        client.sendText("What's the weather?", SendTextOptions(runImmediately = true))
        receive(buildJsonObject {
            put("label", "rtvi-ai")
            put("type", "llm-function-call")
            put("id", "msg_2")
            put("data", buildJsonObject {
                put("function_name", "get_weather")
                put("tool_call_id", "call_1")
                put("args", buildJsonObject { put("city", "Lisbon") })
            })
        })
        client.disconnectBot()
        idle()

        val violations = transport.sent.flatMap {
            document.violations(JSON_INSTANCE.encodeToJsonElement(MsgClientToServer.serializer(), it))
        }
        assertEquals(emptyList<String>(), violations)
        val sent = transport.sent.map { it.type }.toSet()
        assertEquals(notSent.keys.sorted(), document.clientTypes.filter { it !in sent }.sorted())
    }

    @Test
    fun aServerResponseSettlesTheRequestWithItsId() {
        becomeReady()
        val request = Value.Object("city" to Value.Str("Lisbon"))

        var answer: Result<DataMessage, RTVIError>? = null
        client.sendClientRequest("get-weather", request).withCallback { answer = it }
        idle()
        assertEquals(1, transport.sent.size)
        val example = document.example("server-response").jsonObject
        val data = example.getValue("data").jsonObject
        receive(JsonObject(example + mapOf(
            "id" to JsonPrimitive(transport.sent[0].id),
            "data" to JsonObject(data + ("t" to JsonPrimitive("get-weather"))),
        )))
        val ok = answer as? Result.Ok
        assertTrue("the server-response doesn't settle the request: $answer", ok != null)
        assertTrue(same(ok!!.value.data, data["d"] ?: JsonNull))

        var failure: Result<DataMessage, RTVIError>? = null
        client.sendClientRequest("get-weather", request).withCallback { failure = it }
        idle()
        assertEquals(2, transport.sent.size)
        val error = document.example("error-response").jsonObject
        receive(JsonObject(error + ("id" to JsonPrimitive(transport.sent[1].id))))
        assertTrue("the error-response doesn't fail the request: $failure", failure is Result.Err)
    }
}

/**
 * The RTVI protocol's AsyncAPI document the client speaks, in `spec/`.
 */
class RTVIDocument(private val document: JsonObject) {

    companion object {
        fun load(version: String) = RTVIDocument(
            Json.parseToJsonElement(File("../spec/$version/rtvi.asyncapi.json").readText()).jsonObject
        )
    }

    private val components = document.getValue("components").jsonObject
    private val byKey = components.getValue("messages").jsonObject
    private val messages = byKey.values.associateBy { it.jsonObject.getValue("name").jsonPrimitive.content }
    private val validator = SchemaValidator(components.getValue("schemas").jsonObject)

    private fun types(operation: String) =
        (document.getValue("operations").jsonObject.getValue(operation).jsonObject.getValue("messages") as JsonArray)
            .map { ref ->
                val key = ref.jsonObject.getValue("\$ref").jsonPrimitive.content.substringAfterLast("/")
                byKey.getValue(key).jsonObject.getValue("name").jsonPrimitive.content
            }

    val serverTypes = types("sendServerMessages")
    val clientTypes = types("receiveClientMessages")

    /** The first example of a message, as the server or client sends it. */
    fun example(type: String): JsonElement =
        ((messages[type]?.jsonObject?.get("examples") as? JsonArray)?.firstOrNull()?.jsonObject?.get("payload"))
            ?: JsonNull

    /** What is wrong with a message the client sends, by the document's schema. */
    fun violations(message: JsonElement): List<String> {
        val type = (message.jsonObject["type"] as? JsonPrimitive)?.content
        val payload = messages[type]?.jsonObject?.get("payload")
        if (type == null || type !in clientTypes || payload == null) {
            return listOf("${type ?: "a message without a type"} isn't a message a client sends")
        }
        return validator.violations(message, payload).map { "$type $it" }
    }
}
