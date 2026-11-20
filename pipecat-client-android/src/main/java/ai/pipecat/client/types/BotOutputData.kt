@file:OptIn(ExperimentalSerializationApi::class)

package ai.pipecat.client.types

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

/**
 * What the bot says, as it says it: a sentence, a word as it's spoken, or text of another type.
 *
 * Example (Protocol 2.3.0):
 * {"text":"your","output_type":"word","will_be_spoken":true,"spoken_status":"in-progress","segment_id":1}
 */
@Serializable(with = BotOutputDataSerializer::class)
@KeepGeneratedSerializer
data class BotOutputData(
    val text: String,

    /**
     * What form the text is in, such as [BotOutputType.Sentence], [BotOutputType.Word], or a type
     * of the bot's own, such as `"code"`. Bots older than protocol 2.3.0 send it as
     * `aggregated_by`, which fills it in.
     */
    @SerialName("output_type")
    val outputType: BotOutputType,

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
    val segmentId: Int? = null,

    /**
     * Protocol 2.3.0: The delivery of the text just spoken, by its universal names, such as
     * `["warm", "emphasis"]`, empty for the voice's baseline. Present in expressive conversation
     * (alpha).
     */
    val delivery: List<String>? = null,
) {
    @Deprecated("Use the constructor taking outputType instead. This constructor will be removed in a future version.")
    constructor(
        text: String,
        aggregatedBy: String,
        spoken: Boolean? = null,
        willBeSpoken: Boolean? = null,
        spokenStatus: SpokenStatus? = null,
        spokenProgress: BotOutputProgressData? = null,
        segmentId: Int? = null,
    ) : this(
        text = text,
        outputType = BotOutputType(aggregatedBy),
        spoken = spoken,
        willBeSpoken = willBeSpoken,
        spokenStatus = spokenStatus,
        spokenProgress = spokenProgress,
        segmentId = segmentId,
    )

    /** Deprecated (Protocol 2.3.0). Use [outputType] instead, which it's always the same as. */
    @Deprecated("Use outputType instead. This field will be removed in a future version.")
    val aggregatedBy: String
        get() = outputType.value

    /** Protocol 2.0.0: Lifecycle stage of a spoken segment. */
    @Serializable
    @JvmInline
    value class SpokenStatus(val value: String) {
        companion object {
            val New = SpokenStatus("new")
            val InProgress = SpokenStatus("in-progress")
            val Completed = SpokenStatus("completed")
        }
    }
}

/**
 * Reads the output type from the deprecated `text_type` or `aggregated_by` when a bot older than
 * protocol 2.3.0 sends no `output_type`.
 */
internal object BotOutputDataSerializer :
    JsonTransformingSerializer<BotOutputData>(BotOutputData.generatedSerializer()) {

    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element !is JsonObject || "output_type" in element) {
            return element
        }
        val outputType = element["text_type"] ?: element["aggregated_by"] ?: return element
        return JsonObject(element + ("output_type" to outputType))
    }
}
