package ai.pipecat.client

import ai.pipecat.client.result.RTVIError
import ai.pipecat.client.result.Result
import ai.pipecat.client.support.ClientTestCase
import ai.pipecat.client.support.same
import ai.pipecat.client.types.APIRequest
import ai.pipecat.client.types.RTVIFile
import ai.pipecat.client.types.Value
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendFileAndDTMFTests : ClientTestCase() {

    private val server = MockWebServer()

    @After
    fun stopServer() {
        server.shutdown()
    }

    private val sent: List<JsonElement>
        get() = transport.sent.map { it.data ?: JsonNull }

    private fun assertSent(expected: List<JsonElement>) {
        assertEquals(expected.size, sent.size)
        expected.zip(sent).forEach { (want, got) -> assertTrue("sent $got, not $want", same(want, got)) }
    }

    private inline fun <reified E : RTVIError> assertFails(result: Result<*, RTVIError>) {
        assertTrue("$result doesn't fail with ${E::class.simpleName}", (result as? Result.Err)?.error is E)
    }

    /** A file too large to send in a message of 300 bytes. */
    private val largeFile = RTVIFile(data = ByteArray(300) { 7 }, format = "image/png", name = "photo.png")

    private val uploaded = MockResponse().setBody(
        """{"name": "photo.png", "format": "image/png", "source": {"type": "url", "url": "pipecat:abc"}}"""
    )

    private fun JsonElement.file() = jsonObject.getValue("file").jsonObject

    @Test
    fun aFileIsSentWithItsContent() {
        becomeReady()
        val result = await(
            client.sendFile(
                file = RTVIFile(data = "hello".toByteArray(), format = "text/plain", name = "hello.txt"),
                content = "What does it say?"
            )
        )

        assertTrue(result is Result.Ok)
        assertSent(listOf(buildJsonObject {
            put("content", "What does it say?")
            put("file", buildJsonObject {
                put("format", "text/plain")
                put("name", "hello.txt")
                put("source", buildJsonObject {
                    put("type", "bytes")
                    put("bytes", "aGVsbG8=")
                })
            })
        }))
    }

    @Test
    fun aFileIsSentWithItsUrl() {
        becomeReady()
        await(
            client.sendFile(
                file = RTVIFile(url = "https://example.com/photo.png", format = "image/png"),
                content = "What's this?"
            )
        )

        assertTrue(same(
            buildJsonObject {
                put("type", "url")
                put("url", "https://example.com/photo.png")
            },
            sent.single().file().getValue("source")
        ))
    }

    @Test
    fun aBotThatIsNotReadyDoesNotTakeFiles() {
        val result = await(
            client.sendFile(file = RTVIFile(url = "https://example.com/a.png", format = "image/png"), content = "")
        )

        assertFails<RTVIError.InvalidState>(result)
        assertEquals(0, transport.sent.size)
    }

    @Test
    fun aBotOlderThan2_2DoesNotTakeFiles() {
        becomeReady(version = "2.1.0")
        val result = await(
            client.sendFile(file = RTVIFile(url = "https://example.com/a.png", format = "image/png"), content = "")
        )

        assertFails<RTVIError.UnsupportedFeature>(result)
        assertEquals(0, transport.sent.size)
    }

    @Test
    fun aFileTooLargeForAMessageIsUploadedAndSentByItsUrl() {
        server.enqueue(uploaded)
        setUpClient(fileUploadEndpoint = server.url("/files").toString())
        transport.maxMessageSize = 300
        becomeReady()

        val result = await(client.sendFile(file = largeFile, content = "What's this?"))

        assertTrue("$result", result is Result.Ok)
        val upload = server.takeRequest()
        assertEquals("POST", upload.method)
        assertEquals("/files", upload.path)
        assertTrue(upload.getHeader("Content-Type")!!.startsWith("multipart/form-data; boundary="))
        val body = upload.body.readByteArray()
        assertTrue(String(body).contains("""Content-Disposition: form-data; name="file"; filename="photo.png""""))
        assertTrue(String(body, Charsets.ISO_8859_1).contains(String(ByteArray(300) { 7 }, Charsets.ISO_8859_1)))
        assertTrue(same(
            buildJsonObject {
                put("format", "image/png")
                put("name", "photo.png")
                put("source", buildJsonObject {
                    put("type", "url")
                    put("url", "pipecat:abc")
                })
            },
            sent.single().file()
        ))
    }

    @Test
    fun theUploadRouteTheStartBotResponseAdvertisesIsUsed() {
        server.enqueue(MockResponse().setBody("""{"fileUploadUrl": "/sessions/s1/files"}"""))
        server.enqueue(uploaded)
        setUpClient(fileUploadEndpoint = "https://elsewhere.example.com/files")
        transport.maxMessageSize = 300

        await(
            client.startBot(
                APIRequest(
                    endpoint = server.url("/start").toString(),
                    requestData = Value.Object(),
                    headers = mapOf("Authorization" to "Bearer key")
                )
            )
        )
        becomeReady()
        await(client.sendFile(file = largeFile, content = "What's this?"))

        server.takeRequest()
        val upload = server.takeRequest()
        assertEquals("/sessions/s1/files", upload.path)
        assertEquals("Bearer key", upload.getHeader("Authorization"))
        assertEquals(
            "pipecat:abc",
            sent.single().file().getValue("source").jsonObject.getValue("url").jsonPrimitive.content
        )
    }

    @Test
    fun aFileTooLargeForAMessageNeedsSomewhereToBeUploaded() {
        transport.maxMessageSize = 300
        becomeReady()

        assertFails<RTVIError.OtherError>(await(client.sendFile(file = largeFile, content = "What's this?")))
        assertEquals(0, transport.sent.size)
    }

    @Test
    fun aFailedUploadSendsNothing() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("File upload is disabled"))
        setUpClient(fileUploadEndpoint = server.url("/files").toString())
        transport.maxMessageSize = 300
        becomeReady()

        assertFails<RTVIError.HttpError>(await(client.sendFile(file = largeFile, content = "What's this?")))
        assertEquals(0, transport.sent.size)
    }

    @Test
    fun dtmfKeysAreSentInOneMessage() {
        becomeReady(version = "2.1.0")
        await(client.sendDTMF("12*#"))

        assertSent(listOf(buildJsonObject {
            put("buttons", buildJsonArray { listOf("1", "2", "*", "#").forEach { add(it) } })
        }))
    }

    @Test
    fun aBotOf2_0GetsAMessageAKey() {
        becomeReady(version = "2.0.0")
        await(client.sendDTMF("1#"))

        assertSent(listOf(
            buildJsonObject { put("button", "1") },
            buildJsonObject { put("button", "#") },
        ))
    }

    @Test
    fun onlyKeysAreSentAsDtmf() {
        becomeReady()
        for (dtmf in listOf("", "12a", "1 2")) {
            assertFails<RTVIError.OtherError>(await(client.sendDTMF(dtmf)))
        }
        assertEquals(0, transport.sent.size)
    }

    @Test
    fun aBotOlderThan2_0DoesNotTakeDtmf() {
        becomeReady(version = "1.1.0")

        assertFails<RTVIError.UnsupportedFeature>(await(client.sendDTMF("1")))
    }
}
