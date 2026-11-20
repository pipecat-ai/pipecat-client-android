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

    /**
     * Where [PipecatClient.sendFile] uploads a file too large to send in a message, when the
     * start-bot response doesn't advertise a `fileUploadUrl`. A relative URL is resolved against
     * the start-bot endpoint.
     */
    val fileUploadEndpoint: String? = null,
)