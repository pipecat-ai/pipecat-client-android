package ai.pipecat.client.support

import ai.pipecat.client.PipecatClient
import ai.pipecat.client.PipecatClientOptions
import ai.pipecat.client.RTVI_PROTOCOL_VERSION
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

    protected fun setUpClient() {
        transport = TransportStub()
        callbacks = RecordingCallbacks()
        client = PipecatClient(transport, PipecatClientOptions(callbacks = callbacks))
    }

    /**
     * Runs what the client posted to its thread, such as a future's callbacks.
     */
    protected fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
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
