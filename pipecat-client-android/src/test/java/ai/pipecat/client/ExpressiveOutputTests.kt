package ai.pipecat.client

import ai.pipecat.client.support.ClientTestCase
import ai.pipecat.client.types.BotDeliveryUpdatedData
import ai.pipecat.client.types.BotExpressionData
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpressiveOutputTests : ClientTestCase() {

    private fun receiveOutput(text: String, type: String, delivery: List<String>? = null) {
        receive("bot-output", buildJsonObject {
            put("text", text)
            put("output_type", type)
            delivery?.let { names -> put("delivery", JsonArray(names.map { JsonPrimitive(it) })) }
        })
    }

    private fun values(name: String) = callbacks.calls.filter { it.name == name }.map { it.value }

    @Test
    fun soundsAndPausesReachTheAppAsExpressions() {
        receiveOutput("laugh", type = "expression_sound")
        receiveOutput("300", type = "expression_pause")
        receiveOutput("Hello there.", type = "sentence")

        assertEquals(
            listOf(BotExpressionData.Sound("laugh"), BotExpressionData.Pause(durationMs = 300)),
            values("onBotExpression")
        )
    }

    @Test
    fun thePausesDurationMustBeANumber() {
        receiveOutput("long", type = "expression_pause")

        assertEquals(emptyList<Any>(), values("onBotExpression"))
        assertEquals(1, values("onBotOutput").size)
    }

    @Test
    fun theDeliveryIsReportedWhenItChanges() {
        receiveOutput("Oh", type = "word", delivery = listOf("warm"))
        receiveOutput("that's", type = "word", delivery = listOf("warm"))
        receiveOutput("wonderful", type = "word", delivery = listOf("warm", "emphasis"))
        receiveOutput("news", type = "word", delivery = listOf("emphasis", "warm"))
        receiveOutput("Oh, that's wonderful news.", type = "sentence")
        receiveOutput("really", type = "word", delivery = emptyList())
        receive("bot-stopped-speaking")

        assertEquals(
            listOf(
                BotDeliveryUpdatedData(delivery = listOf("warm"), previous = null),
                BotDeliveryUpdatedData(delivery = listOf("warm", "emphasis"), previous = listOf("warm")),
                BotDeliveryUpdatedData(delivery = emptyList(), previous = listOf("warm", "emphasis")),
                BotDeliveryUpdatedData(delivery = null, previous = emptyList()),
            ),
            values("onBotDeliveryUpdated")
        )
    }

    @Test
    fun noDeliveryIsReportedWhenTheBotStopsSpeakingWithoutOne() {
        receive("bot-stopped-speaking")

        assertEquals(emptyList<Any>(), values("onBotDeliveryUpdated"))
    }

    @Test
    fun theDeliveryEndsWhenTheClientDisconnects() {
        receiveOutput("Oh", type = "word", delivery = listOf("warm"))
        client.disconnect()
        idle()

        assertEquals(
            BotDeliveryUpdatedData(delivery = null, previous = listOf("warm")),
            values("onBotDeliveryUpdated").last()
        )
    }

    @Test
    fun theDeliveryEndsWhenTheConnectionDrops() {
        receiveOutput("Oh", type = "word", delivery = listOf("warm"))
        transport.endConnection()

        assertEquals(
            BotDeliveryUpdatedData(delivery = null, previous = listOf("warm")),
            values("onBotDeliveryUpdated").last()
        )
    }
}
