package ai.pipecat.client

import ai.pipecat.client.types.BotOutputData
import ai.pipecat.client.types.BotOutputProgressData
import ai.pipecat.client.types.BotOutputType
import ai.pipecat.client.utils.JSON_INSTANCE
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BotOutputDataTests {

    private fun decode(json: String) = JSON_INSTANCE.decodeFromString(BotOutputData.serializer(), json)

    @Test
    @Suppress("DEPRECATION")
    fun outputTypeFillsAggregatedBy() {
        val data = decode("""{"text": "Hi.", "output_type": "sentence"}""")
        assertEquals(BotOutputType.Sentence, data.outputType)
        assertEquals("sentence", data.aggregatedBy)
    }

    @Test
    @Suppress("DEPRECATION")
    fun aggregatedByFillsOutputTypeForOlderBots() {
        val data = decode("""{"text": "Hi.", "aggregated_by": "word", "spoken": true}""")
        assertEquals(BotOutputType.Word, data.outputType)
        assertEquals("word", data.aggregatedBy)
        assertEquals(true, data.spoken)
    }

    @Test
    fun textTypeFillsOutputType() {
        val data = decode("""{"text": "Hi.", "text_type": "sentence"}""")
        assertEquals(BotOutputType.Sentence, data.outputType)
    }

    @Test
    fun typesAndStatusesTheClientDoesNotNameDecode() {
        val data = decode("""{"text": "let x = 1", "output_type": "code", "spoken_status": "paused"}""")
        assertEquals(BotOutputType("code"), data.outputType)
        assertEquals(BotOutputData.SpokenStatus("paused"), data.spokenStatus)
    }

    @Test
    fun theProtocolFieldsDecode() {
        val data = decode(
            """
            {"text": "Hi there.", "output_type": "sentence", "segment_id": 3, "will_be_spoken": true,
             "spoken_status": "in-progress", "delivery": ["warm"],
             "spoken_progress": {"accumulated_text": "Hi", "remaining_text": " there."}}
            """
        )
        assertEquals(3, data.segmentId)
        assertEquals(true, data.willBeSpoken)
        assertEquals(BotOutputData.SpokenStatus.InProgress, data.spokenStatus)
        assertEquals(listOf("warm"), data.delivery)
        assertEquals(BotOutputProgressData(accumulatedText = "Hi", remainingText = " there."), data.spokenProgress)
    }

    @Test
    fun aMessageWithoutAnyOutputTypeDoesNotDecode() {
        assertThrows(SerializationException::class.java) { decode("""{"text": "Hi."}""") }
    }

    @Test
    @Suppress("DEPRECATION")
    fun theDeprecatedConstructorSetsTheOutputType() {
        val data = BotOutputData(text = "Hi.", aggregatedBy = "sentence", spoken = true)
        assertEquals(BotOutputData(text = "Hi.", outputType = BotOutputType.Sentence, spoken = true), data)
    }
}
