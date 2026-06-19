package ai.pipecat.client.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Protocol 2.0.0: Word-level TTS progress through a spoken segment. */
@Serializable
data class BotOutputProgressData(
    @SerialName("accumulated_text")
    val accumulatedText: String,
    @SerialName("remaining_text")
    val remainingText: String
)

/**
 * Streaming bot output tokens/chunks.
 *
 * Example (Protocol 2.0.0):
 * {"text":"your","aggregated_by":"word","will_be_spoken":true,"spoken_status":"in-progress","segment_id":1}
 */
@Serializable
data class BotOutputData(
    val text: String,
    @SerialName("aggregated_by")
    val aggregatedBy: String,

    /** Deprecated (Protocol 1.4.x). Use [willBeSpoken] instead. */
    @Deprecated("Use willBeSpoken instead. This field will be removed in a future version.")
    val spoken: Boolean? = null,

    /** Protocol 2.0.0: Whether this text will be spoken by TTS. */
    @SerialName("will_be_spoken")
    val willBeSpoken: Boolean? = null,

    /** Protocol 2.0.0: Current lifecycle stage of the spoken segment. */
    @SerialName("spoken_status")
    val spokenStatus: SpokenStatus? = null,

    /** Protocol 2.0.0: Word-level progress through the spoken segment. */
    @SerialName("spoken_progress")
    val spokenProgress: BotOutputProgressData? = null,

    /** Protocol 2.0.0: Correlates progress events for the same spoken segment. */
    @SerialName("segment_id")
    val segmentId: Int? = null
) {
    /** Protocol 2.0.0: Lifecycle stage of a spoken segment. */
    @Serializable
    enum class SpokenStatus {
        @SerialName("new") New,
        @SerialName("in-progress") InProgress,
        @SerialName("completed") Completed
    }
}