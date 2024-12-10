package ai.pipecat.client.types

import ai.pipecat.client.helper.RTVIClientHelper

internal class RegisteredHelper(
    val helper: RTVIClientHelper,
    val supportedMessages: Set<String>
)