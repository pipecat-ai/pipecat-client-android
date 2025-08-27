package ai.pipecat.client.utils

import ai.pipecat.client.result.Future
import ai.pipecat.client.result.PipecatError
import ai.pipecat.client.result.Promise
import android.util.Log
import kotlinx.serialization.json.JsonElement

internal class ResponseWaiters(private val thread: ThreadRef) {

    companion object {
        private const val TAG = "ResponseWaiters"
    }

    private val awaitingServerResponse = mutableMapOf<String, Promise<JsonElement, PipecatError>>()

    fun waitFor(id: String): Future<JsonElement, PipecatError> {
        thread.assertCurrent()
        val promise = Promise<JsonElement, PipecatError>(thread)
        awaitingServerResponse[id] = promise
        return promise
    }

    private fun removePromise(id: String): Promise<JsonElement, PipecatError>? {
        thread.assertCurrent()

        if (id == "END") {
            return null
        }

        val promise = awaitingServerResponse.remove(id)

        if (promise == null) {
            Log.e(TAG, "Received response for unknown ID $id")
        }

        return promise
    }

    fun resolve(id: String, data: JsonElement) {
        removePromise(id)?.resolveOk(data)
    }

    fun reject(id: String, error: PipecatError) {
        removePromise(id)?.resolveErr(error)
    }

    fun clearAll() {
        thread.assertCurrent()

        awaitingServerResponse.values.forEach { it.resolveErr(PipecatError.OperationCancelled) }
        awaitingServerResponse.clear()
    }
}