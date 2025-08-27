package ai.pipecat.client.transport

import ai.pipecat.client.PipecatClientOptions
import ai.pipecat.client.PipecatEventCallbacks
import ai.pipecat.client.utils.ThreadRef

/**
 * Context for an RTVI transport.
 */
interface TransportContext {

    val options: PipecatClientOptions
    val callbacks: PipecatEventCallbacks
    val thread: ThreadRef

    /**
     * Invoked by the transport after the connection has terminated.
     */
    fun onConnectionEnd()

    /**
     * Invoked by the transport when an RTVI message is received.
     */
    fun onMessage(msg: MsgServerToClient)
}