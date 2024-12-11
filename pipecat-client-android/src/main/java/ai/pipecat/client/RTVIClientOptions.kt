package ai.pipecat.client

import ai.pipecat.client.types.ServiceConfig
import ai.pipecat.client.types.ServiceRegistration
import ai.pipecat.client.types.Value

/**
 * Configuration options when instantiating a [RTVIClient].
 */
data class RTVIClientOptions(

    /**
     * Connection parameters.
     */
    val params: RTVIClientParams,

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
     * A list of services to use on the backend.
     */
    val services: List<ServiceRegistration>? = null,
)