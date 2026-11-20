package ai.pipecat.client.support

import ai.pipecat.client.PipecatClient
import ai.pipecat.client.PipecatClientOptions
import ai.pipecat.client.RTVI_PROTOCOL_VERSION
import ai.pipecat.client.result.Future
import ai.pipecat.client.result.RTVIError
import ai.pipecat.client.result.Result
import android.os.Looper
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * A test of a client on a [TransportStub], with its callbacks recorded. The client runs on the
 * test's thread, Robolectric's main looper, which a test runs with [idle].
 */
@RunWith(RobolectricTestRunner::class)
abstract class ClientTestCase {

    protected lateinit var transport: TransportStub
    protected lateinit var callbacks: RecordingCallbacks
    protected lateinit var client: PipecatClient<TransportStub, Unit>

    @Before
    fun setUpClientTestCase() {
        setUpClient()
    }

    protected fun setUpClient(fileUploadEndpoint: String? = null) {
        transport = TransportStub()
        callbacks = RecordingCallbacks()
        client = PipecatClient(
            transport,
            PipecatClientOptions(callbacks = callbacks, fileUploadEndpoint = fileUploadEndpoint)
        )
    }

    /**
     * Runs what the client posted to its thread, such as a future's callbacks.
     */
    protected fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    /**
     * Waits for a future's result, running the client's thread meanwhile, such as while an HTTP
     * request completes on another thread.
     */
    protected fun <V> await(future: Future<V, RTVIError>): Result<V, RTVIError> {
        var result: Result<V, RTVIError>? = null
        future.withCallback { result = it }
        val deadline = System.currentTimeMillis() + 5000
        while (result == null && System.currentTimeMillis() < deadline) {
            idle()
            Thread.sleep(5)
        }
        return result ?: throw AssertionError("the future didn't complete")
    }

    /**
     * Receives a message, and runs what it starts.
     */
    protected fun receive(message: JsonElement) {
        transport.receive(message)
        idle()
    }

    /**
     * Receives a message of a type, with its data if it has any.
     */
    protected fun receive(type: String, data: JsonElement? = null) {
        receive(buildJsonObject {
            put("label", "rtvi-ai")
            put("type", type)
            data?.let { put("data", it) }
        })
    }

    protected fun becomeReady(version: String = RTVI_PROTOCOL_VERSION) {
        receive(buildJsonObject {
            put("label", "rtvi-ai")
            put("type", "bot-ready")
            put("id", "msg_1")
            put("data", buildJsonObject { put("version", version) })
        })
        callbacks.calls.clear()
    }
}
