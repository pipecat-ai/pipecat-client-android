package ai.pipecat.client.result

class RTVIException(
    val error: PipecatError
) : Exception(error.description, error.exception) {

    companion object {
        internal fun <E> from(e: E) = RTVIException(
            (e as? PipecatError) ?: PipecatError.OtherError(e.toString())
        )
    }
}