package ai.pipecat.client

import ai.pipecat.client.support.ClientTestCase
import ai.pipecat.client.types.ErrorData
import ai.pipecat.client.utils.JSON_INSTANCE
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerMessageTests : ClientTestCase() {

    @Test
    fun anErrorReachesTheAppWithWhetherItIsFatal() {
        receive("error", buildJsonObject {
            put("error", "The LLM service failed")
            put("fatal", true)
        })

        assertEquals(listOf("onBackendError", "onBotError"), callbacks.calls.map { it.name })
        assertEquals(
            ErrorData(error = "The LLM service failed", fatal = true),
            JSON_INSTANCE.decodeFromJsonElement(ErrorData.serializer(), callbacks.calls[1].data!!)
        )
    }

    @Test
    fun anErrorWithoutFatalStillReachesOnBackendError() {
        receive("error", buildJsonObject { put("error", "The LLM service failed") })

        assertEquals(listOf("onBackendError"), callbacks.calls.map { it.name })
    }

    @Test
    fun theLocalAudioLevelReachesTheDeprecatedCallback() {
        val heard = mutableListOf<Float>()
        val callbacks = object : PipecatEventCallbacks() {
            override fun onBackendError(message: String) {}

            @Deprecated("Overrides the deprecated callback")
            override fun onUserAudioLevel(level: Float) {
                heard.add(level)
            }
        }

        callbacks.onLocalAudioLevel(0.5f)

        assertEquals(listOf(0.5f), heard)
    }
}
