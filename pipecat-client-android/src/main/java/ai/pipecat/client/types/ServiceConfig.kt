package ai.pipecat.client.types

import kotlinx.serialization.Serializable

@Serializable
data class ServiceConfig(
    val service: String,
    val options: List<Option>
)