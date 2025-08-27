package ai.pipecat.client.types

import kotlinx.serialization.Serializable

@Serializable
data class AppendToContextResultData(
    val result: Value? = null
)