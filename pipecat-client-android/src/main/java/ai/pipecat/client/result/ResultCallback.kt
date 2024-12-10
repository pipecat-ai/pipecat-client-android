package ai.pipecat.client.result

/**
 * A callback function receiving a Result value.
 */
fun interface ResultCallback<V, E> {
    fun onResult(value: Result<V, E>)
}