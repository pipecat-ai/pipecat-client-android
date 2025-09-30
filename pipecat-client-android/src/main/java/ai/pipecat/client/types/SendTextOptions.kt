package ai.pipecat.client.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SendTextOptions(
    @SerialName("run_immediately")
    val runImmediately: Boolean? = null,
    @SerialName("audio_response")
    val audioResponse: Boolean? = null,
)
