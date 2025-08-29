package ai.pipecat.client

/**
 * Configuration options when instantiating a [PipecatClient].
 */
data class PipecatClientOptions(

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