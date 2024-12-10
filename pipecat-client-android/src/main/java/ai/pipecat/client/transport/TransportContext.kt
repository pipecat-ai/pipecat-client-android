package ai.pipecat.client.transport

import ai.pipecat.client.RTVIClientOptions
import ai.pipecat.client.RTVIEventCallbacks
import ai.pipecat.client.utils.ThreadRef

/**
 * Context for an RTVI transport.
 */
interface TransportContext {

    val options: RTVIClientOptions
    val callbacks: RTVIEventCallbacks
    val thread: ThreadRef

    /**
     * Invoked by the transport when an RTVI message is received.
     */
    fun onMessage(msg: MsgServerToClient)
}