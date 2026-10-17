package ai.pipecat.client

import ai.pipecat.client.result.Future
import ai.pipecat.client.result.RTVIError
import ai.pipecat.client.result.Result
import ai.pipecat.client.result.resolvedPromiseErr
import ai.pipecat.client.result.resolvedPromiseOk
import ai.pipecat.client.transport.MsgClientToServer
import ai.pipecat.client.transport.Transport
import ai.pipecat.client.transport.TransportContext
import ai.pipecat.client.types.APIRequest
import ai.pipecat.client.types.MediaDeviceId
import ai.pipecat.client.types.MediaDeviceInfo
import ai.pipecat.client.types.Tracks
import ai.pipecat.client.types.TransportState
import ai.pipecat.client.types.Value
import android.os.Looper
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PipecatClientStartBotTest {

    private val server = MockWebServer()
    private val transport = FakeTransport()
    private lateinit var client: PipecatClient<FakeTransport, String>

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                val response = MockResponse().setBody(body)
                return if (SLOW in body) response.setHeadersDelay(10, TimeUnit.SECONDS) else response
            }
        }
        server.start()
        client = PipecatClient(
            transport,
            PipecatClientOptions(
                callbacks = object : PipecatEventCallbacks() {
                    override fun onBackendError(message: String) {}
                }
            )
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun startBotAndConnectConnectsWithTheParamsFromTheServer() {
        val result = client.startBotAndConnect(startBotRequest()).awaitResult()

        assertEquals(Result.Ok(Unit), result)
        assertEquals(listOf(connectParams(FIRST)), transport.connectCalls)
    }

    @Test
    fun disconnectWhileStartingBotResolvesWithoutWaitingForTheServer() {
        val connectResult = client.startBotAndConnect(startBotRequest(SLOW))

        client.disconnect()

        assertEquals(
            Result.Err(RTVIError.OperationCancelled),
            connectResult.awaitResult(timeoutMs = 1_000)
        )
    }

    @Test
    fun staleStartBotResponseDoesNotAffectANewAttempt() {
        val firstResult = client.startBotAndConnect(startBotRequest(FIRST))
        client.disconnect()

        val secondResult = client.startBotAndConnect(startBotRequest(SECOND)).awaitResult()

        assertEquals(Result.Err(RTVIError.OperationCancelled), firstResult.awaitResult())
        assertEquals(Result.Ok(Unit), secondResult)
        assertEquals(listOf(connectParams(SECOND)), transport.connectCalls)
    }

    @Test
    fun disconnectWhileStartingBotPreventsTheConnection() {
        val connectResult = client.startBotAndConnect(startBotRequest())

        val disconnectResult = client.disconnect().awaitResult()

        assertEquals(Result.Ok(Unit), disconnectResult)
        assertEquals(Result.Err(RTVIError.OperationCancelled), connectResult.awaitResult())
        assertEquals(emptyList<String>(), transport.connectCalls)
        assertEquals(TransportState.Disconnected, transport.state())
    }

    @Test
    fun disconnectAfterStartBotSucceedsWithoutATransportConnection() {
        client.startBot(startBotRequest()).awaitResult()

        val result = client.disconnect().awaitResult()

        assertEquals(Result.Ok(Unit), result)
        assertEquals(TransportState.Disconnected, transport.state())
        assertEquals(0, transport.disconnectCalls)
    }

    @Test
    fun releaseWhileStartingBotPreventsTheConnection() {
        val connectResult = client.startBotAndConnect(startBotRequest())

        client.release()

        assertEquals(Result.Err(RTVIError.OperationCancelled), connectResult.awaitResult())
        assertEquals(emptyList<String>(), transport.connectCalls)
    }

    private fun startBotRequest(attempt: String = FIRST) = APIRequest(
        endpoint = server.url("/start").toString(),
        requestData = Value.Object("attempt" to Value.Str(attempt))
    )

    private fun connectParams(attempt: String) = """{"attempt":"$attempt"}"""

    private fun <V> Future<V, RTVIError>.awaitResult(timeoutMs: Long = 5_000): Result<V, RTVIError> {
        var result: Result<V, RTVIError>? = null
        withCallback { result = it }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (result == null && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        return result ?: throw AssertionError("Future did not complete")
    }

    private class FakeTransport : Transport<String>() {

        private lateinit var context: TransportContext
        private var state = TransportState.Disconnected

        val connectCalls = mutableListOf<String>()
        var disconnectCalls = 0

        override fun initialize(ctx: TransportContext) {
            context = ctx
        }

        override fun deserializeConnectParams(json: String, startBotRequest: APIRequest) = json

        override fun initDevices(): Future<Unit, RTVIError> = resolvedPromiseOk(context.thread, Unit)

        override fun release() {}

        override fun connect(transportParams: String): Future<Unit, RTVIError> {
            connectCalls += transportParams
            state = TransportState.Connected
            return resolvedPromiseOk(context.thread, Unit)
        }

        override fun disconnect(): Future<Unit, RTVIError> {
            disconnectCalls++
            return resolvedPromiseErr(context.thread, RTVIError.TransportNotInitialized)
        }

        override fun getAllMics(): Future<List<MediaDeviceInfo>, RTVIError> = unused()
        override fun getAllCams(): Future<List<MediaDeviceInfo>, RTVIError> = unused()
        override fun updateMic(micId: MediaDeviceId): Future<Unit, RTVIError> = unused()
        override fun updateCam(camId: MediaDeviceId): Future<Unit, RTVIError> = unused()
        override fun selectedMic(): MediaDeviceInfo? = unused()
        override fun selectedCam(): MediaDeviceInfo? = unused()
        override fun enableMic(enable: Boolean): Future<Unit, RTVIError> = unused()
        override fun enableCam(enable: Boolean): Future<Unit, RTVIError> = unused()
        override fun isCamEnabled(): Boolean = unused()
        override fun isMicEnabled(): Boolean = unused()
        override fun sendMessage(message: MsgClientToServer): Future<Unit, RTVIError> = unused()
        override fun tracks(): Tracks = unused()

        override fun state() = state

        override fun setState(state: TransportState) {
            this.state = state
        }

        private fun unused(): Nothing = throw UnsupportedOperationException()
    }

    private companion object {
        const val FIRST = "first"
        const val SECOND = "second"
        const val SLOW = "slow"
    }
}
