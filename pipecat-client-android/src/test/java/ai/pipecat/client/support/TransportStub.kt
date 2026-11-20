package ai.pipecat.client.support

import ai.pipecat.client.result.Future
import ai.pipecat.client.result.RTVIError
import ai.pipecat.client.result.resolvedPromiseOk
import ai.pipecat.client.transport.MsgClientToServer
import ai.pipecat.client.transport.MsgServerToClient
import ai.pipecat.client.transport.Transport
import ai.pipecat.client.transport.TransportContext
import ai.pipecat.client.types.APIRequest
import ai.pipecat.client.types.MediaDeviceId
import ai.pipecat.client.types.MediaDeviceInfo
import ai.pipecat.client.types.ParticipantTracks
import ai.pipecat.client.types.Tracks
import ai.pipecat.client.types.TransportState
import ai.pipecat.client.utils.JSON_INSTANCE
import kotlinx.serialization.json.JsonElement

/**
 * A transport with no connection: it records the messages the client sends, and hands the
 * client the messages a test receives.
 */
class TransportStub : Transport<Unit>() {

    private lateinit var ctx: TransportContext
    private var currentState = TransportState.Disconnected

    val sent = mutableListOf<MsgClientToServer>()

    /**
     * Hands the client a message, as transports do: decoded from its JSON.
     */
    fun receive(message: JsonElement) {
        ctx.onMessage(JSON_INSTANCE.decodeFromJsonElement(MsgServerToClient.serializer(), message))
    }

    /**
     * Ends the connection, as transports do when it drops.
     */
    fun endConnection() {
        ctx.onConnectionEnd()
    }

    private fun <V> ok(value: V): Future<V, RTVIError> = resolvedPromiseOk(ctx.thread, value)

    override fun initialize(ctx: TransportContext) {
        this.ctx = ctx
    }

    override fun deserializeConnectParams(json: String, startBotRequest: APIRequest) = Unit

    override fun initDevices() = ok(Unit)

    override fun release() {}

    override fun connect(transportParams: Unit): Future<Unit, RTVIError> {
        currentState = TransportState.Connected
        return ok(Unit)
    }

    override fun disconnect(): Future<Unit, RTVIError> {
        currentState = TransportState.Disconnected
        return ok(Unit)
    }

    override fun getAllMics() = ok(emptyList<MediaDeviceInfo>())

    override fun getAllCams() = ok(emptyList<MediaDeviceInfo>())

    override fun updateMic(micId: MediaDeviceId) = ok(Unit)

    override fun updateCam(camId: MediaDeviceId) = ok(Unit)

    override fun selectedMic(): MediaDeviceInfo? = null

    override fun selectedCam(): MediaDeviceInfo? = null

    override fun enableMic(enable: Boolean) = ok(Unit)

    override fun enableCam(enable: Boolean) = ok(Unit)

    override fun isCamEnabled() = false

    override fun isMicEnabled() = false

    override fun sendMessage(message: MsgClientToServer): Future<Unit, RTVIError> {
        sent.add(message)
        return ok(Unit)
    }

    override fun state() = currentState

    override fun setState(state: TransportState) {
        currentState = state
    }

    override fun tracks() = Tracks(local = ParticipantTracks(audio = null, video = null), bot = null)
}
