package ai.pipecat.client.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Streaming bot output tokens/chunks.
 *
 * Example:
 * {"text":"your","spoken":true,"aggregated_by":"word"}
 */
@Serializable
data class BotOutputData(
    val text: String,
    val spoken: Boolean,
    @SerialName("aggregated_by")
    val aggregatedBy: String
)