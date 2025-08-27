package ai.pipecat.client.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class DataMessage(
    @SerialName("t")
    val msgType: String,
    @SerialName("d")
    val data: JsonElement
)