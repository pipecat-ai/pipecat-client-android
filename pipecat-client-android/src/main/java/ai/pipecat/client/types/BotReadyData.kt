package ai.pipecat.client.types

import kotlinx.serialization.Serializable

@Serializable
data class BotReadyData(
    val version: String,
    val about: Value? = null
)