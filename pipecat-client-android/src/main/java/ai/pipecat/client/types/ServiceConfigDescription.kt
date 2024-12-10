package ai.pipecat.client.types

import kotlinx.serialization.Serializable

@Serializable
data class ServiceConfigDescription(
    val name: String,
    val options: List<OptionDescription>
)