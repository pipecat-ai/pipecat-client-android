package ai.pipecat.client.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LLMContextMessage(
    val role: Role,
    val content: String,
    @SerialName("run_immediately")
    val runImmediately: Boolean? = false
) {
    @Serializable
    enum class Role(val value: String) {
        @SerialName("user")
        User("user"),
        @SerialName("assistant")
        Assistant("assistant")
    }
}