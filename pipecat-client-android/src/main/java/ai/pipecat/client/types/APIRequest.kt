package ai.pipecat.client.types

data class APIRequest(
    val endpoint: String,
    val requestData: Value,
    val headers: Map<String, String> = emptyMap(),
    val timeoutMs: Long = 30000
)
