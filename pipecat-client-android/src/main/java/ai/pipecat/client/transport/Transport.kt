package ai.pipecat.client.transport

import ai.pipecat.client.result.Future
import ai.pipecat.client.result.PipecatError
import ai.pipecat.client.types.MediaDeviceId
import ai.pipecat.client.types.MediaDeviceInfo
import ai.pipecat.client.types.Tracks
import ai.pipecat.client.types.TransportState
import ai.pipecat.client.types.Value

/**
 * An RTVI transport.
 */
abstract class Transport {

    abstract fun initialize(ctx: TransportContext)

    abstract fun initDevices(): Future<Unit, PipecatError>
    abstract fun release()

    abstract fun connect(transportParams: Value): Future<Unit, PipecatError>

    abstract fun disconnect(): Future<Unit, PipecatError>

    abstract fun getAllMics(): Future<List<MediaDeviceInfo>, PipecatError>
    abstract fun getAllCams(): Future<List<MediaDeviceInfo>, PipecatError>

    abstract fun updateMic(micId: MediaDeviceId): Future<Unit, PipecatError>
    abstract fun updateCam(camId: MediaDeviceId): Future<Unit, PipecatError>

    abstract fun selectedMic(): MediaDeviceInfo?
    abstract fun selectedCam(): MediaDeviceInfo?

    abstract fun enableMic(enable: Boolean): Future<Unit, PipecatError>
    abstract fun enableCam(enable: Boolean): Future<Unit, PipecatError>

    abstract fun isCamEnabled(): Boolean
    abstract fun isMicEnabled(): Boolean

    abstract fun sendMessage(message: MsgClientToServer): Future<Unit, PipecatError>

    abstract fun state(): TransportState
    abstract fun setState(state: TransportState)

    abstract fun tracks(): Tracks
}