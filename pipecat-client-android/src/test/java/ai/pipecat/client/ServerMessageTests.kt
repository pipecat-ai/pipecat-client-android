package ai.pipecat.client

import ai.pipecat.client.support.ClientTestCase
import ai.pipecat.client.types.ErrorData
import ai.pipecat.client.types.Participant
import ai.pipecat.client.types.ParticipantId
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
    @Suppress("DEPRECATION")
    fun theTransportsLocalLevelReachesTheClientUserAudioLevel() {
        val heard = mutableListOf<Float>()
        val callbacks = object : PipecatEventCallbacks() {
            override fun onBackendError(message: String) {}

            override fun onClientUserAudioLevel(level: Float) {
                heard.add(level)
            }
        }

        callbacks.onUserAudioLevel(0.5f)

        assertEquals(listOf(0.5f), heard)
    }

    @Test
    @Suppress("DEPRECATION")
    fun theTransportsRemoteLevelReachesTheClientBotAudioLevel() {
        val heard = mutableListOf<Float>()
        val callbacks = object : PipecatEventCallbacks() {
            override fun onBackendError(message: String) {}

            override fun onClientBotAudioLevel(level: Float) {
                heard.add(level)
            }
        }

        callbacks.onRemoteAudioLevel(0.6f, Participant(ParticipantId("bot"), "Bot", local = false))

        assertEquals(listOf(0.6f), heard)
    }
}
