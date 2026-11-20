# Unreleased

### Added

- Added support for RTVI protocol 2.3.0: the client reports `"2.3.0"` in `client-ready`. The protocol's types are generated from Pipecat's AsyncAPI document of the protocol, vendored in `spec/`, by `scripts/rtvi_protocol.py`.
- `BotOutputData` has `willBeSpoken`, `spokenStatus`, `spokenProgress` and `segmentId`, enabling word-level TTS progress tracking; `outputType`, which bots older than 2.3.0 fill in through `aggregated_by`; and `delivery`.
- Function calls the bot reports with `llm-function-call-in-progress` now run the handler registered with `registerFunctionCallHandler()`, which answers the bot with its result. New callbacks `onLLMFunctionCallStarted`, `onLLMFunctionCallInProgress` and `onLLMFunctionCallStopped` report a call's lifecycle.
- New callbacks for the rest of the bot's messages: `onBotInterrupted`, `onUserMuteStarted`, `onUserMuteStopped`, `onUserInput`, `onUserLLMText`, `onVADUserStartedSpeaking`, `onVADUserStoppedSpeaking`, `onUserAudioLevel(data: AudioLevelData)`, `onBotAudioLevel`, `onSystemLog`, `onBotLLMMarker`, `onSTTRawText`, `onLLMRawText` and `onTTSRawText`, each with an empty default implementation.
- `onBotError` reports an error the bot sends with whether it's fatal, after `onBackendError`.
- Expressive conversation (alpha): `onBotExpression` reports a sound, such as a laugh, or a pause the bot performed, and `onBotDeliveryUpdated` reports when the bot's delivery, such as `["warm"]`, changes. Both are derived from `bot-output`.
- `BotReadyData` has `capabilities`, what the bot does in the session (RTVI 2.2.0+), and `PipecatMetrics` exposes all of its metrics: `processing`, `ttfb`, `ttfa`, `ttfat`, `tokens`, `sttUsage` and `characters`.
- Added `sendFile()`, which sends the bot's LLM a file with text that goes with it (RTVI 2.2.0+). Build the file with `RTVIFile(data, format, name)` or `RTVIFile(url, format, name)`. A file too large for the transport to send in a message is uploaded first, to the `fileUploadUrl` the start-bot response advertises or else the new `PipecatClientOptions.fileUploadEndpoint`, and sent by its URL. Transports report how large a message they send with the new `Transport.maxMessageSize`, 64 KiB by default.
- Added `sendDTMF()`, which sends DTMF keypresses such as `"123#"` (RTVI 2.0.0+).
- Added `onLocalAudioLevel`, the level of the locally captured audio, which the transport measures. Its default implementation calls the deprecated `onUserAudioLevel(level: Float)`.
- The client now logs a warning when the bot is running an older RTVI protocol version than the client.

### Changed

- ⚠️ `BotOutputData.SpokenStatus` takes values the client doesn't name, so a `bot-output` with a status of the bot's own still reaches `onBotOutput`. It's a value class of constants instead of an enum: a `when` over it needs an `else` branch, and it has no `name`, `ordinal` or `values()`.
- ⚠️ `PipecatMetrics.ttfb` is a list of `TTFBMetricsData` instead of `PipecatMetricsData`, which is now an alias of `ProcessingMetricsData`. They have the same fields.
- ⚠️ `BotOutputData`'s constructor takes `outputType` after `text`, so `copy()` and its `componentN()` functions change accordingly.
- `UserLLMText`, `LLMRawText`, `TTSRawText` and `SystemLog` are aliases of `TextMessageData`, the data every text message carries.

### Deprecated

- `onUserTranscript` is deprecated: use `onUserInput` to show what the user said, or `onSTTRawText` to inspect what the STT produced.
- `onBotLLMText` is deprecated: use `onBotOutput` to show the bot's text, or `onLLMRawText` to inspect what the LLM produced. `MsgServerToClient.Data.BotLLMTextData`, which only it takes, is deprecated in favour of `TextMessageData`.
- `onBotTTSText` is deprecated: use `onBotOutput` to show the bot's text, or `onTTSRawText` to inspect what the TTS produced. `MsgServerToClient.Data.BotTTSTextData`, which only it takes, is deprecated in favour of `TextMessageData`.
- `onBotTranscript` is deprecated: use `onBotOutput`.
- `onLLMFunctionCall` is deprecated: use `registerFunctionCallHandler()` to answer a function call, or `onLLMFunctionCallInProgress` to follow it. Only bots that send the deprecated `llm-function-call` invoke it.
- `onUserAudioLevel(level: Float)` is deprecated in favour of `onLocalAudioLevel`. Transports call `onLocalAudioLevel` once they're updated to this version.
- `BotOutputData.aggregatedBy` is deprecated in favour of `outputType`, and the constructor taking `aggregatedBy` in favour of the one taking `outputType`. `BotOutputData.spoken` is deprecated in favour of `willBeSpoken`.
- `MsgServerToClient.Data.Error` is deprecated in favour of `ErrorData` and `ErrorResponseData`.

# 1.1.0

- Update signature of `Transport.deserializeConnectParams()` to also take the startBot `APIRequest`.

- Implemented support for the new `botOutput` RTVI message. This message is now the preferred
  way of communicating a holistic view of what the bot "says". It includes a `spoken` field,
  indicating whether the text has been spoken along with a field, `aggregated_by`, to indicate what
  the text represents. By default, with TTS services that support word-by-word output, you can
  expect two `agggregated_by` values for `botOutput` events: `"sentence"` and `"word"`. All
  sentence events are guaranteed to be in order, while word events come in at the time of being
  spoken. This allows for building karaoke-like UIs where the sentence is displayed and each word
  is highlighted as it's spoken.  This event also provides continuity across bot output even when
  the TTS is skipped or does not exist. And if your pipeline takes advantage of customizing how
  the LLM text is aggregated, you can handle custom `aggregated_by` fields, like `"code"` or
  `"address"` or `"url"`, allowing the server to do the parsing.

# 1.0.2

- Added new `sendText()` method to support the new RTVI `send-text` event. The method
  takes a string, along with an optional set of options to control whether the bot
  should respond immediately and/or whether the bot should respond with audio (vs. text only).
  Note: This is a replacement for the current `appendToContext()` method.

- `appendToContext()` has been deprecated in favor of the new `sendText()` method. 

# 1.0.1

- Changed content type `startBot()` POST request for compatibility reasons 

# 1.0.0

- Added:
  - Methods:
    - `startBot()`
    - `startBotAndConnect()`
    - `appendToContext()`
    - `sendClientMessage()`
    - `sendClientRequest()`
    - `registerFunctionCallHandler()`
    - `unregisterFunctionCallHandler()`
    - `unregisterAllFunctionCallHandlers()`
    - `disconnectBot()`
  - Callbacks:
    - `onBotLlmSearchResponse()`
    - `onLLMFunctionCall()`
- Removed:
  - Helper classes (`LLMHelper`, `RTVIClientHelper`) and associated methods (`registerHelper`, `unregisterHelper`)
  - Server configuration, including `getConfig()`, `updateConfig()`, `describeConfig()` and associated types
  - Actions, including `action()`, `describeActions()` and associated types
  - `expiry` field on client and transports
  - `onStorageItemStored()` callback
  - `RTVIClientParams`
  - `sendWithResponse()`
  - `sendMessage()`
- Changed:
  - `connect()` is modified to no longer make a POST request to the backend, but rather pass the
    specified `Value` straight to the `Transport`. See `startBotAndConnect()` for a helper method
    which also includes the POST request.
  - `Transport` now passed directly into `PipecatClientOptions` rather than using factory
  - `onBotReady()` now receives a `BotReadyData` parameter
- Renamed:
  - `RTVIClient` -> `PipecatClient`
  - `RTVIEventCallbacks` -> `PipecatEventCallbacks`
  - `RTVIClientOptions` -> `PipecatClientOptions`
  - `onPipecatMetrics()` -> `onMetrics()`

# 0.3.4

- Added `onServerMessage` callback

# 0.3.3

- Made `timestamp` and `userId` fields optional in `Transcript`

# 0.3.0

- Project renamed to `pipecat-client-android`

# 0.2.1

- Added callbacks:
  - onBotLLMStarted
  - onBotLLMStopped
  - onBotTTSStarted
  - onBotTTSStopped

# 0.2.0

- Renamed:
  - `VoiceClient` to `RTVIClient`
  - `VoiceClientOptions` to `RTVIClientOptions`
  - `VoiceEventCallbacks` to `RTVIEventCallbacks`
  - `VoiceError` to `RTVIError`
  - `VoiceException` to `RTVIException`
  - `VoiceClientHelper` to `RTVIClientHelper`
  - `RegisteredVoiceClient` to `RegisteredRTVIClient`
  - `FailedToFetchAuthBundle` to `HttpError`
- `RTVIClient()` constructor parameter changes
  - `options` is now mandatory
  - `baseUrl` has been moved to `options.params.baseUrl`
  - `baseUrl` and `endpoints` are now separate, and the endpoint names are appended to the `baseUrl`
- Moved `RTVIClientOptions.config` to `RTVIClientOptions.params.config`
- Moved `RTVIClientOptions.customHeaders` to `RTVIClientOptions.params.headers`
- Moved `RTVIClientOptions.customBodyParams` to `RTVIClientOptions.params.requestData`
- `TransportState` changes
  - Removed `Idle` state, replaced with `Disconnected`
  - Added `Disconnecting` state
- Added callbacks
  - `onBotLLMText()`
  - `onBotTTSText()`
  - `onStorageItemStored()`
