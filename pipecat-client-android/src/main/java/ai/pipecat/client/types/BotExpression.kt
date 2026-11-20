package ai.pipecat.client.types

/**
 * A sound or pause the bot performed in expressive conversation (alpha, RTVI 2.3.0+), reported
 * as it was performed.
 */
sealed interface BotExpressionData {

    /**
     * A sound, such as `"laugh"`, by Pipecat's universal name for it, the same whichever TTS
     * service performs it.
     */
    data class Sound(val name: String) : BotExpressionData

    /**
     * A pause, of a number of milliseconds.
     */
    data class Pause(val durationMs: Int) : BotExpressionData

    companion object {
        /**
         * The expression a bot output reports, if it reports one.
         */
        internal fun from(output: BotOutputData): BotExpressionData? = when (output.outputType) {
            BotOutputType.ExpressionSound -> Sound(output.text)
            BotOutputType.ExpressionPause -> output.text.toIntOrNull()?.let { Pause(it) }
            else -> null
        }
    }
}

/**
 * The bot's delivery changed, in expressive conversation (alpha, RTVI 2.3.0+). Reported only when
 * it changes: a stressed word brings `"emphasis"` and the word after it takes it away again.
 */
data class BotDeliveryUpdatedData(

    /**
     * The delivery of the words the bot is speaking now, such as `["warm"]`; empty for the
     * voice's baseline, and null once it stops speaking.
     */
    val delivery: List<String>?,

    /**
     * The delivery before the change, or null when it wasn't speaking.
     */
    val previous: List<String>?,
)
