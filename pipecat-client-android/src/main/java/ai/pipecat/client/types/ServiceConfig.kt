package ai.pipecat.client.types

import kotlinx.serialization.Serializable

@Serializable
data class ServiceConfig(
    val service: String,
    val options: List<Option>
)

fun List<ServiceConfig>.getOptionsFor(service: String) = firstOrNull { it.service == service }?.options