package ai.pipecat.client

import ai.pipecat.client.transport.Transport

/**
 * Configuration options when instantiating a [PipecatClient].
 */
data class PipecatClientOptions(

    /**
     * Transport class for media streaming.
     */
    val transport: Transport,

    /**
     * Event callbacks.
     */
    val callbacks: PipecatEventCallbacks,

    /**
     * Enable the user mic input.
     *
     * Defaults to true.
     */
    val enableMic: Boolean = true,

    /**
     * Enable user cam input.
     *
     * Defaults to false.
     */
    val enableCam: Boolean = false,
)